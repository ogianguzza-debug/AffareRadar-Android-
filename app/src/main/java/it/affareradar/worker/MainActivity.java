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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String ENDPOINT = "https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";
    private TextView notifStatus, serverStatus, pairStatus;
    private EditText code;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(42,55,42,42);
        TextView title=new TextView(this); title.setText("AFFARERADAR WORKER 24/7"); title.setTextSize(27); title.setGravity(Gravity.CENTER); box.addView(title);
        notifStatus=new TextView(this); notifStatus.setTextSize(19); notifStatus.setPadding(0,35,0,8); box.addView(notifStatus);
        serverStatus=new TextView(this); serverStatus.setTextSize(19); serverStatus.setPadding(0,8,0,18); box.addView(serverStatus);
        code=new EditText(this); code.setHint("Codice associazione a 6 cifre"); code.setInputType(2); box.addView(code);
        Button pair=new Button(this); pair.setText("COLLEGA AL SERVER"); pair.setOnClickListener(v->pair()); box.addView(pair);
        pairStatus=new TextView(this); pairStatus.setTextSize(16); pairStatus.setPadding(0,10,0,22); box.addView(pairStatus);
        Button permission=new Button(this); permission.setText("IMPOSTAZIONI ACCESSO NOTIFICHE"); permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); box.addView(permission);
        TextView info=new TextView(this); info.setText("Nessuna password o cookie dei marketplace. Il Worker inoltra solo notifiche ufficiali autorizzate. Vinted personale resta isolato."); info.setTextSize(15); info.setPadding(0,28,0,0); box.addView(info);
        setContentView(box); refresh(); ping();
    }

    @Override protected void onResume(){ super.onResume(); refresh(); }
    private boolean hasAccess(){
        ComponentName cn=new ComponentName(this,RadarNotificationListener.class);
        if(Build.VERSION.SDK_INT>=27){ NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE); return nm.isNotificationListenerAccessGranted(cn); }
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners"); return enabled!=null&&enabled.contains(getPackageName());
    }
    private void refresh(){ notifStatus.setText(hasAccess()?"NOTIFICHE: ATTIVE":"NOTIFICHE: DA ATTIVARE"); String token=getSharedPreferences("radar",MODE_PRIVATE).getString("worker_token",""); if(token.isEmpty()) serverStatus.setText("SERVER: NON ASSOCIATO"); }
    private void ping(){ String token=getSharedPreferences("radar",MODE_PRIVATE).getString("worker_token",""); if(token.isEmpty())return; new Thread(()->{ try{ HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection(); c.setRequestProperty("x-worker-token",token); int r=c.getResponseCode(); c.disconnect(); runOnUiThread(()->serverStatus.setText(r>=200&&r<300?"SERVER: ONLINE":"SERVER: ERRORE "+r)); }catch(Exception e){runOnUiThread(()->serverStatus.setText("SERVER: OFFLINE"));}}).start(); }
    private void pair(){ final String pc=code.getText().toString().trim(); if(pc.length()!=6){pairStatus.setText("Inserisci il codice di 6 cifre.");return;} pairStatus.setText("Collegamento..."); new Thread(()->{ try{ JSONObject body=new JSONObject(); body.put("event","pair"); HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection(); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("x-pairing-code",pc); c.setDoOutput(true); try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes("UTF-8"));} int r=c.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader((r>=200&&r<300)?c.getInputStream():c.getErrorStream())); StringBuilder s=new StringBuilder(); String line; while((line=br.readLine())!=null)s.append(line); c.disconnect(); if(r>=200&&r<300){JSONObject j=new JSONObject(s.toString()); String token=j.optString("workerToken",j.optString("token","")); if(token.isEmpty())throw new Exception("token mancante"); getSharedPreferences("radar",MODE_PRIVATE).edit().putString("worker_token",token).apply(); runOnUiThread(()->{pairStatus.setText("ASSOCIATO");serverStatus.setText("SERVER: ONLINE");});}else runOnUiThread(()->pairStatus.setText("Codice non valido/scaduto ("+r+")")); }catch(Exception e){runOnUiThread(()->pairStatus.setText("Errore collegamento"));}}).start(); }
}
