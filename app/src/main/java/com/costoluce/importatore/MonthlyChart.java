package com.costoluce.importatore;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.util.Locale;

/** Raster chart embedded in RemoteViews; no unsupported custom widget view. */
final class MonthlyChart {
    private static final String[] MONTHS={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
    static String label(JSONObject r) { return r.has("label")?r.optString("label"):MONTHS[Math.max(1,Math.min(12,r.optInt("month",1)))-1]; }
    private static boolean bills(Context c){return "bill".equals(CostoLuceWidgetProvider.chartMode(c));}
    private static boolean kwh(Context c){return "kwh".equals(CostoLuceWidgetProvider.chartMode(c));}
    private static String years(JSONObject r,boolean compact){int first=r.optInt("year"),last=r.optInt("endYear",first);return compact?(first==last?String.format(Locale.ROOT,"%02d",first%100):String.format(Locale.ROOT,"%02d/%02d",first%100,last%100)):(first==last?Integer.toString(first):first+"–"+last);}
    static double value(Context context, JSONObject r) {
        if(bills(context)||"issued".equals(r.optString("status"))||"allocated".equals(r.optString("status")))return r.optDouble("billEuro",Double.NaN);
        if("missing".equals(r.optString("status")))return Double.NaN;
        double kwh=r.optDouble("kwh",0);
        if(kwh(context))return kwh;
        try {
            CostoLuceWidgetProvider.WidgetConfiguration c=CostoLuceWidgetProvider.readConfiguration(context);
            return c==null?Double.NaN:WidgetPricing.parse(c.ratesJson,c.resident).estimatedCost(kwh,1);
        } catch(Exception e) { return Double.NaN; }
    }
    static String description(Context c, JSONArray rows) {
        StringBuilder b=new StringBuilder(bills(c)?"Bollette elettricità fatturate e stima corrente. Costi fissi, variabili e imposte configurate solo per la stima; canone TV separato. ":"Storico mensile Enel. ");
        if(bills(c)&&!CostoLuceWidgetProvider.offerCostsRecognized(c))b.append("Quote fisse di vendita non riconosciute: verifica le Tariffe nell'app. ");
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            b.append(label(r)).append(' ').append(years(r,false)).append(": ");
            if(!r.optBoolean("available"))b.append("mancante");
            else b.append(String.format(Locale.ITALY,kwh(c)?"%.1f":"%.2f",value(c,r)))
                    .append(kwh(c)?" kWh":"issued".equals(r.optString("status"))?" euro fatturati":"allocated".equals(r.optString("status"))?" euro ripartiti da fattura":" euro stimati")
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
    static Bitmap draw(Context context, JSONArray rows, WidgetLayoutSpec spec, int chartHeight,int bitmapPixels) {
        float fontScale=context.getResources().getConfiguration().fontScale;
        float width=spec.contentWidth(),height=Math.max(40,chartHeight);
        float density=Math.min(3,Math.min(context.getResources().getDisplayMetrics().density,(float)Math.sqrt(bitmapPixels/(width*height))));
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(width*density)),Math.max(1,Math.round(height*density)),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(density,density);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        double[] values=new double[4];boolean[] available=new boolean[4];double max=0;
        for(int i=0;i<4;i++){
            JSONObject row=rows.optJSONObject(i);values[i]=row==null?Double.NaN:value(context,row);
            available[i]=row!=null&&row.optBoolean("available")&&Double.isFinite(values[i]);
            if(available[i])max=Math.max(max,values[i]);
        }
        double ceiling=scale(max);
        float text=12*fontScale,secondary=11*fontScale;
        String topTick=String.format(Locale.ITALY,ceiling<4?"%.1f":"%.0f",ceiling);
        p.setTextSize(secondary);
        float left=spec.compact()?0:Math.max(28,p.measureText(topTick)+7),right=width-2;
        boolean showYears=spec.detailed()||bills(context);
        float top=text+8,bottom=height-(spec.detailed()?secondary*2+text+11:showYears?text+secondary+11:text+9);
        bottom=Math.max(top+8,bottom);
        if(!spec.compact()){
            int ticks=spec.detailed()?4:2;p.setTextAlign(Paint.Align.RIGHT);p.setStrokeWidth(1);
            for(int i=0;i<=ticks;i++){
                float y=bottom-(bottom-top)*i/ticks;p.setColor(Color.rgb(37,67,105));canvas.drawLine(left,y,right,y,p);
                p.setColor(Color.rgb(215,229,249));p.setTextSize(secondary);
                canvas.drawText(String.format(Locale.ITALY,ceiling<4?"%.1f":"%.0f",ceiling*i/ticks),left-6,y+secondary*.33f,p);
            }
        }
        float step=(right-left)/4;p.setTextAlign(Paint.Align.CENTER);
        boolean euros=!kwh(context);
        for(int i=0;i<4;i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            float x=left+step*(i+.5f),barHeight=available[i]?(float)(values[i]/ceiling*(bottom-top)):0;
            boolean current=kwh(context)?i==3:"forecast".equals(row.optString("status"));
            if(available[i]&&barHeight>0){
                int start=current?Color.rgb(115,240,212):Color.rgb(65,166,255),end=current?Color.rgb(28,188,170):Color.rgb(7,91,239);
                p.setShader(new LinearGradient(0,bottom-barHeight,0,bottom,start,end,Shader.TileMode.CLAMP));
                float radius=Math.min(7,barHeight/2);canvas.drawRoundRect(x-step*.34f,bottom-barHeight,x+step*.34f,bottom,radius,radius,p);p.setShader(null);
            }
            p.setColor(Color.WHITE);p.setTextSize(text);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            String amount=available[i]?String.format(Locale.ITALY,euros?"%.2f":"%.1f",values[i]):"—";
            // Keep the font legible; abbreviate only oversized values. Accessibility retains full values.
            if(p.measureText(amount)>step-4&&available[i])amount=compactValue(values[i]);
            canvas.drawText(amount,x,Math.max(text,bottom-barHeight-6),p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(text);p.setColor(Color.rgb(236,243,255));
            String periodLabel=label(row)+(row.optBoolean("partial")?"*":"");
            if(p.measureText(periodLabel)>step-4&&row.has("endMonth"))periodLabel=row.optInt("month")+"–"+row.optInt("endMonth")+(row.optBoolean("partial")?"*":"");
            canvas.drawText(periodLabel,x,bottom+text+5,p);
            if(showYears){
                p.setTextSize(secondary);p.setColor(Color.rgb(174,198,228));String year=years(row,spec.compact());if(p.measureText(year)>step-4)year=years(row,true);canvas.drawText(year,x,bottom+text+secondary+9,p);
            }
            if(spec.detailed()){
                p.setColor(current?Color.rgb(99,230,206):Color.rgb(174,198,228));
                canvas.drawText(!available[i]?"Assente":"issued".equals(row.optString("status"))?"Fatturata":"allocated".equals(row.optString("status"))?"Ripartito":"forecast".equals(row.optString("status"))?(row.optBoolean("partial")?"Stima*":"Stima"):row.optBoolean("partial")?"Parziale":"",x,height-2,p);
            }
        }
        return bitmap;
    }
    private static String compactValue(double value){
        return value>=1_000_000?String.format(Locale.ITALY,"%.1fM",value/1_000_000):value>=1000?String.format(Locale.ITALY,"%.1fk",value/1000):String.format(Locale.ITALY,"%.0f",value);
    }
}
