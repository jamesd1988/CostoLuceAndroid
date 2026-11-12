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
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        Bundle test=new Bundle();test.putString("id","InstrumentationTestRunner");test.putInt("numtests",1);test.putInt("current",1);test.putString("class",getClass().getName());test.putString("test","realRemoteViewsPages");sendStatus(1,test);
        try {
            Context c=getTargetContext();
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2500);capture("app-6.0");
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
                capture(page==0?"widget-summary-6.0":"widget-history-6.0");
            }
            result.putString("stream","PASS: WebView launch and both RemoteViews pages inflated without crash.");
            sendStatus(0,test);finish(Activity.RESULT_OK,result);
        }catch(Throwable e){test.putString("stack",e.toString());sendStatus(-2,test);result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
