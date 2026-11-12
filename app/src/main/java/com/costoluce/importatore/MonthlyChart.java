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
    static double scale(double max) {
        if(max<=0)return 1;
        double base=Math.pow(10,Math.floor(Math.log10(max/4)));
        for(double step:new double[]{1,2,5,10})if(step*base>=max/4)return step*base*4;
        return max;
    }
    static Bitmap draw(Context context, JSONArray rows) {
        Bitmap bitmap=Bitmap.createBitmap(720,420,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        double max=0;boolean any=false;
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&r.optBoolean("available")&&Double.isFinite(value(context,r))){max=Math.max(max,value(context,r));any=true;}
        }
        double ceiling=scale(max);float left=64,right=710,bottom=300,top=74;
        p.setTextSize(23);p.setStrokeWidth(1);p.setTextAlign(Paint.Align.RIGHT);
        for(int i=0;i<=4;i++){
            float y=bottom-(bottom-top)*i/4;
            p.setColor(Color.rgb(37,67,105));canvas.drawLine(left,y,right,y,p);
            p.setColor(Color.rgb(215,229,249));
            if(any||i==0)canvas.drawText(String.format(Locale.ITALY,ceiling<4?"%.1f":"%.0f",ceiling*i/4),left-12,y+8,p);
        }
        canvas.drawLine(left,top,left,bottom,p);
        float step=(right-left)/4;
        p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<4;i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            float x=left+step*(i+0.5f);double v=value(context,r);
            boolean available=r.optBoolean("available")&&Double.isFinite(v);
            float height=available?(float)(v/ceiling*(bottom-top)):0;
            if(available&&height>0){
                int start=i==3?Color.rgb(115,240,212):Color.rgb(65,166,255);
                int end=i==3?Color.rgb(28,188,170):Color.rgb(7,91,239);
                p.setShader(new LinearGradient(0,bottom-height,0,bottom,start,end,Shader.TileMode.CLAMP));
                canvas.drawRoundRect(x-step*0.39f,bottom-height,x+step*0.39f,bottom,17,17,p);p.setShader(null);
            }
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(27);p.setColor(Color.WHITE);
            canvas.drawText(available?String.format(Locale.ITALY,"%.1f",v):"—",x,Math.min(bottom-18,bottom-height-13),p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(25);p.setColor(Color.rgb(236,243,255));
            canvas.drawText(label(r),x,337,p);p.setTextSize(23);canvas.drawText(Integer.toString(r.optInt("year")),x,366,p);
            p.setTextSize(19);p.setColor(i==3?Color.rgb(99,230,206):Color.rgb(174,198,228));
            canvas.drawText(!available?"Mancante":r.optBoolean("partial")?"Parziale":"",x,399,p);
            if(i==3){
                p.setColor(Color.rgb(100,232,208));canvas.drawRoundRect(x-66,8,x+66,44,14,14,p);
                p.setColor(Color.rgb(3,39,49));p.setTextSize(20);canvas.drawText("In corso",x,33,p);
            }
        }
        return bitmap;
    }
}
