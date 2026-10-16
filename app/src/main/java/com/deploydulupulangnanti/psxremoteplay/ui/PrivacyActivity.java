package com.deploydulupulangnanti.psxremoteplay.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline policy, generated from the same index.html supplied for public hosting. */
public final class PrivacyActivity extends Activity {
    private WebView document;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        Ui.button(this, frame, "Back", false, view -> finish());
        document = new WebView(this);
        document.setBackgroundColor(Ui.BG);
        document.getSettings().setJavaScriptEnabled(false);
        document.getSettings().setAllowFileAccess(false);
        document.getSettings().setAllowContentAccess(false);
        document.getSettings().setBlockNetworkLoads(true);
        document.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("mailto".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { /* Policy text contains the support address too. */ }
                }
                return true;
            }
        });
        frame.addView(document, new LinearLayout.LayoutParams(-1, 0, 1));
        try (InputStream input = getAssets().open("privacy/index.html")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            document.loadDataWithBaseURL("https://privacy.psxremoteplay.invalid/",
                    new String(bytes.toByteArray(), StandardCharsets.UTF_8), "text/html", "UTF-8", null);
        } catch (IOException error) {
            Ui.text(this, frame, "Unable to open the policy. Contact seandyadryan2@gmail.com.", 16, Ui.TEXT, false);
        }
        setContentView(frame); Ui.insets(frame);
    }
    @Override public void onDestroy() {
        if (document != null) { document.stopLoading(); document.destroy(); document = null; }
        super.onDestroy();
    }
}
