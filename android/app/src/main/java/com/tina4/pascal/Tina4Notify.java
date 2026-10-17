package com.tina4.pascal;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Local notifications for the Tina4 engine. Native (Tina4NotifyAndroid.pas) calls
 * show(); an HTML notify.show('Title','Body') — or an SSE/WebSocket handler
 * calling Tina4Notify — lands here. init() is called from MainActivity.onCreate
 * to hold the app context and create the channel. Local only; remote (FCM) is
 * Phase 2, and it posts through this same path.
 *
 * POST_NOTIFICATIONS (API 33+) is requested on the FIRST show(), like the camera,
 * microphone and location permissions — never at launch, so an app that never
 * notifies never shows the dialog. The notification that triggered the request is
 * held and posted if the user allows it.
 */
public class Tina4Notify {
    private static Context APP;
    private static Activity ACT;                     // for the runtime permission dialog
    public static final int REQUEST_CODE = 4712;
    private static String[] pending;                 // {title, body, tag} awaiting permission
    private static final String CH = "tina4.default";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int seq = 1;

    public static void init(Context ctx) {
        APP = ctx.getApplicationContext();
        if (ctx instanceof Activity) ACT = (Activity) ctx;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                (NotificationManager) APP.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                CH, "Tina4", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
    }

    private static boolean allowed() {
        return Build.VERSION.SDK_INT < 33 ||
            APP.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    /** MainActivity forwards the answer to the dialog raised by show(). */
    public static void permissionResult(boolean granted) {
        String[] p = pending;
        pending = null;
        if (granted && p != null) post(p[0], p[1], p[2]);
    }

    // called from native via JNI — deliver on the main thread
    public static void show(final String title, final String body, final String tag) {
        if (APP == null) return;
        if (!allowed()) {
            if (ACT == null) return;
            pending = new String[] { title, body, tag };
            MAIN.post(new Runnable() {
                public void run() {
                    ACT.requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, REQUEST_CODE);
                }
            });
            return;
        }
        post(title, body, tag);
    }

    private static void post(final String title, final String body, final String tag) {
        MAIN.post(new Runnable() {
            public void run() {
                NotificationManager nm =
                    (NotificationManager) APP.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                    ? new Notification.Builder(APP, CH)
                    : new Notification.Builder(APP);
                b.setContentTitle(title)
                 .setContentText(body)
                 .setSmallIcon(APP.getApplicationInfo().icon)
                 .setAutoCancel(true);
                int id = (tag != null && tag.length() > 0) ? tag.hashCode() : seq++;
                nm.notify(id, b.build());
            }
        });
    }
}
