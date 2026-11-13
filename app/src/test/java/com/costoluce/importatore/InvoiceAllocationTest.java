package com.costoluce.importatore;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.time.LocalDate;
public class InvoiceAllocationTest {
    @Test public void goldenCasesAndExactInvoiceConservation()throws Exception{
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/invoice-allocation.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(int j=0;j<cases.length();j++){
            JSONObject test=cases.getJSONObject(j),invoice=new JSONObject().put("from",test.getString("from")).put("to",test.getString("to")).put("electricityCents",test.getLong("total"));
            if(test.has("fixed"))invoice.put("fixedGrossCents",test.getLong("fixed"));
            JSONArray archive=new JSONArray(),usage=test.getJSONArray("usage");LocalDate first=LocalDate.parse(test.getString("from"));
            for(int i=0;i<usage.length();i++){LocalDate month=first.plusMonths(i);JSONObject row=new JSONObject().put("year",month.getYear()).put("month",month.getMonthValue()).put("available",!usage.isNull(i)).put("partial",test.optBoolean("partial")&&i==1);if(!usage.isNull(i))row.put("kwh",usage.getDouble(i));archive.put(row);}
            JSONArray result=InvoiceAllocation.split(invoice,archive),expected=test.getJSONArray("expected");assertEquals(test.getString("name"),expected.length(),result.length());long sum=0;
            for(int i=0;i<result.length();i++){assertEquals(test.getString("name"),expected.getLong(i),result.getJSONObject(i).getLong("allocatedCents"));assertEquals("allocated",result.getJSONObject(i).getString("status"));sum+=result.getJSONObject(i).getLong("allocatedCents");}
            if(result.length()>0)assertEquals(test.getLong("total"),sum);
        }
    }
    @Test public void realMonthlyInvoiceNeedsNoConsumptionForAttribution()throws Exception{
        JSONArray result=InvoiceAllocation.split(new JSONObject().put("from","2026-08-01").put("to","2026-08-31").put("electricityCents",1234),new JSONArray());
        assertEquals(12.34,result.getJSONObject(0).getDouble("billEuro"),0);assertEquals("issued",result.getJSONObject(0).getString("status"));
    }
    @Test public void partialCalendarPeriodIsNeverAllocatedAsFullMonths()throws Exception{
        assertEquals(0,InvoiceAllocation.split(new JSONObject().put("from","2026-08-05").put("to","2026-09-30").put("electricityCents",10000),new JSONArray()).length());
    }
}
