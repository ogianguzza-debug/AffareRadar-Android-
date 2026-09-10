package it.affareradar.worker;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48,72,48,48);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("AFFARERADAR\nWORKER 24/7"); title.setTextSize(30); title.setGravity(Gravity.CENTER);
        box.addView(title);

        status = new TextView(this); status.setTextSize(20); status.setPadding(0,50,0,30); box.addView(status);

        Button permission = new Button(this);
        permission.setText("IMPOSTAZIONI ACCESSO NOTIFICHE");
        permission.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        box.addView(permission);

        TextView info = new TextView(this);
        info.setText("Test Note8: controllo permesso con API Android 8.1+.");
        info.setTextSize(16); info.setPadding(0,35,0,0); box.addView(info);
        setContentView(box);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        boolean ok = false;
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                ComponentName cn = new ComponentName(this, RadarNotificationListener.class);
                ok = nm != null && nm.isNotificationListenerAccessGranted(cn);
            } else {
                String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
                ok = enabled != null && enabled.contains(getPackageName());
            }
        } catch (Exception ignored) { }
        status.setText(ok ? "WORKER ATTIVO" : "DA ATTIVARE");
    }
}
