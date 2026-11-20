package com.costoluce.importatore;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.widget.RemoteViews;
import java.text.NumberFormat;
import java.util.Locale;

/** Four real/forecast metrics, arranged from the space actually supplied by the launcher. */
final class WidgetSummaryViews {
    static final int TWO_ROWS=0, FOUR_ROWS=1, GRID=2;
    static int arrangement(int width,int availableHeight,float fontScale){
        if(width>=160&&availableHeight>=64)return GRID;
        return availableHeight>=142*Math.min(fontScale,1.5f)?FOUR_ROWS:TWO_ROWS;
    }
    private static float fit(Context c,String text,float width,float height,float target,boolean bold){
        float density=c.getResources().getDisplayMetrics().density;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);
        float size=Math.max(1,target*density);p.setTextSize(size);
        float ratio=Math.min(1,Math.min(width*density/Math.max(1,p.measureText(text)),height*density/Math.max(1,p.descent()-p.ascent())));
        return size*ratio;
    }
    private static void cell(Context c,RemoteViews row,boolean right,String label,String value,boolean forecast,float width,float height,float scale){
        int container=right?R.id.widget_value_right:R.id.widget_value_left,labelId=right?R.id.widget_value_label_right:R.id.widget_value_label_left,valueId=right?R.id.widget_value_amount_right:R.id.widget_value_amount_left;
        float d=c.getResources().getDisplayMetrics().density,gap=Math.min(8,height*.12f),labelHeight=Math.min(13*scale,height*.3f),valueHeight=Math.max(1,height-labelHeight-gap);
        row.setViewPadding(container,right?Math.round(5*d):0,0,right?0:Math.round(5*d),Math.round(gap*d));
        row.setTextViewText(labelId,label);row.setTextViewText(valueId,value);
        row.setTextViewTextSize(labelId,android.util.TypedValue.COMPLEX_UNIT_PX,fit(c,label,width-5,labelHeight,10*scale,false));
        row.setTextViewTextSize(valueId,android.util.TypedValue.COMPLEX_UNIT_PX,fit(c,value,width-5,valueHeight,30*scale,true));
        row.setTextColor(valueId,forecast?0xff64e7cf:0xfff3f7ff);
    }
    static RemoteViews create(Context c,WidgetLayoutSpec spec,int width,int height){
        Locale locale=Locale.ITALY;NumberFormat usage=NumberFormat.getNumberInstance(locale);usage.setMaximumFractionDigits(1);NumberFormat money=NumberFormat.getCurrencyInstance(locale);
        android.content.SharedPreferences p=CostoLuceWidgetProvider.prefs(c);String kwh="— kWh",cost="— €";
        if(p.getLong("saved_at",0)>0)try{double k=Double.parseDouble(p.getString("kwh","")),v=Double.parseDouble(p.getString("estimated_euro",""));if(Double.isFinite(k)&&k>=0&&Double.isFinite(v)&&v>=0){kwh=usage.format(k)+" kWh";cost=money.format(v);}}catch(RuntimeException ignored){}
        WidgetProjection.Result forecast=CostoLuceWidgetProvider.compactProjection(c);
        String projectedKwh=forecast.available?"≈ "+usage.format(forecast.kwh)+" kWh":"— kWh",projectedCost=forecast.available?"≈ "+money.format(forecast.value):"— €";
        String[] labels={"Consumo attuale","Costo attuale","Consumo previsto","Costo previsto"},values={kwh,cost,projectedKwh,projectedCost};
        int mode=arrangement(width,height,spec.fontScale),rows=mode==FOUR_ROWS?4:2;float rowHeight=Math.max(1,(height-2f)/rows);
        RemoteViews root=new RemoteViews(c.getPackageName(),R.layout.widget_summary_values);
        for(int i=0;i<rows;i++){
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_summary_values_row);
            if(mode==GRID){for(int side=0;side<2;side++){int index=i*2+side;cell(c,row,side==1,labels[index],values[index],index>=2,width/2f,rowHeight,spec.fontScale);}}
            else{row.setViewVisibility(R.id.widget_value_right,android.view.View.GONE);int index=i;String label=mode==TWO_ROWS?(i==0?"Attuale":"Proiezione"):labels[index],value=mode==TWO_ROWS?(i==0?kwh+" · "+cost:projectedKwh+" · "+projectedCost):values[index];cell(c,row,false,label,value,mode==TWO_ROWS?i==1:index>=2,width,rowHeight,spec.fontScale);}
            root.addView(R.id.widget_four_values,row);
        }
        root.setContentDescription(R.id.widget_four_values,"Consumo attuale: "+kwh+". Costo attuale: "+cost+". Consumo previsto: "+projectedKwh+". Costo previsto: "+projectedCost+". "+(forecast.available?"Proiezione dell’intero periodo basata su sette giorni completi.":forecast.reason));
        Intent open=new Intent(c,MainActivity.class).setAction("com.costoluce.cloud.action.OPEN_WIDGET").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        root.setOnClickPendingIntent(R.id.widget_four_values,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        return root;
    }
}
