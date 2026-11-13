package com.costoluce.importatore;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** UI locale e WebView separata per la consultazione SPID/CIE sperimentale. */
public final class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/index.html";
    private static final String PORTAL = "https://www.consumienergia.it/portaleConsumi/";
    private static final int PICK_CSV = 411;
    private static final int MAX_FILE_BYTES = 1_500_000;
    private WebView web;
    private int preferredMonths = 2;
    private int preferredAnchor = 10;
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private boolean enelBusy = false;
    private boolean areraBusy = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int enelRequestId = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);}
        try {
            web = new WebView(this);
            web.setBackgroundColor(android.graphics.Color.rgb(10,25,40));
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true); // Necessario per il calcolatore e per il portale.
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true); // Solo per asset locali del calcolatore.
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            web.setWebViewClient(new PortalRouter(this));
            web.loadUrl(HOME);
            android.widget.FrameLayout container = new android.widget.FrameLayout(this);
            container.setBackgroundColor(android.graphics.Color.rgb(3,19,41));
            container.addView(web,new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
            // Insets belong to the native container: WebView content ignores View padding.
            container.setOnApplyWindowInsetsListener((view,insets) -> {
                int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
                int left=insets.getSystemWindowInsetLeft(),right=insets.getSystemWindowInsetRight();
                if(android.os.Build.VERSION.SDK_INT>=30){
                    android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime());
                    top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;
                }
                view.setPadding(left,top,right,bottom);return insets;
            });
            setContentView(container);
            container.requestApplyInsets();
        } catch (RuntimeException | LinkageError error) {
            // Diagnostica visibile senza PC se WebView non riesce ad inizializzarsi.
            TextView diagnostic = new TextView(this);
            diagnostic.setText("Costo Luce: errore di avvio WebView.\n\n" +
                    error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            diagnostic.setPadding(28, 36, 28, 28);
            setContentView(diagnostic);
        }
    }

    boolean handleUrl(String url) {
        if (url == null) return false;
        final Uri uri;
        try { uri = Uri.parse(url); } catch (Exception e) { return false; }
        if (!"costoluce".equalsIgnoreCase(uri.getScheme())) return false;
        String action = uri.getHost();
        if ("arera-check".equals(action)) {
            if (web != null && HOME.equals(web.getUrl())) checkArera(true);
            return true;
        }
        if ("display-unit".equals(action)) {
            if (web != null && HOME.equals(web.getUrl())) CostoLuceWidgetProvider.saveUnit(this,uri.getQueryParameter("unit"));
            return true;
        }
        if ("widget-config".equals(action)) {
            // Configuration is allowed before any consumption exists. Only the
            // bundled local calculator may send tariffs to the widget.
            if (web == null || web.getUrl() == null || !web.getUrl().startsWith(HOME))
                return true;
            boolean success = false;
            try {
                int months = Integer.parseInt(uri.getQueryParameter("months"));
                String rates = uri.getQueryParameter("rates");
                boolean resident = "1".equals(uri.getQueryParameter("resident"));
                success = CostoLuceWidgetProvider.saveConfiguration(this, months, rates, resident,requestAnchor(uri));
                if(success){preferredMonths=months;preferredAnchor=requestAnchor(uri);}
            } catch (RuntimeException ignored) { }
            boolean enelReady = new EnelCredentialStore(this).hasSaved();
            boolean automatic = "1".equals(uri.getQueryParameter("auto"));
            String message = !success
                ? "Non riesco a trasferire le tariffe al widget: controlla i valori."
                : enelReady
                    ? "Widget sincronizzato automaticamente. Premi ↻ sulla Home per nuovi kWh."
                    : "Widget sincronizzato. Per recuperare nuovi kWh, collega Enel e salva le credenziali cifrate.";
            if (!automatic || !success) {
                web.evaluateJavascript("window.onWidgetSaveStatus("
                        + JSONObject.quote(message) + ", " + (success ? "true" : "false") + ");", null);
            }
            return true;
        }
        if ("widget-save".equals(action)) {
            // Only the locally packaged calculator may send the summary.
            // Never accept data from the embedded external ARERA portal.
            if (web == null || web.getUrl() == null || !web.getUrl().startsWith(HOME))
                return true;
            boolean success = false;
            try {
                double kwh = Double.parseDouble(uri.getQueryParameter("kwh"));
                double euro = Double.parseDouble(uri.getQueryParameter("euro"));
                int months = Integer.parseInt(uri.getQueryParameter("months"));
                String rates = uri.getQueryParameter("rates");
                boolean resident = "1".equals(uri.getQueryParameter("resident"));
                success = CostoLuceWidgetProvider.saveSnapshot(this, kwh, euro, months,
                        uri.getQueryParameter("source"), uri.getQueryParameter("period"),
                        rates, resident, uri.getQueryParameter("monthly"),requestAnchor(uri));
            } catch (RuntimeException ignored) {
                // Invalid input: do not store or log any arbitrary content.
            }
            boolean automatic = "1".equals(uri.getQueryParameter("auto"));
            String message = success
                ? "Widget sincronizzato automaticamente con i consumi e le tariffe."
                : "Non riesco a trasferire i consumi al widget: controlla le tariffe.";
            if (!automatic || !success) {
                web.evaluateJavascript("window.onWidgetSaveStatus("
                        + JSONObject.quote(message) + ", " + (success ? "true" : "false") + ");", null);
            }
            return true;
        }
        if ("enel-history".equals(action)) {
            if(web==null||!HOME.equals(web.getUrl())||enelBusy)return true;
            CostoLuceWidgetProvider.WidgetConfiguration c=CostoLuceWidgetProvider.readConfiguration(this);
            if(c==null||!CostoLuceWidgetProvider.annualHistoryDue(this,c.months,c.anchor))return true;
            if(!new EnelCredentialStore(this).hasSaved()){enelStatus("Collega Enel e salva le credenziali cifrate per recuperare lo storico annuale.",false);return true;}
            preferredMonths=c.months;preferredAnchor=c.anchor;
            try{EnelConnector.Credentials credentials=new EnelCredentialStore(this).read();if(credentials!=null)downloadEnel(credentials,false);else enelStatus("Configura Enel per recuperare lo storico annuale.",false);}
            catch(Exception unavailable){enelStatus("Credenziali salvate non disponibili: configura Enel per recuperare lo storico annuale.",true);}
            return true;
        }
        if ("enel-sync".equals(action)) {
            preferredMonths = "1".equals(uri.getQueryParameter("months")) ? 1 : "3".equals(uri.getQueryParameter("months")) ? 3 : 2;
            preferredAnchor=requestAnchor(uri);
            startEnelSync(); return true;
        }
        if ("enel-config".equals(action)) { showEnelConfiguration(); return true; }
        if ("enel-clear".equals(action)) { confirmRemoveCredentials(); return true; }
        if ("enel-check".equals(action)) { checkEnelPublic(); return true; }
        if ("sync".equals(action)) {
            preferredMonths = "1".equals(uri.getQueryParameter("months")) ? 1 : "3".equals(uri.getQueryParameter("months")) ? 3 : 2;
            preferredAnchor=requestAnchor(uri);
            openPortalInsideApp(); return true;
        }
        if ("portal".equals(action)) { openPortalInExternalBrowser(); return true; }
        if ("home".equals(action)) { returnHome(); return true; }
        if ("import".equals(action)) { selectConsumptionFile(); return true; }
        if ("captured".equals(action)) {
            // Accettare un risultato solo se la WebView si trova sul portale ufficiale.
            if (web != null && PortalRouter.isOfficial(web.getUrl())) {
                String raw = uri.getLastPathSegment();
                if (raw != null && raw.matches("[0-9]{1,8}(\\.[0-9]{1,3})?")) {
                    try {
                        double kwh = Double.parseDouble(raw);
                        if (kwh > 0 && kwh < 1_000_000) web.loadUrl(HOME + "#portal=" + raw);
                    } catch (NumberFormatException ignored) { }
                }
            }
            return true;
        }
        return false;
    }

    /** Restore the latest validated widget value when the user opens the app. */
    void restoreWidgetSnapshot() {
        if (web == null || web.getUrl() == null || !web.getUrl().startsWith(HOME)) return;
        deliverArera(AreraRepository.state(this,""));
        checkArera(false);
        web.evaluateJavascript("window.onHistoryNative("+CostoLuceWidgetProvider.archive(this).toString()+","+CostoLuceWidgetProvider.prefs(this).getLong("history_at",0L)+");",null);
        JSONObject snapshot = CostoLuceWidgetProvider.getLatestSnapshot(this);
        JSONObject config = CostoLuceWidgetProvider.getWidgetConfiguration(this);
        web.evaluateJavascript("window.onWidgetNativeState("
                + (config == null ? "null" : config.toString()) + ","
                + (snapshot == null ? "null" : snapshot.toString()) + ");", null);
    }

    private void deliverArera(JSONObject data) {
        if(web!=null&&HOME.equals(web.getUrl()))web.evaluateJavascript("window.onAreraCatalog&&window.onAreraCatalog("+data.toString()+");",null);
    }

    private void checkArera(boolean force) {
        if(areraBusy||(!force&&!AreraRepository.due(this)))return;
        areraBusy=true;
        background.execute(()->{
            JSONObject result=AreraRepository.refresh(this);
            uiHandler.post(()->{areraBusy=false;deliverArera(result);});
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null && web.getProgress() == 100) restoreWidgetSnapshot();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && "com.costoluce.cloud.action.OPEN_WIDGET".equals(intent.getAction())) {
            restoreWidgetSnapshot();
        }
    }

    private void enelStatus(String message, boolean error) {
        if (web == null || web.getUrl() == null || !web.getUrl().startsWith(HOME)) return;
        web.evaluateJavascript("window.onEnelStatus(" + JSONObject.quote(message) + ", " + (error ? "true" : "false") + ");", null);
    }

    private void startEnelSync() {
        if (enelBusy) { enelStatus("Una sincronizzazione è già in corso.", false); return; }
        try {
            EnelConnector.Credentials credentials = new EnelCredentialStore(this).read();
            if (credentials != null) { downloadEnel(credentials, false); return; }
        } catch (Exception e) {
            enelStatus("Credenziali cifrate non più leggibili. Configura nuovamente Enel Energia.", true);
        }
        showEnelConfiguration();
    }

    private void checkEnelPublic() {
        if (enelBusy) { enelStatus("Altra operazione in corso. Riapri l'app se non risponde.", true); return; }
        enelBusy=true;
        enelStatus("Verifica della sola pagina pubblica Enel, senza usare alcuna credenziale...", false);
        background.execute(() -> {
            String result; boolean error;
            try { result=EnelConnector.checkPublicLogin();error=result.contains("non espone"); }
            catch (IOException e) { result="Verifica rete Enel fallita: " + e.getClass().getSimpleName() + ". " + e.getMessage(); error=true; }
            catch (RuntimeException e) { result="Verifica pubblica fallita: " + e.getClass().getSimpleName(); error=true; }
            final String message=result; final boolean isError=error;
            if(!isFinishing()) runOnUiThread(() -> { enelBusy=false; enelStatus(message,isError); });
        });
    }

    private EditText formField(LinearLayout parent, String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        parent.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private void showEnelConfiguration() {
        if (enelBusy) return;
        ScrollView scroller = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset=(int)(18*getResources().getDisplayMetrics().density);
        content.setPadding(inset, inset, inset, inset);
        TextView intro = new TextView(this);
        intro.setText("Connessione sperimentale a Enel Energia (non ufficiale). Inserisci i dati del TUO account: la password è trasmessa solo ai server HTTPS Enel. Nessuna credenziale viene inviata a ChatGPT o GitHub. L'accesso può richiedere verifiche aggiuntive non automatizzabili.");
        content.addView(intro);
        EditText user=formField(content,"Email account Enel Energia",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pwd=formField(content,"Password Enel Energia",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText pod=formField(content,"POD (IT..., dalla bolletta)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText number=formField(content,"Numero utente Enel (Area Clienti)",InputType.TYPE_CLASS_TEXT);
        CheckBox remember = new CheckBox(this);
        remember.setText("Salva cifrato sul telefono per sincronizzare con un solo tocco");
        remember.setChecked(false); // Explicit opt-in to durable credential storage.
        content.addView(remember);
        TextView note = new TextView(this);
        note.setText("Periodo importato: ciclo configurato di 1, 2 o 3 mesi e storico esteso, quando disponibile, incluso il mese corrente. Il riepilogo con i nomi dei mesi è mostrato nella schermata principale. Non è un'API pubblica ufficiale e potrebbe non funzionare.");
        content.addView(note);
        scroller.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Collega Enel Energia")
                .setView(scroller)
                .setNegativeButton("Annulla", (d, which) -> { })
                .setPositiveButton("Collega e aggiorna", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            EnelConnector.Credentials credentials=new EnelConnector.Credentials(
                    user.getText().toString(),pwd.getText().toString(),pod.getText().toString(),number.getText().toString());
            try { credentials.validate(); }
            catch(Exception e) { number.setError(e.getMessage()); return; }
            dialog.dismiss();
            downloadEnel(credentials, remember.isChecked());
        }));
        dialog.show();
    }

    private void downloadEnel(EnelConnector.Credentials credentials, boolean persistIfValid) {
        if (enelBusy) return;
        enelBusy=true;
        final int requestId = ++enelRequestId;
        enelStatus("Inizio collegamento Enel. Durata massima prevista: circa 90 secondi...", false);
        final int selectedMonths=preferredMonths;
        final int selectedAnchor=preferredAnchor;
        CostoLuceWidgetProvider.markAnnualHistoryAttempt(this,selectedMonths,selectedAnchor);
        // This UI watchdog guarantees visible feedback even when an HTTP library blocks.
        uiHandler.postDelayed(() -> {
            if(enelBusy && enelRequestId == requestId && !isFinishing()) {
                enelStatus("Collegamento Enel non completato entro 100 secondi. La richiesta potrebbe essere bloccata dal login o dalla rete. Chiudi e riapri l'app prima di riprovare.", true);
            }
        }, 100000);
        background.execute(() -> {
            try {
                EnelConnector.Result result=EnelConnector.fetch(credentials,selectedMonths,selectedAnchor, phase -> {
                    if (!isFinishing()) runOnUiThread(() -> {
                        if (enelRequestId == requestId && enelBusy) enelStatus(phase, false);
                    });
                });
                CostoLuceWidgetProvider.saveHistory(this,result);
                if(persistIfValid) new EnelCredentialStore(this).save(credentials);
                // Keep the Home widget and the in-app consumption summary in sync
                // when the user already configured the widget with tariff data.
                if (CostoLuceWidgetProvider.readConfiguration(this) != null) {
                    try { CostoLuceWidgetProvider.saveResultFromEnel(this, result, selectedMonths); }
                    catch (Exception ignored) { /* The in-app result must still be shown. */ }
                }
                if(!isFinishing()) runOnUiThread(() -> {
                    if (enelRequestId != requestId) return;
                    enelBusy=false;
                    if(web != null && web.getUrl() != null && web.getUrl().startsWith(HOME)) {
                        try {
                            org.json.JSONObject detail=new org.json.JSONObject();
                            detail.put("kwh",result.kwh);
                            detail.put("months",selectedMonths);
                            detail.put("anchor",selectedAnchor);
                            detail.put("period",result.period);
                            org.json.JSONArray monthly=new org.json.JSONArray();
                            for(EnelConnector.MonthUsage m : result.monthly) {
                                org.json.JSONObject row=new org.json.JSONObject();
                                row.put("year",m.year);
                                row.put("month",m.month);
                                row.put("kwh",m.kwh);
                                row.put("days",m.days);
                                row.put("expectedDays",m.expectedDays);
                                row.put("hourSamples",m.hourSamples);
                                row.put("future",m.future);
                                row.put("lastMeasuredDay",m.lastMeasuredDay);
                                monthly.put(row);
                            }
                            detail.put("monthly",monthly);
                            web.evaluateJavascript("window.onHistoryNative("+CostoLuceWidgetProvider.archive(this).toString()+","+CostoLuceWidgetProvider.prefs(this).getLong("history_at",0L)+");",null);
                            web.evaluateJavascript("window.onEnelConsumption("+detail.toString()+");",null);
                        } catch(org.json.JSONException e) {
                            enelStatus("Errore nella visualizzazione del riepilogo mensile.",true);
                        }
                    }
                });
            } catch (Exception error) {
                // Do not print credentials, HTTP bodies, tokens or authenticated URLs.
                String msg=error instanceof java.io.IOException ? error.getMessage()
                        : "Dati Enel non disponibili o formato inatteso. La sincronizzazione automatica non è confermata.";
                if(!isFinishing()) runOnUiThread(() -> {
                    if (enelRequestId != requestId) return;
                    enelBusy=false;
                    enelStatus("Collegamento interrotto: " + msg, true);
                });
            }
        });
    }

    private void confirmRemoveCredentials() {
        new AlertDialog.Builder(this).setTitle("Scollegare Enel?")
                .setMessage("Le credenziali cifrate saranno eliminate dal telefono.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Scollega", (d,w) -> {
                    new EnelCredentialStore(this).clear();
                    enelStatus("Collegamento Enel rimosso dal dispositivo.", false);
                }).show();
    }

    int getPreferredMonths() { return preferredMonths; }
    private int requestAnchor(Uri uri){
        try{String raw=uri.getQueryParameter("anchor");int a=raw==null?CostoLuceWidgetProvider.prefs(this).getInt("billing_anchor",10):Integer.parseInt(raw);return a>=1&&a<=12?a:0;}catch(Exception e){return 0;}
    }

    void openPortalInsideApp() {
        if (web != null) web.loadUrl(PORTAL);
    }

    void returnHome() {
        if (web != null) web.loadUrl(HOME);
    }

    void openPortalInExternalBrowser() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL))); }
        catch (ActivityNotFoundException ignored) { /* Accessibile da WebView. */ }
    }

    void selectConsumptionFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try { startActivityForResult(intent, PICK_CSV); }
        catch (ActivityNotFoundException e) { sendImportError("Selettore file non disponibile."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData())) {
            if (input == null) throw new IOException("Impossibile aprire il documento.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (out.size() + read > MAX_FILE_BYTES) throw new IOException("File troppo grande (massimo 1,5 MB).");
                out.write(buffer, 0, read);
            }
            String csv = new String(out.toByteArray(), StandardCharsets.UTF_8);
            // Quote del payload: nessuna esecuzione JS derivante dal file.
            if (web != null) web.evaluateJavascript("window.onFileSelected(" + JSONObject.quote(csv) + ");", null);
        } catch (Exception e) { sendImportError(e.getMessage()); }
    }

    private void sendImportError(String message) {
        if (web != null) web.evaluateJavascript("window.onImportError(" +
                JSONObject.quote(message == null ? "Errore apertura file" : message) + ");", null);
    }

    @Override public void onBackPressed() {
        if (web != null && !HOME.equals(web.getUrl()) && !String.valueOf(web.getUrl()).startsWith(HOME + "#")) returnHome();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        background.shutdownNow();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
