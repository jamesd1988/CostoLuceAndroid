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

    static final class Result {
        final double kwh;
        final String period;
        final int days;
        Result(double kwh, String period, int days) { this.kwh=kwh; this.period=period; this.days=days; }
    }

    static Result fetch(Credentials credentials, int months) throws IOException, JSONException {
        return fetch(credentials, months, null);
    }

    static Result fetch(Credentials credentials, int months, Progress progress) throws IOException, JSONException {
        final long deadline = System.nanoTime() + MAX_SYNC_MS * 1_000_000L;
        credentials.validate();
        if (months != 1 && months != 2) throw new IOException("Periodo non supportato.");
        // Last full calendar month(s): avoids presenting incomplete measures as a final total.
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"), Locale.ITALY);
        now.set(Calendar.DAY_OF_MONTH, 1);
        now.add(Calendar.MONTH, -1);
        Calendar end = (Calendar) now.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        Calendar start = (Calendar) now.clone();
        start.add(Calendar.MONTH, -(months - 1));
        SimpleDateFormat date = new SimpleDateFormat("ddMMyyyy", Locale.ITALY);
        date.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
        date.setLenient(false);
        String from = date.format(start.getTime());
        String to = date.format(end.getTime());

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
            String response = request(url, null, deadline);
            report(progress, "Analisi del totale kWh ricevuto da Enel...");
            if (!response.trim().startsWith("{")) throw new IOException("Enel non ha restituito dati JSON. Controlla l'accesso e riprova.");
            Result parsed = parseTotal(new JSONObject(response), from, to);
            return new Result(parsed.kwh, from.substring(0,2)+"/"+from.substring(2,4)+"/"+from.substring(4)
                +" – "+to.substring(0,2)+"/"+to.substring(2,4)+"/"+to.substring(4), parsed.days);
        } finally {
            jar.getCookieStore().removeAll();
            CookieHandler.setDefault(previous);
        }
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
            JSONObject entry = aggregations.optJSONObject(i);
            if (entry != null && "hourlyConsumption".equals(entry.optString("referenceID"))) {
                if (results != null) throw new IOException("Risultati orari duplicati: importazione annullata.");
                results = entry.optJSONArray("results");
            }
        }
        if (results == null) throw new IOException("Nessun dato di consumo orario trovato per la fornitura.");
        final SimpleDateFormat date = new SimpleDateFormat("ddMMyyyy", Locale.ITALY);
        date.setLenient(false);
        java.util.Date dFrom, dTo;
        try { dFrom=date.parse(from);dTo=date.parse(to); }
        catch (ParseException e) { throw new IOException("Intervallo date non valido."); }
        Set<String> dates = new HashSet<>();
        double total = 0; int points = 0;
        for (int i=0; i<results.length(); i++) {
            JSONObject day = results.optJSONObject(i);
            if (day == null) throw new IOException("Risultato consumi malformato.");
            String stamp = day.optString("date", "");
            java.util.Date parsedDate;
            try { parsedDate=date.parse(stamp); }
            catch (ParseException e) { throw new IOException("Data consumi non valida."); }
            if (parsedDate.before(dFrom) || parsedDate.after(dTo)) continue;
            if (!dates.add(stamp)) throw new IOException("Data duplicata nei consumi Enel; importazione annullata.");
            JSONArray bins=day.optJSONArray("binValues");
            if (bins == null) throw new IOException("Mancano le misure orarie per una giornata.");
            Set<String> hours = new HashSet<>();
            for (int j=0;j<bins.length();j++) {
                JSONObject bin=bins.optJSONObject(j);
                if (bin==null || !(bin.opt("value") instanceof Number)) throw new IOException("Una misura oraria non è numerica.");
                String hour=bin.optString("name", "");
                if (!hour.matches("H[0-9]{1,2}") || !hours.add(hour))
                    throw new IOException("Misure orarie duplicate o senza identificativo.");
                double v = ((Number)bin.opt("value")).doubleValue();
                if (!Double.isFinite(v) || v < 0 || v > 10000) throw new IOException("Valore kWh fuori intervallo.");
                total += v; points++;
            }
        }
        if (points == 0) throw new IOException("Non risultano misure disponibili nel periodo selezionato.");
        if (!Double.isFinite(total) || total > 1_000_000) throw new IOException("Totale kWh fuori intervallo.");
        return new Result(total, from+"-"+to, dates.size());
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
