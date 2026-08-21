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
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
    private static final long MAX_SPLASH_DURATION = 8000;

    private WebView webView;
    private ProgressBar progressBar;
    private View splashView;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    private long splashStartTime;
    private boolean pageVisible = false;
    private boolean splashHidden = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        // BARRA SUPERIOR: HORA, SEÑAL, 5G, BATERÍA
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars());
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        splashStartTime = System.currentTimeMillis();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 235, 0));

        // WEBVIEW
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webView.setBackgroundColor(Color.WHITE);
        webView.setVisibility(View.INVISIBLE);

        // BARRA DE CARGA
        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8
        ));
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        // PANTALLA AMARILLA DE INICIO
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.rgb(255, 235, 0));
        splash.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int logoSize = (int) (
                180 * getResources().getDisplayMetrics().density
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(logoSize, logoSize);

        logoParams.gravity = Gravity.CENTER;
        splash.addView(logo, logoParams);

        splashView = splash;

        root.addView(webView);
        root.addView(progressBar);
        root.addView(splash);

        setContentView(root);

        configureWebView();

        if (savedInstanceState == null) {
            Uri incoming = getIntent() != null
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
            tryHideSplash();
        }

        handler.postDelayed(
                this::forceHideSplash,
                MAX_SPLASH_DURATION
        );
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();

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
                s.getUserAgentString() + " YossiHubAndroid/1.0"
        );

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return routeUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return routeUrl(Uri.parse(url));
            }

            @Override
            public void onPageCommitVisible(
                    WebView view,
                    String url) {

                super.onPageCommitVisible(view, url);

                pageVisible = true;
                tryHideSplash();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int progress) {

                progressBar.setProgress(progress);

                progressBar.setVisibility(
                        progress >= 100
                                ? View.GONE
                                : View.VISIBLE
                );
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {

                if (checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                        ||
                        checkSelfPermission(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED) {

                    callback.invoke(origin, true, false);

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
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_REQUEST);

                } catch (ActivityNotFoundException e) {
                    fileCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No se encontró una aplicación para seleccionar el archivo.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }

                return true;
            }
        });
    }

    private boolean routeUrl(Uri uri) {
        if (uri == null) return false;

        String scheme = uri.getScheme();

        if (scheme == null
                || scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https")) {
            return false;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            startActivity(intent);
            return true;

        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    this,
                    "No se puede abrir este enlace.",
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        }
    }

    private void tryHideSplash() {
        if (splashHidden) return;

        long elapsed =
                System.currentTimeMillis() - splashStartTime;

        long remaining =
                SPLASH_DURATION - elapsed;

        if (remaining > 0) {
            handler.postDelayed(
                    this::tryHideSplash,
                    remaining
            );
            return;
        }

        if (!pageVisible) return;

        showWebView();
    }

    private void showWebView() {
        if (splashHidden) return;

        splashHidden = true;
        webView.setVisibility(View.VISIBLE);

        if (splashView != null) {
            splashView.setVisibility(View.GONE);
            splashView = null;
        }
    }

    private void forceHideSplash() {
        showWebView();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_REQUEST
                && geoCallback != null) {

            boolean granted = false;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            geoCallback.invoke(
                    geoOrigin,
                    granted,
                    false
            );

            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_REQUEST
                && fileCallback != null) {

            Uri[] results = null;

            if (resultCode == RESULT_OK) {
                results =
                        WebChromeClient.FileChooserParams
                                .parseResult(
                                        resultCode,
                                        data
                                );
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
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
    protected void onSaveInstanceState(
            Bundle outState) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
