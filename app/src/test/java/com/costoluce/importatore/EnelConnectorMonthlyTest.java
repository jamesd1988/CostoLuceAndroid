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
}
