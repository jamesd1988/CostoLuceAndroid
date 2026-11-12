package com.costoluce.importatore;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.graphics.Bitmap;
import java.io.*;

/** Runs only in CI: inflate both real RemoteViews pages and capture their empty-data states. */
public class WidgetSmokeTestRunner extends Instrumentation {
    private void checkedOnMainSync(Runnable work){
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        super.runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError("UI check: "+failure.get(),failure.get());
    }
    private android.webkit.WebView previewWeb;
    private String resizeEvidence="";
    private void clearSystemDialog()throws Exception{
        for(int attempt=0;attempt<3;attempt++){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root==null||root.findAccessibilityNodeInfosByText("isn't responding").isEmpty())return;
            boolean system=!root.findAccessibilityNodeInfosByText("Pixel Launcher").isEmpty()||!root.findAccessibilityNodeInfosByText("System UI").isEmpty();
            if(!system)throw new AssertionError("An application ANR covers the preview");
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByText("Close app");
            if(buttons.isEmpty())throw new AssertionError("Cannot dismiss system launcher dialog");
            android.view.accessibility.AccessibilityNodeInfo button=buttons.get(0);
            if(!button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)&&button.getParent()!=null)button.getParent().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            Thread.sleep(700);
        }
    }
    private void capture(String name) throws Exception {
        clearSystemDialog();waitForIdleSync();
        if(previewWeb!=null&&previewWeb.isAttachedToWindow()){
            java.util.concurrent.CountDownLatch rendered=new java.util.concurrent.CountDownLatch(1);
            checkedOnMainSync(()->previewWeb.postVisualStateCallback(System.nanoTime(),new android.webkit.WebView.VisualStateCallback(){@Override public void onComplete(long id){rendered.countDown();}}));
            if(!rendered.await(12,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("WebView did not render the requested state");
        }
        if(previewWeb!=null&&previewWeb.isAttachedToWindow()){
            java.util.concurrent.CountDownLatch frames=new java.util.concurrent.CountDownLatch(1);
            checkedOnMainSync(()->{previewWeb.invalidate();android.view.Choreographer.getInstance().postFrameCallback(t->android.view.Choreographer.getInstance().postFrameCallback(next->frames.countDown()));});
            if(!frames.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("No committed display frame");
        }
        // Drain the emulator's buffered display frames before exporting the current state.
        for(int frame=0;frame<3;frame++){Thread.sleep(500);Bitmap old=getUiAutomation().takeScreenshot();if(old!=null)old.recycle();}
        clearSystemDialog();Thread.sleep(500);
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
        checkedOnMainSync(()->web.evaluateJavascript("(function(){"+body+";return 'PASS';})()",v->{answer.set(v);done.countDown();}));
        if(!done.await(12,java.util.concurrent.TimeUnit.SECONDS)||!"\"PASS\"".equals(answer.get()))throw new AssertionError("Tariff UI assertion failed: "+answer.get());
    }
    private void assertBounds(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;
                if(child.getTop()<0||child.getLeft()<0||child.getBottom()>group.getHeight()+1||child.getRight()>group.getWidth()+1)
                    throw new AssertionError("Clipped widget view: "+(child.getId()==View.NO_ID?child.getClass().getSimpleName():getTargetContext().getResources().getResourceEntryName(child.getId()))+" at "+group.getWidth()+"x"+group.getHeight());
                assertBounds(child);
            }
        }
    }
    private void checkWidget(Activity activity,Context c,int width,int height,int page,float fontScale)throws Exception{
        c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putInt("page_4242",page).commit();
        android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.fontScale=fontScale;
        Context sizedContext=activity.createConfigurationContext(config);
        java.util.concurrent.atomic.AtomicReference<View> applied=new java.util.concurrent.atomic.AtomicReference<>();
        checkedOnMainSync(()->{
            android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(sizedContext,4242,width,height);
            View view=remote.apply(sizedContext,null);applied.set(view);
            if(view.findViewById(R.id.widget_summary).getVisibility()!=(page==0?View.VISIBLE:View.GONE))throw new AssertionError("Wrong summary visibility");
            if(view.findViewById(R.id.widget_chart).getVisibility()!=(page==1?View.VISIBLE:View.GONE))throw new AssertionError("Wrong chart visibility");
            WidgetLayoutSpec spec=new WidgetLayoutSpec(width,height);
            if(view.findViewById(R.id.widget_caption).getVisibility()!=(spec.compact()?View.GONE:View.VISIBLE))throw new AssertionError("Wrong caption detail level");
            if(view.findViewById(R.id.widget_source).getVisibility()!=(spec.detailed()?View.VISIBLE:View.GONE))throw new AssertionError("Wrong source detail level");
            android.widget.FrameLayout root=new android.widget.FrameLayout(activity);root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));
            float density=c.getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(Math.round(width*density),Math.round(height*density));lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
        });
        waitForIdleSync();Thread.sleep(300);
        checkedOnMainSync(()->{
            View view=applied.get();assertBounds(view);
            float density=c.getResources().getDisplayMetrics().density;
            int[] targets=page==0?new int[]{R.id.widget_refresh,R.id.widget_prev,R.id.widget_next,R.id.widget_dots}:new int[]{R.id.widget_unit_kwh,R.id.widget_unit_eur,R.id.widget_prev,R.id.widget_next,R.id.widget_dots};
            for(int id:targets){View target=view.findViewById(id);if(target.getWidth()<48*density-1||target.getHeight()<48*density-1)throw new AssertionError("Small touch target");}
            if(page==1){android.widget.ImageView chart=view.findViewById(R.id.widget_chart);if(chart.getHeight()<40*density)throw new AssertionError("Chart collapsed");}
        });
        if(fontScale==1&&width<=400&&height<=350)capture("widget-"+(page==0?"summary":"history")+"-"+(width==280?"compact":width==340?"medium":"large")+"-6.4.1");
        // Measurement fixtures stay in detached views: never saved or published as Enel data.
        checkedOnMainSync(()->{
            if(page==0){
                android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(sizedContext,4242,width,height);
                remote.setTextViewText(R.id.widget_euro,"1.234,56 €");remote.setTextViewText(R.id.widget_kwh,"4.567,8 kWh");
                View sample=remote.apply(sizedContext,null);float d=c.getResources().getDisplayMetrics().density;
                sample.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*d),View.MeasureSpec.EXACTLY));sample.layout(0,0,sample.getMeasuredWidth(),sample.getMeasuredHeight());assertBounds(sample);
                for(int id:new int[]{R.id.widget_euro,R.id.widget_kwh}){android.widget.TextView text=sample.findViewById(id);if(text.getLayout()==null||text.getLayout().getEllipsisCount(0)>0)throw new AssertionError("Truncated summary value at "+width+"x"+height+" font "+fontScale);}
            }
            try{
                org.json.JSONArray fixture=new org.json.JSONArray("[{\"year\":2026,\"month\":8,\"kwh\":0,\"available\":false},{\"year\":2026,\"month\":9,\"kwh\":0,\"available\":true},{\"year\":2026,\"month\":10,\"kwh\":238,\"available\":true},{\"year\":2026,\"month\":11,\"kwh\":241,\"available\":true,\"partial\":true}]");
                Bitmap b=MonthlyChart.draw(sizedContext,fixture,new WidgetLayoutSpec(width,height),100,100000);
                if(b.getWidth()*b.getHeight()>101000)throw new AssertionError("Unbounded chart allocation");b.recycle();
            }catch(org.json.JSONException e){throw new AssertionError(e);}
        });
    }

    private void checkIntrinsicResize(Context c){
        checkedOnMainSync(()->{
            float density=c.getResources().getDisplayMetrics().density;
            int fixtureId=getContext().getResources().getIdentifier("widget_legacy_background","drawable",getContext().getPackageName());
            if(fixtureId==0)fixtureId=getContext().getResources().getIdentifier("widget_legacy_background","drawable","com.costoluce.importatore.test");
            android.graphics.drawable.Drawable legacy=getContext().getDrawable(fixtureId);
            if(legacy==null||legacy.getMinimumHeight()<319*density||legacy.getMinimumWidth()<399*density)throw new AssertionError("Legacy background fixture did not reproduce intrinsic minimum");
            for(int page=0;page<2;page++){
                c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putInt("page_4242",page).commit();
                android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(c,4242,280,260);
                View fixed=remote.apply(c,null),before=remote.apply(c,null);
                before.setBackground(legacy.getConstantState().newDrawable(getContext().getResources()));
                if(fixed.getBackground().getMinimumHeight()>2*density||fixed.getBackground().getMinimumWidth()>2*density)throw new AssertionError("Decorative background still imposes a widget minimum");
                int w=View.MeasureSpec.makeMeasureSpec(Math.round(280*density),View.MeasureSpec.EXACTLY),natural=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
                before.measure(w,natural);fixed.measure(w,natural);
                if(before.getMeasuredHeight()<319*density)throw new AssertionError("Legacy background no longer reproduces the resize regression");
                if(fixed.getMeasuredHeight()>261*density||fixed.getMeasuredHeight()>=before.getMeasuredHeight())throw new AssertionError("Widget still requests excess natural height: "+fixed.getMeasuredHeight()/density);
                // Exercise the real host in both bounded and non-exact measurement, including reuse after shrinking.
                android.appwidget.AppWidgetProviderInfo info=null;
                for(android.appwidget.AppWidgetProviderInfo candidate:android.appwidget.AppWidgetManager.getInstance(c).getInstalledProviders())if(new ComponentName(c,CostoLuceWidgetProvider.class).equals(candidate.provider)){info=candidate;break;}
                if(info==null)throw new AssertionError("Widget provider not installed for host test");
                android.appwidget.AppWidgetHostView host=new android.appwidget.AppWidgetHostView(c);host.setAppWidget(4242,info);host.setPadding(0,0,0,0);host.updateAppWidget(remote);
                for(int height:new int[]{500,350,300,260}){
                    host.measure(w,View.MeasureSpec.makeMeasureSpec(Math.round(height*density),View.MeasureSpec.EXACTLY));host.layout(0,0,host.getMeasuredWidth(),host.getMeasuredHeight());assertBounds(host);
                }
                host.measure(w,View.MeasureSpec.makeMeasureSpec(Math.round(260*density),View.MeasureSpec.AT_MOST));host.layout(0,0,host.getMeasuredWidth(),host.getMeasuredHeight());
                if(host.getMeasuredHeight()>261*density)throw new AssertionError("Host remained taller than the resize bounds");assertBounds(host);
                android.util.Log.i("CostoLuceResizeTest","page="+page+" legacyNaturalDp="+before.getMeasuredHeight()/density+" fixedNaturalDp="+fixed.getMeasuredHeight()/density);
                resizeEvidence+=" page="+page+" legacy="+before.getMeasuredHeight()/density+"dp fixed="+fixed.getMeasuredHeight()/density+"dp;";
            }
        });
    }

    private void checkSmallWidget(Activity activity,Context c,int width,int height,int savedPage,float fontScale)throws Exception{
        SharedPreferences prefs=c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE);
        prefs.edit().putInt("page_4242",savedPage).putString("display_unit","eur").commit();
        android.content.res.Configuration configuration=new android.content.res.Configuration(c.getResources().getConfiguration());configuration.fontScale=fontScale;
        Context sized=activity.createConfigurationContext(configuration);WidgetLayoutSpec spec=new WidgetLayoutSpec(width,height);
        java.util.concurrent.atomic.AtomicReference<View> rendered=new java.util.concurrent.atomic.AtomicReference<>();
        checkedOnMainSync(()->{
            android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(sized,4242,width,height);
            View view=remote.apply(sized,null);rendered.set(view);
            if(view.findViewById(R.id.widget_chart)!=null||view.findViewById(R.id.widget_prev)!=null||view.findViewById(R.id.widget_brand)!=null)throw new AssertionError("Small widget retained full-page chrome");
            if(spec.statsOnly()&&view.findViewById(R.id.widget_small_period)!=null)throw new AssertionError("2x1 must show only the two values");
            if(spec.smallSummary()){
                if(view.findViewById(R.id.widget_small_updated).getVisibility()!=(spec.smallDate()?View.VISIBLE:View.GONE))throw new AssertionError("Wrong intermediate date visibility");
                if(view.findViewById(R.id.widget_small_refresh).getVisibility()!=(spec.smallRefresh()?View.VISIBLE:View.GONE))throw new AssertionError("Wrong intermediate refresh visibility");
            }
            float density=c.getResources().getDisplayMetrics().density;
            android.widget.FrameLayout root=new android.widget.FrameLayout(activity);root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));
            android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(Math.round(width*density),Math.round(height*density));lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
        });
        waitForIdleSync();Thread.sleep(200);checkedOnMainSync(()->{
            View root=rendered.get();assertBounds(root);
            android.graphics.Outline outline=new android.graphics.Outline();root.getOutlineProvider().getOutline(root,outline);
            if(!root.getClipToOutline()||!outline.canClip())throw new AssertionError("Decorative waves are not clipped to rounded widget bounds");
        });
        checkedOnMainSync(()->{
            View root=rendered.get(),refresh=root.findViewById(R.id.widget_small_refresh);
            if(spec.statsOnly()&&refresh.getVisibility()!=(spec.statsRefreshPossible()?View.VISIBLE:View.GONE))throw new AssertionError("Missing compact refresh");
            if(refresh.getVisibility()==View.VISIBLE){
                float density=c.getResources().getDisplayMetrics().density;
                if(refresh.getWidth()<48*density-1||refresh.getHeight()<48*density-1||root.hasOnClickListeners())throw new AssertionError("Refresh target small or intercepted by parent");
            }
        });
        if(prefs.getInt("page_4242",-1)!=savedPage||!"eur".equals(prefs.getString("display_unit","")))throw new AssertionError("Collapsing widget lost the saved page or unit");
        if(fontScale==1&&savedPage==0){
            if(width==130&&height==64)capture("widget-2x1-6.4.1");
            if(width==300&&height==64)capture("widget-wide-row-6.4.1");
            if(width==180&&height==180)capture("widget-intermediate-6.4.1");
            if(width==150&&height==80)capture("widget-compact-refresh-6.4.1");
        }
        // Isolated measurement fixtures, never stored in the real widget preferences or exported screenshots.
        checkedOnMainSync(()->{
            Context fixture=new android.content.ContextWrapper(sized){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(name.equals(CostoLuceWidgetProvider.PREFS)?"widget_small_measurement_fixture":name,mode);}};
            fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().clear().putLong("saved_at",1700000000000L).putString("kwh","4567.8").putString("estimated_euro","1234.56").putString("period","Ott + Nov 2026").putInt("page_4242",savedPage).commit();
            View measured=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,width,height).apply(fixture,null);
            float density=c.getResources().getDisplayMetrics().density;
            for(int settle=0;settle<2;settle++){measured.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*density),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*density),View.MeasureSpec.EXACTLY));measured.layout(0,0,measured.getMeasuredWidth(),measured.getMeasuredHeight());}assertBounds(measured);
            for(int id:new int[]{R.id.widget_small_kwh,R.id.widget_small_cost}){
                android.widget.TextView value=measured.findViewById(id);
                if(value.getLayout()==null||value.getLayout().getEllipsisCount(0)>0)throw new AssertionError("Clipped small value id="+id+" at "+width+"x"+height+" font="+fontScale+" pixels="+value.getTextSize()+" width="+value.getWidth());
                if(value.getTextSize()/density<10.5f)throw new AssertionError("Small value became illegible at "+width+"x"+height);
            }
            if(!((android.widget.TextView)measured.findViewById(R.id.widget_small_kwh)).getText().toString().contains("4.567,8"))throw new AssertionError("Small consumption was not real snapshot value");
            if(!((android.widget.TextView)measured.findViewById(R.id.widget_small_cost)).getText().toString().contains("1.234,56"))throw new AssertionError("Small cost was not real snapshot value");
            if(width==150&&height==80){
                fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putString("kwh","383.5").putString("estimated_euro","93.56").putBoolean("refresh_busy",true).commit();
                View reused=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,96,48).apply(fixture,null);
                if(!reused.hasOnClickListeners())throw new AssertionError("Baseline mini value area must open app");
                CostoLuceWidgetProvider.createViewsForSize(fixture,4242,width,height).reapply(fixture,reused);
                android.widget.TextView refresh=reused.findViewById(R.id.widget_small_refresh);
                if(refresh.getVisibility()!=View.VISIBLE||!"…".equals(refresh.getText().toString())||reused.hasOnClickListeners())throw new AssertionError("Compact busy feedback or parent listener reset failed");
                for(int settle=0;settle<2;settle++){reused.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*density),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*density),View.MeasureSpec.EXACTLY));reused.layout(0,0,reused.getMeasuredWidth(),reused.getMeasuredHeight());}assertBounds(reused);
            }
        });
    }

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        Bundle test=new Bundle();test.putString("id","InstrumentationTestRunner");test.putInt("numtests",1);test.putInt("current",1);test.putString("class",getClass().getName());test.putString("test","realRemoteViewsPages");sendStatus(1,test);
        try {
            Context c=getTargetContext();
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2500);previewWeb=findWeb(activity.findViewById(android.R.id.content));capture("app-6.4.1");
            android.webkit.WebView web=findWeb(activity.findViewById(android.R.id.content));
            if(web==null)throw new AssertionError("Missing WebView");
            assertScript(web,"showView('rates');if(document.querySelectorAll('.rate-card').length!==10)throw Error('Default rows');openRateEditor('energy');if(!document.getElementById('rateName').disabled)throw Error('Energy lock');closeRateEditor()");
            capture("tariffe-6.4.1");
            assertScript(web,"openRateEditor('transport','fixed');if(document.getElementById('rateAmount').value!=='')throw Error('Automatic conversion');document.getElementById('rateName').value='Quota fissa mensile';document.getElementById('rateAmount').value='8,00'");
            capture("tariffe-editor-6.4.1");
            assertScript(web,"if(!saveRateEditor())throw Error('Save');setRateFilter('fixed');if(document.querySelectorAll('.rate-card').length!==1)throw Error('Filter');if(JSON.parse(localStorage.getItem('costoluce_rates_v1'))._meta.transport.name!=='Quota fissa mensile')throw Error('Persistence')");
            Thread.sleep(1200);waitForIdleSync();
            CostoLuceWidgetProvider.WidgetConfiguration saved=CostoLuceWidgetProvider.readConfiguration(c);
            if(saved==null||!WidgetPricing.parse(saved.ratesJson,saved.resident).hasFixedCosts())throw new AssertionError("Widget did not receive fixed tariff");
            assertScript(web,"setRateFilter('all');openNewRate();document.getElementById('rateName').value='Servizio aggiuntivo';document.getElementById('rateAmount').value='0,02';if(!saveRateEditor())throw Error('Create row');if(JSON.parse(localStorage.getItem('costoluce_rates_v1'))._custom.length!==1)throw Error('Custom persistence');openRateEditor('dispatch');requestDeleteRate();if(document.getElementById('rateDeleteConfirm').hidden)throw Error('Delete confirmation');if(!confirmDeleteRate())throw Error('Delete default');if(rateMeta.dispatch.enabled)throw Error('Default still active')");
            Thread.sleep(1200);waitForIdleSync();
            saved=CostoLuceWidgetProvider.readConfiguration(c);
            org.json.JSONObject customConfig=new org.json.JSONObject(saved.ratesJson);
            if(customConfig.getJSONArray("_custom").length()!=1||customConfig.getJSONObject("_meta").getJSONObject("dispatch").getBoolean("enabled"))throw new AssertionError("Widget did not receive added/deleted rows");
            WidgetPricing pricing=WidgetPricing.parse(saved.ratesJson,saved.resident);
            double nativeCost=pricing.estimatedCost(479,2,218,2);
            assertScript(web,"if(Math.abs(calculateCosts(479,2,218,2).total-"+nativeCost+")>0.000001)throw Error('App/widget pricing mismatch');if(!undoRateDeletion()||!rateMeta.dispatch.enabled)throw Error('Restore default');openRateEditor(customKeys[0]);requestDeleteRate();if(!confirmDeleteRate()||customKeys.length!==0)throw Error('Delete custom');if(!undoRateDeletion()||customKeys.length!==1)throw Error('Restore custom');openRateEditor('energy');if(!document.getElementById('rateDeleteButton').hidden||requestDeleteRate())throw Error('Protected Energy');closeRateEditor()");
            assertScript(web,"resetRates();setRateFilter('all');showView('home')");Thread.sleep(1000);
            checkIntrinsicResize(c);
            int[][] smallSizes={{96,48},{110,56},{130,64},{150,80},{180,96},{300,64},{130,160},{180,180},{240,220},{130,500}};
            for(int[] size:smallSizes)for(int page=0;page<2;page++)for(float scale:new float[]{1,1.3f})checkSmallWidget(activity,c,size[0],size[1],page,scale);
            int[][] sizes={{280,260},{340,300},{400,350},{280,500},{500,260}};
            for(int[] size:sizes)for(int page=0;page<2;page++)checkWidget(activity,c,size[0],size[1],page,1);
            for(int page=0;page<2;page++)checkWidget(activity,c,280,260,page,1.3f);
            android.os.Bundle options=new android.os.Bundle();
            options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280);options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,260);
            options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,500);options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,500);
            CostoLuceWidgetProvider.createViews(c,4242,options);
            if(android.os.Build.VERSION.SDK_INT>=31){java.util.ArrayList<android.util.SizeF> exact=new java.util.ArrayList<>();exact.add(new android.util.SizeF(280,260));exact.add(new android.util.SizeF(500,260));options.putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,exact);CostoLuceWidgetProvider.createViews(c,4242,options);}
            result.putString("stream","PASS: WebView launch and both RemoteViews pages inflated without crash. Resize regression:"+resizeEvidence);
            sendStatus(0,test);finish(Activity.RESULT_OK,result);
        }catch(Throwable e){test.putString("stack",e.toString());sendStatus(-2,test);result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
