package it.affareradar.worker;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadarNotificationListener extends NotificationListenerService {
    private static final String ENDPOINT = "https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatLoop = new Runnable() {
        @Override public void run() {
            sendHeartbeat();
            handler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        handler.removeCallbacks(heartbeatLoop);
        handler.post(heartbeatLoop);
    }

    @Override public void onListenerDisconnected() {
        handler.removeCallbacks(heartbeatLoop);
        try { requestRebind(new ComponentName(this, RadarNotificationListener.class)); } catch (Exception ignored) { }
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(heartbeatLoop);
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName().toLowerCase();
        if (!(pkg.contains("vinted") || pkg.contains("facebook") || pkg.contains("jofogas") || pkg.contains("vatera"))) return;
        Bundle e = sbn.getNotification().extras;
        String title = String.valueOf(e.getCharSequence("android.title", ""));
        String text = String.valueOf(e.getCharSequence("android.text", ""));
        String key = sbn.getKey();
        new Thread(() -> sendNotification(pkg, title, text, key)).start();
    }

    private String token() { return getSharedPreferences("radar", MODE_PRIVATE).getString("worker_token", ""); }

    private void sendHeartbeat() {
        final String t = token();
        if (t.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("event", "heartbeat");
                body.put("appVersion", "1.1-note8-worker");
                body.put("deviceModel", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                JSONObject caps = new JSONObject();
                caps.put("officialNotifications", true);
                caps.put("notificationListener", true);
                caps.put("autoRebind", true);
                caps.put("bootRebind", true);
                body.put("capabilities", caps);
                post(body, t);
            } catch (Exception ignored) { }
        }).start();
    }

    private void sendNotification(String pkg, String title, String text, String key) {
        final String t = token();
        if (t.isEmpty()) return;
        try {
            JSONObject item = new JSONObject();
            item.put("packageName", pkg); item.put("title", title); item.put("content", text); item.put("key", key == null ? "" : key);
            JSONArray items = new JSONArray(); items.put(item);
            JSONObject root = new JSONObject(); root.put("event", "official_notifications"); root.put("items", items);
            post(root, t); sendHeartbeat();
        } catch (Exception ignored) { }
    }

    private void post(JSONObject body, String t) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-worker-token", t);
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes("UTF-8")); }
        c.getResponseCode(); c.disconnect();
    }
}
