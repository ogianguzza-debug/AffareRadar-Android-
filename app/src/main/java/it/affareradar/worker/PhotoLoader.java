package it.affareradar.worker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.*;
import java.net.*;

final class PhotoLoader {
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    static Bitmap load(String url) throws IOException {
        Bitmap existing=cache.get(url);if(existing!=null)return existing;
        if(!AppState.safeLink(url))throw new IOException("URL foto non valido");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>6*1024*1024)throw new IOException("Foto troppo grande");}
            byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
            while(options.outWidth/options.inSampleSize>960||options.outHeight/options.inSampleSize>960)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(image!=null)cache.put(url,image);return image;
        }finally{c.disconnect();}
    }
}
