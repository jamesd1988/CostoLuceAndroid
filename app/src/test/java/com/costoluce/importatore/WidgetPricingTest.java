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
}
