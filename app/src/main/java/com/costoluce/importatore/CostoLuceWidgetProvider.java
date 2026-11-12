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
    static final String ACTION_UNIT = "com.costoluce.cloud.action.WIDGET_UNIT";
    static final String ACTION_PAGE = "com.costoluce.cloud.action.WIDGET_PAGE";
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
        saveHistory(context, result);
        WidgetConfiguration config = readConfiguration(context);
        if (config == null || config.months != months || result == null
                || result.monthly == null || result.monthly.size() != months)
            return false;
        WidgetPricing pricing = WidgetPricing.parse(config.ratesJson, config.resident);
        org.json.JSONArray rows = new org.json.JSONArray();
        double monthlyTaxable = 0;
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
            row.put("future", month.future);
            row.put("lastMeasuredDay", month.lastMeasuredDay);
            rows.put(row);
            // The current billing month remains in progress even if every day
            // elapsed so far has supplied measurements.
            if (month.future || BillingPeriod.currentMonth(month)
                    || month.days < month.expectedDays
                    || month.hourSamples < month.days * 23)
                partial = true;
            monthlyTaxable += WidgetPricing.taxableMonthly(month.kwh);
            if (period.length() > 0) period.append(" + ");
            period.append(names[month.month - 1]).append(' ').append(month.year);
        }
        double cost = pricing.estimatedCost(result.kwh, months, monthlyTaxable, activeBillingMonths(rows.toString(),months));
        return saveFetchedResult(context, result.kwh, cost, months,
                period.toString(), rows.toString(), partial);
    }

    static void saveHistory(Context context, EnelConnector.Result result) throws JSONException {
        if (result != null && !result.history.isEmpty() && result.history.size() <= 4) {
            prefs(context).edit().putString("history_json", HistoryMonths.serialize(result.history).toString())
                    .putLong("history_at",System.currentTimeMillis()).commit();
            refresh(context);
        }
    }
    static org.json.JSONArray history(Context context) {
        SharedPreferences p = prefs(context);
        // Upgrade: reuse the verified Enel months from 5.x until the first wider fetch.
        // Manual totals cannot supply a monthly history.
        String previous = "enel".equals(p.getString(KEY_SOURCE,""))
                ? p.getString(KEY_MONTHLY,"[]") : "[]";
        try { return HistoryMonths.slots(p.getString("history_json", previous), java.util.Calendar.getInstance()); }
        catch (JSONException e) { return new org.json.JSONArray(); }
    }
    static void saveUnit(Context context, String unit) {
        if (!"kwh".equals(unit) && !"eur".equals(unit)) return;
        prefs(context).edit().putString("display_unit", unit).apply(); refresh(context);
    }
    /**
     * Configure the widget without making up consumption data. A first Enel
     * fetch from the Home widget can then populate the first real snapshot.
     * This does not change the date or value of any previous measured usage.
     */
    static boolean saveConfiguration(Context context, int months, String ratesJson, boolean resident) {
        if (months != 1 && months != 2 || ratesJson == null || ratesJson.length() > 16000)
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
                    edit.putString(KEY_EUR, Double.toString(pricing.estimatedCost(lastKwh,
                            months, taxableFromMonthly(p.getString(KEY_MONTHLY, null),
                                    lastKwh, months), activeBillingMonths(p.getString(KEY_MONTHLY,null),months))));
            } catch (Exception ignored) { }
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

    private static int activeBillingMonths(String monthlyJson,int months) throws JSONException {
        if(monthlyJson==null||monthlyJson.isEmpty())return months;
        org.json.JSONArray rows=new org.json.JSONArray(monthlyJson);if(rows.length()!=months)throw new JSONException("Periodo non valido.");
        int active=0;for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).optBoolean("future",false))active++;return active;
    }

    private static double taxableFromMonthly(String monthlyJson, double total, int months)
            throws JSONException {
        if (monthlyJson == null || monthlyJson.isEmpty())
            return months * WidgetPricing.taxableMonthly(total / months);
        org.json.JSONArray entries = new org.json.JSONArray(monthlyJson);
        if (entries.length() != months || monthlyJson.length() > 2500)
            throw new JSONException("Numero mensilita non corretto.");
        double sum = 0;
        double taxable = 0;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject row = entries.getJSONObject(i);
            double value = row.getDouble("kwh");
            int year = row.getInt("year");
            int month = row.getInt("month");
            if (!Double.isFinite(value) || value < 0 || value > 1_000_000 ||
                    year < 2000 || year > 2100 || month < 1 || month > 12)
                throw new JSONException("Dettaglio mensile non valido.");
            sum += value;
            taxable += WidgetPricing.taxableMonthly(value);
        }
        if (Math.abs(sum - total) > 0.02)
            throw new JSONException("Somma mensile diversa dal consumo totale.");
        return taxable;
    }

    /** Called from a trusted, local WebView navigation, after the user taps Save. */
    static boolean saveSnapshot(Context context, double kwh, double euro, int months,
                                String source, String period, String ratesJson, boolean resident,
                                String monthlyJson) {
        if (!valid(kwh, euro, months, period)
                || (!"manuale".equals(source) && !"file".equals(source)
                    && !"portale".equals(source) && !"enel".equals(source)))
            return false;
        if (!"enel".equals(source)) monthlyJson = null;
        try {
            WidgetPricing pricing = WidgetPricing.parse(ratesJson, resident);
            double taxable = taxableFromMonthly(monthlyJson, kwh, months);
            if (Math.abs(pricing.estimatedCost(kwh, months, taxable, activeBillingMonths(monthlyJson,months)) - euro) > 0.025)
                return false;
        } catch (Exception ex) {
            return false;
        }
        SharedPreferences previous = prefs(context);
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
        if (monthlyJson != null) edit.putString(KEY_MONTHLY, monthlyJson);
        else if (!sameMeasurement) edit.remove(KEY_MONTHLY);
        if (!sameMeasurement) {
            if ("enel".equals(source) && monthlyJson != null)
                edit.putString(KEY_STATUS, "Ciclo in corso · consumi disponibili");
            else edit.putString(KEY_STATUS, "");
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
                .putString(KEY_STATUS, partial ? "Bimestre in corso · costo non definitivo" : "")
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
            state.put("rates", new JSONObject(p.getString(KEY_RATES,"{}")));
            state.put("unit", p.getString("display_unit", "eur"));
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

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, android.os.Bundle options) {
        manager.updateAppWidget(id,createViews(context,id,options==null?new android.os.Bundle():options));
    }
    @Override public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor e=prefs(context).edit();for(int id:ids)e.remove("page_"+id);e.apply();
    }
    @Override public void onDisabled(Context context) {
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs!=null)jobs.cancel(225902);
        super.onDisabled(context);
    }
    private static void schedulePeriodic(Context context) {
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs!=null && jobs.getPendingJob(225902)==null) {
            try { jobs.schedule(new JobInfo.Builder(225902,new ComponentName(context,WidgetUpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(6*60*60*1000L).build()); }
            catch(RuntimeException ignored) { }
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        super.onUpdate(context, manager, ids);
        schedulePeriodic(context);
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_UNIT.equals(intent.getAction())) { saveUnit(context,intent.getStringExtra("unit"));return; }
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            requestDataRefresh(context);
            return;
        }
        if (intent != null && ACTION_PAGE.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info != null && info.provider.equals(new ComponentName(context, CostoLuceWidgetProvider.class))) {
                prefs(context).edit().putInt("page_"+id,intent.getIntExtra("page",0)==1?1:0).apply();
                render(context,manager,id);
            }
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
        manager.updateAppWidget(widgetId, createViews(context,widgetId));
    }

    /** API 31+ gets exact launcher sizes; older launchers get both orientation bounds. */
    static RemoteViews createViews(Context context,int widgetId){
        android.os.Bundle options=AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId);
        return createViews(context,widgetId,options==null?new android.os.Bundle():options);
    }
    static RemoteViews createViews(Context context,int widgetId,android.os.Bundle options){
        if(android.os.Build.VERSION.SDK_INT>=31){
            java.util.ArrayList<android.util.SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if(sizes!=null&&!sizes.isEmpty()){
                java.util.Map<android.util.SizeF,RemoteViews> variants=new java.util.LinkedHashMap<>();
                for(android.util.SizeF size:sizes){
                    if(size==null||!Float.isFinite(size.getWidth())||!Float.isFinite(size.getHeight())||size.getWidth()<64||size.getHeight()<32||size.getWidth()>1600||size.getHeight()>1600)continue;
                    variants.put(size,createViewsForSize(context,widgetId,Math.round(size.getWidth()),Math.round(size.getHeight()),bitmapBudget(context)/Math.min(16,sizes.size())));
                    if(variants.size()==16)break;
                }
                if(!variants.isEmpty())return new RemoteViews(variants);
            }
        }
        int minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,WidgetLayoutSpec.DEFAULT_WIDTH),minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,WidgetLayoutSpec.DEFAULT_HEIGHT);
        if(minW<64)minW=WidgetLayoutSpec.DEFAULT_WIDTH;if(minH<32)minH=WidgetLayoutSpec.DEFAULT_HEIGHT;
        int maxW=Math.max(minW,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW)),maxH=Math.max(minH,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH));
        if(minW==maxW&&minH==maxH)return createViewsForSize(context,widgetId,minW,minH);
        return new RemoteViews(createViewsForSize(context,widgetId,maxW,minH,bitmapBudget(context)/2),createViewsForSize(context,widgetId,minW,maxH,bitmapBudget(context)/2));
    }
    private static String compactPeriod(org.json.JSONArray rows){
        JSONObject last=rows.optJSONObject(rows.length()-1);if(last==null)return "Periodo da aggiornare";
        StringBuilder result=new StringBuilder();int lastYear=last.optInt("year");
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;if(result.length()>0)result.append(" + ");result.append(MonthlyChart.label(r));
            if(r.optInt("year")!=lastYear)result.append(' ').append(r.optInt("year"));
        }
        return result.append(' ').append(lastYear).toString();
    }
    private static int bitmapBudget(Context context){
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        return Math.max(50_000,Math.min(1_000_000,metrics.widthPixels*metrics.heightPixels));
    }
    private static void applySize(Context context,RemoteViews views,WidgetLayoutSpec spec,boolean chart,long savedAt,int bitmapPixels){
        SharedPreferences p=prefs(context);boolean busy=p.getBoolean(KEY_BUSY,false);
        String status=p.getString(KEY_STATUS,"");boolean showStatus=!spec.compact()||busy||(status!=null&&!status.isEmpty());
        // RemoteViews padding is expressed in physical pixels.
        float density=context.getResources().getDisplayMetrics().density;
        int pad=Math.round(spec.padding*density);views.setViewPadding(R.id.widget_root,pad,pad,pad,pad);
        views.setTextViewTextSize(R.id.widget_brand,android.util.TypedValue.COMPLEX_UNIT_SP,spec.compact()?13:15);
        views.setTextViewTextSize(R.id.widget_euro,android.util.TypedValue.COMPLEX_UNIT_SP,spec.compact()?32:spec.detailed()?42:38);
        views.setTextViewTextSize(R.id.widget_kwh,android.util.TypedValue.COMPLEX_UNIT_SP,spec.compact()?19:22);
        views.setViewVisibility(R.id.widget_caption,spec.compact()?android.view.View.GONE:android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_source,spec.detailed()?android.view.View.VISIBLE:android.view.View.GONE);
        views.setViewVisibility(R.id.widget_status,showStatus?android.view.View.VISIBLE:android.view.View.GONE);
        views.setContentDescription(R.id.widget_status,status==null||status.isEmpty()?"Aggiorna i consumi Enel":status+". Tocca per aggiornare.");
        String date=savedAt>0?new SimpleDateFormat(spec.compact()?"HH:mm":"dd/MM · HH:mm",IT).format(new Date(savedAt)):"";
        if(!spec.detailed()&&savedAt>0)views.setTextViewText(R.id.widget_updated,(spec.compact()?"Agg. ":"Ultimo aggiornamento · ")+date);
        org.json.JSONArray history=history(context);String years="";
        if(history.length()>0){JSONObject first=history.optJSONObject(0),last=history.optJSONObject(history.length()-1);if(first!=null&&last!=null)years=first.optInt("year")==last.optInt("year")?Integer.toString(last.optInt("year")):first.optInt("year")+"–"+last.optInt("year");}
        if(chart){
            views.setTextViewText(R.id.widget_brand,spec.compact()?"4 mesi\n"+years:spec.detailed()?"Consumi ultimi 4 mesi":"Ultimi 4 mesi\n"+years);
            boolean missing=false;for(int i=0;i<history.length();i++){JSONObject r=history.optJSONObject(i);if(r==null||!r.optBoolean("available"))missing=true;}
            JSONObject last=history.optJSONObject(history.length()-1);
            String note=last!=null&&last.optBoolean("partial")?"* "+MonthlyChart.label(last)+" parziale":"";
            if(missing)note+=(note.isEmpty()?"":" · ")+"— dato mancante";
            if(!spec.compact()&&savedAt>0)note+=(note.isEmpty()?"":" · ")+"Agg. "+date;
            views.setTextViewText(R.id.widget_chart_note,note.isEmpty()?"Misure mensili Enel":note);
            views.setViewVisibility(R.id.widget_chart_note,android.view.View.VISIBLE);
            float fontScale=context.getResources().getConfiguration().fontScale;
            views.setImageViewBitmap(R.id.widget_chart,MonthlyChart.draw(context,history,spec,spec.chartHeight(fontScale,showStatus),bitmapPixels));
        }else{
            views.setTextViewText(R.id.widget_brand,spec.compact()?"Costo Luce":"Costo Luce · v6.4.1");
            views.setViewVisibility(R.id.widget_chart_note,android.view.View.GONE);
            if("enel".equals(p.getString(KEY_SOURCE,"")))try{org.json.JSONArray rows=new org.json.JSONArray(p.getString(KEY_MONTHLY,"[]"));if(rows.length()>0)views.setTextViewText(R.id.widget_period,compactPeriod(rows));}catch(Exception ignored){}
        }
    }

    /** One-cell-high and intermediate layouts have their own structure, not a shrunken full widget. */
    private static RemoteViews createSmallViews(Context context,WidgetLayoutSpec spec){
        int layout=spec.statsOnly()?(spec.wideStats()?R.layout.costoluce_widget_stats_wide:R.layout.costoluce_widget_stats):R.layout.costoluce_widget_small_summary;
        RemoteViews views=new RemoteViews(context.getPackageName(),layout);SharedPreferences p=prefs(context);
        long savedAt=p.getLong(KEY_TIMESTAMP,0);String energy="— kWh",cost="— €";
        if(savedAt>0)try{
            double kwh=Double.parseDouble(p.getString(KEY_KWH,"")),eur=Double.parseDouble(p.getString(KEY_EUR,""));
            if(!Double.isFinite(kwh)||!Double.isFinite(eur)||kwh<0||eur<0)throw new NumberFormatException();
            NumberFormat usage=NumberFormat.getNumberInstance(IT);usage.setMaximumFractionDigits(1);
            energy=usage.format(kwh)+" kWh";cost=NumberFormat.getCurrencyInstance(IT).format(eur);
        }catch(RuntimeException ignored){}
        views.setTextViewText(R.id.widget_small_kwh,energy);views.setTextViewText(R.id.widget_small_cost,cost);
        boolean refreshShown=spec.smallRefresh();
        if(spec.statsRefreshPossible()){
            float candidateWidth=spec.width-20-52;if(spec.wideStats())candidateWidth/=2;
            float usableHeight=spec.height-8,density=context.getResources().getDisplayMetrics().density;
            refreshShown=fitSmallValue(context,energy,candidateWidth,spec.wideStats()?usableHeight:usableHeight*.56f,22,true)>=11*density
                    &&fitSmallValue(context,cost,candidateWidth,spec.wideStats()?usableHeight:usableHeight*.44f,18,false)>=11*density;
        }
        String period=p.getString(KEY_PERIOD,"");if(period==null||period.trim().isEmpty())period="Periodo corrente";
        if("enel".equals(p.getString(KEY_SOURCE,"")))try{org.json.JSONArray rows=new org.json.JSONArray(p.getString(KEY_MONTHLY,"[]"));if(rows.length()>0)period=compactPeriod(rows);}catch(Exception ignored){}
        String date=savedAt>0?new SimpleDateFormat("dd/MM · HH:mm",IT).format(new Date(savedAt)):"In attesa di dati";
        String status=p.getString(KEY_STATUS,"");
        String description="Consumi: "+energy+". Costo stimato: "+cost+". "+period+". Ultimo aggiornamento: "+date+(status==null||status.isEmpty()?"":". "+status);
        views.setContentDescription(R.id.widget_small_root,description+". Tocca i valori per aprire Costo Luce.");
        Intent open=new Intent(context,MainActivity.class).setAction("com.costoluce.cloud.action.OPEN_WIDGET").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        // Clear a parent action left by a previous smaller rendering of the same layout.
        views.setOnClickPendingIntent(R.id.widget_small_root,null);
        if(spec.statsOnly()){
            if(!refreshShown)views.setOnClickPendingIntent(R.id.widget_small_root,openPending);
            for(int id:new int[]{R.id.widget_small_kwh,R.id.widget_small_cost})views.setOnClickPendingIntent(id,openPending);
        }
        else{
            if(!spec.smallRefresh())views.setOnClickPendingIntent(R.id.widget_small_root,openPending);
            views.setTextViewText(R.id.widget_small_period,period);
            views.setTextViewText(R.id.widget_small_updated,savedAt>0?"Agg. "+date:date);
            views.setViewVisibility(R.id.widget_small_updated,spec.smallDate()?android.view.View.VISIBLE:android.view.View.GONE);
            views.setViewVisibility(R.id.widget_small_refresh,spec.smallRefresh()?android.view.View.VISIBLE:android.view.View.GONE);
            for(int id:new int[]{R.id.widget_small_kwh,R.id.widget_small_cost,R.id.widget_small_period,R.id.widget_small_updated})views.setOnClickPendingIntent(id,openPending);
        }
        views.setViewVisibility(R.id.widget_small_refresh,refreshShown?android.view.View.VISIBLE:android.view.View.GONE);
        Intent refresh=new Intent(context,CostoLuceWidgetProvider.class).setAction(ACTION_REFRESH).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        views.setOnClickPendingIntent(R.id.widget_small_refresh,PendingIntent.getBroadcast(context,1002,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        views.setTextViewText(R.id.widget_small_refresh,p.getBoolean(KEY_BUSY,false)?"…":"↻");
        float fontScale=context.getResources().getConfiguration().fontScale;
        float available=spec.height-(spec.statsOnly()?8:16);
        if(spec.smallSummary())available-=(spec.smallRefresh()?48:Math.max(20,14*fontScale))+(spec.smallDate()?14*fontScale+4:0);
        float width=spec.width-20-(spec.statsOnly()&&refreshShown?52:0);
        if(spec.wideStats())width/=2;
        float primaryHeight=spec.wideStats()?available:available*.56f,secondaryHeight=spec.wideStats()?available:available*.44f;
        views.setTextViewTextSize(R.id.widget_small_kwh,android.util.TypedValue.COMPLEX_UNIT_PX,fitSmallValue(context,energy,width,primaryHeight,spec.statsOnly()?22:26,true));
        views.setTextViewTextSize(R.id.widget_small_cost,android.util.TypedValue.COMPLEX_UNIT_PX,fitSmallValue(context,cost,width,secondaryHeight,spec.statsOnly()?18:20,false));
        return views;
    }
    private static float fitSmallValue(Context context,String text,float widthDp,float heightDp,int desiredSp,boolean bold){
        android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics();
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));
        float pixels=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,desiredSp,dm);paint.setTextSize(pixels);
        android.graphics.Paint.FontMetrics fm=paint.getFontMetrics();
        float ratio=Math.min(1,Math.min(Math.max(1,(widthDp-1)*dm.density)/Math.max(1,paint.measureText(text)),Math.max(1,(heightDp-1)*dm.density)/(fm.descent-fm.ascent)));
        return pixels*ratio;
    }

    static RemoteViews createViewsForSize(Context context,int widgetId,int width,int height) {
        return createViewsForSize(context,widgetId,width,height,bitmapBudget(context));
    }
    private static RemoteViews createViewsForSize(Context context,int widgetId,int width,int height,int bitmapPixels) {
        WidgetLayoutSpec spec=new WidgetLayoutSpec(width,height);
        if(!spec.fullPages())return createSmallViews(context,spec);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.costoluce_widget);
        SharedPreferences p = prefs(context);
        long savedAt = p.getLong(KEY_TIMESTAMP, 0L);
        if (savedAt <= 0L) {
            views.setTextViewText(R.id.widget_euro, "—");
            views.setTextViewText(R.id.widget_kwh, "— kWh");
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
                views.setTextViewText(R.id.widget_source,sourceText + " · ultimo aggiornamento");
                views.setTextViewText(R.id.widget_updated,new SimpleDateFormat("dd/MM/yyyy, HH:mm",IT).format(new Date(savedAt)));
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
        boolean chart = p.getInt("page_"+widgetId,0)==1;
        views.setTextViewText(R.id.widget_brand,chart?"Consumi ultimi 4 mesi":"COSTO LUCE · v6.4.1");
        views.setViewVisibility(R.id.widget_units,chart?android.view.View.VISIBLE:android.view.View.GONE);
        views.setViewVisibility(R.id.widget_refresh,chart?android.view.View.GONE:android.view.View.VISIBLE);
        boolean showKwh="kwh".equals(p.getString("display_unit","eur"));
        views.setInt(R.id.widget_unit_kwh,"setBackgroundResource",showKwh?R.drawable.costoluce_widget_pill_selected:R.drawable.costoluce_widget_pill_idle);
        views.setInt(R.id.widget_unit_eur,"setBackgroundResource",showKwh?R.drawable.costoluce_widget_pill_idle:R.drawable.costoluce_widget_pill_selected);
        String[] units={"kwh","eur"};int[] buttons={R.id.widget_unit_kwh,R.id.widget_unit_eur};
        for(int i=0;i<2;i++){
            Intent unit=new Intent(context,CostoLuceWidgetProvider.class).setAction(ACTION_UNIT).putExtra("unit",units[i])
                    .setData(android.net.Uri.parse("costoluce://widget-unit/"+units[i]));
            views.setOnClickPendingIntent(buttons[i],PendingIntent.getBroadcast(context,2100+i,unit,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        }
        try {
            if("enel".equals(p.getString(KEY_SOURCE,""))){
                org.json.JSONArray list=new org.json.JSONArray(p.getString(KEY_MONTHLY,"[]"));StringBuilder shortPeriod=new StringBuilder();boolean ongoing=false;
                String[] names=new java.text.DateFormatSymbols(IT).getShortMonths();
                java.util.Calendar now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"));
                for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);if(i>0)shortPeriod.append(" + ");shortPeriod.append(names[row.getInt("month")-1]).append(' ').append(row.getInt("year"));if(row.getInt("year")==now.get(java.util.Calendar.YEAR)&&row.getInt("month")==now.get(java.util.Calendar.MONTH)+1)ongoing=true;}
                if(shortPeriod.length()>0)views.setTextViewText(R.id.widget_period,shortPeriod.toString());
                views.setTextViewText(R.id.widget_period_badge,ongoing?"In corso":"Archiviato");
            }else views.setTextViewText(R.id.widget_period_badge,savedAt>0?"Stima":"Da collegare");
        }catch(Exception ignored){}

        views.setViewVisibility(R.id.widget_spacer,chart?android.view.View.GONE:android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_summary,chart?android.view.View.GONE:android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_chart,chart?android.view.View.VISIBLE:android.view.View.GONE);
        views.setTextViewText(R.id.widget_dots, chart ? "○  ●" : "●  ○");
        views.setTextViewText(R.id.widget_caption, "kwh".equals(p.getString("display_unit","eur")) ? "CONSUMO DEL PERIODO" : "COSTO VARIABILE STIMATO");
        if(!"kwh".equals(p.getString("display_unit","eur"))){
            try{WidgetConfiguration c=readConfiguration(context);if(c!=null&&WidgetPricing.parse(c.ratesJson,c.resident).hasFixedCosts())views.setTextViewText(R.id.widget_caption,"COSTO TOTALE STIMATO");}catch(Exception ignored){}
        }
        if ("kwh".equals(p.getString("display_unit","eur")) && savedAt>0) {
            try {
                NumberFormat usage=NumberFormat.getNumberInstance(IT);usage.setMaximumFractionDigits(1);
                views.setTextViewText(R.id.widget_euro,usage.format(Double.parseDouble(p.getString(KEY_KWH,"0")))+" kWh");
            } catch(RuntimeException ignored) { views.setTextViewText(R.id.widget_euro,"—"); }
            try { views.setTextViewText(R.id.widget_kwh,NumberFormat.getCurrencyInstance(IT).format(Double.parseDouble(p.getString(KEY_EUR,"0")))+" stimati"); }
            catch (RuntimeException ignored) { }
        }
        if(chart) {

            views.setContentDescription(R.id.widget_chart,MonthlyChart.description(context,history(context)));
        }
        int target=chart?0:1;
        for(int viewId : new int[]{R.id.widget_prev,R.id.widget_next,R.id.widget_dots}) {
            Intent page = new Intent(context,CostoLuceWidgetProvider.class).setAction(ACTION_PAGE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra("page",target)
                    .setData(android.net.Uri.parse("costoluce://widget-page/"+widgetId+"/"+target));
            views.setOnClickPendingIntent(viewId,PendingIntent.getBroadcast(context,widgetId,page,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        }
        applySize(context,views,spec,chart,savedAt,bitmapPixels);
        return views;
    }
}
