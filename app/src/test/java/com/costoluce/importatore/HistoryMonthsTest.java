package com.costoluce.importatore;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class HistoryMonthsTest {
    private Calendar day(int y,int m,int d) {Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));c.clear();c.set(y,m-1,d,12,0);return c;}
    @Test public void historyStartsThreeMonthsBeforeCurrentIncludingYearBoundary(){
        assertEquals("01082026",HistoryMonths.from(day(2026,11,12)));
        assertEquals("01102026",HistoryMonths.from(day(2027,1,3)));
    }
    @Test public void missingIsNotZeroButMeasuredZeroIsAvailable()throws Exception{
        List<EnelConnector.MonthUsage> rows=Arrays.asList(
            new EnelConnector.MonthUsage(2026,8,0,0,31,0),
            new EnelConnector.MonthUsage(2026,9,0,30,30,720),
            new EnelConnector.MonthUsage(2026,10,200,31,31,744),
            new EnelConnector.MonthUsage(2026,11,0,12,12,288));
        JSONArray slots=HistoryMonths.slots(HistoryMonths.serialize(rows).toString(),day(2026,11,12));
        assertFalse(slots.getJSONObject(0).getBoolean("available"));
        assertFalse(slots.getJSONObject(0).has("kwh"));
        assertTrue(slots.getJSONObject(1).getBoolean("available"));
        assertEquals(0,slots.getJSONObject(1).getDouble("kwh"),0);
        assertFalse(slots.getJSONObject(2).getBoolean("partial"));
        assertTrue(slots.getJSONObject(3).getBoolean("partial"));
        JSONArray rollover=HistoryMonths.slots(HistoryMonths.serialize(rows).toString(),day(2026,12,1));
        assertEquals(9,rollover.getJSONObject(0).getInt("month"));
        assertFalse(rollover.getJSONObject(3).getBoolean("available"));
    }
    @Test public void widerHistoryDoesNotChangeOctoberNovemberBilling()throws Exception{
        List<EnelConnector.MonthUsage> rows=Arrays.asList(
            new EnelConnector.MonthUsage(2026,8,100,31,31,744),
            new EnelConnector.MonthUsage(2026,9,90,30,30,720),
            new EnelConnector.MonthUsage(2026,10,238,31,31,744),
            new EnelConnector.MonthUsage(2026,11,241,12,12,288));
        EnelConnector.Result wide=new EnelConnector.Result(669,"",104,rows);
        EnelConnector.Result result=EnelConnector.forBillingWindow(wide,BillingPeriod.select(day(2026,11,12),2));
        assertEquals(479,result.kwh,0.00001);assertEquals(2,result.monthly.size());assertEquals(4,result.history.size());
        assertEquals(10,result.monthly.get(0).month);assertEquals(11,result.monthly.get(1).month);
        assertEquals(218,result.monthly.stream().mapToDouble(m->WidgetPricing.taxableMonthly(m.kwh)).sum(),0.00001);
    }
    @Test public void decemberKeepsFutureJanuaryOutsideHistory()throws Exception{
        List<EnelConnector.MonthUsage> rows=Arrays.asList(new EnelConnector.MonthUsage(2026,12,12,1,12,24));
        EnelConnector.Result result=EnelConnector.forBillingWindow(new EnelConnector.Result(12,"",1,rows),BillingPeriod.select(day(2026,12,12),2));
        assertEquals(2,result.monthly.size());assertTrue(result.monthly.get(1).future);assertEquals(2027,result.monthly.get(1).year);assertEquals(12,result.kwh,0);
    }
    @Test public void rangeCoversFourQuarterlyBillsWithoutInventingMonths(){
        assertEquals("01022026",HistoryMonths.from(day(2026,11,13),3,8));
        assertEquals("01112025",HistoryMonths.from(day(2026,10,13),3,8));
        assertEquals("01082026",HistoryMonths.from(day(2026,11,13),1,8));
    }
    @Test public void cachedCurrentMonthRemainsPartialAfterRollover()throws Exception{
        JSONArray saved=HistoryMonths.serialize(Arrays.asList(new EnelConnector.MonthUsage(2026,11,40,12,12,288)));
        JSONArray rows=HistoryMonths.slots(saved.toString(),day(2026,12,1),12);
        assertEquals(12,rows.length());JSONObject november=rows.getJSONObject(10);assertEquals(11,november.getInt("month"));assertTrue(november.getBoolean("partial"));
        assertFalse(rows.getJSONObject(11).getBoolean("available"));
    }
    @Test public void mergingMissingResponsesPreservesMeasuredZeros()throws Exception{
        JSONArray old=HistoryMonths.serialize(Arrays.asList(new EnelConnector.MonthUsage(2026,9,0,30,30,720)));
        JSONArray unavailable=HistoryMonths.serialize(Arrays.asList(new EnelConnector.MonthUsage(2026,9,0,0,30,0)));
        JSONArray merged=HistoryMonths.merge(old.toString(),unavailable);assertEquals(1,merged.length());assertEquals(720,merged.getJSONObject(0).getInt("hourSamples"));
    }
}
