package it.affareradar.worker;

import android.app.Activity;
import android.content.Intent;
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
    private static final String ENDPOINT="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";
    private TextView server, result;
    private EditText code;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(48,72,48,48); box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=new TextView(this); title.setText("AFFARERADAR\nWORKER 24/7"); title.setTextSize(30); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView notif=new TextView(this); notif.setText("NOTIFICHE: AUTORIZZATE"); notif.setTextSize(20); notif.setPadding(0,35,0,12); box.addView(notif);
        server=new TextView(this); server.setTextSize(20); server.setPadding(0,8,0,25); box.addView(server);
        code=new EditText(this); code.setHint("Codice associazione a 6 cifre"); code.setInputType(2); box.addView(code);
        Button pair=new Button(this); pair.setText("COLLEGA AL SERVER"); pair.setOnClickListener(v->pair()); box.addView(pair);
        result=new TextView(this); result.setTextSize(16); result.setPadding(0,12,0,24); box.addView(result);
        Button settings=new Button(this); settings.setText("IMPOSTAZIONI NOTIFICHE"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); box.addView(settings);
        TextView info=new TextView(this); info.setText("Il Worker non usa password o cookie dei marketplace. Vinted personale resta isolato."); info.setTextSize(15); info.setPadding(0,28,0,0); box.addView(info);
        setContentView(box); refreshServer();
    }

    private void refreshServer(){ String t=getSharedPreferences("radar",MODE_PRIVATE).getString("worker_token",""); if(t.isEmpty()){server.setText("SERVER: NON ASSOCIATO");return;} server.setText("SERVER: VERIFICA..."); ping(t); }
    private void ping(String token){ new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection(); c.setRequestProperty("x-worker-token",token); c.setConnectTimeout(10000); c.setReadTimeout(10000); int r=c.getResponseCode(); c.disconnect(); runOnUiThread(()->server.setText(r>=200&&r<300?"SERVER: ONLINE":"SERVER: ERRORE "+r));}catch(Exception e){runOnUiThread(()->server.setText("SERVER: OFFLINE"));}}).start(); }
    private void pair(){ final String pc=code.getText().toString().trim(); if(pc.length()!=6){result.setText("Inserisci le 6 cifre.");return;} result.setText("Collegamento in corso..."); new Thread(()->{try{JSONObject b=new JSONObject(); b.put("event","pair"); HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection(); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("x-pairing-code",pc); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setDoOutput(true); try(OutputStream os=c.getOutputStream()){os.write(b.toString().getBytes("UTF-8"));} int r=c.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(r>=200&&r<300?c.getInputStream():c.getErrorStream())); StringBuilder s=new StringBuilder(); String l; while((l=br.readLine())!=null)s.append(l); c.disconnect(); if(r>=200&&r<300){JSONObject j=new JSONObject(s.toString()); String token=j.optString("workerToken",j.optString("token","")); if(token.length()<10) throw new Exception("token"); getSharedPreferences("radar",MODE_PRIVATE).edit().putString("worker_token",token).apply(); runOnUiThread(()->{result.setText("ASSOCIATO CORRETTAMENTE");server.setText("SERVER: ONLINE");});}else runOnUiThread(()->result.setText("Codice non valido o scaduto ("+r+")"));}catch(Exception e){runOnUiThread(()->result.setText("Errore di collegamento"));}}).start(); }
}
