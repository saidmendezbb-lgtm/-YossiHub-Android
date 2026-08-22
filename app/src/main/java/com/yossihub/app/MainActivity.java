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

    private final Handler handler =
            new Handler(Looper.getMainLooper());

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

        webView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        webView.setBackgroundColor(
                Color.rgb(255, 196, 0)
        );

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        /*
         * Comunicación página ↔ APK
         */
        webView.addJavascriptInterface(
                new YossiHubBridge(),
                "YossiHub"
        );

        /*
         * SPLASH
         */
        splashView = new FrameLayout(this);

        splashView.setBackgroundColor(
                Color.rgb(255, 196, 0)
        );

        splashView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

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

        logoParams.gravity =
                android.view.Gravity.CENTER;

        splashView.addView(
                logo,
                logoParams
        );

        root.addView(webView);
        root.addView(splashView);

        setContentView(root);

        /*
         * WEBVIEW CLIENT
         */
        webView.setWebViewClient(
                new WebViewClient() {

            /*
             * Android moderno
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
             * Android anteriores
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

                super.onPageFinished(
                        view,
                        url
                );

                pageLoaded = true;

                showWebsite();
            }
        });

        /*
         * Tiempo mínimo del splash
         */
        handler.postDelayed(
                () -> {

                    splashTimeElapsed = true;

                    showWebsite();

                },
                SPLASH_DURATION
        );

        /*
         * Abrir YOSSI HUB
         */
        webView.loadUrl(HOME_URL);
    }


    /*
     * =====================================================
     * ENLACES EXTERNOS
     * =====================================================
     *
     * Maps, WhatsApp, teléfono, etc.
     * NO se cargan dentro de YOSSI HUB.
     *
     * Así, al regresar desde otra aplicación,
     * YOSSI HUB permanece exactamente donde estaba.
     */
    private boolean handleExternalUrl(
            String url
    ) {

        if (url == null ||
                url.trim().isEmpty()) {

            return false;
        }

        try {

            /*
             * =============================================
             * INTENT://
             * =============================================
             */
            if (url.startsWith("intent://")) {

                Intent parsedIntent =
                        Intent.parseUri(
                                url,
                                Intent.URI_INTENT_SCHEME
                        );

                try {

                    startActivity(parsedIntent);

                    return true;

                } catch (
                        ActivityNotFoundException e
                ) {

                    String fallback =
                            parsedIntent
                                    .getStringExtra(
                                            "browser_fallback_url"
                                    );

                    if (fallback != null &&
                            !fallback
                                    .trim()
                                    .isEmpty()) {

                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(fallback)
                                );

                        startActivity(
                                browserIntent
                        );

                        return true;
                    }

                    String httpsUrl =
                            url.replaceFirst(
                                    "^intent://",
                                    "https://"
                            );

                    int marker =
                            httpsUrl.indexOf(
                                    "#Intent;"
                            );

                    if (marker >= 0) {

                        httpsUrl =
                                httpsUrl.substring(
                                        0,
                                        marker
                                );
                    }

                    Intent browserIntent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(httpsUrl)
                            );

                    startActivity(
                            browserIntent
                    );

                    return true;
                }
            }


            /*
             * =============================================
             * GOOGLE NAVIGATION
             * =============================================
             */
            if (url.startsWith(
                    "google.navigation:"
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

                    startActivity(
                            mapsIntent
                    );

                } catch (
                        ActivityNotFoundException e
                ) {

                    mapsIntent.setPackage(null);

                    startActivity(
                            mapsIntent
                    );
                }

                return true;
            }


            /*
             * =============================================
             * GEO
             * =============================================
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

                    startActivity(
                            mapsIntent
                    );

                } catch (
                        ActivityNotFoundException e
                ) {

                    mapsIntent.setPackage(null);

                    startActivity(
                            mapsIntent
                    );
                }

                return true;
            }


            /*
             * =============================================
             * GOOGLE MAPS HTTPS
             * =============================================
             */
            if (
                    url.startsWith(
                            "https://www.google.com/maps"
                    )
                    ||
                    url.startsWith(
                            "https://maps.google.com"
                    )
            ) {

                Intent mapsIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                mapsIntent.setPackage(
                        "com.google.android.apps.maps"
                );

                try {

                    startActivity(
                            mapsIntent
                    );

                } catch (
                        ActivityNotFoundException e
                ) {

                    mapsIntent.setPackage(null);

                    startActivity(
                            mapsIntent
                    );
                }

                return true;
            }


            /*
             * =============================================
             * WHATSAPP
             * =============================================
             *
             * IMPORTANTE:
             *
             * wa.me y api.whatsapp.com también
             * se interceptan aquí.
             *
             * De esta manera la página de WhatsApp
             * NO reemplaza la pantalla del viaje
             * dentro de YOSSI HUB.
             */
            if (
                    url.startsWith(
                            "whatsapp:"
                    )
                    ||
                    url.startsWith(
                            "https://wa.me/"
                    )
                    ||
                    url.startsWith(
                            "http://wa.me/"
                    )
                    ||
                    url.startsWith(
                            "https://api.whatsapp.com/"
                    )
                    ||
                    url.startsWith(
                            "http://api.whatsapp.com/"
                    )
            ) {

                /*
                 * WhatsApp normal
                 */
                try {

                    Intent whatsappIntent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url)
                            );

                    whatsappIntent.setPackage(
                            "com.whatsapp"
                    );

                    startActivity(
                            whatsappIntent
                    );

                    return true;

                } catch (Exception e) {

                    /*
                     * WhatsApp Business
                     */
                    try {

                        Intent businessIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)
                                );

                        businessIntent.setPackage(
                                "com.whatsapp.w4b"
                        );

                        startActivity(
                                businessIntent
                        );

                        return true;

                    } catch (
                            Exception ignored
                    ) {

                        /*
                         * Si WhatsApp no está instalado,
                         * abrir navegador EXTERNO.
                         */
                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)
                                );

                        startActivity(
                                browserIntent
                        );

                        return true;
                    }
                }
            }


            /*
             * =============================================
             * TELÉFONO
             * CORREO
             * PLAY STORE
             * =============================================
             */
            if (
                    url.startsWith("tel:")
                    ||
                    url.startsWith("mailto:")
                    ||
                    url.startsWith("market:")
            ) {

                Intent externalIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                startActivity(
                        externalIntent
                );

                return true;
            }

        } catch (Exception e) {

            /*
             * Evitar que un enlace externo defectuoso
             * bloquee la WebView.
             */
            return true;
        }


        /*
         * Los enlaces normales siguen
         * navegando dentro de YOSSI HUB.
         */
        return false;
    }


    /*
     * =====================================================
     * FIREBASE TOKEN
     * =====================================================
     */
    private void refreshFcmToken() {

        FirebaseMessaging
                .getInstance()
                .getToken()
                .addOnCompleteListener(
                        task -> {

            if (!task.isSuccessful() ||
                    task.getResult() == null) {

                return;
            }

            fcmToken =
                    task.getResult();

            if (webView != null) {

                webView.post(
                        () ->
                                webView.evaluateJavascript(
                                        "window.dispatchEvent(new Event('yossihub-fcm-ready'));",
                                        null
                                )
                );
            }
        });
    }


    /*
     * =====================================================
     * PUENTE JAVASCRIPT
     * =====================================================
     */
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


    /*
     * =====================================================
     * PERMISO DE NOTIFICACIONES
     * =====================================================
     */
    private void requestNotificationPermission() {

        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    checkSelfPermission(
                            Manifest.permission
                                    .POST_NOTIFICATIONS
                    )
                            !=
                    PackageManager
                            .PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission
                                        .POST_NOTIFICATIONS
                        },
                        1001
                );
            }
        }
    }


    /*
     * =====================================================
     * QUITAR SPLASH
     * =====================================================
     */
    private void showWebsite() {

        if (
                !pageLoaded ||
                !splashTimeElapsed
        ) {

            return;
        }

        webView.setBackgroundColor(
                Color.WHITE
        );

        splashView.setVisibility(
                View.GONE
        );
    }


    /*
     * =====================================================
     * BOTÓN ATRÁS
     * =====================================================
     */
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


    /*
     * =====================================================
     * CERRAR
     * =====================================================
     */
    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        if (webView != null) {

            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
}
