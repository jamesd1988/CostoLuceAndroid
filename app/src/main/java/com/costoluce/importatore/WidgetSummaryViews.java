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
        float ratio=Math.min(1,Math.min(Math.max(1,(width-2)*density)/Math.max(1,p.measureText(text)),Math.max(1,(height-1)*density)/Math.max(1,p.descent()-p.ascent())));
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
    private static final class Values {
        final String[] amounts;
        final String forecastNote;

        Values(Context context) {
            NumberFormat usage=NumberFormat.getNumberInstance(Locale.ITALY);
            usage.setMaximumFractionDigits(1);
            NumberFormat money=NumberFormat.getCurrencyInstance(Locale.ITALY);
            android.content.SharedPreferences preferences=CostoLuceWidgetProvider.prefs(context);
            String kwh="— kWh", cost="— €";
            if(preferences.getLong("saved_at",0)>0)try{
                double measured=Double.parseDouble(preferences.getString("kwh",""));
                double estimate=Double.parseDouble(preferences.getString("estimated_euro",""));
                if(Double.isFinite(measured)&&measured>=0&&Double.isFinite(estimate)&&estimate>=0){
                    kwh=usage.format(measured)+" kWh";
                    cost=money.format(estimate);
                }
            }catch(NumberFormatException ignored){}
            WidgetProjection.Result forecast=CostoLuceWidgetProvider.compactProjection(context);
            amounts=new String[]{kwh,cost,forecast.available?usage.format(forecast.kwh)+" kWh":"— kWh",
                    forecast.available?money.format(forecast.value):"— €"};
            forecastNote=forecast.available?"Proiezione dell’intero periodo basata su sette giorni completi.":forecast.reason;
        }

        String description(int start){
            return "Consumo: "+amounts[start]+". Costo: "+amounts[start+1]+".";
        }
    }
    private static PendingIntent openCalculator(Context context){
        Intent open=new Intent(context,MainActivity.class).setAction("com.costoluce.cloud.action.OPEN_WIDGET")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews createShortWide(Context context,WidgetLayoutSpec spec,boolean refresh){
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.costoluce_widget_stats_wide);
        Values values=new Values(context);
        float columnWidth=(spec.width-16-8-(refresh?52:0))/2f;
        float height=spec.height-8;
        int[] amountIds={R.id.widget_short_actual_kwh,R.id.widget_short_actual_euro,
                R.id.widget_short_forecast_kwh,R.id.widget_short_forecast_euro};
        float size=Float.MAX_VALUE;
        for(String value:values.amounts)size=Math.min(size,fit(context,value,columnWidth,height*.35f,16*spec.fontScale,true));
        for(int i=0;i<amountIds.length;i++){
            views.setTextViewText(amountIds[i],values.amounts[i]);
            views.setTextViewTextSize(amountIds[i],android.util.TypedValue.COMPLEX_UNIT_PX,size);
        }
        float labelSize=fit(context,"Proiezione",columnWidth,height*.30f,11*spec.fontScale,false);
        views.setTextViewTextSize(R.id.widget_short_actual_label,android.util.TypedValue.COMPLEX_UNIT_PX,labelSize);
        views.setTextViewTextSize(R.id.widget_short_forecast_label,android.util.TypedValue.COMPLEX_UNIT_PX,labelSize);
        views.setContentDescription(R.id.widget_short_actual,"Attuale. "+values.description(0));
        views.setContentDescription(R.id.widget_short_forecast,"Proiezione. "+values.description(2)+" "+values.forecastNote);
        PendingIntent open=openCalculator(context);
        views.setOnClickPendingIntent(R.id.widget_short_actual,open);
        views.setOnClickPendingIntent(R.id.widget_short_forecast,open);
        return views;
    }

    static RemoteViews create(Context c,WidgetLayoutSpec spec,int width,int height){
        Values data=new Values(c);
        String[] values=data.amounts.clone();
        for(int i=2;i<values.length;i++)if(!values[i].startsWith("—"))values[i]="≈ "+values[i];
        String kwh=values[0],cost=values[1],projectedKwh=values[2],projectedCost=values[3];
        String[] labels={"Consumo attuale","Costo attuale","Consumo previsto","Costo previsto"};
        int mode=arrangement(width,height,spec.fontScale),rows=mode==FOUR_ROWS?4:2;float rowHeight=Math.max(1,(height-2f)/rows);
        RemoteViews root=new RemoteViews(c.getPackageName(),R.layout.widget_summary_values);
        for(int i=0;i<rows;i++){
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_summary_values_row);
            if(mode==GRID){for(int side=0;side<2;side++){int index=i*2+side;cell(c,row,side==1,labels[index],values[index],index>=2,width/2f,rowHeight,spec.fontScale);}}
            else{row.setViewVisibility(R.id.widget_value_right,android.view.View.GONE);int index=i;String label=mode==TWO_ROWS?(i==0?"Attuale":"Proiezione"):labels[index],value=mode==TWO_ROWS?(i==0?kwh+" · "+cost:projectedKwh+" · "+projectedCost):values[index];cell(c,row,false,label,value,mode==TWO_ROWS?i==1:index>=2,width,rowHeight,spec.fontScale);}
            root.addView(R.id.widget_four_values,row);
        }
        root.setContentDescription(R.id.widget_four_values,"Consumo attuale: "+kwh+". Costo attuale: "+cost+". Consumo previsto: "+projectedKwh+". Costo previsto: "+projectedCost+". "+data.forecastNote);
        root.setOnClickPendingIntent(R.id.widget_four_values,openCalculator(c));
        return root;
    }
}
