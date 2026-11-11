package com.costoluce.importatore;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * User-triggered widget refresh. The last verified values remain visible if Enel fails.
 * Stores only kWh, estimates, tariffs and timestamps: no passwords or POD.
 */
public final class CostoLuceWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "costoluce_widget_snapshot_v1";
    static final String ACTION_REFRESH = "com.costoluce.cloud.action.WIDGET_REFRESH";
    private static final int REFRESH_JOB_ID = 225901;
    private static final String KEY_KWH = "kwh";
    private static final String KEY_EUR = "estimated_euro";
    private static final String KEY_MONTHS = "months"; // Period of the last actual consumption
    private static final String KEY_CONFIG_MONTHS = "configured_months"; // Period requested next
    private static final String KEY_SOURCE = "source";
    private static final String KEY_PERIOD = "period";
    private static final String KEY_TIMESTAMP = "saved_at";
    private static final String KEY_RATES = "rates_json";
    private static final String KEY_RESIDENT = "resident";
    private static final String KEY_MONTHLY = "monthly_json";
    private static final String KEY_STATUS = "refresh_status";
    private static final String KEY_BUSY = "refresh_busy";
    private static final String KEY_LAST_REQUEST = "last_request_at";
    private static final Locale IT = Locale.ITALY;

    static final class WidgetConfiguration {
        final String ratesJson;
        final boolean resident;
        final int months;
        WidgetConfiguration(String ratesJson, boolean resident, int months) {
            this.ratesJson = ratesJson;
            this.resident = resident;
            this.months = months;
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static WidgetConfiguration readConfiguration(Context context) {
        SharedPreferences p = prefs(context);
        String rates = p.getString(KEY_RATES, null);
        int months = p.getInt(KEY_CONFIG_MONTHS, p.getInt(KEY_MONTHS, 2));
        if (rates == null || (months != 1 && months != 2)) return null;
        return new WidgetConfiguration(rates, p.getBoolean(KEY_RESIDENT, false), months);
    }

    /** Shared by both in-app sync and the one-tap Home widget refresh. */
    static boolean saveResultFromEnel(Context context, EnelConnector.Result result, int months)
            throws JSONException {
        WidgetConfiguration config = readConfiguration(context);
        if (config == null || config.months != months || result == null
                || result.monthly == null || result.monthly.size() != months)
            return false;
        WidgetPricing pricing = WidgetPricing.parse(config.ratesJson, config.resident);
        double cost = pricing.estimatedCost(result.kwh, months);
        org.json.JSONArray rows = new org.json.JSONArray();
        boolean partial = false;
        StringBuilder period = new StringBuilder();
        String[] names = new java.text.DateFormatSymbols(IT).getMonths();
        for (EnelConnector.MonthUsage month : result.monthly) {
            JSONObject row = new JSONObject();
            row.put("year", month.year);
            row.put("month", month.month);
            row.put("kwh", month.kwh);
            row.put("days", month.days);
            row.put("expectedDays", month.expectedDays);
            row.put("hourSamples", month.hourSamples);
            rows.put(row);
            if (month.days < month.expectedDays || month.hourSamples < month.days * 23)
                partial = true;
            if (period.length() > 0) period.append(" + ");
            period.append(names[month.month - 1]).append(' ').append(month.year);
        }
        return saveFetchedResult(context, result.kwh, cost, months,
                period.toString(), rows.toString(), partial);
    }

    /**
     * Configure the widget without making up consumption data. A first Enel
     * fetch from the Home widget can then populate the first real snapshot.
     * This does not change the date or value of any previous measured usage.
     */
    static boolean saveConfiguration(Context context, int months, String ratesJson, boolean resident) {
        if (months != 1 && months != 2 || ratesJson == null || ratesJson.length() > 1500)
            return false;
        final WidgetPricing pricing;
        try { pricing = WidgetPricing.parse(ratesJson, resident); }
        catch (Exception invalidRates) { return false; }

        SharedPreferences p = prefs(context);
        SharedPreferences.Editor edit = p.edit()
                .putInt(KEY_CONFIG_MONTHS, months)
                .putString(KEY_RATES, ratesJson)
                .putBoolean(KEY_RESIDENT, resident);

        // Price-only edits recompute the old kWh, without changing the time
        // when those kWh were actually acquired.
        if (p.getLong(KEY_TIMESTAMP, 0L) > 0 && p.getInt(KEY_MONTHS, 2) == months) {
            try {
                double lastKwh = Double.parseDouble(p.getString(KEY_KWH, ""));
                if (Double.isFinite(lastKwh) && lastKwh > 0)
                    edit.putString(KEY_EUR, Double.toString(pricing.estimatedCost(lastKwh, months)));
            } catch (RuntimeException ignored) { }
        }

        // Keep the previous fetch error/partial-data warning if measurements exist.
        if (p.getLong(KEY_TIMESTAMP, 0L) <= 0) {
            edit.putString(KEY_STATUS, new EnelCredentialStore(context).hasSaved()
                    ? "Pronto: premi ↻ per leggere i kWh Enel."
                    : "Impostazioni pronte. Collega Enel per i consumi.");
        }
        boolean ok = edit.commit();
        if (ok) refresh(context);
        return ok;
    }

    /** Called from a trusted, local WebView navigation, after the user taps Save. */
    static boolean saveSnapshot(Context context, double kwh, double euro, int months,
                                String source, String period, String ratesJson, boolean resident) {
        if (!valid(kwh, euro, months, period)
                || (!"manuale".equals(source) && !"file".equals(source)
                    && !"portale".equals(source) && !"enel".equals(source)))
            return false;
        try {
            WidgetPricing pricing = WidgetPricing.parse(ratesJson, resident);
            // Native and JavaScript calculations should agree to the cent.
            if (Math.abs(pricing.estimatedCost(kwh, months) - euro) > 0.025) return false;
        } catch (Exception ex) {
            return false;
        }
        SharedPreferences previous = prefs(context);
        // Avoid claiming fresh Enel readings when the UI merely redraws
        // or the user changes a tariff. Same measurement = same acquisition time.
        boolean sameMeasurement = previous.getLong(KEY_TIMESTAMP, 0L) > 0
                && previous.getInt(KEY_MONTHS, 2) == months
                && source.equals(previous.getString(KEY_SOURCE, ""))
                && period.equals(previous.getString(KEY_PERIOD, ""));
        if (sameMeasurement) {
            try {
                sameMeasurement = Math.abs(kwh -
                        Double.parseDouble(previous.getString(KEY_KWH, ""))) <= 0.0005;
            } catch (RuntimeException invalid) { sameMeasurement = false; }
        }
        long at = sameMeasurement ? previous.getLong(KEY_TIMESTAMP, 0L)
                : System.currentTimeMillis();
        SharedPreferences.Editor edit = previous.edit()
                .putString(KEY_KWH, Double.toString(kwh))
                .putString(KEY_EUR, Double.toString(euro))
                .putInt(KEY_MONTHS, months)
                .putInt(KEY_CONFIG_MONTHS, months)
                .putString(KEY_SOURCE, source)
                .putString(KEY_PERIOD, period)
                .putString(KEY_RATES, ratesJson)
                .putBoolean(KEY_RESIDENT, resident)
                .putLong(KEY_TIMESTAMP, at)
                .putBoolean(KEY_BUSY, false);
        if (!sameMeasurement) {
            edit.remove(KEY_MONTHLY).putString(KEY_STATUS, "");
        }
        boolean ok = edit.commit();
        if (ok) refresh(context);
        return ok;
    }

    /** Called only after an actual successful background consumption fetch. */
    static boolean saveFetchedResult(Context context, double kwh, double euro, int months,
                                     String period, String monthlyJson, boolean partial) {
        if (!valid(kwh, euro, months, period) || monthlyJson == null
                || monthlyJson.length() > 2400)
            return false;
        // Never write a synthetic zero or a response without the expected months.
        try {
            org.json.JSONArray monthsData = new org.json.JSONArray(monthlyJson);
            if (monthsData.length() != months) return false;
        } catch (JSONException ex) { return false; }
        boolean ok = prefs(context).edit()
                .putString(KEY_KWH, Double.toString(kwh))
                .putString(KEY_EUR, Double.toString(euro))
                .putInt(KEY_MONTHS, months)
                .putInt(KEY_CONFIG_MONTHS, months)
                .putString(KEY_SOURCE, "enel")
                .putString(KEY_PERIOD, period)
                .putString(KEY_MONTHLY, monthlyJson)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_STATUS, partial ? "Attenzione: consumi parziali" : "")
                .putBoolean(KEY_BUSY, false)
                .commit();
        if (ok) refresh(context);
        return ok;
    }

    /** Current settings are separate from the period of the last actual measurement. */
    static JSONObject getWidgetConfiguration(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getString(KEY_RATES, null) == null) return null;
        try {
            JSONObject state = new JSONObject();
            state.put("months", p.getInt(KEY_CONFIG_MONTHS, p.getInt(KEY_MONTHS, 2)));
            state.put("resident", p.getBoolean(KEY_RESIDENT, false));
            return state;
        } catch (JSONException ignored) { return null; }
    }

    static JSONObject getLatestSnapshot(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(KEY_TIMESTAMP, 0L) <= 0) return null;
        try {
            JSONObject data = new JSONObject();
            data.put("kwh", Double.parseDouble(p.getString(KEY_KWH, "")));
            data.put("euro", Double.parseDouble(p.getString(KEY_EUR, "")));
            data.put("months", p.getInt(KEY_MONTHS, 2));
            data.put("source", p.getString(KEY_SOURCE, "manuale"));
            data.put("period", p.getString(KEY_PERIOD, ""));
            String monthly = p.getString(KEY_MONTHLY, "");
            if (monthly != null && !monthly.isEmpty())
                data.put("monthly", new org.json.JSONArray(monthly));
            return data;
        } catch (Exception ignored) { return null; }
    }

    private static boolean valid(double kwh, double euro, int months, String period) {
        return Double.isFinite(kwh) && kwh > 0 && kwh < 1_000_000
                && Double.isFinite(euro) && euro >= 0 && euro <= 10_000_000
                && (months == 1 || months == 2)
                && period != null && period.length() <= 90
                && period.indexOf('\n') < 0 && period.indexOf('\r') < 0;
    }

    static void setRefreshStatus(Context context, String status, boolean busy) {
        prefs(context).edit().putString(KEY_STATUS, status).putBoolean(KEY_BUSY, busy).apply();
        refresh(context);
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CostoLuceWidgetProvider.class));
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        super.onUpdate(context, manager, ids);
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            requestDataRefresh(context);
            return;
        }
        super.onReceive(context, intent);
    }

    static void requestDataRefresh(Context context) {
        // Confirm every actual tap before checking any credentials or network state.
        String time = new SimpleDateFormat("HH:mm:ss", IT).format(new Date());
        SharedPreferences p = prefs(context);
        // Acknowledge immediately without clearing the existing busy flag.
        p.edit().putString(KEY_STATUS, "↻ Tasto premuto alle " + time).apply();
        refresh(context);
        long now = System.currentTimeMillis();
        long last = p.getLong(KEY_LAST_REQUEST, 0L);
        if (p.getBoolean(KEY_BUSY, false) && now - last < 110_000L) {
            setRefreshStatus(context, "Aggiornamento gia in corso...", true);
            return;
        }
        if (last > 0 && now >= last && now - last < 20_000L) {
            setRefreshStatus(context, "Riprova tra qualche secondo.", false);
            return;
        }
        if (readConfiguration(context) == null) {
            setRefreshStatus(context, "Apri l'app e salva tariffe e consumi.", false);
            return;
        }
        if (!new EnelCredentialStore(context).hasSaved()) {
            setRefreshStatus(context, "Enel non collegato: salva le credenziali nell'app.", false);
            return;
        }
        p.edit().putLong(KEY_LAST_REQUEST, now).commit();
        setRefreshStatus(context, "↻ Aggiornamento richiesto: attendo la rete...", true);
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            setRefreshStatus(context, "Aggiornamento Android non disponibile.", false);
            return;
        }
        try {
            int response = JobScheduler.RESULT_FAILURE;
            if (Build.VERSION.SDK_INT >= 31) {
                // User-initiated expedited work when available. If the platform
                // rejects it, fall back to an ordinary network-constrained job.
                try {
                    response = scheduler.schedule(new JobInfo.Builder(REFRESH_JOB_ID,
                            new ComponentName(context, WidgetUpdateJobService.class))
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setExpedited(true).build());
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                    response = JobScheduler.RESULT_FAILURE;
                }
            }
            if (response != JobScheduler.RESULT_SUCCESS) {
                response = scheduler.schedule(new JobInfo.Builder(REFRESH_JOB_ID,
                        new ComponentName(context, WidgetUpdateJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
            }
            if (response != JobScheduler.RESULT_SUCCESS)
                setRefreshStatus(context, "Android ha rifiutato l'aggiornamento: riprova.", false);
        } catch (SecurityException e) {
            setRefreshStatus(context, "Impossibile usare la rete: permessi Android.", false);
        } catch (RuntimeException e) {
            setRefreshStatus(context, "Impossibile programmare il recupero Enel.", false);
        }
    }

    private static void render(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.costoluce_widget);
        SharedPreferences p = prefs(context);
        long savedAt = p.getLong(KEY_TIMESTAMP, 0L);
        if (savedAt <= 0L) {
            views.setTextViewText(R.id.widget_euro, "—");
            views.setTextViewText(R.id.widget_kwh, "Nessun consumo disponibile");
            if (readConfiguration(context) == null) {
                views.setTextViewText(R.id.widget_period, "Configura le tariffe nell'app");
                views.setTextViewText(R.id.widget_updated, "Prima configurazione necessaria");
            } else {
                views.setTextViewText(R.id.widget_period, "Pronto per il recupero dei consumi");
                views.setTextViewText(R.id.widget_updated, "I dati compariranno dopo la prima lettura");
            }
        } else {
            try {
                double kwh = Double.parseDouble(p.getString(KEY_KWH, ""));
                double euro = Double.parseDouble(p.getString(KEY_EUR, ""));
                if (!Double.isFinite(kwh) || !Double.isFinite(euro) || kwh <= 0 || euro < 0)
                    throw new NumberFormatException();
                NumberFormat money = NumberFormat.getCurrencyInstance(IT);
                NumberFormat consumption = NumberFormat.getNumberInstance(IT);
                consumption.setMaximumFractionDigits(1);
                consumption.setMinimumFractionDigits(0);
                String period = p.getString(KEY_PERIOD, "");
                String source = p.getString(KEY_SOURCE, "manuale");
                String sourceText;
                switch (source) {
                    case "enel": sourceText = "Enel"; break;
                    case "file": sourceText = "Da file"; break;
                    case "portale": sourceText = "Portale"; break;
                    default: sourceText = "Manuale"; break;
                }
                if (period == null || period.trim().isEmpty())
                    period = p.getInt(KEY_MONTHS, 2) + " mesi selezionati";
                views.setTextViewText(R.id.widget_euro, money.format(euro));
                views.setTextViewText(R.id.widget_kwh, consumption.format(kwh) + " kWh");
                views.setTextViewText(R.id.widget_period, period);
                SimpleDateFormat date = new SimpleDateFormat("dd/MM 'alle' HH:mm", IT);
                views.setTextViewText(R.id.widget_updated, sourceText + " · dati del " + date.format(new Date(savedAt)));
            } catch (RuntimeException ignored) {
                views.setTextViewText(R.id.widget_euro, "—");
                views.setTextViewText(R.id.widget_kwh, "Dato non valido");
                views.setTextViewText(R.id.widget_period, "Apri l'app e aggiorna");
                views.setTextViewText(R.id.widget_updated, "Dati da riconfigurare");
            }
        }
        String status = p.getString(KEY_STATUS, "");
        views.setTextViewText(R.id.widget_status,
                status == null || status.isEmpty() ? "↻  Aggiorna consumi e costo" : status);
        views.setTextViewText(R.id.widget_refresh,
                p.getBoolean(KEY_BUSY, false) ? "…" : "↻");

        // No click on widget_root: some launchers prefer the parent's click
        // over the refresh button's click, making refresh look unresponsive.
        Intent open = new Intent(context, MainActivity.class);
        open.setAction("com.costoluce.cloud.action.OPEN_WIDGET");
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_brand, openPending);
        views.setOnClickPendingIntent(R.id.widget_euro, openPending);
        views.setOnClickPendingIntent(R.id.widget_kwh, openPending);
        views.setOnClickPendingIntent(R.id.widget_period, openPending);
        views.setOnClickPendingIntent(R.id.widget_updated, openPending);

        // Clicking Refresh must NEVER launch an Activity. Deliver the user's
        // interaction to this explicit AppWidgetProvider broadcast receiver,
        // then enqueue the Enel network JobService without a UI screen.
        Intent refreshIntent = new Intent(context, CostoLuceWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 1002, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending);
        views.setOnClickPendingIntent(R.id.widget_status, refreshPending);
        manager.updateAppWidget(widgetId, views);
    }
}
