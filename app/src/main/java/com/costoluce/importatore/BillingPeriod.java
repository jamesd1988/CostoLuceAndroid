package com.costoluce.importatore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Italian electricity billing period, anchored to even starting months:
 * Oct-Nov, Dec-Jan, Feb-Mar, Apr-May, Jun-Jul, Aug-Sep.
 * An in-progress month is queried through today; a future month is never
 * requested from Enel and is displayed as "not started".
 */
final class BillingPeriod {
    private static final TimeZone ROME = TimeZone.getTimeZone("Europe/Rome");

    static final class Window {
        final int months;
        final String from;
        final String to;
        final int startYear;
        final int startMonth;
        final int currentYear;
        final int currentMonth;
        Window(int months, String from, String to, int startYear, int startMonth,
               int currentYear, int currentMonth) {
            this.months = months;
            this.from = from;
            this.to = to;
            this.startYear = startYear;
            this.startMonth = startMonth;
            this.currentYear = currentYear;
            this.currentMonth = currentMonth;
        }

        List<EnelConnector.MonthUsage> withFutureMonths(List<EnelConnector.MonthUsage> data) {
            List<EnelConnector.MonthUsage> result = new ArrayList<>(data);
            Calendar month = Calendar.getInstance(ROME, Locale.ITALY);
            month.clear();
            month.set(startYear, startMonth - 1, 1);
            for (int index = data.size(); index < months; index++) {
                Calendar next = (Calendar) month.clone();
                next.add(Calendar.MONTH, index);
                result.add(new EnelConnector.MonthUsage(
                        next.get(Calendar.YEAR), next.get(Calendar.MONTH) + 1,
                        0, 0, next.getActualMaximum(Calendar.DAY_OF_MONTH), 0, true));
            }
            return result;
        }
    }

    static Window select(Calendar reference, int months) {
        if (months != 1 && months != 2)
            throw new IllegalArgumentException("Periodo non supportato.");
        Calendar now = Calendar.getInstance(ROME, Locale.ITALY);
        now.setTimeInMillis(reference.getTimeInMillis());
        Calendar start = (Calendar) now.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        // January is odd and is the second month of the December-January cycle.
        if (months == 2 && (start.get(Calendar.MONTH) + 1) % 2 == 1)
            start.add(Calendar.MONTH, -1);
        SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy", Locale.ITALY);
        formatter.setTimeZone(ROME);
        return new Window(months, formatter.format(start.getTime()),
                formatter.format(now.getTime()),
                start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1,
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1);
    }

    static Window today(int months) {
        return select(Calendar.getInstance(ROME, Locale.ITALY), months);
    }

    static boolean currentMonth(EnelConnector.MonthUsage m) {
        Calendar now = Calendar.getInstance(ROME, Locale.ITALY);
        return m.year == now.get(Calendar.YEAR) && m.month == now.get(Calendar.MONTH) + 1;
    }
}
