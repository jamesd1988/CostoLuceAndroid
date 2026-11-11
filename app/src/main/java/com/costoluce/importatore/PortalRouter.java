package com.costoluce.importatore;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Esegue lo script esclusivamente sulle pagine ufficiali del Portale Consumi. */
public final class PortalRouter extends WebViewClient {
    private final MainActivity host;

    PortalRouter(MainActivity host) { this.host = host; }

    static boolean isOfficial(String address) {
        if (address == null) return false;
        try {
            Uri uri = Uri.parse(address);
            String hostname = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                    ("consumienergia.it".equalsIgnoreCase(hostname) ||
                    "www.consumienergia.it".equalsIgnoreCase(hostname));
        } catch (Exception ignored) { return false; }
    }

    @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return host.handleUrl(url);
    }

    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return host.handleUrl(request.getUrl().toString());
    }

    @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (url != null && url.startsWith("file:///android_asset/index.html")) {
            host.restoreWidgetSnapshot();
            return;
        }
        if (!isOfficial(url)) return;
        try (InputStream input = host.getAssets().open("portal_capture.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) > 0) out.write(chunk, 0, count);
            view.evaluateJavascript("window.__costoLuceMonths = " + host.getPreferredMonths() + ";" +
                    new String(out.toByteArray(), StandardCharsets.UTF_8), null);
        } catch (Exception ignored) { /* Il calcolatore deve rimanere utilizzabile. */ }
    }
}
