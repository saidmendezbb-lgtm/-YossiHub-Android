package com.yossihub.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
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

        webView.addJavascriptInterface(
                new YossiHubBridge(),
                "YossiHub"
        );

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
                new FrameLayout.LayoutParams(
                        logoSize,
                        logoSize
                );

        logoParams.gravity = android.view.Gravity.CENTER;

        splashView.addView(logo, logoParams);

        root.addView(webView);
        root.addView(splashView);

        setContentView(root);

        webView.setWebViewClient(new WebViewClient() {

            /*
             * Android moderno.
             */
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                if (request == null ||
                        request.getUrl() == null) {
                    return false;
                }

                return handleExternalUrl(
                        request.getUrl().toString()
                );
            }

            /*
             * Compatibilidad con versiones anteriores.
             */
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {

                return handleExternalUrl(url);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                super.onPageFinished(view, url);

                pageLoaded = true;
                showWebsite();
            }
        });

        handler.postDelayed(() -> {

            splashTimeElapsed = true;
            showWebsite();

        }, SPLASH_DURATION);

        webView.loadUrl(HOME_URL);
    }

    /*
     * IMPORTANTE:
     *
     * Los enlaces externos se abren fuera de WebView.
     * YOSSI HUB NO navega hacia ellos.
     *
     * De esta manera, cuando Google Maps se cierre
     * o el conductor pulse Atrás, vuelve a la pantalla
     * que ya estaba abierta dentro de YOSSI HUB.
     */
    private boolean handleExternalUrl(String url) {

        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {

            /*
             * GOOGLE MAPS / INTENT
             */
            if (url.startsWith("intent://")) {

                Intent parsedIntent = Intent.parseUri(
                        url,
                        Intent.URI_INTENT_SCHEME
                );

                /*
                 * Intentamos abrir directamente la aplicación
                 * indicada por el enlace.
                 */
                try {

                    startActivity(parsedIntent);
                    return true;

                } catch (ActivityNotFoundException e) {

                    /*
                     * Si esa aplicación no está disponible,
                     * buscamos el enlace alternativo.
                     */
                    String fallback =
                            parsedIntent.getStringExtra(
                                    "browser_fallback_url"
                            );

                    if (fallback != null &&
                            !fallback.trim().isEmpty()) {

                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(fallback)
                                );

                        startActivity(browserIntent);

                        return true;
                    }

                    /*
                     * Última alternativa:
                     * convertir intent:// en https://
                     */
                    String httpsUrl =
                            url.replaceFirst(
                                    "^intent://",
                                    "https://"
                            );

                    int intentMarker =
                            httpsUrl.indexOf("#Intent;");

                    if (intentMarker >= 0) {
                        httpsUrl =
                                httpsUrl.substring(
                                        0,
                                        intentMarker
                                );
                    }

                    Intent browserIntent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(httpsUrl)
                            );

                    startActivity(browserIntent);

                    return true;
                }
            }

            /*
             * GOOGLE NAVIGATION
             */
            if (url.startsWith("google.navigation:")) {

                Intent mapsIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                mapsIntent.setPackage(
                        "com.google.android.apps.maps"
                );

                try {

                    startActivity(mapsIntent);

                } catch (ActivityNotFoundException e) {

                    mapsIntent.setPackage(null);
                    startActivity(mapsIntent);
                }

                return true;
            }

            /*
             * GEO
             */
            if (url.startsWith("geo:")) {

                Intent mapsIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                mapsIntent.setPackage(
                        "com.google.android.apps.maps"
                );

                try {

                    startActivity(mapsIntent);

                } catch (ActivityNotFoundException e) {

                    mapsIntent.setPackage(null);
                    startActivity(mapsIntent);
                }

                return true;
            }

            /*
             * Google Maps HTTPS.
             *
             * Esto evita que Maps cargue dentro de YOSSI HUB.
             */
            if (url.startsWith(
                    "https://www.google.com/maps"
            ) ||
                    url.startsWith(
                            "https://maps.google.com"
                    )) {

                Intent mapsIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                mapsIntent.setPackage(
                        "com.google.android.apps.maps"
                );

                try {

                    startActivity(mapsIntent);

                } catch (ActivityNotFoundException e) {

                    mapsIntent.setPackage(null);
                    startActivity(mapsIntent);
                }

                return true;
            }

            /*
             * OTRAS APLICACIONES EXTERNAS
             */
            if (url.startsWith("whatsapp:")
                    || url.startsWith("tel:")
                    || url.startsWith("mailto:")
                    || url.startsWith("market:")) {

                Intent externalIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                startActivity(externalIntent);

                return true;
            }

        } catch (Exception e) {

            /*
             * No dejamos que una URL externa defectuosa
             * rompa o bloquee la WebView.
             */
            return true;
        }

        /*
         * Las páginas normales continúan dentro
         * de YOSSI HUB.
         */
        return false;
    }

    private void refreshFcmToken() {

        FirebaseMessaging.getInstance()
                .getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()
                            || task.getResult() == null) {
                        return;
                    }

                    fcmToken = task.getResult();

                    if (webView != null) {

                        webView.post(() ->
                                webView.evaluateJavascript(
                                        "window.dispatchEvent(new Event('yossihub-fcm-ready'));",
                                        null
                                )
                        );
                    }
                });
    }

    private class YossiHubBridge {

        @JavascriptInterface
        public String getFcmToken() {

            return fcmToken == null
                    ? ""
                    : fcmToken;
        }

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }
    }

    private void requestNotificationPermission() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        1001
                );
            }
        }
    }

    private void showWebsite() {

        if (!pageLoaded ||
                !splashTimeElapsed) {
            return;
        }

        webView.setBackgroundColor(Color.WHITE);
        splashView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

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
