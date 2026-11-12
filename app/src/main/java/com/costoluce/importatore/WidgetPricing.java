package com.costoluce.importatore;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared native counterpart of the tariff editor's calculation. Legacy numeric JSON stays valid. */
final class WidgetPricing {
    private static final String[] KEYS = {"energy","losses","dispatch","balancing","transport","equalization","asos","arim","excise","vat"};
    private final double[] values;
    private final boolean[] fixed;
    private final boolean resident;
    private WidgetPricing(double[] values, boolean[] fixed, boolean resident) {
        this.values=values;this.fixed=fixed;this.resident=resident;
    }
    static WidgetPricing parse(String serialized, boolean resident) throws JSONException {
        if(serialized==null||serialized.length()>8000)throw new JSONException("Tariffe non valide.");
        JSONObject rates=new JSONObject(serialized);
        if(rates.has("_meta")&&!(rates.opt("_meta") instanceof JSONObject))throw new JSONException("Metadati non validi.");
        JSONObject meta=rates.optJSONObject("_meta");double[] values=new double[KEYS.length];boolean[] fixed=new boolean[KEYS.length];
        for(int i=0;i<KEYS.length;i++){
            String key=KEYS[i];boolean percent=i==1||i==9;
            if(!(rates.opt(key) instanceof Number))throw new JSONException("Corrispettivo non disponibile: "+key);
            double value=rates.getDouble(key);
            if(!Double.isFinite(value)||value<0||value>(percent?100:1000))throw new JSONException("Corrispettivo non valido: "+key);
            values[i]=value;
            if(meta!=null&&meta.has(key)){
                JSONObject entry=meta.optJSONObject(key);if(entry==null)throw new JSONException("Voce non valida.");
                if(entry.has("name")){
                    if(!(entry.opt("name") instanceof String))throw new JSONException("Nome non valido.");
                    String name=entry.getString("name");
                    if(name.trim().isEmpty()||name.length()>60||name.chars().anyMatch(c -> c < 32 || c == 127))throw new JSONException("Nome non valido.");
                    if(i==0&&!"Energia €/kWh".equals(name))throw new JSONException("Il nome Energia è protetto.");
                }
                String type=entry.optString("type",percent?"percent":"variable");
                if(percent?!"percent".equals(type):!"variable".equals(type)&&!"fixed".equals(type))throw new JSONException("Tipo di costo non valido.");
                fixed[i]="fixed".equals(type);
            }
        }
        return new WidgetPricing(values,fixed,resident);
    }
    static double taxableMonthly(double kwh) {
        if(kwh<=150)return 0;if(kwh<=220)return kwh-150;if(kwh<=370)return 2*kwh-370;return kwh;
    }
    boolean hasFixedCosts(){for(int i=0;i<fixed.length;i++)if(fixed[i]&&values[i]>0)return true;return false;}
    double estimatedCost(double kwh,int months){return estimatedCost(kwh,months,months*taxableMonthly(kwh/months),months);}
    double estimatedCost(double kwh,int months,double residentTaxableKwh){return estimatedCost(kwh,months,residentTaxableKwh,months);}
    double estimatedCost(double kwh,int months,double residentTaxableKwh,int activeMonths){
        if(!Double.isFinite(kwh)||kwh<0||kwh>=1_000_000||(months!=1&&months!=2)||activeMonths<0||activeMonths>months
                ||!Double.isFinite(residentTaxableKwh)||residentTaxableKwh<0||residentTaxableKwh>kwh+0.0001)
            throw new IllegalArgumentException("Consumo o imponibile non valido.");
        double energy=values[0]*(fixed[0]?activeMonths:kwh);
        double preVat=energy*(1+values[1]/100);
        for(int i=2;i<=7;i++)preVat+=values[i]*(fixed[i]?activeMonths:kwh);
        preVat+=values[8]*(fixed[8]?activeMonths:resident?residentTaxableKwh:kwh);
        double total=preVat*(1+values[9]/100);
        if(!Double.isFinite(total)||total>10_000_000)throw new IllegalArgumentException("Costo fuori intervallo.");
        return total;
    }
}
