package it.affareradar.worker;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final ExecutorService photos=Executors.newFixedThreadPool(2);
    private final ArrayList<JSONObject> items=new ArrayList<>();
    private TextView connection,heartbeat,permissions,summary;
    private EditText code;
    private Button pair;
    private LinearLayout cards;
    private boolean home=true,loading=false;
    private int screen=0;
    private String filter="all";
    private JSONObject health=new JSONObject();
    private static final int BG=Color.rgb(11,18,30),CARD=Color.rgb(22,34,49),TEXT=Color.rgb(235,240,245),MUTED=Color.rgb(167,188,207),GREEN=Color.rgb(87,214,160);

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        DealAlertJob.schedule(this);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1001);
        showHome();
    }
    @Override public void onResume(){super.onResume();if(home&&permissions!=null)refreshPermissionLabel();}
    @Override public void onDestroy(){screen++;io.shutdownNow();photos.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(!home)showHome();else super.onBackPressed();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private LinearLayout page(){
        LinearLayout box=column();box.setPadding(dp(18),dp(28),dp(18),dp(24));box.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(box);setContentView(scroll);return box;
    }
    private TextView text(LinearLayout parent,String value,int size,int color){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(5),0,dp(7));parent.addView(view);return view;
    }
    private Button button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());parent.addView(b);return b;
    }
    private void open(String link){
        if(!AppState.safeLink(link)){Toast.makeText(this,"Link non disponibile",Toast.LENGTH_SHORT).show();return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception e){Toast.makeText(this,"Impossibile aprire il link",Toast.LENGTH_SHORT).show();}
    }
    private String token(){return getSharedPreferences("radar",MODE_PRIVATE).getString("worker_token","");}
    private void refreshPermissionLabel(){
        boolean alerts=AppState.notificationsEnabled(this),listener=AppState.listenerEnabled(this);
        permissions.setText("Avvisi affari: "+(alerts?"abilitati":"disabilitati")+"\nAccesso notifiche marketplace: "+(listener?"abilitato":"da autorizzare"));
    }
    private void showHome(){
        home=true;screen++;LinearLayout box=page();
        text(box,"AFFARERADAR",29,GREEN);text(box,"Ricerca, foto e opportunità",18,TEXT);
        text(box,"Versione "+AppState.version(this),13,MUTED);
        permissions=text(box,"Verifica permessi…",15,MUTED);refreshPermissionLabel();
        connection=text(box,"Server: verifica in corso…",18,TEXT);
        heartbeat=text(box,"",15,MUTED);
        code=new EditText(this);code.setTextColor(TEXT);code.setHintTextColor(MUTED);code.setHint("Codice associazione a 6 cifre");code.setInputType(2);box.addView(code);
        pair=button(box,"Collega dispositivo",this::pair);
        button(box,"Apri il radar",this::showRadar);
        button(box,"Autorizza notifiche marketplace",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(box,"Impostazioni avvisi",()->{
            Intent intent=Build.VERSION.SDK_INT>=26?new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()):
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
            startActivity(intent);
        });
        text(box,"Il telefono legge il feed e invia i segnali autorizzati. Lo stato delle singole fonti è visibile nel radar.",14,MUTED);
        final int generation=screen;String t=token();code.setVisibility(t.isEmpty()?View.VISIBLE:View.GONE);pair.setVisibility(t.isEmpty()?View.VISIBLE:View.GONE);
        if(t.isEmpty()){connection.setText("Dispositivo da associare");heartbeat.setText("Il radar si può consultare anche prima dell’associazione.");return;}
        io.execute(()->{try{
            AppState.request("affareradar-worker",t,null);
            JSONObject body=new JSONObject();body.put("event","heartbeat");body.put("appVersion",AppState.version(this));body.put("deviceModel",Build.MANUFACTURER+" "+Build.MODEL);
            JSONObject caps=new JSONObject();caps.put("officialNotifications",true);caps.put("notificationListener",AppState.listenerEnabled(this));caps.put("jobAlerts",true);caps.put("photoCards",true);body.put("capabilities",caps);
            AppState.request("affareradar-worker",t,body);
            runOnUiThread(()->{if(screen==generation){connection.setText("Server collegato");heartbeat.setText("Ultima comunicazione: adesso. Controlla nel radar eventuali fonti bloccate.");}});
        }catch(Exception e){runOnUiThread(()->{if(screen==generation){connection.setText("Collegamento da verificare");heartbeat.setText(e.getMessage());}});}});
    }
    private void pair(){
        String value=code.getText().toString().trim();if(!value.matches("[0-9]{6}")){heartbeat.setText("Inserisci le 6 cifre del codice.");return;}
        pair.setEnabled(false);heartbeat.setText("Associazione in corso…");final int generation=screen;
        io.execute(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(AppState.API+"affareradar-worker").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-pairing-code",value);
            String saved;
            try{
                try(OutputStream out=c.getOutputStream()){out.write("{\"event\":\"pair\"}".getBytes(StandardCharsets.UTF_8));}
                if(c.getResponseCode()!=200)throw new IOException("Codice non valido o scaduto");
                StringBuilder s=new StringBuilder();try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null)s.append(line);}
                saved=new JSONObject(s.toString()).optString("workerToken","");
            }finally{c.disconnect();}
            if(saved.length()<10)throw new IOException("Risposta di associazione non valida");
            getSharedPreferences("radar",MODE_PRIVATE).edit().putString("worker_token",saved).apply();
            runOnUiThread(()->{if(screen==generation)showHome();});
        }catch(Exception e){runOnUiThread(()->{if(screen==generation){pair.setEnabled(true);heartbeat.setText(e.getMessage());}});}});
    }
    private void showRadar(){
        home=false;screen++;loading=false;filter="all";LinearLayout root=page();
        text(root,"IL RADAR",27,GREEN);button(root,"Torna al dispositivo",this::showHome);
        button(root,"Aggiorna",this::load);
        summary=text(root,"Caricamento…",15,MUTED);
        HorizontalScrollView rowScroll=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);rowScroll.addView(row);root.addView(rowScroll);
        for(String[] f:new String[][]{{"all","Tutti"},{"opportunity","Opportunità"},{"resale","Rivendita"},{"collection","Collezione"},{"pending","In analisi"}}){
            Button b=new Button(this);b.setText(f[1]);b.setAllCaps(false);b.setOnClickListener(v->{filter=f[0];render();});row.addView(b);
        }
        cards=column();root.addView(cards);load();
    }
    private void load(){
        if(loading)return;loading=true;summary.setText("Aggiornamento in corso…");final int generation=screen;
        io.execute(()->{try{
            JSONObject data=AppState.request("affareradar-feed",null,null);
            ArrayList<JSONObject> next=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(String key:new String[]{"deals","candidates"}){JSONArray a=data.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++){JSONObject d=a.optJSONObject(i);if(d!=null&&seen.add(d.optString("id")))next.add(d);}}
            JSONObject nextHealth=data.optJSONObject("health");
            runOnUiThread(()->{if(screen!=generation)return;loading=false;items.clear();items.addAll(next);health=nextHealth==null?new JSONObject():nextHealth;render();});
        }catch(Exception e){runOnUiThread(()->{if(screen==generation){loading=false;summary.setText("Aggiornamento non riuscito: "+e.getMessage()+". Tocca Aggiorna.");}});}});
    }
    private void render(){
        cards.removeAllViews();int ready=0,reviewed=0;
        for(JSONObject d:items){if(DealData.eligible(d))ready++;if("checked".equals(d.optString("vision_status")))reviewed++;}
        summary.setText(ready+" opportunità · "+reviewed+" con foto esaminate · "+items.size()+" elementi\nLe stime preliminari richiedono verifica; l’autenticità non è certificata.");
        LinearLayout diagnostics=card();
        text(diagnostics,"Stato della ricerca",17,TEXT);
        String last=health.optString("lastVisionAt","");text(diagnostics,last.isEmpty()||"null".equals(last)?"Esame fotografico: primo ciclo in attesa":"Ultimo esame fotografico: "+last,13,MUTED);
        LinearLayout details=column();details.setVisibility(View.GONE);
        button(diagnostics,"Fonti e collegamenti",()->details.setVisibility(details.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
        diagnostics.addView(details);
        JSONArray sources=health.optJSONArray("sources");if(sources!=null)for(int i=0;i<sources.length();i++){
            JSONObject s=sources.optJSONObject(i);if(s==null)continue;String id=s.optString("id");
            if(!s.optBoolean("enabled")&&!Arrays.asList("ebay","etsy","vestiaire","facebook-marketplace").contains(id))continue;
            String status=s.optString("connector_status",s.optString("mode","da verificare"));
            String label=s.optBoolean("enabled")?sourceLabel(status):status.contains("credentials")?"credenziali API necessarie":"collegamento assistito necessario";
            text(details,s.optString("name")+": "+label,13,MUTED);
            String error=s.optString("last_error","");if(!error.isEmpty()&&!"null".equals(error))text(details,error,12,Color.rgb(255,185,122));
        }
        int pending=0;JSONArray signals=health.optJSONArray("signals");if(signals!=null)for(int i=0;i<signals.length();i++){JSONObject s=signals.optJSONObject(i);if(s!=null&&!s.optString("resolution_status").equals("resolved"))pending+=s.optInt("count");}
        if(pending>0)text(details,pending+" segnali marketplace ancora da collegare a un annuncio verificato",13,MUTED);
        int shown=0;final int generation=screen;
        for(JSONObject d:items){
            String mode=d.optString("mode","watch");
            if("opportunity".equals(filter)&&!DealData.eligible(d)||"pending".equals(filter)&&"checked".equals(d.optString("vision_status")))continue;
            if(("resale".equals(filter)||"collection".equals(filter))&&!filter.equals(mode)&&!"both".equals(mode))continue;
            shown++;LinearLayout box=card();
            boolean eligible=DealData.eligible(d),checked="checked".equals(d.optString("vision_status"));
            text(box,eligible?"OPPORTUNITÀ DA VERIFICARE":checked?"FOTO ESAMINATE · VALUTAZIONE IN CORSO":"CANDIDATO · IN ATTESA DI ANALISI",12,eligible?GREEN:MUTED);
            text(box,d.optString("title","Senza titolo"),19,TEXT);
            JSONArray images=DealData.images(d);
            if(images!=null&&images.length()>0){
                ImageView image=new ImageView(this);image.setContentDescription("Fotografia dell’annuncio");image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setVisibility(View.GONE);TextView photoState=text(box,"Caricamento foto…",12,MUTED);
                box.addView(image,new LinearLayout.LayoutParams(-1,dp(210)));
                String url=images.optString(0);
                photos.execute(()->{try{Bitmap pic=PhotoLoader.load(url);runOnUiThread(()->{if(screen==generation&&image.isAttachedToWindow()){if(pic!=null){image.setImageBitmap(pic);image.setVisibility(View.VISIBLE);photoState.setVisibility(View.GONE);}else photoState.setText("Foto non disponibile · apri l’annuncio");}});}catch(Exception ignored){runOnUiThread(()->{if(screen==generation)photoState.setText("Foto non disponibile · apri l’annuncio");});}});
            }
            text(box,DealData.source(d)+" · "+DealData.price(d)+" Ft",16,TEXT);
            if(checked)text(box,d.optString("vision_identification","")+" · confidenza "+d.optInt("vision_confidence")+"%",15,MUTED);
            long low=DealData.value(d),high=d.optLong("estimatedHighHuf",d.optLong("estimated_high_huf",0));
            boolean valued=low>0&&d.optBoolean("valuationCurrent",false);
            text(box,valued?"Valore netto prudente: "+low+(high>low?"–"+high:"")+" Ft\nMargine stimato: "+DealData.profit(d)+" Ft · "+d.optInt("sold_comp_count")+" comparabili":"Valore e margine: ancora da verificare",15,eligible?GREEN:MUTED);
            text(box,d.optString("verificationNote","Candidato da controllare"),14,MUTED);
            String observations=DealData.lines(d.optJSONArray("vision_reasons"));if(!observations.isEmpty())text(box,observations,14,MUTED);
            String risk=d.optString("authenticity_risk",d.optString("risk",""));if(!risk.isEmpty()&&!"null".equals(risk))text(box,risk,13,MUTED);
            String missing=DealData.lines(d.optJSONArray("vision_missing_photos"));if(!missing.isEmpty())text(box,"Foto mancanti:\n"+missing,13,Color.rgb(255,185,122));
            String red=DealData.lines(d.optJSONArray("vision_red_flags"));if(!red.isEmpty())text(box,"Elementi da verificare:\n"+red,13,Color.rgb(255,185,122));
            button(box,"Apri annuncio",()->open(d.optString("url")));
            JSONArray comps=d.optJSONArray("comparables");if(comps!=null)for(int i=0;i<Math.min(3,comps.length());i++){
                JSONObject c=comps.optJSONObject(i);if(c!=null&&AppState.safeLink(c.optString("url"))){String link=c.optString("url");button(box,"Venduto di confronto · "+c.optLong("price")+" Ft",()->open(link));}
            }
        }
        if(shown==0)text(cards,"Nessun elemento in questo filtro. Gli avvisi vengono inviati solo quando ci sono foto, comparabili e margine sufficienti.",16,MUTED);
    }
    private LinearLayout card(){
        LinearLayout box=column();box.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(12));box.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(12),0,0);cards.addView(box,params);return box;
    }
    private String sourceLabel(String value){
        switch(value){case "live":return "attiva";case "protected":return "accesso pubblico bloccato";case "stale":return "raccolta recente da verificare";case "no_results":return "nessun annuncio letto";case "partial":return "raccolta parziale";case "error":return "errore di collegamento";default:return value;}
    }
}
