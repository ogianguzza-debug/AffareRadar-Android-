package it.affareradar.worker;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.util.*;

public class DealAlertJob extends JobService {
    private static final int JOB_ID=8472;
    private volatile boolean stopped;
    private Thread worker;
    public static void schedule(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        JobInfo.Builder periodic=new JobInfo.Builder(JOB_ID,new ComponentName(c,DealAlertJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
        if(Build.VERSION.SDK_INT>=24)periodic.setPeriodic(15*60*1000L,5*60*1000L);else periodic.setPeriodic(15*60*1000L);
        scheduler.schedule(periodic.build());
        scheduler.schedule(new JobInfo.Builder(JOB_ID+1,new ComponentName(c,DealAlertJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).setOverrideDeadline(1000).build());
    }
    @Override public synchronized boolean onStartJob(JobParameters parameters){
        if(worker!=null&&worker.isAlive())return false;
        stopped=false;worker=new Thread(()->{try{poll();}finally{if(!stopped)jobFinished(parameters,false);}});
        worker.start();return true;
    }
    @Override public synchronized boolean onStopJob(JobParameters parameters){
        stopped=true;if(worker!=null)worker.interrupt();return true;
    }
    @Override public void onDestroy(){stopped=true;if(worker!=null)worker.interrupt();super.onDestroy();}
    private void poll(){
        SharedPreferences preferences=getSharedPreferences("deal_alerts",MODE_PRIVATE);int sent=0,total=0;
        try{
            JSONObject feed=AppState.request("affareradar-feed",null,null);
            JSONArray deals=feed.optJSONArray("deals");if(deals==null)throw new IllegalStateException("Feed privo di annunci");
            total=deals.length();LinkedHashSet<String> seen=new LinkedHashSet<>();
            JSONArray previous=new JSONArray(preferences.getString("seen_ordered","[]"));
            for(int i=0;i<previous.length();i++)seen.add(previous.getString(i));
            // Keep previous installations' deduplication history during the update.
            seen.addAll(preferences.getStringSet("seen",Collections.emptySet()));
            for(int i=0;i<deals.length()&&sent<5&&!stopped;i++){
                JSONObject deal=deals.optJSONObject(i);if(deal==null||!DealData.eligible(deal))continue;
                String id=deal.optString("id","");
                if(id.isEmpty()||seen.contains(id)||!AppState.safeLink(deal.optString("url")))continue;
                if(notifyDeal(deal,10000+Math.floorMod(id.hashCode(),1000000))){seen.add(id);sent++;}
            }
            while(seen.size()>1000)seen.remove(seen.iterator().next());
            JSONArray saved=new JSONArray();for(String id:seen)saved.put(id);
            preferences.edit().putString("seen_ordered",saved.toString()).remove("seen")
                .putLong("lastPoll",System.currentTimeMillis()).putInt("lastTotal",total).putInt("lastSent",sent)
                .putString("lastError",AppState.notificationsEnabled(this)?"":"Avvisi disabilitati nelle impostazioni Android").apply();
        }catch(Exception error){
            preferences.edit().putLong("lastPoll",System.currentTimeMillis()).putInt("lastSent",sent)
                .putString("lastError",String.valueOf(error.getMessage())).apply();
        }
    }
    private boolean notifyDeal(JSONObject deal,int id)throws Exception{
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager==null||!AppState.notificationsEnabled(this)||stopped)return false;
        String channel="affareradar_deals";
        if(Build.VERSION.SDK_INT>=26){
            manager.createNotificationChannel(new NotificationChannel(channel,"Opportunità AffareRadar",NotificationManager.IMPORTANCE_HIGH));
            if(manager.getNotificationChannel(channel).getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        }
        String body=DealData.price(deal)+" Ft · netto prudente "+DealData.value(deal)+" Ft\nMargine stimato "+DealData.profit(deal)+" Ft · "+DealData.source(deal)+"\nAutenticità da verificare prima dell’acquisto";
        Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(deal.getString("url")));
        PendingIntent intent=PendingIntent.getActivity(this,id,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Opportunità · "+deal.optString("title"))
            .setContentText(body).setContentIntent(intent).setAutoCancel(true).setStyle(new Notification.BigTextStyle().bigText(body));
        JSONArray images=DealData.images(deal);
        if(images!=null&&images.length()>0)try{
            Bitmap photo=PhotoLoader.load(images.getString(0));
            if(photo!=null)builder.setLargeIcon(photo).setStyle(new Notification.BigPictureStyle().bigPicture(photo).setSummaryText(body));
        }catch(Exception ignored){}
        if(stopped)return false;
        manager.notify(id,builder.build());return true;
    }
}
