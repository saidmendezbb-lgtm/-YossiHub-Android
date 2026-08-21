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
import android.provider.Settings;
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

    // Duración mínima del splash
    private static final long SPLASH_DURATION = 3000;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    private View splashView;
    private long splashStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        splashStartTime = System.currentTimeMillis();

        FrameLayout root = new FrameLayout(this);

        // Fondo amarillo para evitar cualquier destello blanco
        root.setBackgroundColor(Color.rgb(255, 235, 0));

        // --------------------------------------------------
        // WEBVIEW
        // --------------------------------------------------

        webView = new WebView(this);

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Oculto mientras aparece el splash
        webView.setVisibility(View.INVISIBLE);

        // --------------------------------------------------
        // BARRA DE PROGRESO
        // --------------------------------------------------

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

        // --------------------------------------------------
        // SPLASH PROPIO
        // --------------------------------------------------

        FrameLayout splash = new FrameLayout(this);

        splash.setBackgroundColor(Color.rgb(255, 235, 0));

        FrameLayout.LayoutParams splashParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        splash.setLayoutParams(splashParams);

        ImageView logo = new ImageView(this);

        logo.setImageResource(R.mipmap.ic_launcher);

        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int logoSize = (int) (
                180 * getResources().getDisplayMetrics().density
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(
                        logoSize,
                        logoSize
                );

        logoParams.gravity = Gravity.CENTER;

        splash.addView(logo, logoParams);

        splashView = splash;

        // --------------------------------------------------
        // ORDEN DE LAS VISTAS
        // --------------------------------------------------

        root.addView(webView);
        root.addView(progressBar);
        root.addView(splash);

        setContentView(root);

        // Configuramos el WebView
        configureWebView();

        // Cargamos la página mientras el splash permanece visible
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

        }

        // El splash dura como mínimo 3 segundos
        new Handler(Looper.getMainLooper()).postDelayed(
                this::hideSplash,
                SPLASH_DURATION
        );
    }

    private void hideSplash() {

        if (splashView == null) {
            return;
        }

        long elapsed =
                System.currentTimeMillis() - splashStartTime;

        long remaining =
                SPLASH_DURATION - elapsed;

        if (remaining > 0) {

            new Handler(Looper.getMainLooper()).postDelayed(
                    this::hideSplash,
                    remaining
            );

            return;
        }

        // Mostrar WebView
        webView.setVisibility(View.VISIBLE);

        // Quitar splash
        splashView.setVisibility(View.GONE);

        splashView = null;
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
                s.getUserAgentString()
                        + " YossiHubAndroid/1.0"
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
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress) {

                progressBar.setProgress(newProgress);

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
                        ) == PackageManager.PERMISSION_GRANTED
                        ||
                        checkSelfPermission(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
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
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = filePathCallback;

                try {

                    Intent intent =
                            fileChooserParams.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_REQUEST
                    );

                } catch (ActivityNotFoundException ex) {

                    fileCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No hay una aplicación disponible para seleccionar el archivo.",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }

                return true;
            }
        });

        webView.setDownloadListener(
                (url, userAgent, contentDisposition,
                 mimeType, contentLength) -> {

                    try {

                        startActivity(
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)
                                )
                        );

                    } catch (Exception e) {

                        Toast.makeText(
                                this,
                                "No se pudo abrir la descarga.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private boolean routeUrl(Uri uri) {

        if (uri == null) {
            return false;
        }

        String scheme =
                uri.getScheme() == null
                        ? ""
                        : uri.getScheme().toLowerCase();

        String host =
                uri.getHost() == null
                        ? ""
                        : uri.getHost().toLowerCase();

        if (
                (scheme.equals("https")
                        || scheme.equals("http"))
                &&
                (
                        host.equals("yossihub.com")
                                || host.equals("www.yossihub.com")
                                || host.endsWith(".netlify.app")
                )
        ) {

            return false;
        }

        if (
                scheme.equals("https")
                        || scheme.equals("http")
                        || scheme.equals("mailto")
                        || scheme.equals("tel")
                        || scheme.equals("sms")
                        || scheme.equals("geo")
                        || scheme.equals("market")
                        || scheme.equals("whatsapp")
        ) {

            try {

                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                uri
                        );

                startActivity(intent);

            } catch (ActivityNotFoundException e) {

                Toast.makeText(
                        this,
                        "No se encontró una aplicación para abrir este enlace.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return true;
        }

        return false;
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

        if (
                requestCode == LOCATION_REQUEST
                        && geoCallback != null
        ) {

            boolean granted = false;

            for (int result : grantResults) {

                if (
                        result
                                == PackageManager.PERMISSION_GRANTED
                ) {

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

            if (
                    !granted
                            &&
                            !shouldShowRequestPermissionRationale(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            )
            ) {

                Toast.makeText(
                        this,
                        "Puedes activar la ubicación desde Ajustes de YOSSI HUB.",
                        Toast.LENGTH_LONG
                ).show();
            }
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

        if (requestCode == FILE_REQUEST) {

            Uri[] result = null;

            if (resultCode == RESULT_OK) {

                result =
                        WebChromeClient.FileChooserParams
                                .parseResult(
                                        resultCode,
                                        data
                                );
            }

            if (fileCallback != null) {
                fileCallback.onReceiveValue(result);
            }

            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (
                webView != null
                        && webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

        webView.saveState(outState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {

            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }

        super.onDestroy();
    }
}
