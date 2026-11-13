package com.costoluce.importatore;

import org.json.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Read-only customer-area protocol, independently implemented; see docs/enel-invoice-protocol.md. */
final class EnelInvoices {
    static final String CARDS="https://www.enel.it/bin/areaclienti/auth/getCards?username=&";
    static final String ARCHIVE="https://www.enel.it/bin/areaclienti/auth/getArchivioBollette";
    interface Transport {byte[] request(String address,String form,int maxBytes)throws IOException;}
    interface DocumentReader {IssuedInvoice read(byte[] pdf)throws Exception;}
    static final class Outcome {
        final List<IssuedInvoice> invoices=new ArrayList<>();
        String status="Fatture: aggiornamento non completato. Archivio precedente conservato.";
        boolean needsFiscalCode=false,checked=false;
    }
    static final class Supply {
        final String account,partner,number,cf;
        Supply(String account,String partner,String number,String cf){this.account=account;this.partner=partner;this.number=number;this.cf=cf;}
    }
    private static String required(JSONObject o,String key)throws JSONException{
        String s=o.optString(key,"").trim();if(s.isEmpty()||s.length()>80||!s.matches("[A-Za-z0-9_-]+"))throw new JSONException("Identificativo archivio non valido.");return s;
    }
    static Supply supply(JSONObject root,String pod,String optionalCf)throws JSONException{
        JSONObject data=root.optJSONObject("data");JSONArray cards=data==null?null:data.optJSONArray("Cards");
        if(cards==null)throw new JSONException("Elenco forniture Enel non riconosciuto.");
        JSONObject match=null;
        for(int i=0;i<cards.length();i++){JSONObject card=cards.getJSONObject(i);if(pod.equalsIgnoreCase(card.optString("pod").trim())){if(match!=null)throw new JSONException("Fornitura ambigua.");match=card;}}
        if(match==null)throw new JSONException("POD non presente nelle forniture dell'account.");
        String cf=optionalCf;
        if(cf.isEmpty())for(JSONObject context:new JSONObject[]{match,data,root}){
            for(String key:new String[]{"cf","codiceFiscale","fiscalCode","codiceFiscaleCliente"}){String candidate=context.optString(key,"").trim().toUpperCase(Locale.ROOT);if(candidate.matches("[A-Z0-9]{16}|[0-9]{11}")){cf=candidate;break;}}if(!cf.isEmpty())break;
        }
        return new Supply(required(match,"contoContrattuale"),required(match,"businessPartner"),required(match,"codiceFornitura"),cf);
    }
    static JSONObject archiveRequest(Supply s)throws JSONException{
        return new JSONObject().put("cache",true).put("canale","W").put("cf",s.cf).put("inputList",new JSONArray().put(new JSONObject().put("businessPartner",s.partner).put("contoContrattuale",new JSONArray().put(s.account)))).put("numeroMassimoX2",100).put("tipologia","");
    }
    static List<JSONObject> documents(JSONObject root,Supply supply)throws JSONException{
        JSONObject data=root.optJSONObject("data");JSONArray results=data==null?null:data.optJSONArray("results");if(results==null)throw new JSONException("Archivio fatture Enel non riconosciuto.");
        List<JSONObject> list=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<results.length();i++){JSONObject group=results.getJSONObject(i);if(!supply.account.equals(group.optString("contoContrattuale")))continue;JSONArray rows=group.getJSONArray("fatture");if(rows.length()>200)throw new JSONException("Archivio troppo esteso.");
            for(int j=0;j<rows.length();j++){JSONObject doc=rows.getJSONObject(j);String id=required(doc,"Numerodocumento");if(!id.matches("[0-9]{6,30}")||!ids.add(canonical(id)))throw new JSONException("Numero documento duplicato o non valido.");LocalDate.parse(doc.getString("Dataemissione"));amount(doc.get("Importofattura"));list.add(doc);}
        }
        list.sort((a,b)->b.optString("Dataemissione").compareTo(a.optString("Dataemissione")));return list;
    }
    static String canonical(String id){return id.replaceFirst("^0+(?!$)","");}
    static long amount(Object raw){String s=String.valueOf(raw).trim();if(s.contains(","))s=s.replace(".","").replace(",",".");return new BigDecimal(s).movePointRight(2).longValueExact();}
    static String download(Supply s,JSONObject doc,boolean legacy)throws Exception{
        String id=required(doc,"Numerodocumento"),date=LocalDate.parse(doc.getString("Dataemissione")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if(legacy)return "https://www.enel.it/bin/areaclienti/auth/bollette?action=download-fattura&dispositivo=&tipoDocumento=fattura&numeroFattura="+enc(id)+"&dataEmissione="+enc(date);
        return "https://www.enel.it/bin/areaclienti/auth/downloadFARO?numeroFattura="+enc(id)+"&contractAccount="+enc(s.account)+"&businessPartner="+enc(s.partner)+"&numFornitura="+enc(s.number)+"&dataEm="+enc(date)+"&canale=WR&queryDesc=path&tipoDocumento=fattura";
    }
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    static byte[] pdf(byte[] payload)throws JSONException,IOException{
        byte[] result=payload;
        if(payload.length==0)throw new IOException("Documento vuoto.");
        if(payload[0]!='%'){
            JSONObject envelope=new JSONObject(new String(payload,java.nio.charset.StandardCharsets.UTF_8));JSONObject map=envelope.optJSONObject("map");String data=map==null?"":map.optString("data","");
            if(data.length()>8_000_000)throw new IOException("Documento troppo grande.");
            try{result=Base64.getMimeDecoder().decode(data);}catch(IllegalArgumentException invalid){throw new IOException("Documento non valido.");}
        }
        if(result.length<5||result.length>6_000_000||result[0]!='%'||result[1]!='P'||result[2]!='D'||result[3]!='F'||result[4]!='-')throw new IOException("Risposta Enel non PDF.");return result;
    }
    static Outcome fetch(EnelConnector.Credentials credentials,Transport http,DocumentReader reader,JSONArray cached,long deadline,EnelConnector.Progress progress){
        Outcome out=new Outcome();boolean cfMissing=credentials.fiscalCode.isEmpty();int known=0,failed=0,remaining=0;String stage="elenco forniture";
        try{
            if(progress!=null)progress.step("Fatture Enel: verifica fornitura...");
            Supply supply=supply(new JSONObject(new String(http.request(CARDS+"_="+System.currentTimeMillis(),null,2_000_000),java.nio.charset.StandardCharsets.UTF_8)),credentials.pod,credentials.fiscalCode);cfMissing=supply.cf.isEmpty();
            stage="archivio bollette";
            JSONObject archive=new JSONObject(new String(http.request(ARCHIVE,"pagamentiRequest="+enc(archiveRequest(supply).toString()),2_000_000),java.nio.charset.StandardCharsets.UTF_8));
            List<JSONObject> docs=documents(archive,supply);out.checked=true;
            Map<String,JSONObject> existing=new HashMap<>();for(int i=0;i<cached.length();i++){JSONObject r=cached.getJSONObject(i);existing.put(canonical(r.getString("id")),r);}
            LocalDate cutoff=LocalDate.now(java.time.ZoneId.of("Europe/Rome")).minusMonths(16);
            for(JSONObject doc:docs){
                if(Thread.currentThread().isInterrupted())throw new IOException("Interrotto.");
                if(LocalDate.parse(doc.getString("Dataemissione")).isBefore(cutoff))continue;
                if(doc.optString("Statopagabilita").equalsIgnoreCase("ANNULLATA"))continue;
                long payable=amount(doc.get("Importofattura"));if(payable<0){failed++;continue;}
                JSONObject previous=existing.get(canonical(doc.getString("Numerodocumento")));
                if(previous!=null&&previous.optLong("payableCents",-1)==payable){known++;continue;}
                if(deadline-System.nanoTime()<8_000_000_000L||out.invoices.size()>=24){remaining++;continue;}
                if(progress!=null)progress.step("Fatture Enel: lettura documento "+(out.invoices.size()+failed+1)+"...");
                try{
                    byte[] document;
                    try{document=pdf(http.request(download(supply,doc,false),null,8_500_000));}
                    catch(Exception modernUnavailable){document=pdf(http.request(download(supply,doc,true),null,8_500_000));}
                    IssuedInvoice invoice=reader.read(document);
                    if(!credentials.pod.equals(invoice.pod)||!canonical(invoice.id).equals(canonical(doc.getString("Numerodocumento")))||invoice.payableCents!=payable)throw new IOException("Documento non corrispondente.");
                    out.invoices.add(invoice);
                }catch(Exception unusable){failed++;}
            }
            out.status="Fatture Enel: "+out.invoices.size()+" lette, "+known+" già archiviate"+(failed>0?", "+failed+" non utilizzabili":"")+(remaining>0?", recupero parziale: premi Aggiorna per continuare":"")+". Canone TV escluso dall'elettricità.";
        }catch(Exception unavailable){out.needsFiscalCode=cfMissing;out.status="Fatture Enel non recuperate: "+stage+" non utilizzabile."+(cfMissing?" Se richiesto dall'account, completa il codice fiscale in Dati archivio Enel.":"")+" Fatture precedenti conservate; consumi aggiornabili separatamente.";}
        return out;
    }
}
