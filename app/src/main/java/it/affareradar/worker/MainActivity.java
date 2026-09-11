package it.affareradar.worker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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
    private static final String ENDPOINT="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";
    private TextView server, result, status;
    private EditText code;
    private Button pair;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        DealAlertJob.schedule(this);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(48,72,48,48); box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=new TextView(this); title.setText("AFFARERADAR\nWORKER 24/7"); title.setTextSize(30); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView notif=new TextView(this); notif.setText("NOTIFICHE: AUTORIZZATE"); notif.setTextSize(20); notif.setPadding(0,35,0,12); box.addView(notif);
        server=new TextView(this); server.setTextSize(20); server.setPadding(0,8,0,8); box.addView(server);
        status=new TextView(this); status.setTextSize(18); status.setPadding(0,8,0,20); box.addView(status);
        code=new EditText(this); code.setHint("Codice associazione a 6 cifre"); code.setInputType(2); box.addView(code);
        pair=new Button(this); pair.setText("COLLEGA AL SERVER"); pair.setOnClickListener(v->pair()); box.addView(pair);
        result=new TextView(this); result.setTextSize(16); result.setPadding(0,12,0,24); box.addView(result);
        Button settings=new Button(this); settings.setText("IMPOSTAZIONI NOTIFICHE"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); box.addView(settings);
        TextView info=new TextView(this); info.setText("Il Worker non usa password o cookie dei marketplace. Vinted personale resta isolato."); info.setTextSize(15); info.setPadding(0,28,0,0); box.addView(info);
        setContentView(box); refreshServer();
    }

    private String token(){ return getSharedPreferences("radar",MODE_PRIVATE).getString("worker_token",""); }

    private void refreshServer(){
        String t=token();
        if(t.isEmpty()){
            server.setText("SERVER: NON ASSOCIATO");
            status.setText("STATO: IN ATTESA DI ASSOCIAZIONE");
            code.setVisibility(View.VISIBLE);
            pair.setVisibility(View.VISIBLE);
            return;
        }
        code.setVisibility(View.GONE);
        pair.setVisibility(View.GONE);
        result.setText("ASSOCIATO");
        server.setText("SERVER: VERIFICA...");
        status.setText("WORKER: AVVIO...");
        pingAndHeartbeat(t);
    }

    private void pingAndHeartbeat(String t){
        new Thread(()->{
            boolean ok=false;
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                c.setRequestProperty("x-worker-token",t);
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                int r=c.getResponseCode(); c.disconnect(); ok=r>=200&&r<300;
            }catch(Exception ignored){}
            final boolean pingOk=ok;
            runOnUiThread(()->server.setText(pingOk?"SERVER: ONLINE":"SERVER: OFFLINE"));
            if(pingOk) sendHeartbeat(t);
        }).start();
    }

    private void sendHeartbeat(String t){
        new Thread(()->{
            try{
                JSONObject b=new JSONObject();
                b.put("event","heartbeat");
                b.put("appVersion","0.5-note8-worker");
                b.put("deviceModel",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
                JSONObject caps=new JSONObject(); caps.put("officialNotifications",true); caps.put("notificationListener",true); caps.put("mainScreenHeartbeat",true);
                b.put("capabilities",caps);
                HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("x-worker-token",t);
                c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setDoOutput(true);
                try(OutputStream os=c.getOutputStream()){os.write(b.toString().getBytes("UTF-8"));}
                int r=c.getResponseCode(); c.disconnect();
                runOnUiThread(()->status.setText(r>=200&&r<300?"WORKER: ATTIVO 24/7":"WORKER: ERRORE HEARTBEAT "+r));
            }catch(Exception e){runOnUiThread(()->status.setText("WORKER: HEARTBEAT NON RIUSCITO"));}
        }).start();
    }

    private void pair(){ final String pc=code.getText().toString().trim(); if(pc.length()!=6){result.setText("Inserisci le 6 cifre.");return;} result.setText("Collegamento in corso..."); new Thread(()->{try{JSONObject b=new JSONObject(); b.put("event","pair"); HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection(); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("x-pairing-code",pc); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setDoOutput(true); try(OutputStream os=c.getOutputStream()){os.write(b.toString().getBytes("UTF-8"));} int r=c.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(r>=200&&r<300?c.getInputStream():c.getErrorStream())); StringBuilder s=new StringBuilder(); String l; while((l=br.readLine())!=null)s.append(l); c.disconnect(); if(r>=200&&r<300){JSONObject j=new JSONObject(s.toString()); String token=j.optString("workerToken",j.optString("token","")); if(token.length()<10) throw new Exception("token"); getSharedPreferences("radar",MODE_PRIVATE).edit().putString("worker_token",token).apply(); runOnUiThread(()->{result.setText("ASSOCIATO CORRETTAMENTE"); code.setVisibility(View.GONE); pair.setVisibility(View.GONE); server.setText("SERVER: ONLINE"); status.setText("WORKER: AVVIO...");}); sendHeartbeat(token);}else runOnUiThread(()->result.setText("Codice non valido o scaduto ("+r+")"));}catch(Exception e){runOnUiThread(()->result.setText("Errore di collegamento"));}}).start(); }
}
