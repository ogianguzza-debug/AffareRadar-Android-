package it.affareradar.worker;

import android.app.*;
import android.app.job.*;
import android.content.*;
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
        JobInfo.Builder b=new JobInfo.Builder(JOB_ID,new ComponentName(c,DealAlertJob.class))
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
        if(Build.VERSION.SDK_INT>=24)b.setPeriodic(15*60*1000L,5*60*1000L); else b.setPeriodic(15*60*1000L);
        js.schedule(b.build());
    }
    @Override public boolean onStartJob(JobParameters p){new Thread(()->{poll();jobFinished(p,false);}).start();return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}
    private void poll(){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(FEED).openConnection();
            c.setRequestProperty("apikey",KEY);c.setConnectTimeout(15000);c.setReadTimeout(25000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder s=new StringBuilder();String l;while((l=br.readLine())!=null)s.append(l);c.disconnect();
            JSONArray a=new JSONObject(s.toString()).optJSONArray("deals");if(a==null)return;
            Set<String> seen=getPreferences(MODE_PRIVATE).getStringSet("seen",new HashSet<>());
            Set<String> next=new HashSet<>(seen);int sent=0;
            for(int i=0;i<a.length()&&sent<4;i++){
                JSONObject d=a.getJSONObject(i);String id=d.optString("id"),source=d.optString("sourceId");
                int score=d.optInt("score");if(!"vinted".equals(source)||score<85||seen.contains(id))continue;
                notifyDeal(d,10000+i);next.add(id);sent++;
            }
            if(next.size()>500){ArrayList<String>x=new ArrayList<>(next);next=new HashSet<>(x.subList(x.size()-500,x.size()));}
            getPreferences(MODE_PRIVATE).edit().putStringSet("seen",next).apply();
        }catch(Exception ignored){}
    }
    private void notifyDeal(JSONObject d,int id)throws Exception{
        String ch="affareradar_deals";NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"Affari trovati",NotificationManager.IMPORTANCE_HIGH));
        String title="AffareRadar "+d.optInt("score")+" • "+d.optString("title");
        String body=d.optInt("priceHuf")+" Ft • "+d.optString("category");
        Intent open=new Intent(Intent.ACTION_VIEW, Uri.parse(d.optString("url")));PendingIntent pi=PendingIntent.getActivity(this,id,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true);
        nm.notify(id,b.build());
    }
}