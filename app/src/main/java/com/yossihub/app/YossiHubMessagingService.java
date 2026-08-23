package com.yossihub.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class YossiHubMessagingService extends FirebaseMessagingService {

    private static final String TAG = "YossiHubFCM";
    private static final String CHANNEL_ADMIN = "yossihub_admin";
private static final String CHANNEL_OWNER = "yossihub_owner";
private static final String CHANNEL_DRIVER = "yossihub_driver";
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "Mensaje recibido de Firebase");

        String title = "YossiHub";
        String body = "Tienes una nueva notificación";
         String role = "";

if (!remoteMessage.getData().isEmpty()
        && remoteMessage.getData().containsKey("role")) {

    role = remoteMessage.getData().get("role");

    if (role == null) {
        role = "";
    }
}
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }

            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }

            if (remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
        }
         String roleLabel = "";

if (role.equalsIgnoreCase("admin") ||
        role.equalsIgnoreCase("administrator")) {

    roleLabel = "ADMINISTRADOR";

} else if (role.equalsIgnoreCase("owner") ||
        role.equalsIgnoreCase("propietario")) {

    roleLabel = "PROPIETARIO";

} else if (role.equalsIgnoreCase("driver") ||
        role.equalsIgnoreCase("conductor")) {

    roleLabel = "CONDUCTOR";
}

if (!roleLabel.isEmpty()) {
    title = roleLabel + " • " + title;
}
        createNotificationChannel();
        showNotification(title, body, role, remoteMessage.getData());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Log.d(TAG, "Nuevo token FCM: " + token);
    }

private void showNotification(String title, String body, String role, java.util.Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
    intent.putExtra(entry.getKey(), entry.getValue());
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String channelId = CHANNEL_DRIVER;

if (role.equalsIgnoreCase("admin") ||
        role.equalsIgnoreCase("administrator")) {

    channelId = CHANNEL_ADMIN;

} else if (role.equalsIgnoreCase("owner") ||
        role.equalsIgnoreCase("propietario")) {

    channelId = CHANNEL_OWNER;
}
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        try {
            notificationManager.notify(
                    (int) System.currentTimeMillis(),
                    builder.build()
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Permiso de notificaciones no concedido", e);
        }
    }

    private void createNotificationChannel() {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        NotificationChannel adminChannel = new NotificationChannel(
                CHANNEL_ADMIN,
                "YossiHub • Administrador",
                NotificationManager.IMPORTANCE_HIGH
        );

        adminChannel.setDescription(
                "Notificaciones para administradores de YossiHub"
        );

        NotificationChannel ownerChannel = new NotificationChannel(
                CHANNEL_OWNER,
                "YossiHub • Propietario",
                NotificationManager.IMPORTANCE_HIGH
        );

        ownerChannel.setDescription(
                "Notificaciones para propietarios de YossiHub"
        );

        NotificationChannel driverChannel = new NotificationChannel(
                CHANNEL_DRIVER,
                "YossiHub • Conductor",
                NotificationManager.IMPORTANCE_HIGH
        );

        driverChannel.setDescription(
                "Notificaciones para conductores de YossiHub"
        );

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        if (notificationManager != null) {

            notificationManager.createNotificationChannel(adminChannel);
            notificationManager.createNotificationChannel(ownerChannel);
            notificationManager.createNotificationChannel(driverChannel);
        }
    }
    }
}
