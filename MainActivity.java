package com.yossihub.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://yossihub.com/";
    private static final int LOCATION_REQUEST = 2101;
    private static final int FILE_REQUEST = 2102;

    private static final long SPLASH_DURATION = 3000;
    private static final long PAGE_RENDER_DELAY = 200;
    private static final long MAX_SPLASH_DURATION = 8000;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    private View splashView;
    private long splashStartTime;
    private boolean pageVisible = false;
    private boolean splashHidden = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        splashStartTime = System.currentTimeMillis();

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(
                Color.rgb(255, 235, 0)
        );

        // WEBVIEW
        webView = new WebView(this);

        webView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        webView.setBackgroundColor(
                Color.rgb(255, 235, 0)
        );

        webView.setVisibility(View.INVISIBLE);

        // BARRA DE PROGRESO
        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        8
                );

        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        // SPLASH DE YOSSIHUB
        FrameLayout splash = new FrameLayout(this);

        splash.setBackgroundColor(
                Color.rgb(255, 235, 0)
        );

        splash.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        ImageView logo = new ImageView(this);

        logo.setImageResource(
                R.mipmap.ic_launcher
        );

        logo.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        int logoSize =
                (int) (
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
        root.addView(progressBar);
        root.addView(splash);

        setContentView(root);

        configureWebView();

        // CARGAR YOSSIHUB DETRÁS DEL SPLASH
        if (savedInstanceState == null) {

            Uri incoming =
                    getIntent() != null
                            ? getIntent().getData()
                            : null;

            webView.loadUrl(
                    incoming != null
                            ? incoming.toString()
                            : HOME_URL
            );

        } else {

            webView.restoreState(savedInstanceState);
            pageVisible = true;

            // En una restauración también intentamos
            // retirar el splash cuando se cumpla el tiempo.
            tryHideSplash();
        }

        // SEGURIDAD: NO DEJAR EL SPLASH BLOQUEADO
        handler.postDelayed(
                this::forceHideSplash,
                MAX_SPLASH_DURATION
        );
    }

    private void tryHideSplash() {

        if (splashHidden) {
            return;
        }

        long elapsed =
                System.currentTimeMillis()
                        - splashStartTime;

        long remaining =
                SPLASH_DURATION - elapsed;

        // Mantener el logo como mínimo 3 segundos.
        if (remaining > 0) {

            handler.postDelayed(
                    this::tryHideSplash,
                    remaining
            );

            return;
        }

        // Si la página todavía no está lista,
        // mantener el splash.
        if (!pageVisible) {
            return;
        }

        handler.postDelayed(
                this::showWebView,
                PAGE_RENDER_DELAY
        );
    }

    private void showWebView() {

        if (splashHidden) {
            return;
        }

        splashHidden = true;

        webView.setVisibility(
                View.VISIBLE
        );

        if (splashView != null) {

            splashView.setVisibility(
                    View.GONE
            );

            splashView = null;
        }
    }

    private void forceHideSplash() {

        if (splashHidden) {
            return;
        }

        splashHidden = true;

        webView.setVisibility(
                View.VISIBLE
        );

        if (splashView != null) {

            splashView.setVisibility(
                    View.GONE
            );

            splashView = null;
        }
    }

    private void configureWebView() {

        WebSettings s =
                webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        s.setUserAgentString(
                s.getUserAgentString()
                        + " YossiHubAndroid/1.0"
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        return routeUrl(
                                request.getUrl()
                        );
                    }

                    @Override
                    @SuppressWarnings("deprecation")
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        return routeUrl(
                                Uri.parse(url)
                        );
                    }

                    @Override
                    public void onPageCommitVisible(
                            WebView view,
                            String url) {

                        super.onPageCommitVisible(
                                view,
                                url
                        );

                        pageVisible = true;

                        tryHideSplash();
                    }
                }
        );

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public void onProgressChanged(
                            WebView view,
                            int newProgress) {

                        progressBar.setProgress(
                                newProgress
                        );

                        progressBar.setVisibility(
                                newProgress >= 100
                                        ? View.GONE
                                        : View.VISIBLE
                        );
                    }

                    @Override
                    public void onGeolocationPermissionsShowPrompt(
                            String origin,
                            GeolocationPermissions.Callback callback) {

                        if (
                                checkSelfPermission(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                )
                                        == PackageManager.PERMISSION_GRANTED
                                ||
                                checkSelfPermission(
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                        == PackageManager.PERMISSION_GRANTED
                        ) {

                            callback.invoke(
                                    origin,
                                    true,
                                    false
                            );

                        } else {

                            geoOrigin = origin;
                            geoCallback = callback;

                            requestPermissions(
                                    new String[]{
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                    },
                                    LOCATION_REQUEST
                            );
                        }
                    }

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> filePathCallback,
                            FileChooserParams fileChooserParams) {

                        if (fileCallback != null) {

                            fileCallback.onReceiveValue(
                                    null
                            );
                        }

                        fileCallback =
                                filePathCallback;

                        try {

                            Intent intent =
                                    fileChooserParams
                                            .createIntent();

                           
