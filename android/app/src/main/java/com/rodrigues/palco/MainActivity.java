package com.rodrigues.palco;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 7;
    private WebView web;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private PermissionRequest pendingRequest;
    private WebChromeClient chrome;
    private final String appUrl = BuildConfig.APP_URL;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#121212"));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        immersive();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " PalcoKaraokeApp/1");
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                if (host.equals(Uri.parse(appUrl).getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                        "<html><body style='background:#121212;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
                        + "<div><h2>Sem internet</h2><p style='color:#aaa'>Confira o Wi-Fi e aperte OK para tentar de novo.</p>"
                        + "<button autofocus onclick=\"location.href='" + appUrl + "'\" style='padding:14px 28px;border-radius:99px;border:0;background:#ff4f87;font-size:18px;font-weight:bold'>Tentar de novo</button></div></body></html>",
                        "text/html", "utf-8", null);
                }
            }
        });

        chrome = new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean wantsMic = false;
                    for (String res : req.getResources())
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) wantsMic = true;
                    if (!wantsMic) { req.deny(); return; }
                    if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        pendingRequest = req;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    } else {
                        req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    }
                });
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullscreenView != null) { cb.onCustomViewHidden(); return; }
                fullscreenView = view; fullscreenCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                immersive();
            }
            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) return;
                root.removeView(fullscreenView);
                fullscreenView = null;
                web.setVisibility(View.VISIBLE);
                if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
                immersive();
            }
        };
        web.setWebChromeClient(chrome);

        if (state != null) web.restoreState(state); else web.loadUrl(appUrl);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_MIC && pendingRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                pendingRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingRequest.deny();
            pendingRequest = null;
        }
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) { chrome.onHideCustomView(); return true; }
            if (web.canGoBack()) { web.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
}
