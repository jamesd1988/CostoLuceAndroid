package com.costoluce.importatore;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class WidgetPricingTest {
    private static String rates() {
        return "{"
            + "\"energy\":0.135,\"losses\":10,"
            + "\"dispatch\":0.021117,\"balancing\":0.000787,"
            + "\"transport\":0.0119,\"equalization\":0.00283,"
            + "\"asos\":0.030095,\"arim\":0.001638,"
            + "\"excise\":0.0227,\"vat\":10}";
    }

    @Test public void twoMonthsAndResidentRatesAreCalculated() throws Exception {
        WidgetPricing pricing = WidgetPricing.parse(rates(), true);
        double value = pricing.estimatedCost(479, 2);
        assertEquals(119.71, value, 0.10);
    }

    @Test public void residentExciseDiffersFromNonResident() throws Exception {
        WidgetPricing resident = WidgetPricing.parse(rates(), true);
        WidgetPricing nonResident = WidgetPricing.parse(rates(), false);
        assertTrue(nonResident.estimatedCost(238, 1) > resident.estimatedCost(238, 1));
    }

    @Test public void measuredMonthlyExciseIsNotAveragedAcrossTwoMonths() throws Exception {
        WidgetPricing pricing = WidgetPricing.parse(rates(), true);
        double taxable = WidgetPricing.taxableMonthly(400) + WidgetPricing.taxableMonthly(100);
        double exact = pricing.estimatedCost(500, 2, taxable);
        double oldAveraged = pricing.estimatedCost(500, 2);
        assertTrue("October/November excise must use actual monthly kWh", exact > oldAveraged);
    }

    @Test(expected=JSONException.class)
    public void missingRatesAreRejected() throws Exception {
        WidgetPricing.parse("{\"energy\":0.1}", true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidConsumptionCannotBeStored() throws Exception {
        WidgetPricing.parse(rates(), true).estimatedCost(-1, 2);
    }
    @Test public void quarterKeepsMonthlyExciseAndExcludesFutureFixedFees()throws Exception{
        WidgetPricing pricing=WidgetPricing.parse(rates(),true);
        double taxable=WidgetPricing.taxableMonthly(178)+WidgetPricing.taxableMonthly(160)+WidgetPricing.taxableMonthly(400);
        assertEquals(pricing.estimatedCost(178,1)+pricing.estimatedCost(160,1)+pricing.estimatedCost(400,1),pricing.estimatedCost(738,3,taxable,3),1e-8);
        org.json.JSONObject json=new org.json.JSONObject(rates());org.json.JSONObject meta=new org.json.JSONObject();meta.put("transport",new org.json.JSONObject().put("type","fixed"));json.put("_meta",meta);json.put("transport",12);
        WidgetPricing fixed=WidgetPricing.parse(json.toString(),true);
        assertEquals(26.4,fixed.estimatedCost(100,3,0,3)-fixed.estimatedCost(100,3,0,1),1e-8);
    }
}
