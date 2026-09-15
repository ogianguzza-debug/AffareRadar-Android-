package it.affareradar.worker;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class DealAlertJob extends JobService {
    private static final int JOB_ID=8472;
    private static final String FEED="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/affareradar-feed";
    private static final String KEY="sb_publishable_oIkCZfnXytk2QL2WgYGQYQ_tQHUR22w";
    public static void schedule(Context c){
        JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo.Builder b=new JobInfo.Builder(JOB_ID,new ComponentName(c,DealAlertJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
        if(Build.VERSION.SDK_INT>=24)b.setPeriodic(15*60*1000L,5*60*1000L);else b.setPeriodic(15*60*1000L);
        js.schedule(b.build());
        JobInfo immediate=new JobInfo.Builder(JOB_ID+1,new ComponentName(c,DealAlertJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).setOverrideDeadline(1000).build();
        js.schedule(immediate);
    }
    @Override public boolean onStartJob(JobParameters p){new Thread(()->{poll();jobFinished(p,false);}).start();return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}

    private int treasureScore(JSONObject d){
        int score=d.optInt("score",0), price=d.optInt("priceHuf",0);
        int prudent=d.optInt("prudentValueHuf",d.optInt("estimatedValueHuf",0));
        String cat=d.optString("category","").toLowerCase(Locale.ROOT);
        String title=d.optString("title","").toLowerCase(Locale.ROOT);
        if(price>0&&prudent>=price*3) score+=12;
        if(price>0&&prudent>=price*5) score+=10;
        if(price>0&&price<=10000) score+=5;
        if(cat.contains("da identificare")||cat.contains("unknown")||title.length()<18) score+=6;
        String[] hunt={"lamp","lámp","szék","fotel","óra","watch","robot","játék","toy","modell","kerám","üveg","glass","váza","design","retro","régi","vintage","hangfal","speaker","audio"};
        for(String k:hunt)if(cat.contains(k)||title.contains(k)){score+=5;break;}
        return Math.min(100,score);
    }

    private void poll(){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(FEED).openConnection();c.setRequestProperty("apikey",KEY);c.setConnectTimeout(15000);c.setReadTimeout(25000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder s=new StringBuilder();String l;while((l=br.readLine())!=null)s.append(l);c.disconnect();
            JSONArray a=new JSONObject(s.toString()).optJSONArray("deals");if(a==null)return;
            SharedPreferences prefs=getSharedPreferences("deal_alerts",MODE_PRIVATE);
            Set<String> seen=prefs.getStringSet("seen",new HashSet<>());Set<String> next=new HashSet<>(seen);int sent=0;
            ArrayList<JSONObject> candidates=new ArrayList<>();
            for(int i=0;i<a.length();i++){JSONObject d=a.getJSONObject(i);if(!seen.contains(d.optString("id")))candidates.add(d);}
            Collections.sort(candidates,(x,y)->Integer.compare(treasureScore(y),treasureScore(x)));
            for(JSONObject d:candidates){
                if(sent>=5)break;
                int ts=treasureScore(d), price=d.optInt("priceHuf",0), prudent=d.optInt("prudentValueHuf",d.optInt("estimatedValueHuf",0));
                boolean strong=ts>=82;
                boolean asymmetric=price>0&&prudent>0&&prudent>=price*3&&prudent-price>=15000;
                boolean mystery=price>0&&price<=10000&&ts>=65;
                if(!strong&&!asymmetric&&!mystery)continue;
                notifyDeal(d,10000+sent,strong||asymmetric,ts);next.add(d.optString("id"));sent++;
            }
            if(next.size()>1000){ArrayList<String>x=new ArrayList<>(next);next=new HashSet<>(x.subList(Math.max(0,x.size()-1000),x.size()));}
            prefs.edit().putStringSet("seen",next).apply();
        }catch(Exception ignored){}
    }
    private Bitmap loadPhoto(JSONObject d){try{JSONArray images=d.optJSONArray("imageUrls");if(images==null||images.length()==0)return null;HttpURLConnection c=(HttpURLConnection)new URL(images.getString(0)).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);Bitmap pic=BitmapFactory.decodeStream(c.getInputStream());c.disconnect();return pic;}catch(Exception ignored){return null;}}
    private void notifyDeal(JSONObject d,int id,boolean strong,int ts)throws Exception{
        String ch="affareradar_deals";NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"Affari e possibili tesori",NotificationManager.IMPORTANCE_HIGH));
        int price=d.optInt("priceHuf"), prudent=d.optInt("prudentValueHuf",d.optInt("estimatedValueHuf",0));
        String prefix=ts>=92?"💎 POSSIBILE TESORO":strong?"🔥 AFFARE FORTE":"🔎 DA VERIFICARE";
        String body=price+" Ft"+(prudent>0?" • prudente "+prudent+" Ft":"")+" • score "+ts+"/100 • "+d.optString("sourceId");
        Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(d.optString("url")));PendingIntent pi=PendingIntent.getActivity(this,id,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(prefix+" • "+d.optString("title")).setContentText(body).setContentIntent(pi).setAutoCancel(true);Bitmap pic=loadPhoto(d);if(pic!=null)b.setLargeIcon(pic).setStyle(new Notification.BigPictureStyle().bigPicture(pic).setSummaryText(body));else b.setStyle(new Notification.BigTextStyle().bigText(body));nm.notify(id,b.build());
    }
}
