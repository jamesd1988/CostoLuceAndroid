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
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private boolean enelBusy = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true); // Necessario per il calcolatore e per il portale.
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true); // Solo per asset locali del calcolatore.
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            web.setWebViewClient(new PortalRouter(this));
            web.loadUrl(HOME);
            setContentView(web);
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
        if ("enel-sync".equals(action)) {
            preferredMonths = "1".equals(uri.getQueryParameter("months")) ? 1 : 2;
            startEnelSync(); return true;
        }
        if ("enel-config".equals(action)) { showEnelConfiguration(); return true; }
        if ("enel-clear".equals(action)) { confirmRemoveCredentials(); return true; }
        if ("sync".equals(action)) {
            preferredMonths = "1".equals(uri.getQueryParameter("months")) ? 1 : 2;
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
            new EnelCredentialStore(this).clear();
            enelStatus("Credenziali cifrate non più leggibili. Configura nuovamente Enel Energia.", true);
        }
        showEnelConfiguration();
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
        note.setText("Periodo importato: ultimi " + preferredMonths + " mesi solari completi. Non è un'API pubblica ufficiale e potrebbe non funzionare.");
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
        enelStatus("Collegamento sicuro a Enel Energia in corso...", false);
        final int selectedMonths=preferredMonths;
        background.execute(() -> {
            try {
                EnelConnector.Result result=EnelConnector.fetch(credentials,selectedMonths);
                if(persistIfValid) new EnelCredentialStore(this).save(credentials);
                if(!isFinishing()) runOnUiThread(() -> {
                    enelBusy=false;
                    if(web != null) web.loadUrl(HOME+"#enel="+String.format(java.util.Locale.ROOT,"%.3f",result.kwh)+"&months="+selectedMonths+"&days="+result.days);
                });
            } catch (Exception error) {
                // Do not print credentials, HTTP bodies, tokens or authenticated URLs.
                String msg=error instanceof java.io.IOException ? error.getMessage()
                        : "Dati Enel non disponibili o formato inatteso. La sincronizzazione automatica non è confermata.";
                if(!isFinishing()) runOnUiThread(() -> { enelBusy=false; enelStatus(msg,true); });
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
