package it.affareradar.worker;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadarNotificationListener extends NotificationListenerService {
    private static final String ENDPOINT = "https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName().toLowerCase();
        if (!(pkg.contains("vinted") || pkg.contains("facebook") || pkg.contains("jofogas") || pkg.contains("vatera"))) return;
        Bundle e = sbn.getNotification().extras;
        String title = String.valueOf(e.getCharSequence("android.title", ""));
        String text = String.valueOf(e.getCharSequence("android.text", ""));
        new Thread(() -> send(pkg,title,text)).start();
    }

    private void send(String pkg,String title,String text) {
        try {
            JSONObject item = new JSONObject(); item.put("packageName",pkg); item.put("title",title); item.put("content",text);
            JSONObject root = new JSONObject(); root.put("event","public_notification_candidate"); root.put("item",item);
            HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
            c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setDoOutput(true);
            try(OutputStream os=c.getOutputStream()){ os.write(root.toString().getBytes("UTF-8")); }
            c.getResponseCode(); c.disconnect();
        } catch(Exception ignored) {}
    }
}
