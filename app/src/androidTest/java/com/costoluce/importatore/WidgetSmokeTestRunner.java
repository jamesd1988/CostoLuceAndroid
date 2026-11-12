package com.costoluce.importatore;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.graphics.Bitmap;
import java.io.*;

/** Runs only in CI: inflate both real RemoteViews pages and capture their empty-data states. */
public class WidgetSmokeTestRunner extends Instrumentation {
    private void capture(String name) throws Exception {
        waitForIdleSync(); Thread.sleep(1000);
        Bitmap b=getUiAutomation().takeScreenshot();
        if(b==null)throw new AssertionError("Missing screenshot");
        File dir=new File(getTargetContext().getFilesDir(),"screenshots");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
        b.recycle();
    }
    private android.webkit.WebView findWeb(View view){
        if(view instanceof android.webkit.WebView)return (android.webkit.WebView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.webkit.WebView found=findWeb(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    private void assertScript(android.webkit.WebView web,String body)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> answer=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript("(function(){"+body+";return 'PASS';})()",v->{answer.set(v);done.countDown();}));
        if(!done.await(12,java.util.concurrent.TimeUnit.SECONDS)||!"\"PASS\"".equals(answer.get()))throw new AssertionError("Tariff UI assertion failed: "+answer.get());
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        Bundle test=new Bundle();test.putString("id","InstrumentationTestRunner");test.putInt("numtests",1);test.putInt("current",1);test.putString("class",getClass().getName());test.putString("test","realRemoteViewsPages");sendStatus(1,test);
        try {
            Context c=getTargetContext();
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2500);capture("app-6.1");
            android.webkit.WebView web=findWeb(activity.findViewById(android.R.id.content));
            if(web==null)throw new AssertionError("Missing WebView");
            assertScript(web,"showView('rates');if(document.querySelectorAll('.rate-card').length!==10)throw Error('Default rows');openRateEditor('energy');if(!document.getElementById('rateName').disabled)throw Error('Energy lock');closeRateEditor()");
            capture("tariffe-6.1");
            assertScript(web,"openRateEditor('transport','fixed');if(document.getElementById('rateAmount').value!=='')throw Error('Automatic conversion');document.getElementById('rateName').value='Quota fissa mensile';document.getElementById('rateAmount').value='8,00'");
            capture("tariffe-editor-6.1");
            assertScript(web,"if(!saveRateEditor())throw Error('Save');setRateFilter('fixed');if(document.querySelectorAll('.rate-card').length!==1)throw Error('Filter');if(JSON.parse(localStorage.getItem('costoluce_rates_v1'))._meta.transport.name!=='Quota fissa mensile')throw Error('Persistence')");
            Thread.sleep(1200);waitForIdleSync();
            CostoLuceWidgetProvider.WidgetConfiguration saved=CostoLuceWidgetProvider.readConfiguration(c);
            if(saved==null||!WidgetPricing.parse(saved.ratesJson,saved.resident).hasFixedCosts())throw new AssertionError("Widget did not receive fixed tariff");
            assertScript(web,"resetRates();setRateFilter('all');showView('home')");Thread.sleep(1000);
            for(int page=0;page<2;page++) {
                final int selected=page;
                c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putInt("page_4242",page).commit();
                runOnMainSync(()->{
                    android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViews(c,4242);
                    View view=remote.apply(c,null);
                    if(view.findViewById(R.id.widget_summary).getVisibility()!=(selected==0?View.VISIBLE:View.GONE))throw new AssertionError("Wrong summary visibility");
                    if(view.findViewById(R.id.widget_chart).getVisibility()!=(selected==1?View.VISIBLE:View.GONE))throw new AssertionError("Wrong chart visibility");
                    if(view.findViewById(R.id.widget_spacer).getVisibility()!=(selected==0?View.VISIBLE:View.GONE))throw new AssertionError("Chart must own remaining height");
                    android.widget.FrameLayout root=new android.widget.FrameLayout(activity);
                    root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));
                    android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams((int)(340*c.getResources().getDisplayMetrics().density),(int)(320*c.getResources().getDisplayMetrics().density));
                    lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
                });
                capture(page==0?"widget-summary-6.1":"widget-history-6.1");
            }
            result.putString("stream","PASS: WebView launch and both RemoteViews pages inflated without crash.");
            sendStatus(0,test);finish(Activity.RESULT_OK,result);
        }catch(Throwable e){test.putString("stack",e.toString());sendStatus(-2,test);result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
