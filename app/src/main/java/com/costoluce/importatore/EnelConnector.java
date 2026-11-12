package com.costoluce.importatore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental client for the non-public Enel customer-area interface described by
 * the MIT-licensed EnelGrid project (https://github.com/sathia-musso/enelgrid).
 * No Enel affiliation, API guarantee, browser bypass, or CAPTCHA/MFA avoidance.
 * Never persist response payloads, cookies, login HTML or credentials to logs.
 */
public final class EnelConnector {
    private static final String LOGIN = "https://www.enel.it/it/login";
    private static final String AUTH = "https://accounts.enel.com/samlsso";
    private static final String SAML = "https://www.enel.it/bin/samlauth";
    private static final String AGGREGATE = "https://www.enel.it/bin/areaclienti/auth/aggregateConsumption";
    private static final int MAX_RESPONSE = 2_000_000;
    private static final long MAX_SYNC_MS = 90000;
    // Short per-connection limits plus one aggregate deadline: no indefinite spinner.
    interface Progress { void step(String phase); }
    private static void report(Progress p, String phase) { if (p != null) p.step(phase); }
    private static long remaining(long deadline) throws IOException {
        long ms = (deadline - System.nanoTime()) / 1_000_000L;
        if (ms <= 0) throw new IOException("Tempo massimo di collegamento Enel superato (90 secondi). Verifica rete o autenticazione.");
        return ms;
    }
    private EnelConnector() { }

    static final class Credentials {
        final String username, password, pod, userNumber;
        Credentials(String username, String password, String pod, String userNumber) {
            this.username = username.trim();
            this.password = password;
            this.pod = pod.trim().toUpperCase(Locale.ROOT);
            this.userNumber = userNumber.trim();
        }
        void validate() throws IOException {
            if (!username.contains("@") || username.length() > 200 || password.isEmpty() || password.length() > 1000)
                throw new IOException("Inserisci email e password dell'account Enel Energia.");
            if (!pod.matches("IT[A-Z0-9]{10,18}")) throw new IOException("POD non valido: verifica il codice sulla bolletta.");
            if (!userNumber.matches("[A-Za-z0-9_-]{3,30}")) throw new IOException("Numero utente Enel non valido.");
        }
    }

    /** A month is reported independently, including months with no measurements. */
    static final class MonthUsage {
        final int year, month, days, expectedDays, hourSamples;
        final double kwh;
        final boolean future;
        final int lastMeasuredDay;
        MonthUsage(int year, int month, double kwh, int days, int expectedDays, int hourSamples) {
            this(year, month, kwh, days, expectedDays, hourSamples, false, 0);
        }
        MonthUsage(int year, int month, double kwh, int days, int expectedDays, int hourSamples,
                   boolean future) {
            this(year, month, kwh, days, expectedDays, hourSamples, future, 0);
        }
        MonthUsage(int year, int month, double kwh, int days, int expectedDays, int hourSamples,
                   boolean future, int lastMeasuredDay) {
            this.year=year; this.month=month; this.kwh=kwh;
            this.days=days; this.expectedDays=expectedDays; this.hourSamples=hourSamples;
            this.future=future; this.lastMeasuredDay=lastMeasuredDay;
        }
    }

    private static final class MonthAccumulator {
        final int year, month, expectedDays;
        final Set<String> observedDays = new HashSet<>();
        double kwh;
        int hourSamples;
        int lastMeasuredDay;
        MonthAccumulator(int year, int month, int expectedDays) {
            this.year=year; this.month=month; this.expectedDays=expectedDays;
        }
    }

    static final class Result {
        final double kwh;
        java.util.List<MonthUsage> history = java.util.Collections.emptyList();
        final String period;
        final int days;
        final java.util.List<MonthUsage> monthly;
        Result(double kwh, String period, int days, java.util.List<MonthUsage> monthly) {
            this.kwh=kwh; this.period=period; this.days=days; this.monthly=monthly;
        }
    }

    static Result fetch(Credentials credentials, int months) throws IOException, JSONException {
        return fetch(credentials, months, null);
    }

    static synchronized Result fetch(Credentials credentials, int months, Progress progress) throws IOException, JSONException {
        final long deadline = System.nanoTime() + MAX_SYNC_MS * 1_000_000L;
        credentials.validate();
        if (months != 1 && months != 2) throw new IOException("Periodo non supportato.");
        // Respect the customer's bimonthly billing cycle. The active month
        // is included only through today's date; no future kWh are inferred.
        BillingPeriod.Window window = BillingPeriod.today(months);
        String from = HistoryMonths.from(java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome")));
        String to = window.to;

        CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        // Session cookies stay in RAM. They are not saved to Android preferences.
        CookieHandler previous = CookieHandler.getDefault();
        try {
            CookieHandler.setDefault(jar);
            report(progress, "Fase 1/4: apertura pagina di accesso Enel...");
            String loginHtml = request(LOGIN, null, deadline);
            String key = hiddenValue(loginHtml, "sessionDataKey");
            if (key == null) throw new IOException("La pagina di accesso Enel è cambiata (sessionDataKey assente). La connessione automatica non è disponibile.");
            report(progress, "Fase 2/4: verifica accesso Enel...");
            String authHtml = request(AUTH, "username="+encode(credentials.username)+"&password="+encode(credentials.password)
                    +"&sessionDataKey="+encode(key)+"&tocommonauth=true", deadline);
            String saml = hiddenValue(authHtml, "SAMLResponse");
            if (saml == null) throw new IOException("Autenticazione Enel non completata. Può essere richiesto un passaggio aggiuntivo o il login può essere cambiato.");
            report(progress, "Fase 3/4: conferma sessione Enel...");
            request(SAML, "SAMLResponse="+encode(saml), deadline);
            String url = AGGREGATE + "?pod=" + encode(credentials.pod) + "&userNumber=" + encode(credentials.userNumber)
                    + "&validityFrom=" + from + "&validityTo=" + to;
            report(progress, "Fase 4/4: recupero dei consumi elettrici...");
            Result parsed;
            try {
                parsed = parseTotal(new JSONObject(request(url,null,deadline)),from,to);
            } catch(IOException | JSONException historyUnavailable) {
                // Keep the working billing sync when a wider history is refused.
                report(progress,"Storico esteso non disponibile: recupero il periodo di fatturazione.");
                String billingUrl=AGGREGATE+"?pod="+encode(credentials.pod)+"&userNumber="+encode(credentials.userNumber)
                        +"&validityFrom="+window.from+"&validityTo="+to;
                parsed=parseTotal(new JSONObject(request(billingUrl,null,deadline)),window.from,to);
            }
            return forBillingWindow(parsed,window);
        } finally {
            jar.getCookieStore().removeAll();
            CookieHandler.setDefault(previous);
        }
    }

    static Result forBillingWindow(Result parsed, BillingPeriod.Window window) {
            java.util.List<MonthUsage> billing = new java.util.ArrayList<>();
            int start = window.startYear * 100 + window.startMonth;
            for (MonthUsage m : parsed.monthly) if (m.year * 100 + m.month >= start) billing.add(m);
            java.util.List<MonthUsage> monthly = window.withFutureMonths(billing);
            double billingKwh = 0; int billingDays = 0;
            for (MonthUsage m : monthly) { billingKwh += m.kwh; billingDays += m.days; }
            Result result = new Result(billingKwh, window.from.substring(0,2)+"/"+window.from.substring(2,4)+"/"+window.from.substring(4)
                +" – "+window.to.substring(0,2)+"/"+window.to.substring(2,4)+"/"+window.to.substring(4),
                billingDays, monthly);
            result.history = parsed.monthly;
            return result;
    }

    /** Public unauthenticated check: no username, password, POD, token or personal data transmitted. */
    static String checkPublicLogin() throws IOException {
        long deadline = System.nanoTime() + 25000L * 1000000L;
        String html = request(LOGIN, null, deadline);
        String key = hiddenValue(html, "sessionDataKey");
        if (key == null || key.isEmpty())
            return "Enel raggiungibile, ma la pagina di accesso non espone il parametro SAML atteso (sessionDataKey). Il metodo EnelGrid potrebbe non essere piu compatibile.";
        return "Pagina Enel raggiungibile e parametro SAML iniziale presente. Per verificare la sessione serve l'accesso dal dispositivo.";
    }

    static Result parseTotal(JSONObject root, String from, String to) throws JSONException, IOException {
        JSONArray aggregations = root.optJSONObject("data") == null ? null
                : root.optJSONObject("data").optJSONObject("aggregationResult") == null ? null
                : root.optJSONObject("data").optJSONObject("aggregationResult").optJSONArray("aggregations");
        if (aggregations == null) throw new IOException("Formato dati Enel inatteso: mancano le aggregazioni.");
        JSONArray results = null;
        for (int i=0; i<aggregations.length(); i++) {
            JSONObject entry=aggregations.optJSONObject(i);
            if (entry!=null && "hourlyConsumption".equals(entry.optString("referenceID"))) {
                if (results!=null) throw new IOException("Risultati orari duplicati: importazione annullata.");
                results=entry.optJSONArray("results");
            }
        }
        if (results==null) throw new IOException("Nessun dato di consumo orario trovato per la fornitura.");

        final TimeZone tz=TimeZone.getTimeZone("Europe/Rome");
        final SimpleDateFormat date=new SimpleDateFormat("ddMMyyyy",Locale.ITALY);
        date.setTimeZone(tz);
        date.setLenient(false);
        if (!from.matches("\\d{8}") || !to.matches("\\d{8}"))
            throw new IOException("Intervallo date non valido.");
        java.util.Date dFrom, dTo;
        try { dFrom=date.parse(from); dTo=date.parse(to); }
        catch(ParseException e) { throw new IOException("Intervallo date non valido."); }
        if (dTo.before(dFrom)) throw new IOException("Intervallo date invertito.");

        // Build every requested month in chronological order, including months with zero data.
        java.util.LinkedHashMap<Integer,MonthAccumulator> buckets=new java.util.LinkedHashMap<>();
        Calendar cursor=Calendar.getInstance(tz,Locale.ITALY);
        cursor.setTime(dFrom);
        final int startDay=cursor.get(Calendar.DAY_OF_MONTH);
        final int startKey=cursor.get(Calendar.YEAR)*100+cursor.get(Calendar.MONTH)+1;
        cursor.set(Calendar.DAY_OF_MONTH,1);
        Calendar endCal=Calendar.getInstance(tz,Locale.ITALY);
        endCal.setTime(dTo);
        final int endDay=endCal.get(Calendar.DAY_OF_MONTH);
        final int endKey=endCal.get(Calendar.YEAR)*100+endCal.get(Calendar.MONTH)+1;
        for (int index=0; index<12; index++) {
            int y=cursor.get(Calendar.YEAR);
            int m=cursor.get(Calendar.MONTH)+1;
            int key=y*100+m;
            int first=key==startKey?startDay:1;
            int last=key==endKey?endDay:cursor.getActualMaximum(Calendar.DAY_OF_MONTH);
            int expected=last-first+1;
            if (expected<1) throw new IOException("Periodo mensile non valido.");
            buckets.put(key,new MonthAccumulator(y,m,expected));
            if(key==endKey) break;
            cursor.add(Calendar.MONTH,1);
        }
        if (!buckets.containsKey(endKey))
            throw new IOException("Intervallo mensile troppo esteso.");

        Set<String> uniqueDays=new HashSet<>();
        double total=0; int points=0;
        for(int i=0;i<results.length();i++) {
            JSONObject day=results.optJSONObject(i);
            if(day==null) throw new IOException("Risultato consumi malformato.");
            String stamp=day.optString("date","");
            if(!stamp.matches("\\d{8}")) throw new IOException("Data consumi non valida.");
            java.util.Date parsedDate;
            try { parsedDate=date.parse(stamp); }
            catch(ParseException e) { throw new IOException("Data consumi non valida."); }
            if(parsedDate.before(dFrom)||parsedDate.after(dTo)) continue;
            if(!uniqueDays.add(stamp))
                throw new IOException("Data duplicata nei consumi Enel; importazione annullata.");
            Calendar dayCalendar=Calendar.getInstance(tz,Locale.ITALY);
            dayCalendar.setTime(parsedDate);
            int key=dayCalendar.get(Calendar.YEAR)*100+dayCalendar.get(Calendar.MONTH)+1;
            MonthAccumulator month=buckets.get(key);
            if(month==null) throw new IOException("Data consumi fuori dal periodo richiesto.");
            JSONArray bins=day.optJSONArray("binValues");
            if(bins==null) throw new IOException("Mancano le misure orarie per una giornata.");
            Set<String> hours=new HashSet<>();
            for(int j=0;j<bins.length();j++) {
                JSONObject bin=bins.optJSONObject(j);
                if(bin==null || !(bin.opt("value") instanceof Number))
                    throw new IOException("Una misura oraria non è numerica.");
                String hour=bin.optString("name","");
                if(!hour.matches("H[0-9]{1,2}") || !hours.add(hour))
                    throw new IOException("Misure orarie duplicate o senza identificativo.");
                double value=((Number)bin.opt("value")).doubleValue();
                if(!Double.isFinite(value)||value<0||value>10000)
                    throw new IOException("Valore kWh fuori intervallo.");
                month.kwh+=value;
                total+=value;
                month.hourSamples++;
                points++;
            }
            if(bins.length()>0) {
                month.observedDays.add(stamp);
                month.lastMeasuredDay = Math.max(month.lastMeasuredDay,
                        dayCalendar.get(Calendar.DAY_OF_MONTH));
            }
        }
        if(points==0) throw new IOException("Non risultano misure disponibili nel periodo selezionato.");
        if(!Double.isFinite(total)||total>1_000_000)
            throw new IOException("Totale kWh fuori intervallo.");
        java.util.List<MonthUsage> monthly=new java.util.ArrayList<>();
        int countedDays=0;
        for(MonthAccumulator m : buckets.values()) {
            int n=m.observedDays.size();
            countedDays+=n;
            monthly.add(new MonthUsage(m.year,m.month,m.kwh,n,m.expectedDays,m.hourSamples,
                    false,m.lastMeasuredDay));
        }
        return new Result(total,from+"-"+to,countedDays,monthly);
    }

    private static String hiddenValue(String html, String name) {
        Matcher inputs=Pattern.compile("<input\\b[^>]*>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        while(inputs.find()) {
            String tag=inputs.group();
            String n=attr(tag,"name");
            if (name.equals(n)) { String v=attr(tag,"value"); return v==null?null:unescape(v); }
        }
        return null;
    }
    private static String attr(String tag, String attr) {
        Matcher m=Pattern.compile("\\b"+Pattern.quote(attr)+"\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",Pattern.CASE_INSENSITIVE).matcher(tag);
        if(!m.find())return null;
        for(int i=1;i<=3;i++)if(m.group(i)!=null)return m.group(i);
        return null;
    }
    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
    private static String encode(String value) throws IOException { return URLEncoder.encode(value, "UTF-8"); }
    private static boolean allowed(URL url) {
        String host=url.getHost().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(url.getProtocol()) && (
            host.equals("www.enel.it") || host.equals("accounts.enel.com"));
    }
    private static String request(String url, String data, long deadline) throws IOException {
        URL target = new URL(url);
        for(int redirects=0; redirects<6; redirects++) {
            long wait = remaining(deadline);
            if(!allowed(target)) throw new IOException("Reindirizzamento a un dominio inatteso: accesso interrotto per sicurezza.");
            HttpURLConnection con=(HttpURLConnection)target.openConnection();
            try {
                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout((int)Math.min(8000L, wait));
                con.setReadTimeout((int)Math.min(12000L, wait));
                con.setRequestProperty("Accept", "text/html,application/json,*/*");
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36");
                if(data != null) {
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    byte[] body=data.getBytes(StandardCharsets.UTF_8);
                    con.setFixedLengthStreamingMode(body.length);
                    try(OutputStream out=con.getOutputStream()){out.write(body);}
                }
                int code=con.getResponseCode();
                remaining(deadline);
                if(code==301||code==302||code==303||code==307||code==308) {
                    String location=con.getHeaderField("Location");
                    if(location==null) throw new IOException("Reindirizzamento Enel incompleto.");
                    URL next = new URL(target,location);
                    if(!allowed(next)) throw new IOException("Enel richiede un passaggio esterno non automatizzabile (MFA/CAPTCHA/login). ");
                    if(code!=307&&code!=308)data=null;
                    target=next; continue;
                }
                if(code==401||code==403)throw new IOException("Accesso Enel rifiutato o sessione scaduta (HTTP "+code+").");
                if(code<200||code>=300)throw new IOException("Servizio Enel non disponibile (HTTP "+code+").");
                try(InputStream in=con.getInputStream(); ByteArrayOutputStream buf=new ByteArrayOutputStream()) {
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){
                        remaining(deadline);
                        if(buf.size()+n>MAX_RESPONSE)throw new IOException("Risposta Enel troppo grande.");
                        buf.write(chunk,0,n);
                    }
                    return buf.toString("UTF-8");
                }
            } finally { con.disconnect(); }
        }
        throw new IOException("Troppi reindirizzamenti durante il login Enel.");
    }
}
