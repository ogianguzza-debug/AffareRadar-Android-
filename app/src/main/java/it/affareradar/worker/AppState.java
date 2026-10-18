package it.affareradar.worker;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class AppState {
    static final String API="https://tquprajezqoasqbmknwc.supabase.co/functions/v1/";
    static final String KEY="sb_publishable_oIkCZfnXytk2QL2WgYGQYQ_tQHUR22w";
    static boolean listenerEnabled(Context c) {
        String value=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        if(value==null)return false;
        for(String s:value.split(":")){
            ComponentName component=ComponentName.unflattenFromString(s);
            if(component!=null&&component.equals(new ComponentName(c,RadarNotificationListener.class)))return true;
        }
        return false;
    }
    static boolean notificationsEnabled(Context c) {
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager!=null&&(Build.VERSION.SDK_INT<24||manager.areNotificationsEnabled());
    }
    static String version(Context c) {
        try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}
        catch(Exception e){return "2.0";}
    }
    static boolean safeLink(String value) {
        try{URL u=new URL(value);return "https".equals(u.getProtocol())&&u.getHost()!=null&&!u.getHost().isEmpty();}
        catch(Exception e){return false;}
    }
    static JSONObject request(String endpoint,String token,JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API+endpoint).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("Connection","close");
        if(token==null)c.setRequestProperty("apikey",KEY);else c.setRequestProperty("x-worker-token",token);
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
            StringBuilder text=new StringBuilder();
            if(stream!=null)try(BufferedReader in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;while((line=in.readLine())!=null){text.append(line);if(text.length()>2_000_000)throw new IOException("Risposta troppo grande");}}
            if(status<200||status>=300)throw new IOException("HTTP "+status);
            return new JSONObject(text.toString());
        }finally{c.disconnect();}
    }
}
