package it.affareradar.worker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String ENDPOINT="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-worker";
    private static final String DASHBOARD_API="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-dashboard?api=1";
    private TextView server, result, status;
    private EditText code;
    private Button pair;
    private JSONArray dealsCache = new JSONArray();
    private LinearLayout dealsBox;
    private TextView dashStatus;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        DealAlertJob.schedule(this);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
        showWorkerHome();
    }

    private void showWorkerHome(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(48,72,48,48); box.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView scroll=new ScrollView(this); scroll.addView(box);

        TextView title=new TextView(this); title.setText("AFFARERADAR\nWORKER 24/7"); title.setTextSize(30); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView notif=new TextView(this); notif.setText("NOTIFICHE: AUTORIZZATE"); notif.setTextSize(20); notif.setPadding(0,35,0,12); box.addView(notif);
        server=new TextView(this); server.setTextSize(20); server.setPadding(0,8,0,8); box.addView(server);
        status=new TextView(this); status.setTextSize(18); status.setPadding(0,8,0,20); box.addView(status);
        code=new EditText(this); code.setHint("Codice associazione a 6 cifre"); code.setInputType(2); box.addView(code);
        pair=new Button(this); pair.setText("COLLEGA AL SERVER"); pair.setOnClickListener(v->pair()); box.addView(pair);
        result=new TextView(this); result.setTextSize(16); result.setPadding(0,12,0,24); box.addView(result);

        Button radar=new Button(this); radar.setText("🔎 VEDI COSA HA TROVATO"); radar.setTextSize(17); radar.setPadding(20,18,20,18); radar.setOnClickListener(v->showRadar()); box.addView(radar);

        Button settings=new Button(this); settings.setText("IMPOSTAZIONI NOTIFICHE"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); box.addView(settings);
        TextView info=new TextView(this); info.setText("Il Worker non usa password o cookie dei marketplace. Vinted personale resta isolato."); info.setTextSize(15); info.setPadding(0,28,0,0); box.addView(info);
        setContentView(scroll); refreshServer();
    }

    private void showRadar(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,40,28,24); root.setBackgroundColor(Color.rgb(11,16,32));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root);

        TextView title=new TextView(this); title.setText("🔎 AFFARERADAR"); title.setTextColor(Color.WHITE); title.setTextSize(28); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView sub=new TextView(this); sub.setText("Il piccoletto sta cercando tesori"); sub.setTextColor(Color.rgb(165,180,252)); sub.setTextSize(15); sub.setGravity(Gravity.CENTER); sub.setPadding(0,4,0,22); root.addView(sub);

        Button back=new Button(this); back.setText("← TORNA AL WORKER"); back.setOnClickListener(v->showWorkerHome()); root.addView(back);
        Button refresh=new Button(this); refresh.setText("AGGIORNA ORA"); refresh.setOnClickListener(v->loadDeals("all")); root.addView(refresh);

        dashStatus=new TextView(this); dashStatus.setText("Carico gli ultimi candidati..."); dashStatus.setTextColor(Color.LTGRAY); dashStatus.setTextSize(16); dashStatus.setPadding(0,18,0,12); root.addView(dashStatus);

        LinearLayout filters=new LinearLayout(this); filters.setOrientation(LinearLayout.VERTICAL);
        Button all=new Button(this); all.setText("TUTTO"); all.setOnClickListener(v->renderDeals("all")); filters.addView(all);
        Button resale=new Button(this); resale.setText("💰 RIVENDITA"); resale.setOnClickListener(v->renderDeals("resale")); filters.addView(resale);
        Button collection=new Button(this); collection.setText("🏠 COLLEZIONE"); collection.setOnClickListener(v->renderDeals("collection")); filters.addView(collection);
        Button both=new Button(this); both.setText("🔥 ENTRAMBE"); both.setOnClickListener(v->renderDeals("both")); filters.addView(both);
        Button hot=new Button(this); hot.setText("⚡ SCORE 90+"); hot.setOnClickListener(v->renderDeals("hot")); filters.addView(hot);
        root.addView(filters);

        TextView learn=new TextView(this);
        learn.setText("🧠 COSA STA CERCANDO\n\n• Valore nascosto: annunci descritti male, marchi mancanti e prezzi incompatibili con l'oggetto.\n• Design/collezione: lampade insolite, Space Age/Pop, ceramiche, vetri, oggetti oversize, IKEA vintage firmato, Lumibär/Flötotto, Bay Keramik, KARE e pezzi non marchiati ad alto impatto.\n• Resale: margine netto serio, poco capitale quando possibile, liquidità reale e rischio autenticità.\n• Jackpot: oggetti anonimi o sottovalutati che possono valere molte volte il prezzo richiesto.");
        learn.setTextColor(Color.rgb(203,213,225)); learn.setTextSize(14); learn.setPadding(0,18,0,18); root.addView(learn);

        dealsBox=new LinearLayout(this); dealsBox.setOrientation(LinearLayout.VERTICAL); root.addView(dealsBox);
        setContentView(scroll);
        loadDeals("all");
    }

    private void loadDeals(final String filter){
        dashStatus.setText("Aggiornamento in corso...");
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(DASHBOARD_API).openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(12000); c.setRequestProperty("Accept","application/json");
                int r=c.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(r>=200&&r<300?c.getInputStream():c.getErrorStream()));
                StringBuilder s=new StringBuilder(); String l; while((l=br.readLine())!=null)s.append(l); c.disconnect();
                if(r<200||r>=300) throw new Exception("HTTP "+r);
                JSONObject j=new JSONObject(s.toString()); dealsCache=j.optJSONArray("deals"); if(dealsCache==null) dealsCache=new JSONArray();
                final int n=dealsCache.length();
                runOnUiThread(()->{dashStatus.setText(n+" candidati visibili • aggiornato adesso"); renderDeals(filter);});
            }catch(Exception e){runOnUiThread(()->dashStatus.setText("Non riesco a caricare i candidati. Tocca AGGIORNA ORA."));}
        }).start();
    }

    private void renderDeals(String filter){
        if(dealsBox==null) return; dealsBox.removeAllViews();
        int shown=0;
        for(int i=0;i<dealsCache.length();i++){
            JSONObject d=dealsCache.optJSONObject(i); if(d==null) continue;
            String mode=d.optString("mode","watch"); int score=d.optInt("score",0);
            boolean ok="all".equals(filter) || filter.equals(mode) || ("hot".equals(filter)&&score>=90) || ("resale".equals(filter)&&"both".equals(mode)) || ("collection".equals(filter)&&"both".equals(mode));
            if(!ok) continue; shown++;

            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(22,20,22,20); card.setBackgroundColor(Color.rgb(17,24,49));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); cp.setMargins(0,0,0,18); card.setLayoutParams(cp);

            String badge="resale".equals(mode)?"💰 RIVENDITA":"collection".equals(mode)?"🏠 COLLEZIONE":"both".equals(mode)?"🔥 ENTRAMBE":"🔎 DA STUDIARE";
            TextView top=new TextView(this); top.setText(badge+"   SCORE "+score); top.setTextColor(Color.rgb(199,210,254)); top.setTextSize(13); card.addView(top);
            TextView name=new TextView(this); name.setText(d.optString("title","Senza titolo")); name.setTextColor(Color.WHITE); name.setTextSize(18); name.setPadding(0,8,0,8); card.addView(name);
            TextView price=new TextView(this); price.setText(d.optString("source_name","")+"   •   "+d.optLong("price_huf",0)+" Ft"); price.setTextColor(Color.rgb(248,250,252)); price.setTextSize(15); card.addView(price);
            TextView why=new TextView(this); why.setText(d.optString("verdict",d.optString("category","Da controllare"))); why.setTextColor(Color.rgb(165,180,252)); why.setTextSize(14); why.setPadding(0,8,0,8); card.addView(why);
            TextView cat=new TextView(this); cat.setText(d.optString("category","")+"  •  "+d.optString("valuation_status","")); cat.setTextColor(Color.GRAY); cat.setTextSize(12); card.addView(cat);
            final String url=d.optString("url","");
            Button open=new Button(this); open.setText("APRI ANNUNCIO ↗"); open.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));}catch(Exception ignored){}}); card.addView(open);
            dealsBox.addView(card);
        }
        if(shown==0){TextView none=new TextView(this); none.setText("Nessun candidato in questo filtro al momento."); none.setTextColor(Color.LTGRAY); none.setTextSize(16); dealsBox.addView(none);}
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
                b.put("appVersion","0.6-dashboard-worker");
                b.put("deviceModel",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
                JSONObject caps=new JSONObject(); caps.put("officialNotifications",true); caps.put("notificationListener",true); caps.put("mainScreenHeartbeat",true); caps.put("nativeDealsDashboard",true);
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
