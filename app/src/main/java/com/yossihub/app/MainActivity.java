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
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
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

    private static final int REQUEST_NOTIFICATIONS = 1001;
    private static final int REQUEST_LOCATION = 1002;

    private WebView webView;
    private FrameLayout splashView;

    private boolean pageLoaded = false;
    private boolean splashTimeElapsed = false;

    private volatile String fcmToken = "";

    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermission();
        requestLocationPermission();
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

        webView.setBackgroundColor(Color.rgb(255, 196, 0));

        /*
         * WEBVIEW
         */
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        /*
         * IMPORTANTE:
         * Permite navigator.geolocation dentro de YossiHub.
         */
        settings.setGeolocationEnabled(true);

        /*
         * Puente Android ↔ YossiHub
         */
        webView.addJavascriptInterface(
                new YossiHubBridge(),
                "YossiHub"
        );

        /*
         * =================================================
         * GEOLOCALIZACIÓN DEL WEBVIEW
         * =================================================
         */
        webView.setWebChromeClient(
                new WebChromeClient() {

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback
            ) {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {

                    callback.invoke(
                            origin,
                            true,
                            false
                    );

                    return;
                }

                boolean fine =
                        checkSelfPermission(
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                boolean coarse =
                        checkSelfPermission(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                if (fine || coarse) {

                    callback.invoke(
                            origin,
                            true,
                            false
                    );

                } else {

                    /*
                     * Guardamos la solicitud de la página
                     * mientras Android pregunta el permiso.
                     */
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;

                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            REQUEST_LOCATION
                    );
                }
            }

            @Override
            public void onPermissionRequest(
                    PermissionRequest request
            ) {

                /*
                 * No concedemos permisos web desconocidos
                 * automáticamente.
                 */
                super.onPermissionRequest(request);
            }
        });


        /*
         * =================================================
         * SPLASH
         * =================================================
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
         * =================================================
         * WEBVIEW CLIENT
         * =================================================
         */
        webView.setWebViewClient(
                new WebViewClient() {

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
         * Tiempo del splash
         */
        handler.postDelayed(
                () -> {

                    splashTimeElapsed = true;

                    showWebsite();

                },
                SPLASH_DURATION
        );


        /*
         * Abrir YossiHub
         */
        webView.loadUrl(HOME_URL);
    }


    /*
     * =====================================================
     * ENLACES EXTERNOS
     * =====================================================
     */
    private boolean handleExternalUrl(String url) {

        if (url == null ||
                url.trim().isEmpty()) {

            return false;
        }

        try {

            /*
             * INTENT://
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

                } catch (ActivityNotFoundException e) {

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

                    startActivity(browserIntent);

                    return true;
                }
            }


            /*
             * GOOGLE NAVIGATION
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
             * GOOGLE MAPS HTTPS
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

                    startActivity(mapsIntent);

                } catch (ActivityNotFoundException e) {

                    mapsIntent.setPackage(null);

                    startActivity(mapsIntent);
                }

                return true;
            }


            /*
             * WHATSAPP
             */
            if (
                    url.startsWith("whatsapp:")
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

                    } catch (Exception ignored) {

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
             * TELÉFONO / CORREO / PLAY STORE
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

            return true;
        }

        return false;
    }


    /*
     * =====================================================
     * FIREBASE
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

            fcmToken = task.getResult();

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
     * PUENTE ANDROID
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
     * PERMISO NOTIFICACIONES
     * =====================================================
     */
    private void requestNotificationPermission() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                    )
                            !=
                    PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        REQUEST_NOTIFICATIONS
                );
            }
        }
    }


    /*
     * =====================================================
     * PERMISO GPS
     * =====================================================
     */
    private void requestLocationPermission() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.M
        ) {

            boolean fine =
                    checkSelfPermission(
                            Manifest.permission.ACCESS_FINE_LOCATION
                    )
                            ==
                    PackageManager.PERMISSION_GRANTED;

            boolean coarse =
                    checkSelfPermission(
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                            ==
                    PackageManager.PERMISSION_GRANTED;

            if (!fine && !coarse) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        REQUEST_LOCATION
                );
            }
        }
    }


    /*
     * =====================================================
     * RESULTADO DE LOS PERMISOS
     * =====================================================
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode ==
                REQUEST_LOCATION
        ) {

            boolean granted = false;

            if (grantResults != null) {

                for (int result : grantResults) {

                    if (
                            result ==
                            PackageManager.PERMISSION_GRANTED
                    ) {

                        granted = true;
                        break;
                    }
                }
            }

            if (
                    pendingGeoCallback != null &&
                    pendingGeoOrigin != null
            ) {

                pendingGeoCallback.invoke(
                        pendingGeoOrigin,
                        granted,
                        false
                );

                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
        }
    }


    /*
     * =====================================================
     * SPLASH
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
     * ATRÁS
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
