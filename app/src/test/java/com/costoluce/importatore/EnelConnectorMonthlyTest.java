package com.costoluce.importatore;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EnelConnectorMonthlyTest {
    private static JSONObject payload(String results) throws Exception {
        return new JSONObject("{\"data\":{\"aggregationResult\":{\"aggregations\":[{\"referenceID\":\"hourlyConsumption\",\"results\":["+results+"]}]}}}");
    }

    @Test public void twoMonthsAreSeparateAndTotalMatches() throws Exception {
        JSONObject json=payload(
            "{\"date\":\"01092026\",\"binValues\":[{\"name\":\"H1\",\"value\":1.25},{\"name\":\"H2\",\"value\":0.75}]},"+
            "{\"date\":\"31102026\",\"binValues\":[{\"name\":\"H1\",\"value\":2.50}]}"
        );
        EnelConnector.Result r=EnelConnector.parseTotal(json,"01092026","31102026");
        assertEquals(4.5,r.kwh,0.000001);
        assertEquals(2,r.monthly.size());
        assertEquals(9,r.monthly.get(0).month);
        assertEquals(2026,r.monthly.get(0).year);
        assertEquals(2.0,r.monthly.get(0).kwh,0.000001);
        assertEquals(30,r.monthly.get(0).expectedDays);
        assertEquals(1,r.monthly.get(0).days);
        assertEquals(10,r.monthly.get(1).month);
        assertEquals(2.5,r.monthly.get(1).kwh,0.000001);
        assertEquals(31,r.monthly.get(1).expectedDays);
        assertEquals(1,r.monthly.get(1).days);
        assertEquals(2,r.days);
    }

    @Test public void currentNovemberIsPartialAndUsesTheLastPublishedMeasurement() throws Exception {
        JSONObject json=payload(
            "{\"date\":\"31102026\",\"binValues\":[{\"name\":\"H1\",\"value\":12.5}]},"+
            "{\"date\":\"09112026\",\"binValues\":[{\"name\":\"H1\",\"value\":2.0}]}"
        );
        EnelConnector.Result r=EnelConnector.parseTotal(json,"01102026","11112026");
        assertEquals(2,r.monthly.size());
        assertEquals(10,r.monthly.get(0).month);
        assertEquals(31,r.monthly.get(0).expectedDays);
        assertEquals(11,r.monthly.get(1).month);
        assertEquals(11,r.monthly.get(1).expectedDays);
        assertEquals(9,r.monthly.get(1).lastMeasuredDay);
        assertEquals(14.5,r.kwh,0.00001);
    }

    @Test public void missingMonthIsNotSilentlyTreatedAsRealZero() throws Exception {
        JSONObject json=payload("{\"date\":\"01092026\",\"binValues\":[{\"name\":\"H1\",\"value\":3.75}]}");
        EnelConnector.Result r=EnelConnector.parseTotal(json,"01092026","31102026");
        assertEquals(2,r.monthly.size());
        assertEquals(3.75,r.monthly.get(0).kwh,0.000001);
        assertEquals(0,r.monthly.get(1).days);
        assertEquals(0,r.monthly.get(1).hourSamples);
        assertEquals(31,r.monthly.get(1).expectedDays);
    }

    @Test public void yearBoundaryIsGroupedCorrectly() throws Exception {
        JSONObject json=payload(
          "{\"date\":\"31122026\",\"binValues\":[{\"name\":\"H1\",\"value\":1.0}]},"+
          "{\"date\":\"01012027\",\"binValues\":[{\"name\":\"H1\",\"value\":2.0}]}"
        );
        EnelConnector.Result r=EnelConnector.parseTotal(json,"01122026","31012027");
        assertEquals(2,r.monthly.size());
        assertEquals(12,r.monthly.get(0).month);
        assertEquals(2026,r.monthly.get(0).year);
        assertEquals(1,r.monthly.get(1).month);
        assertEquals(2027,r.monthly.get(1).year);
        assertEquals(3,r.kwh,0.000001);
    }

    @Test(expected=java.io.IOException.class)
    public void duplicateDailyResultsAreRejected() throws Exception {
        JSONObject json=payload(
           "{\"date\":\"01092026\",\"binValues\":[{\"name\":\"H1\",\"value\":1}]},"+
           "{\"date\":\"01092026\",\"binValues\":[{\"name\":\"H2\",\"value\":2}]}"
        );
        EnelConnector.parseTotal(json,"01092026","31102026");
    }
    private static String fullDay(String date,int hours) throws Exception {
        org.json.JSONArray bins=new org.json.JSONArray();for(int hour=1;hour<=hours;hour++)bins.put(new JSONObject().put("name","H"+hour).put("value",.25));
        return new JSONObject().put("date",date).put("binValues",bins).toString();
    }
    @Test public void dailyReadingsSurviveHistoryAndDistinguishPartialDays() throws Exception {
        EnelConnector.Result r=EnelConnector.parseTotal(payload(fullDay("09112026",24)+","+fullDay("10112026",12)),"01112026","14112026");
        assertEquals(2,r.monthly.get(0).daily.size());assertTrue(r.monthly.get(0).daily.get(0).complete);assertFalse(r.monthly.get(0).daily.get(1).complete);
        assertEquals(6,r.monthly.get(0).daily.get(0).kwh,0);
        org.json.JSONArray serialized=HistoryMonths.serialize(r.monthly);
        java.util.Calendar reference=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"));reference.clear();reference.set(2026,10,14);
        org.json.JSONArray slots=HistoryMonths.slots(serialized.toString(),reference);
        org.json.JSONArray days=slots.getJSONObject(3).getJSONArray("daily");assertEquals(2,days.length());assertEquals(9,days.getJSONObject(0).getInt("day"));assertTrue(days.getJSONObject(0).getBoolean("complete"));assertEquals(24,days.getJSONObject(0).getInt("hourSamples"));
        assertEquals(9,r.kwh,0);
    }
    @Test public void daylightSavingDaysUseActualItalianDayLength() throws Exception {
        EnelConnector.Result spring=EnelConnector.parseTotal(payload(fullDay("29032026",23)),"29032026","29032026");
        EnelConnector.Result autumn=EnelConnector.parseTotal(payload(fullDay("25102026",25)),"25102026","25102026");
        assertTrue(spring.monthly.get(0).daily.get(0).complete);assertTrue(autumn.monthly.get(0).daily.get(0).complete);
        EnelConnector.Result incomplete=EnelConnector.parseTotal(payload(fullDay("25102026",24)),"25102026","25102026");assertFalse(incomplete.monthly.get(0).daily.get(0).complete);
    }
    @Test public void requestedMonthUsesCalendarBoundariesAndStopsAtToday()throws Exception {
        java.util.Calendar now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"));now.clear();now.set(2026,10,14);
        assertArrayEquals(new String[]{"01102026","31102026"},EnelConnector.requestedMonthRange(2026,10,now));
        assertArrayEquals(new String[]{"01112026","14112026"},EnelConnector.requestedMonthRange(2026,11,now));
        assertArrayEquals(new String[]{"01122024","31122024"},EnelConnector.requestedMonthRange(2024,12,now));
        assertNull(EnelConnector.requestedMonthRange(0,0,now));
        for(int[] invalid:new int[][]{{2026,12},{2024,11},{2026,0},{2026,13}}){try{EnelConnector.requestedMonthRange(invalid[0],invalid[1],now);fail("Invalid month accepted");}catch(java.io.IOException expected){}}
        now.set(2028,2,1);assertArrayEquals(new String[]{"01022028","29022028"},EnelConnector.requestedMonthRange(2028,2,now));
    }
}
