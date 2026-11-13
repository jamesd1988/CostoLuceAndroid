package com.costoluce.importatore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Values read from an issued electricity document, never calculated from tariffs. */
final class IssuedInvoice {
    final String id,pod,from,to;
    final long electricityCents,payableCents;
    Long fixedGrossCents;
    static final int ALLOCATION_VERSION=1;
    IssuedInvoice(String id,String pod,String from,String to,long electricity,long payable){
        LocalDate start=LocalDate.parse(from),end=LocalDate.parse(to);
        if(id.isEmpty()||!pod.matches("IT[A-Z0-9]{10,18}")||end.isBefore(start)||end.isAfter(start.plusMonths(14))||electricity<0||payable<electricity||payable>100_000_000)throw new IllegalArgumentException("Fattura non valida o nota di credito: richiede verifica.");
        this.id=id;this.pod=pod;this.from=from;this.to=to;electricityCents=electricity;payableCents=payable;
    }
    JSONObject json()throws JSONException{JSONObject result=new JSONObject().put("id",id).put("from",from).put("to",to).put("electricityCents",electricityCents).put("payableCents",payableCents).put("source","enel-pdf").put("allocationVersion",ALLOCATION_VERSION);if(fixedGrossCents!=null)result.put("fixedGrossCents",fixedGrossCents);return result;}
    JSONObject enrichStored(JSONObject prior)throws JSONException{
        if(!EnelInvoices.canonical(prior.getString("id")).equals(EnelInvoices.canonical(id))||!prior.getString("from").equals(from)||!prior.getString("to").equals(to)||prior.getLong("electricityCents")!=electricityCents||prior.getLong("payableCents")!=payableCents)throw new IllegalArgumentException("Numero fattura già presente con dati diversi: verifica il documento.");
        JSONObject result=new JSONObject(prior.toString());if(prior.optInt("allocationVersion",0)<ALLOCATION_VERSION){result.put("allocationVersion",ALLOCATION_VERSION);if(fixedGrossCents!=null)result.put("fixedGrossCents",fixedGrossCents);}return result;
    }
    static long money(String text){return new BigDecimal(text.replace(".","").replace(",",".")).movePointRight(2).longValueExact();}
    private static String unique(String text,String regex,String missing){
        Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);Set<String> values=new HashSet<>();while(m.find())values.add(m.group(1));
        if(values.size()!=1)throw new IllegalArgumentException(missing);return values.iterator().next();
    }
    static IssuedInvoice parse(String raw){
        if(raw==null||raw.length()>600_000)throw new IllegalArgumentException("Documento troppo grande.");
        String text=raw.replace('\u00a0',' ').replaceAll("[\\t ]+"," ");
        if(!text.toLowerCase(Locale.ROOT).contains("enel"))throw new IllegalArgumentException("Documento Enel non riconosciuto.");
        String pod=unique(text,"\\b(IT[A-Z0-9]{10,18})\\b","POD assente o più forniture: impossibile associare la fattura.").toUpperCase(Locale.ROOT);
        String id=unique(text,"fattura(?:\\s+elettronica)?\\s*(?:n[.°]?|numero)\\s*([0-9]{6,30})","Numero fattura assente o ambiguo.");
        String number="([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})(?![0-9])";
        long electricity=money(unique(text,"totale\\s+bolletta\\s*:?\\s*"+number,"Totale elettricità assente o ambiguo. Non uso il totale da pagare al suo posto."));
        long payable=money(unique(text,"totale\\s+da\\s+pagare\\s*:?\\s*"+number,"Totale da pagare assente o ambiguo."));
        String months="(GEN|FEB|MAR|APR|MAG|GIU|LUG|AGO|SET|OTT|NOV|DIC)";
        Matcher period=Pattern.compile("periodo\\s*:?\\s*"+months+"[.a-z]*\\s+(20[0-9]{2})\\s*[-–]\\s*"+months+"[.a-z]*\\s+(20[0-9]{2})",Pattern.CASE_INSENSITIVE).matcher(text);
        Set<String> ranges=new HashSet<>();String[] names={"GEN","FEB","MAR","APR","MAG","GIU","LUG","AGO","SET","OTT","NOV","DIC"};
        while(period.find()){
            int sm=Arrays.asList(names).indexOf(period.group(1).toUpperCase(Locale.ROOT))+1,em=Arrays.asList(names).indexOf(period.group(3).toUpperCase(Locale.ROOT))+1;
            LocalDate start=LocalDate.of(Integer.parseInt(period.group(2)),sm,1),end=LocalDate.of(Integer.parseInt(period.group(4)),em,1);ranges.add(start+"/"+end.withDayOfMonth(end.lengthOfMonth()));
        }
        if(ranges.size()!=1)throw new IllegalArgumentException("Periodo di consumo assente o ambiguo: nessun importo importato.");
        String[] dates=ranges.iterator().next().split("/");IssuedInvoice invoice=new IssuedInvoice(id,pod,dates[0],dates[1],electricity,payable);
        // Only use an explicit aggregate and a unique VAT rate read from this invoice.
        // Otherwise the caller labels a proportional attribution of the full electricity total.
        try{
            long net=money(unique(text,"quota\\s+fissa\\s+e\\s+quota\\s+potenza\\s*:?\\s*"+number,"Quote fisse non riconosciute."));
            String vat=unique(text,"IVA\\s+([0-9]{1,2}(?:[,.][0-9]{1,2})?)\\s*%\\s+su\\s+imponibile","Aliquota IVA non univoca.");
            BigDecimal factor=BigDecimal.ONE.add(new BigDecimal(vat.replace(',','.')).movePointLeft(2));
            long gross=BigDecimal.valueOf(net).multiply(factor).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
            if(gross>=0&&gross<=electricity)invoice.fixedGrossCents=gross;
        }catch(IllegalArgumentException|ArithmeticException unknown){ /* Keep the original total, without guessing a split. */ }
        return invoice;
    }
}
