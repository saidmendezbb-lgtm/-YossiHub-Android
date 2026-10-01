package com.yossihub.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.FileProvider;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://yossihub.com/";
    private static final long SPLASH_DURATION = 4000;

    private static final int REQUEST_NOTIFICATIONS = 1001;
    private static final int REQUEST_LOCATION = 1002;
    private static final int REQUEST_FILE_CHOOSER = 1003;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private FrameLayout splashView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    private volatile boolean forceCameraCapture = false;
    private volatile String pendingCameraFacing = "environment";
    private volatile String fcmToken = "";

    private boolean pageLoaded = false;
    private boolean splashTimeElapsed = false;

    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    /*
     * Solo HTTPS y los dos dominios exactos de Yossi Hub.
     * No acepta archivos, JavaScript, credenciales ni otros puertos.
     */
    private static boolean isTrustedYossiUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 32 || c == 127 || c == '\\') {
                return false;
            }
        }

        try {
            URI parsed = new URI(url);

            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || parsed.isOpaque()
                    || parsed.getRawUserInfo() != null) {
                return false;
            }

            String host = parsed.getHost();
            String authority = parsed.getRawAuthority();

            if (host == null || authority == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);
            authority = authority.toLowerCase(Locale.ROOT);

            if (!"yossihub.com".equals(host)
                    && !"www.yossihub.com".equals(host)) {
                return false;
            }

            int port = parsed.getPort();

            if (port != -1 && port != 443) {
                return false;
            }

            String expectedAuthority = host + (port == 443 ? ":443" : "");

            if (!expectedAuthority.equals(authority)) {
                return false;
            }

            Uri androidUri = Uri.parse(url);

            return "https".equalsIgnoreCase(androidUri.getScheme())
                    && host.equalsIgnoreCase(androidUri.getHost())
                    && androidUri.getPort() == port
                    && androidUri.getUserInfo() == null;

        } catch (Exception ignored) {
            return false;
        }
    }

    private String trustedTargetFromIntent(Intent intent) {
        if (intent == null) {
            return HOME_URL;
        }

        try {
            String target = intent.getStringExtra("target");

            if (isTrustedYossiUrl(target)) {
                return target;
            }

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri data = intent.getData();

                if (data != null && isTrustedYossiUrl(data.toString())) {
                    return data.toString();
                }
            }

        } catch (Exception ignored) {
        }

        return HOME_URL;
    }

    private void loadTrustedUrl(String url) {
        if (webView != null) {
            webView.loadUrl(isTrustedYossiUrl(url) ? url : HOME_URL);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(20, 24, 28));
        getWindow().getDecorView().setSystemUiVisibility(0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 196, 0));

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.rgb(255, 196, 0));

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setGeolocationEnabled(true);

        // Impide cargar archivos privados o contenido local en el visor.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new YossiHubBridge(), "YossiHub");

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                cancelFileChooser();

                if (!isTrustedYossiUrl(view.getUrl())) {
                    callback.onReceiveValue(null);
                    return true;
                }

                filePathCallback = callback;

                boolean useCamera = forceCameraCapture
                        || (params != null && params.isCaptureEnabled());

                forceCameraCapture = false;

                try {
                    if (useCamera) {
                        File photo = File.createTempFile(
                                "yossihub_camera_", ".jpg", getCacheDir());

                        cameraImageUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                photo);

                        Intent camera = new Intent(
                                android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                        if ("user".equals(pendingCameraFacing)) {
                            camera.putExtra("android.intent.extras.CAMERA_FACING", 1);
                            camera.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                        } else {
                            camera.putExtra("android.intent.extras.CAMERA_FACING", 0);
                        }

                        camera.putExtra(
                                android.provider.MediaStore.EXTRA_OUTPUT,
                                cameraImageUri);

                        camera.setClipData(ClipData.newRawUri(
                                "YossiHub photo", cameraImageUri));

                        camera.addFlags(
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        if (camera.resolveActivity(getPackageManager()) == null) {
                            cancelFileChooser();
                            return true;
                        }

                        startActivityForResult(camera, REQUEST_FILE_CHOOSER);

                    } else {
                        Intent gallery;

                        if (params != null) {
                            gallery = params.createIntent();
                        } else {
                            gallery = new Intent(Intent.ACTION_GET_CONTENT);
                            gallery.addCategory(Intent.CATEGORY_OPENABLE);
                            gallery.setType("image/*");
                        }

                        startActivityForResult(gallery, REQUEST_FILE_CHOOSER);
                    }

                } catch (Exception ignored) {
                    forceCameraCapture = false;
                    pendingCameraFacing = "environment";
                    cancelFileChooser();
                }

                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {

                if (!isTrustedYossiUrl(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }

                boolean granted = hasLocationPermission();

                if (granted) {
                    callback.invoke(origin, true, false);
                } else {
                    if (pendingGeoCallback != null) {
                        pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
                    }

                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestLocationPermission();
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                if (request == null || request.getUrl() == null) {
                    return true;
                }

                String url = request.getUrl().toString();

                if (!request.isForMainFrame()) {
                    return !isTrustedYossiUrl(url);
                }

                return handleExternalUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {

                if (request == null || request.getUrl() == null) {
                    return blockedResponse();
                }

                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if ("file".equalsIgnoreCase(scheme)
                        || "content".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme)) {
                    return blockedResponse();
                }

                if (request.isForMainFrame()
                        && !isTrustedYossiUrl(uri.toString())) {
                    return blockedResponse();
                }

                return null;
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon) {

                if (!isTrustedYossiUrl(url)) {
                    view.stopLoading();
                    loadTrustedUrl(HOME_URL);
                    return;
                }

                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (isTrustedYossiUrl(url)) {
                    pageLoaded = true;
                    showWebsite();
                }
            }
        });

        splashView = new FrameLayout(this);
        splashView.setBackgroundColor(Color.rgb(255, 196, 0));
        splashView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int size = (int) (180 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(size, size);
        logoParams.gravity = Gravity.CENTER;

        splashView.addView(logo, logoParams);
        root.addView(webView);
        root.addView(splashView);
        setContentView(root);

        handler.postDelayed(() -> {
            splashTimeElapsed = true;
            showWebsite();
        }, SPLASH_DURATION);

        loadTrustedUrl(trustedTargetFromIntent(getIntent()));

        refreshFcmToken();
        requestNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadTrustedUrl(trustedTargetFromIntent(intent));
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    /*
     * Las páginas externas se abren fuera del WebView.
     * Los esquemas peligrosos se descartan.
     */
    private boolean handleExternalUrl(String url) {
        if (isTrustedYossiUrl(url)) {
            return false;
        }

        if (url == null || url.isEmpty()) {
            return true;
        }

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();

            if (scheme == null) {
                return true;
            }

            scheme = scheme.toLowerCase(Locale.ROOT);

            if ("intent".equals(scheme)) {
                handleIntentUrl(url);
                return true;
            }

            if (!isAllowedExternalUri(uri)) {
                return true;
            }

            String host = uri.getHost();
            host = host == null ? "" : host.toLowerCase(Locale.ROOT);

            if ("geo".equals(scheme)
                    || "google.navigation".equals(scheme)
                    || isGoogleMapsUrl(url)) {

                openGoogleMaps(url);

            } else if ("whatsapp".equals(scheme)
                    || "wa.me".equals(host)
                    || "api.whatsapp.com".equals(host)) {

                openWhatsApp(url);

            } else {
                openExternal(uri);
            }

        } catch (Exception ignored) {
        }

        return true;
    }

    private boolean isAllowedExternalUri(Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        if ("https".equals(scheme) || "http".equals(scheme)) {
            return uri.getHost() != null && uri.getUserInfo() == null;
        }

        return "tel".equals(scheme)
                || "mailto".equals(scheme)
                || "market".equals(scheme)
                || "whatsapp".equals(scheme)
                || "geo".equals(scheme)
                || "google.navigation".equals(scheme);
    }

    private void handleIntentUrl(String url) {
        try {
            Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String fallback = parsed.getStringExtra("browser_fallback_url");
            Uri data = parsed.getData();

            if (isGoogleMapsUrl(fallback)) {
                openGoogleMaps(fallback);
                return;
            }

            if (data != null && isGoogleMapsUrl(data.toString())) {
                openGoogleMaps(data.toString());
                return;
            }

            if (isAllowedExternalUri(data)) {
                String packageName = parsed.getPackage();

                // Reconstruir el Intent sin componentes ni extras recibidos.
                Intent safe = new Intent(Intent.ACTION_VIEW, data);
                safe.addCategory(Intent.CATEGORY_BROWSABLE);

                if ("com.whatsapp".equals(packageName)
                        || "com.whatsapp.w4b".equals(packageName)
                        || "com.google.android.apps.maps".equals(packageName)) {
                    safe.setPackage(packageName);
                }

                try {
                    startActivity(safe);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }

            if (fallback != null) {
                Uri fallbackUri = Uri.parse(fallback);
                String scheme = fallbackUri.getScheme();

                if (("https".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme))
                        && isAllowedExternalUri(fallbackUri)) {
                    openExternal(fallbackUri);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private boolean isGoogleMapsUrl(String url) {
        if (url == null) {
            return false;
        }

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (!"https".equalsIgnoreCase(scheme)
                    && !"http".equalsIgnoreCase(scheme)) {
                return false;
            }

            if (host == null || uri.getUserInfo() != null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);

            if ("maps.google.com".equals(host)) {
                return true;
            }

            String path = uri.getPath();

            return "www.google.com".equals(host)
                    && path != null
                    && ("/maps".equals(path) || path.startsWith("/maps/"));

        } catch (Exception ignored) {
            return false;
        }
    }

    private void openExternal(Uri uri) {
        if (!isAllowedExternalUri(uri)) {
            return;
        }

        try {
            Intent external = new Intent(Intent.ACTION_VIEW, uri);
            external.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(external);
        } catch (Exception ignored) {
        }
    }

    private void openGoogleMaps(String url) {
        try {
            Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            maps.setPackage("com.google.android.apps.maps");

            try {
                startActivity(maps);
            } catch (ActivityNotFoundException ignored) {
                openExternal(Uri.parse(url));
            }

        } catch (Exception ignored) {
        }
    }

    private void openWhatsApp(String url) {
        Uri uri = Uri.parse(url);

        for (String packageName : new String[]{
                "com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                Intent whatsapp = new Intent(Intent.ACTION_VIEW, uri);
                whatsapp.setPackage(packageName);
                startActivity(whatsapp);
                return;
            } catch (Exception ignored) {
            }
        }

        openExternal(uri);
    }

    private void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                return;
            }

            fcmToken = task.getResult();

            handler.post(() -> {
                if (webView != null && isTrustedYossiUrl(webView.getUrl())) {
                    webView.evaluateJavascript(
                            "window.dispatchEvent(new Event('yossihub-fcm-ready'));",
                            null);
                }
            });
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

        @JavascriptInterface
        public void prepareCamera(String facing) {
            pendingCameraFacing = "user".equalsIgnoreCase(facing)
                    ? "user" : "environment";
            forceCameraCapture = true;
        }

        @JavascriptInterface
        public void prepareGallery() {
            forceCameraCapture = false;
            pendingCameraFacing = "environment";
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (!hasLocationPermission()) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION && pendingGeoCallback != null) {
            pendingGeoCallback.invoke(
                    pendingGeoOrigin,
                    hasLocationPermission() && isTrustedYossiUrl(pendingGeoOrigin),
                    false);

            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    private void showWebsite() {
        if (pageLoaded && splashTimeElapsed
                && webView != null && splashView != null) {
            webView.setBackgroundColor(Color.WHITE);
            splashView.setVisibility(View.GONE);
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

    private void cancelFileChooser() {
        ValueCallback<Uri[]> callback = filePathCallback;
        filePathCallback = null;
        cameraImageUri = null;

        if (callback != null) {
            try {
                callback.onReceiveValue(null);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_FILE_CHOOSER) {
            return;
        }

        ValueCallback<Uri[]> callback = filePathCallback;
        filePathCallback = null;

        if (callback == null) {
            cameraImageUri = null;
            return;
        }

        Uri[] results = null;

        try {
            if (resultCode == Activity.RESULT_OK) {
                if (cameraImageUri != null
                        && (data == null || data.getData() == null)) {
                    results = new Uri[]{cameraImageUri};

                } else if (data != null && data.getClipData() != null) {
                    ClipData clips = data.getClipData();
                    results = new Uri[clips.getItemCount()];

                    for (int i = 0; i < clips.getItemCount(); i++) {
                        results[i] = clips.getItemAt(i).getUri();
                    }

                } else if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }

        } catch (Exception ignored) {
            results = null;

        } finally {
            cameraImageUri = null;
            forceCameraCapture = false;
            pendingCameraFacing = "environment";
        }

        try {
            callback.onReceiveValue(results);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cancelFileChooser();

        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }

        if (webView != null) {
            webView.removeJavascriptInterface("YossiHub");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
