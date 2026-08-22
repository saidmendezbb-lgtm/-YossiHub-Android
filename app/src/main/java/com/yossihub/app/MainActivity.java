package com.yossihub.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://yossihub.com/";
    private static final long SPLASH_DURATION = 4000;

    private WebView webView;
    private FrameLayout splashView;

    private boolean pageLoaded = false;
    private boolean splashTimeElapsed = false;
    private volatile String fcmToken = "";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermission();
        refreshFcmToken();

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(20, 24, 28));
        window.getDecorView().setSystemUiVisibility(0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 196, 0));

        webView = new WebView(this);

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        webView.setBackgroundColor(Color.rgb(255, 196, 0));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Puente entre Android y YossiHub
        webView.addJavascriptInterface(new YossiHubBridge(), "YossiHub");

        splashView = new FrameLayout(this);
        splashView.setBackgroundColor(Color.rgb(255, 196, 0));

        splashView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int logoSize = (int) (
                180 * getResources().getDisplayMetrics().density
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(logoSize, logoSize);

        logoParams.gravity = android.view.Gravity.CENTER;

        splashView.addView(logo, logoParams);

        root.addView(webView);
        root.addView(splashView);

        setContentView(root);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);

                pageLoaded = true;
                showWebsite();
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                splashTimeElapsed = true;
                showWebsite();
            }
        }, SPLASH_DURATION);

        webView.loadUrl(HOME_URL);
    }

    private void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful() || task.getResult() == null) {
                        return;
                    }

                    fcmToken = task.getResult();

                    if (webView != null) {
                        webView.post(() -> webView.evaluateJavascript(
                                "window.dispatchEvent(new Event('yossihub-fcm-ready'));",
                                null
                        ));
                    }
                });
    }

    private class YossiHubBridge {

        @JavascriptInterface
        public String getFcmToken() {
            return fcmToken == null ? "" : fcmToken;
        }

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }
    }

    private void requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1001
                );
            }
        }
    }

    private void showWebsite() {

        if (!pageLoaded || !splashTimeElapsed) {
            return;
        }

        webView.setBackgroundColor(Color.WHITE);
        splashView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
