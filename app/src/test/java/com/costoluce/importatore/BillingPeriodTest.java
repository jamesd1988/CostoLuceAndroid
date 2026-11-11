package com.costoluce.importatore;

import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Locale;
import static org.junit.Assert.*;

public final class BillingPeriodTest {
    private static Calendar day(int year, int month, int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"), Locale.ITALY);
        c.clear();
        c.set(year, month - 1, day, 12, 0);
        return c;
    }

    @Test public void octoberAndNovemberAreTheActiveBillingCycle() {
        BillingPeriod.Window w = BillingPeriod.select(day(2026, 11, 11), 2);
        assertEquals("01102026", w.from);
        assertEquals("11112026", w.to);
        assertEquals(2, w.months);
        assertEquals(10, w.startMonth);
        assertEquals(11, w.currentMonth);
    }

    @Test public void oneMonthModeUsesNovemberNotOctober() {
        BillingPeriod.Window w = BillingPeriod.select(day(2026, 11, 11), 1);
        assertEquals("01112026", w.from);
        assertEquals("11112026", w.to);
    }

    @Test public void decemberToJanuaryCrossesTheYearBoundary() {
        BillingPeriod.Window december = BillingPeriod.select(day(2026, 12, 15), 2);
        assertEquals("01122026", december.from);
        assertEquals("15122026", december.to);
        assertEquals(12, december.startMonth);
        assertEquals(2026, december.startYear);
        BillingPeriod.Window january = BillingPeriod.select(day(2027, 1, 5), 2);
        assertEquals("01122026", january.from);
        assertEquals("05012027", january.to);
    }

    @Test public void futureMonthsArePlaceholdersNotConsumption() {
        BillingPeriod.Window dec = BillingPeriod.select(day(2026, 12, 15), 2);
        EnelConnector.MonthUsage measured =
                new EnelConnector.MonthUsage(2026, 12, 13.5, 7, 15, 130);
        java.util.List<EnelConnector.MonthUsage> months =
                dec.withFutureMonths(Arrays.asList(measured));
        assertEquals(2, months.size());
        assertEquals(12, months.get(0).month);
        assertEquals(1, months.get(1).month);
        assertEquals(2027, months.get(1).year);
        assertTrue(months.get(1).future);
        assertEquals(0, months.get(1).days);
        assertEquals(0.0, months.get(1).kwh, 0.0001);
        assertEquals(31, months.get(1).expectedDays);
    }

    @Test public void juneJulyPairMatchesTheInvoiceCycle() {
        BillingPeriod.Window july = BillingPeriod.select(day(2026, 7, 24), 2);
        assertEquals("01062026", july.from);
        assertEquals("24072026", july.to);
    }
}
