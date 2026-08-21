package com.yossihub.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        // BARRA SUPERIOR OSCURA + ICONOS BLANCOS
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(20, 24, 28));

        if (android.os.Build.VERSION.SDK_INT >= 30) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                controller.show(
                        WindowInsets.Type.statusBars()
                );

                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }

        } else {

            window.getDecorView().setSystemUiVisibility(0);
        }

        // CONTENEDOR PRINCIPAL AMARILLO
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(
                Color.rgb(255, 196, 0)
        );

        // WEBVIEW
        webView = new WebView(this);

        webView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // Evita pantalla blanca mientras carga
        webView.setBackgroundColor(
                Color.rgb(255, 196, 0)
        );

        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // SPLASH AMARILLO
        FrameLayout splash = new FrameLayout(this);

        splash.setBackgroundColor(
                Color.rgb(255, 196, 0)
        );

        splash.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // LOGO
        ImageView logo = new ImageView(this);

        logo.setImageResource(
                R.mipmap.ic_launcher
        );

        logo.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        int logoSize = (int) (
                180 *
                getResources()
                        .getDisplayMetrics()
                        .density
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(
                        logoSize,
                        logoSize
                );

        logoParams.gravity = Gravity.CENTER;

        splash.addView(
                logo,
                logoParams
        );

        splashView = splash;

        // ORDEN DE LAS CAPAS
        root.addView(webView);
        root.addView(splash);

        setContentView(root);

        // CUANDO LA WEB TERMINA DE CARGAR
        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        handler.postDelayed(
                                () -> {

                                    webView.setBackgroundColor(
                                            Color.WHITE
                                    );

                                    webView.setVisibility(
                                            View.VISIBLE
                                    );

                                    splashView.setVisibility(
                                            View.GONE
                                    );

                                },
                                SPLASH_DURATION
                        );
                    }
                }
        );

        // CARGAR YOSSI HUB
        webView.loadUrl(HOME_URL);
    }

    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                webView.canGoBack()
        ) {

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
