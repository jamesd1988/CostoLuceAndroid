package com.costoluce.importatore;

import android.app.Activity;
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
        if ("sync".equals(action)) { openPortalInsideApp(); return true; }
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
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
