package com.costoluce.importatore;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Home-screen widget: displays the last summary explicitly saved from Costo Luce.
 * Does not fetch data from Enel or show the example value as a real measurement.
 */
public final class CostoLuceWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "costoluce_widget_snapshot_v1";
    private static final String KEY_KWH = "kwh";
    private static final String KEY_EUR = "estimated_euro";
    private static final String KEY_MONTHS = "months";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_PERIOD = "period";
    private static final String KEY_TIMESTAMP = "saved_at";
    private static final Locale IT = Locale.ITALY;

    /** Only a validated, user-requested snapshot is saved. No credentials or POD. */
    static boolean saveSnapshot(Context context, double kwh, double euro, int months, String source, String period) {
        if (!Double.isFinite(kwh) || kwh <= 0 || kwh >= 1_000_000
                || !Double.isFinite(euro) || euro < 0 || euro > 10_000_000
                || (months != 1 && months != 2)) return false;
        if (!"manuale".equals(source) && !"file".equals(source)
                && !"portale".equals(source) && !"enel".equals(source)) return false;
        if (period == null || period.length() > 56 || period.indexOf('\n') >= 0
                || period.indexOf('\r') >= 0) return false;

        boolean ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_KWH, Double.toString(kwh))
                .putString(KEY_EUR, Double.toString(euro))
                .putInt(KEY_MONTHS, months)
                .putString(KEY_SOURCE, source)
                .putString(KEY_PERIOD, period)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .commit();
        if (ok) refresh(context);
        return ok;
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, CostoLuceWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        super.onUpdate(context, manager, ids);
        for (int id : ids) render(context, manager, id);
    }

    private static void render(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.costoluce_widget);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long savedAt = prefs.getLong(KEY_TIMESTAMP, 0L);
        if (savedAt <= 0L) {
            views.setTextViewText(R.id.widget_euro, "—");
            views.setTextViewText(R.id.widget_kwh, "Nessun dato");
            views.setTextViewText(R.id.widget_period, "Apri l'app e salva i consumi");
            views.setTextViewText(R.id.widget_updated, "Non ancora aggiornato");
        } else {
            try {
                double kwh = Double.parseDouble(prefs.getString(KEY_KWH, ""));
                double euro = Double.parseDouble(prefs.getString(KEY_EUR, ""));
                if (!Double.isFinite(kwh) || !Double.isFinite(euro) || kwh <= 0 || euro < 0)
                    throw new NumberFormatException("Invalid stored summary");
                NumberFormat money = NumberFormat.getCurrencyInstance(IT);
                NumberFormat consumption = NumberFormat.getNumberInstance(IT);
                consumption.setMaximumFractionDigits(1);
                consumption.setMinimumFractionDigits(0);
                String period = prefs.getString(KEY_PERIOD, "");
                String source = prefs.getString(KEY_SOURCE, "manuale");
                String sourceText;
                switch (source) {
                    case "enel": sourceText = "Enel"; break;
                    case "file": sourceText = "Da file"; break;
                    case "portale": sourceText = "Portale"; break;
                    default: sourceText = "Manuale"; break;
                }
                if (period == null || period.trim().isEmpty())
                    period = prefs.getInt(KEY_MONTHS, 2) + " mesi selezionati";
                views.setTextViewText(R.id.widget_euro, money.format(euro));
                views.setTextViewText(R.id.widget_kwh, consumption.format(kwh) + " kWh");
                views.setTextViewText(R.id.widget_period, period);
                SimpleDateFormat date = new SimpleDateFormat("dd/MM 'alle' HH:mm", IT);
                views.setTextViewText(R.id.widget_updated, sourceText + " · salvato " + date.format(new Date(savedAt)));
            } catch (RuntimeException ignored) {
                views.setTextViewText(R.id.widget_euro, "—");
                views.setTextViewText(R.id.widget_kwh, "Dato non valido");
                views.setTextViewText(R.id.widget_period, "Apri l'app e aggiorna");
                views.setTextViewText(R.id.widget_updated, "Dati da riconfigurare");
            }
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setAction(Intent.ACTION_MAIN);
        open.addCategory(Intent.CATEGORY_LAUNCHER);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        manager.updateAppWidget(widgetId, views);
    }
}
