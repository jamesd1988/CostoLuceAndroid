package com.costoluce.importatore;

import org.json.JSONException;
import org.json.JSONObject;

/** Same variable-cost calculation as the local HTML calculator. */
final class WidgetPricing {
    private static final String[] KEYS = {
            "energy", "losses", "dispatch", "balancing", "transport",
            "equalization", "asos", "arim", "excise", "vat"
    };
    private final double[] values;
    private final boolean resident;

    private WidgetPricing(double[] values, boolean resident) {
        this.values = values;
        this.resident = resident;
    }

    static WidgetPricing parse(String serialized, boolean resident) throws JSONException {
        JSONObject rates = new JSONObject(serialized);
        double[] values = new double[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            if (!rates.has(KEYS[i]) || !(rates.opt(KEYS[i]) instanceof Number))
                throw new JSONException("Corrispettivo non disponibile: " + KEYS[i]);
            double value = rates.getDouble(KEYS[i]);
            if (!Double.isFinite(value) || value < 0 || value > 1000)
                throw new JSONException("Corrispettivo non valido: " + KEYS[i]);
            values[i] = value;
        }
        if (values[1] > 100 || values[9] > 100)
            throw new JSONException("Percentuale non valida");
        return new WidgetPricing(values, resident);
    }

    private static double taxableMonthly(double kwh) {
        if (kwh <= 150) return 0;
        if (kwh <= 220) return kwh - 150;
        if (kwh <= 370) return 2 * kwh - 370;
        return kwh;
    }

    double estimatedCost(double kwh, int months) {
        if (!Double.isFinite(kwh) || kwh < 0 || kwh >= 1_000_000
                || (months != 1 && months != 2))
            throw new IllegalArgumentException("Consumo o periodo non valido.");
        double taxKwh = resident ? months * taxableMonthly(kwh / months) : kwh;
        double preVat = kwh * (
                values[0] + values[0] * values[1] / 100
                + values[2] + values[3] + values[4] + values[5] + values[6] + values[7])
                + taxKwh * values[8];
        double total = preVat * (1 + values[9] / 100);
        if (!Double.isFinite(total) || total > 10_000_000)
            throw new IllegalArgumentException("Costo calcolato fuori intervallo.");
        return total;
    }
}
