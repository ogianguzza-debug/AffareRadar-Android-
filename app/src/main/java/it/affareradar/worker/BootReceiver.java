package it.affareradar.worker;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                ComponentName cn = new ComponentName(context, RadarNotificationListener.class);
                NotificationListenerService.requestRebind(cn);
            } catch (Exception ignored) { }
        }
    }
}
