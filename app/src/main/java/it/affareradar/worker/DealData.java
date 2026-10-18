package it.affareradar.worker;

import org.json.JSONArray;
import org.json.JSONObject;

final class DealData {
    static long price(JSONObject d){return d.optLong("priceHuf",d.optLong("price_huf",0));}
    static long value(JSONObject d){return d.optLong("prudentValueHuf",d.optLong("estimated_low_huf",0));}
    static long profit(JSONObject d){return d.optLong("netProfitHuf",d.optLong("conservative_profit_huf",0));}
    static String source(JSONObject d){return d.optString("sourceName",d.optString("source_name",d.optString("sourceId",d.optString("source_id",""))));}
    static JSONArray images(JSONObject d){JSONArray a=d.optJSONArray("imageUrls");return a==null?d.optJSONArray("image_urls"):a;}
    static boolean eligible(JSONObject d){return d.optBoolean("alertEligible",false);}
    static String lines(JSONArray a){if(a==null)return "";StringBuilder s=new StringBuilder();for(int i=0;i<a.length();i++){if(s.length()>0)s.append("\n");s.append("• ").append(a.optString(i));}return s.toString();}
}
