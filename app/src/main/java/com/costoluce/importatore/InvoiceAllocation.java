package com.costoluce.importatore;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

/** Attributes an issued total; never recalculates historical prices or creates missing consumption. */
final class InvoiceAllocation {
    static JSONArray split(JSONObject invoice,JSONArray archive)throws JSONException{
        LocalDate from=LocalDate.parse(invoice.getString("from")),to=LocalDate.parse(invoice.getString("to"));
        int count=(int)ChronoUnit.MONTHS.between(from.withDayOfMonth(1),to.withDayOfMonth(1))+1;
        JSONArray result=new JSONArray();long total=invoice.getLong("electricityCents");
        if(count<1||count>14||from.getDayOfMonth()!=1||to.getDayOfMonth()!=to.lengthOfMonth()||total<0)return result;
        if(count==1){result.put(new JSONObject().put("year",from.getYear()).put("month",from.getMonthValue()).put("billEuro",total/100.0).put("status","issued").put("available",true).put("partial",false));return result;}
        double[] usage=new double[count];double sum=0;
        for(int i=0;i<count;i++){
            LocalDate month=from.plusMonths(i);JSONObject measured=null;
            for(int j=0;j<archive.length();j++){JSONObject row=archive.getJSONObject(j);if(row.optInt("year")==month.getYear()&&row.optInt("month")==month.getMonthValue()){if(measured!=null)return result;measured=row;}}
            if(measured==null||!measured.optBoolean("available")||measured.optBoolean("partial")||!Double.isFinite(measured.optDouble("kwh"))||measured.optDouble("kwh")<0)return result;
            usage[i]=measured.getDouble("kwh");sum+=usage[i];
        }
        boolean fixedKnown=invoice.opt("fixedGrossCents") instanceof Number&&Double.isFinite(invoice.optDouble("fixedGrossCents"))&&invoice.optDouble("fixedGrossCents")==Math.rint(invoice.optDouble("fixedGrossCents"));long fixed=fixedKnown?invoice.getLong("fixedGrossCents"):0;
        if(fixed<0||fixed>total||!Double.isFinite(sum)||(sum==0&&total!=fixed))return result;
        long[] cents=new long[count];double[] fractions=new double[count];long assigned=0;
        for(int i=0;i<count;i++){
            double exact=(double)fixed/count+(sum>0?(total-fixed)*usage[i]/sum:0);
            cents[i]=(long)Math.floor(exact);fractions[i]=exact-cents[i];assigned+=cents[i];
        }
        long remainder=total-assigned;if(remainder<0||remainder>count)return result;
        Integer[] order=new Integer[count];for(int i=0;i<count;i++)order[i]=i;
        Arrays.sort(order,(a,b)->{int compare=Double.compare(fractions[b],fractions[a]);return compare!=0?compare:Integer.compare(a,b);});
        for(int i=0;i<remainder;i++)cents[order[i]]++;
        for(int i=0;i<count;i++){
            LocalDate month=from.plusMonths(i);result.put(new JSONObject().put("year",month.getYear()).put("month",month.getMonthValue()).put("billEuro",cents[i]/100.0)
                .put("allocatedCents",cents[i]).put("allocationMethod",fixedKnown?"fixed+kwh":"kwh").put("invoiceFrom",from.toString()).put("invoiceTo",to.toString()).put("invoiceTotalCents",total)
                .put("status","allocated").put("available",true).put("partial",false));
        }return result;
    }
}
