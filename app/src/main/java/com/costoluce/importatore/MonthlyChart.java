package com.costoluce.importatore;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.util.Locale;

/** Raster chart embedded in RemoteViews; no unsupported custom widget view. */
final class MonthlyChart {
    private static final String[] MONTHS={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
    static String label(JSONObject r) { return MONTHS[Math.max(1,Math.min(12,r.optInt("month",1)))-1]; }
    static double value(Context context, JSONObject r) {
        double kwh=r.optDouble("kwh",0);
        if("kwh".equals(CostoLuceWidgetProvider.prefs(context).getString("display_unit","eur")))return kwh;
        try {
            CostoLuceWidgetProvider.WidgetConfiguration c=CostoLuceWidgetProvider.readConfiguration(context);
            return c==null?Double.NaN:WidgetPricing.parse(c.ratesJson,c.resident).estimatedCost(kwh,1);
        } catch(Exception e) { return Double.NaN; }
    }
    static String description(Context c, JSONArray rows) {
        StringBuilder b=new StringBuilder("Storico Enel. ");
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            b.append(label(r)).append(' ').append(r.optInt("year")).append(": ");
            if(!r.optBoolean("available"))b.append("mancante");
            else b.append(String.format(Locale.ITALY,"%.1f",value(c,r)))
                    .append("kwh".equals(CostoLuceWidgetProvider.prefs(c).getString("display_unit","eur"))?" kWh":" euro stimati")
                    .append(r.optBoolean("partial")?", parziale":"");
            b.append(". ");
        }
        return b.toString();
    }
    static Bitmap draw(Context context, JSONArray rows) {
        Bitmap bitmap=Bitmap.createBitmap(720,340,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setColor(Color.rgb(165,187,209));p.setTextSize(22);
        boolean kwh="kwh".equals(CostoLuceWidgetProvider.prefs(context).getString("display_unit","eur"));
        canvas.drawText(kwh?"CONSUMI · kWh":"COSTI VARIABILI · € STIMATI",12,28,p);
        double max=0;for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&r.optBoolean("available"))max=Math.max(max,value(context,r));}
        p.setColor(Color.rgb(48,72,94));p.setStrokeWidth(1);canvas.drawLine(12,238,708,238,p);
        p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<4;i++) {
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            float x=90+i*180; double v=value(context,r);
            boolean available=r.optBoolean("available")&&Double.isFinite(v);
            float h=available&&max>0?(float)(v/max*150):0;
            p.setColor(i==3?Color.rgb(99,230,206):Color.rgb(46,126,137));
            if(available&&h>0)canvas.drawRoundRect(x-37,238-h,x+37,238,12,12,p);
            p.setTextSize(24);p.setColor(Color.WHITE);
            canvas.drawText(available?String.format(Locale.ITALY,"%.1f",v):"—",x,Math.min(220,228-h),p);
            p.setTextSize(22);p.setColor(Color.rgb(208,225,237));canvas.drawText(label(r),x,271,p);
            p.setTextSize(18);p.setColor(Color.rgb(153,177,196));
            canvas.drawText(!available?"Mancante":r.optBoolean("partial")?"Parziale":"Completo",x,299,p);
        }
        return bitmap;
    }
}
