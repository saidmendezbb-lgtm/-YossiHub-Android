package com.yossihub.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://yossihub.com/";
    private static final long SPLASH_DURATION = 3000;

    private WebView webView;
    private FrameLayout splashView;
    private boolean pageLoaded = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // BARRA SUPERIOR OSCURA + ICONOS BLANCOS
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(20, 24, 28));
        window.getDecorView().setSystemUiVisibility(0);

        // CONTENEDOR PRINCIPAL
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 196, 0));

        // WEBVIEW
        webView = new WebView(this);

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Amarillo mientras carga para evitar destello blanco
        webView.setBackgroundColor(Color.rgb(255, 196, 0));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // SPLASH AMARILLO
        splashView = new FrameLayout(this);

        splashView.setBackgroundColor(Color.rgb(255, 196, 0));

        splashView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // LOGO
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int logoSize = (int) (
                180 * getResources().getDisplayMetrics().density
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(logoSize, logoSize);

        logoParams.gravity = Gravity.CENTER;

        splashView.addView(logo, logoParams);

        // Primero WebView y encima el splash
        root.addView(webView);
        root.addView(splashView);

        setContentView(root);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                pageLoaded = true;

                // La página ya está lista.
                // Si ya pasaron los 3 segundos, mostramos la web.
                showWebsite();
            }
        });

        // A los 3 segundos intentamos quitar el splash.
        // Si la web todavía carga, el splash permanece.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showWebsite();
            }
        }, SPLASH_DURATION);

        webView.loadUrl(HOME_URL);
    }

    private void showWebsite() {

        if (!pageLoaded) {
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
