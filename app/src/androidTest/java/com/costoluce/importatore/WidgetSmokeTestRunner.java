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
    private boolean populatedPreview;
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
        if(name.startsWith("widget-")&&!populatedPreview)return;
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
        if(populatedPreview){Bitmap labelled=b.copy(Bitmap.Config.ARGB_8888,true);b.recycle();b=labelled;android.graphics.Canvas canvas=new android.graphics.Canvas(b);android.graphics.Paint label=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);label.setColor(android.graphics.Color.WHITE);label.setTextSize(16*getTargetContext().getResources().getDisplayMetrics().density);canvas.drawText("ANTEPRIMA · DATI DI TEST",32,120,label);}
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
        checkedOnMainSync(()->web.evaluateJavascript("(function(){try{"+body+";return 'PASS';}catch(error){return 'FAIL: '+String(error&&error.stack||error);}})()",v->{answer.set(v);done.countDown();}));
        if(!done.await(12,java.util.concurrent.TimeUnit.SECONDS)||!"\"PASS\"".equals(answer.get()))throw new AssertionError("UI assertion failed: "+answer.get()+" in "+body.substring(0,Math.min(160,body.length())));
    }
    private void assertBounds(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof android.widget.TextView&&(view.getId()==R.id.widget_value_amount_left||view.getId()==R.id.widget_value_amount_right)){
            android.widget.TextView text=(android.widget.TextView)view;
            if(text.getLayout()==null||text.getLayout().getLineCount()!=1||text.getLayout().getLineEnd(0)<text.getText().length()||text.getLayout().getLineWidth(0)>text.getWidth()-text.getPaddingLeft()-text.getPaddingRight()+1)throw new AssertionError("Four-metric number clipped: "+text.getText());
        }
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
        WidgetLayoutSpec actual=new WidgetLayoutSpec(width,height,fontScale);
        if(!actual.fullPages()){checkSmallWidget(activity,c,width,height,page,fontScale);return;}
        c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putInt("page_4242",page).commit();
        android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.fontScale=fontScale;
        Context sizedContext=new android.content.ContextWrapper(activity.createConfigurationContext(config)){@Override public SharedPreferences getSharedPreferences(String name,int mode){return c.getSharedPreferences(name,mode);}};
        java.util.concurrent.atomic.AtomicReference<View> applied=new java.util.concurrent.atomic.AtomicReference<>();
        checkedOnMainSync(()->{
            android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(sizedContext,4242,width,height);
            View view=remote.apply(sizedContext,null);applied.set(view);
            if(view.findViewById(R.id.widget_summary).getVisibility()!=(!actual.unified()&&page==1?View.GONE:View.VISIBLE))throw new AssertionError("Wrong summary visibility");
            if(view.findViewById(R.id.widget_chart).getVisibility()!=(actual.unified()||page==1?View.VISIBLE:View.GONE))throw new AssertionError("Wrong chart visibility");
            WidgetLayoutSpec spec=new WidgetLayoutSpec(width,height,fontScale);
            if(view.findViewById(R.id.widget_caption).getVisibility()!=((page==0||actual.unified()||spec.shortWide())?View.GONE:View.VISIBLE))throw new AssertionError("Wrong caption detail level");
            if(view.findViewById(R.id.widget_source).getVisibility()!=View.GONE)throw new AssertionError("Wrong source detail level");
            android.widget.FrameLayout root=new android.widget.FrameLayout(activity);root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));
            float density=c.getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(Math.round(width*density),Math.round(height*density));lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
        });
        waitForIdleSync();Thread.sleep(300);
        checkedOnMainSync(()->{
            View view=applied.get();assertBounds(view);
            float density=c.getResources().getDisplayMetrics().density;
            int[] targets={R.id.widget_refresh,R.id.widget_prev,R.id.widget_next,R.id.widget_dots,R.id.widget_unit_bill,R.id.widget_unit_kwh,R.id.widget_unit_eur,R.id.widget_mode_cycle};
            for(int id:targets){View target=view.findViewById(id);if(target.getVisibility()!=View.VISIBLE||target.getParent() instanceof View&&((View)target.getParent()).getVisibility()!=View.VISIBLE)continue;if(target.getWidth()<48*density-1||target.getHeight()<48*density-1||!target.hasOnClickListeners())throw new AssertionError("Small or inactive touch target "+id+" at "+width+"x"+height);}
            for(int id:new int[]{R.id.widget_unit_bill,R.id.widget_unit_kwh,R.id.widget_unit_eur,R.id.widget_mode_cycle}){android.widget.TextView label=view.findViewById(id);if(label.getVisibility()!=View.VISIBLE||((View)label.getParent()).getVisibility()!=View.VISIBLE)continue;if(label.getLayout()==null||label.getLayout().getLineWidth(0)>label.getWidth()-label.getCompoundPaddingLeft()-label.getCompoundPaddingRight()+1)throw new AssertionError("Clipped mode label at "+width+"x"+height);}
            if(actual.unified()&&view.findViewById(R.id.widget_navigation).getVisibility()!=View.GONE)throw new AssertionError("Unified layout retained redundant pages");
            if(page==1||actual.unified()){android.widget.ImageView chart=view.findViewById(R.id.widget_chart);if(chart.getHeight()<40*density)throw new AssertionError("Chart collapsed");}

        });
        if(fontScale==1&&((width==280&&height==260)||(width==340&&height==300)||(width==400&&height==350)))capture("widget-"+(page==0?"summary":"bill".equals(CostoLuceWidgetProvider.chartMode(c))?"bill":"history")+"-"+(width==280?"compact":width==340?"medium":"large")+"-6.24.1");
        if(fontScale==1&&((width==130&&height==500)||(width==190&&height==420)||(width==260&&height==252)||(width==410&&height==210)||(width==420&&height==450)))capture("widget-adaptive-"+width+"x"+height+"-"+page+"-6.24.1");
        // Measurement fixtures stay in detached views: never saved or published as Enel data.
        checkedOnMainSync(()->{
            if(page==0){
                Context fixture=new android.content.ContextWrapper(sizedContext){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(name.equals(CostoLuceWidgetProvider.PREFS)?"widget_full_measurement_fixture":name,mode);}};
                fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().clear().putLong("saved_at",1700000000000L).putString("kwh","4567.8").putString("estimated_euro","1234.56").putString("period","Ott + Nov 2026").commit();
                android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,width,height);
                View sample=remote.apply(sizedContext,null);float d=c.getResources().getDisplayMetrics().density;
                sample.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*d),View.MeasureSpec.EXACTLY));sample.layout(0,0,sample.getMeasuredWidth(),sample.getMeasuredHeight());assertBounds(sample);
                for(int id:new int[]{R.id.widget_euro,R.id.widget_kwh}){android.widget.TextView text=sample.findViewById(id);if(text.getVisibility()==View.VISIBLE&&(text.getLayout()==null||text.getLayout().getEllipsisCount(0)>0))throw new AssertionError("Truncated summary value at "+width+"x"+height+" font "+fontScale);}
            }
            try{
                org.json.JSONArray fixture=new org.json.JSONArray("[{\"year\":2026,\"month\":8,\"kwh\":0,\"available\":false},{\"year\":2026,\"month\":9,\"kwh\":0,\"available\":true},{\"year\":2026,\"month\":10,\"kwh\":238,\"available\":true},{\"year\":2026,\"month\":11,\"kwh\":241,\"available\":true,\"partial\":true}]");
                if("bill".equals(CostoLuceWidgetProvider.chartMode(sizedContext))){java.util.Calendar reference=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"));reference.clear();reference.set(2026,10,13);CostoLuceWidgetProvider.WidgetConfiguration pricing=CostoLuceWidgetProvider.readConfiguration(c);fixture=BillHistory.rows(fixture,reference,2,8,WidgetPricing.parse(pricing.ratesJson,pricing.resident));}
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

    private void checkPocketWidget(Activity activity,Context c,int width,int height,int savedPage,float fontScale)throws Exception{
        CostoLuceWidgetProvider.prefs(c).edit().putInt("page_4242",savedPage).commit();
        android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.fontScale=fontScale;
        Context sized=new android.content.ContextWrapper(activity.createConfigurationContext(config)){@Override public SharedPreferences getSharedPreferences(String name,int mode){return c.getSharedPreferences(name,mode);}};
        checkedOnMainSync(()->{
            View view=CostoLuceWidgetProvider.createViewsForSize(sized,4242,width,height).apply(sized,null);float d=c.getResources().getDisplayMetrics().density;
            android.widget.FrameLayout root=new android.widget.FrameLayout(activity);root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(Math.round(width*d),Math.round(height*d));lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
            for(int settle=0;settle<2;settle++){view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*d),View.MeasureSpec.EXACTLY));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}assertBounds(view);
            if(view.findViewById(R.id.widget_chart).getVisibility()!=(savedPage==1?View.VISIBLE:View.GONE))throw new AssertionError("Pocket chart not accessible");
            for(int id:new int[]{R.id.widget_pocket_cycle,R.id.widget_small_refresh}){View target=view.findViewById(id);if(target.getWidth()<48*d-1||target.getHeight()<48*d-1||!target.hasOnClickListeners())throw new AssertionError("Pocket target clipped or inactive");}
            if(view.hasOnClickListeners()||view.findViewById(R.id.widget_prev)!=null)throw new AssertionError("Pocket layout retained unnecessary navigation");
            if(savedPage==1&&view.findViewById(R.id.widget_chart).getHeight()<70*d)throw new AssertionError("Pocket chart too short");
        });
        waitForIdleSync();
        if(fontScale==1&&savedPage==0&&width==180&&height==180)capture("widget-intermediate-6.24.1");
        if(populatedPreview&&width==130&&height==160)capture("widget-2x2-"+(savedPage==0?"summary":CostoLuceWidgetProvider.chartMode(c))+"-6.24.1");
        if(CostoLuceWidgetProvider.prefs(c).getInt("page_4242",-1)!=savedPage)throw new AssertionError("Pocket rendering reset page");
    }

    private void checkSmallWidget(Activity activity,Context c,int width,int height,int savedPage,float fontScale)throws Exception{
        if(new WidgetLayoutSpec(width,height,fontScale).pocketPages()){checkPocketWidget(activity,c,width,height,savedPage,fontScale);return;}
        if(new WidgetLayoutSpec(width,height,fontScale).fullPages()){checkWidget(activity,c,width,height,savedPage,fontScale);return;}
        SharedPreferences prefs=c.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE);
        prefs.edit().putInt("page_4242",savedPage).putString("display_unit","eur").commit();
        android.content.res.Configuration configuration=new android.content.res.Configuration(c.getResources().getConfiguration());configuration.fontScale=fontScale;
        Context sized=new android.content.ContextWrapper(activity.createConfigurationContext(configuration)){@Override public SharedPreferences getSharedPreferences(String name,int mode){return c.getSharedPreferences(name,mode);}};WidgetLayoutSpec spec=new WidgetLayoutSpec(width,height,fontScale);
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
            if(spec.statsOnly()){
                android.widget.TextView energy=root.findViewById(R.id.widget_small_kwh),cost=root.findViewById(R.id.widget_small_cost);
                if(Math.abs(energy.getTextSize()-cost.getTextSize())>.1f)throw new AssertionError("Minimal widget text sizes differ at "+width+"x"+height+" font="+fontScale);
            }
            if(spec.statsOnly()&&refresh.getVisibility()!=(spec.statsRefreshPossible()?View.VISIBLE:View.GONE))throw new AssertionError("Missing compact refresh at "+width+"x"+height+" font="+fontScale);
            if(refresh.getVisibility()==View.VISIBLE){
                float density=c.getResources().getDisplayMetrics().density;
                if(refresh.getWidth()<48*density-1||refresh.getHeight()<48*density-1||root.hasOnClickListeners())throw new AssertionError("Refresh target small or intercepted by parent");
            }
        });
        if(prefs.getInt("page_4242",-1)!=savedPage||!"eur".equals(prefs.getString("display_unit","")))throw new AssertionError("Collapsing widget lost the saved page or unit");
        if(fontScale==1&&savedPage==0){
            if(width==96&&height==48)capture("widget-mini-6.24.1");
            if(width==130&&height==64)capture("widget-2x1-6.24.1");
            if(width==300&&height==64)capture("widget-wide-row-6.24.1");
            if(width==180&&height==180)capture("widget-intermediate-6.24.1");
            if(width==150&&height==80)capture("widget-compact-refresh-6.24.1");
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
            if(spec.statsOnly()&&Math.abs(((android.widget.TextView)measured.findViewById(R.id.widget_small_kwh)).getTextSize()-((android.widget.TextView)measured.findViewById(R.id.widget_small_cost)).getTextSize())>.1f)throw new AssertionError("Long values resized independently");
            if(!((android.widget.TextView)measured.findViewById(R.id.widget_small_kwh)).getText().toString().contains("4.567,8"))throw new AssertionError("Small consumption was not real snapshot value");
            if(!((android.widget.TextView)measured.findViewById(R.id.widget_small_cost)).getText().toString().contains("1.234,56"))throw new AssertionError("Small cost was not real snapshot value");
            if(width==150&&height==80){
                fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().putString("kwh","383.5").putString("estimated_euro","93.56").putBoolean("refresh_busy",true).commit();
                View reused=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,96,48).apply(fixture,null);
                if(!reused.hasOnClickListeners())throw new AssertionError("Baseline mini value area must open app");
                CostoLuceWidgetProvider.createViewsForSize(fixture,4242,width,height).reapply(fixture,reused);
                android.widget.ImageView refresh=reused.findViewById(R.id.widget_small_refresh);
                if(refresh.getVisibility()!=View.VISIBLE||refresh.getDrawable()==null||!refresh.getContentDescription().toString().contains("in corso")||reused.hasOnClickListeners())throw new AssertionError("Compact busy feedback or parent listener reset failed");
                for(int settle=0;settle<2;settle++){reused.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*density),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*density),View.MeasureSpec.EXACTLY));reused.layout(0,0,reused.getMeasuredWidth(),reused.getMeasuredHeight());}assertBounds(reused);
            }
        });
    }

    /** Exercise both sides of layout breakpoints, long values and refresh feedback without touching account data. */
    private void checkAdaptiveEdges(Activity activity,Context c){
        checkedOnMainSync(()->{
            for(float fs:new float[]{1,1.3f,2})for(int[] size:new int[][]{{120,130},{120,150},{130,160},{150,160},{180,180},{130,330},{170,330},{239,400},{240,230},{240,250},{299,250},{340,190},{360,420}})for(int page=0;page<2;page++)for(boolean busy:new boolean[]{false,true}){
                android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.fontScale=fs;
                Context sized=activity.createConfigurationContext(config);
                Context fixture=new android.content.ContextWrapper(sized){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(name.equals(CostoLuceWidgetProvider.PREFS)?"widget_adaptive_edge_fixture":name,mode);}};
                fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).edit().clear().putLong("saved_at",1700000000000L).putString("kwh","398.7").putString("estimated_euro","141.45").putString("period","Dic 2026 + Gen 2027 + Feb 2027").putInt("page_4242",page).putBoolean("refresh_busy",busy).putString("refresh_status",busy?"Aggiornamento in corso":"").commit();
                View view=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,size[0],size[1]).apply(sized,null);float d=c.getResources().getDisplayMetrics().density;
                for(int settle=0;settle<2;settle++){view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(size[0]*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(size[1]*d),View.MeasureSpec.EXACTLY));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
                try{assertBounds(view);}catch(AssertionError e){throw new AssertionError("Adaptive edge "+size[0]+"x"+size[1]+" font="+fs+" page="+page+" busy="+busy+": "+e.getMessage(),e);}
                if(fixture.getSharedPreferences(CostoLuceWidgetProvider.PREFS,Context.MODE_PRIVATE).getInt("page_4242",-1)!=page)throw new AssertionError("Adaptive rendering reset the saved page");
            }
        });
    }

    private void checkCompactCostChoice(Activity activity,Context fixture)throws Exception{
        SharedPreferences p=CostoLuceWidgetProvider.prefs(fixture);String history=p.getString("history_json","[]"),rates=p.getString("rates_json","");
        String actual=p.getString("estimated_euro","");
        if(!CostoLuceWidgetProvider.saveCompactCostMode(fixture,"projection")||!"projection".equals(CostoLuceWidgetProvider.compactCostMode(fixture)))throw new AssertionError("Projection choice not saved");
        WidgetProjection.Result expected=CostoLuceWidgetProvider.compactProjection(fixture);
        if(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).getDayOfMonth()>=8&&!expected.available)throw new AssertionError("Native widget projection missing: "+expected.reason);
        for(int[] size:new int[][]{{96,48},{150,80}}){
            populatedPreview=false;checkSmallWidget(activity,fixture,size[0],size[1],0,1);populatedPreview=true;
            capture("widget-projection-"+(size[0]==96?"mini":"refresh")+"-6.24.1");
            checkedOnMainSync(()->{View view=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,size[0],size[1]).apply(fixture,null);String text=((android.widget.TextView)view.findViewById(R.id.widget_small_cost)).getText().toString();if(expected.available&&!text.startsWith("≈"))throw new AssertionError("Projection not identified");if(!((android.widget.TextView)view.findViewById(R.id.widget_small_kwh)).getText().toString().contains("398,7"))throw new AssertionError("Measured consumption replaced by forecast");});
        }
        checkedOnMainSync(()->{View bigger=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,130,160).apply(fixture,null);if(((android.widget.TextView)bigger.findViewById(R.id.widget_small_cost)).getText().toString().startsWith("≈"))throw new AssertionError("Preference affected non-minimal widgets");});
        org.json.JSONArray noDays=new org.json.JSONArray(history);for(int i=0;i<noDays.length();i++)noDays.getJSONObject(i).remove("daily");p.edit().putString("history_json",noDays.toString()).commit();
        if(CostoLuceWidgetProvider.compactProjection(fixture).available)throw new AssertionError("Projection invented without daily readings");
        populatedPreview=false;checkSmallWidget(activity,fixture,150,80,0,1);populatedPreview=true;capture("widget-projection-unavailable-6.24.1");
        checkedOnMainSync(()->{View view=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,150,80).apply(fixture,null);if(!"— €".equals(((android.widget.TextView)view.findViewById(R.id.widget_small_cost)).getText().toString()))throw new AssertionError("Missing forecast silently fell back to current cost");});
        p.edit().putString("history_json",history).commit();CostoLuceWidgetProvider.saveCompactCostMode(fixture,"current");
        if(CostoLuceWidgetProvider.saveCompactCostMode(fixture,"invalid")||!"current".equals(CostoLuceWidgetProvider.compactCostMode(fixture)))throw new AssertionError("Invalid compact mode accepted");
        if(!rates.equals(p.getString("rates_json",""))||!actual.equals(p.getString("estimated_euro","")))throw new AssertionError("Widget choice changed tariffs or measured cost");
    }

    private void checkFourMetricSummaries(Activity activity,Context fixture)throws Exception{
        int[][] sizes={{130,160},{130,240},{190,240},{250,240}};
        for(int i=0;i<sizes.length;i++){
            int width=sizes[i][0],height=sizes[i][1];final int shape=i+1;CostoLuceWidgetProvider.prefs(fixture).edit().putInt("page_4242",0).commit();
            checkedOnMainSync(()->{
                android.widget.RemoteViews remote=CostoLuceWidgetProvider.createViewsForSize(fixture,4242,width,height);View view=remote.apply(fixture,null);float d=fixture.getResources().getDisplayMetrics().density;
                remote.reapply(fixture,view);
                if(((ViewGroup)view.findViewById(R.id.widget_summary_extra)).getChildCount()!=1)throw new AssertionError("Repeated update duplicated summary");
                ViewGroup values=view.findViewById(R.id.widget_four_values);int rows=width==130&&height==240?4:2;
                if(values==null||values.getChildCount()!=rows)throw new AssertionError("Wrong arrangement "+width+"x"+height);
                String description=values.getContentDescription().toString();if(!description.contains("398,7 kWh")||!description.contains("Consumo previsto")||!description.contains("Costo previsto"))throw new AssertionError("Missing real/forecast values: "+description);
                android.widget.FrameLayout root=new android.widget.FrameLayout(activity);root.setBackgroundColor(android.graphics.Color.rgb(8,17,29));android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(Math.round(width*d),Math.round(height*d));lp.gravity=Gravity.CENTER;root.addView(view,lp);activity.setContentView(root);
                for(int settle=0;settle<2;settle++){view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*d),View.MeasureSpec.EXACTLY));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}assertBounds(view);
                android.widget.TextView cycle=view.findViewById(R.id.widget_pocket_cycle);if(cycle!=null&&(cycle.getText().length()==0||cycle.getTextSize()<8*d))throw new AssertionError("Missing graph navigation label");
                int pad=Math.round(16*d);Bitmap bitmap=Bitmap.createBitmap(view.getWidth()+pad*2,view.getHeight()+pad*2+Math.round(28*d),Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(8,17,29));android.graphics.Paint caption=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);caption.setColor(android.graphics.Color.WHITE);caption.setTextSize(11*d);canvas.drawText("ANTEPRIMA · DATI DI TEST",pad,20*d,caption);canvas.save();canvas.translate(pad,pad+28*d);view.draw(canvas);canvas.restore();File dir=new File(getTargetContext().getFilesDir(),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"widget-four-values-"+shape+"-6.24.1.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new AssertionError(error);}finally{bitmap.recycle();}
            });waitForIdleSync();
        }
    }

    /** Test APK only: isolated, explicitly labelled populated previews; no synthetic production snapshot. */
    private void capturePopulatedWidgets(Activity activity,Context context)throws Exception{
        Context fixture=new android.content.ContextWrapper(context){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("widget_visual_fixture_"+name,mode);}};
        org.json.JSONObject rates=new org.json.JSONObject("{\"energy\":0.135,\"losses\":10,\"dispatch\":0.021115752,\"balancing\":0.000786979,\"transport\":0.0119,\"equalization\":0.00283,\"asos\":0.031515,\"arim\":0.001638,\"excise\":0.0227,\"vat\":10}");
        org.json.JSONArray fees=new org.json.JSONArray();String[] ids={"commercialization","dispbt","rete_fissa","rete_potenza","qualita_potenza"},names={"Commercializzazione","DispBT","Rete quota fissa","Rete quota potenza","Rete qualita quota potenza"};double[] fixed={10.5,1.3,1.92,5.88,.0497};
        for(int i=0;i<ids.length;i++)fees.put(new org.json.JSONObject().put("id","custom_offer_"+ids[i]).put("name",names[i]).put("type","fixed").put("value",fixed[i]));rates.put("_custom",fees);
        java.time.LocalDate current=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).withDayOfMonth(1),start=current.minusMonths(1);
        org.json.JSONArray history=new org.json.JSONArray(),monthly=new org.json.JSONArray(),invoices=new org.json.JSONArray();double[] readings={170,158,225,193,177.9,160.2,218.1,180.6};
        for(int i=0;i<8;i++){java.time.LocalDate date=current.minusMonths(7-i);org.json.JSONObject row=new org.json.JSONObject().put("year",date.getYear()).put("month",date.getMonthValue()).put("kwh",readings[i]).put("days",i==7?13:date.lengthOfMonth()).put("hourSamples",24*(i==7?13:date.lengthOfMonth())).put("lastMeasuredDay",i==7?13:date.lengthOfMonth()).put("available",true).put("partial",i==7);if(i==7){int n=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).getDayOfMonth()-1;org.json.JSONArray days=new org.json.JSONArray();for(int day=1;day<=n;day++)days.put(new org.json.JSONObject().put("day",day).put("kwh",180.6/n).put("complete",true).put("hourSamples",24));row.put("daily",days).put("days",n).put("hourSamples",n*24).put("lastMeasuredDay",n);}history.put(row);if(i>=6)monthly.put(row);}
        long[] totals={11520,16361,12756};for(int i=0;i<3;i++){java.time.LocalDate from=start.minusMonths(6-i*2),to=from.plusMonths(1);to=to.withDayOfMonth(to.lengthOfMonth());invoices.put(new org.json.JSONObject().put("id","TEST-"+i).put("from",from.toString()).put("to",to.toString()).put("electricityCents",totals[i]).put("fixedGrossCents",4800));}
        WidgetPricing pricing=WidgetPricing.parse(rates.toString(),true);double euro=pricing.estimatedCost(218.1,1)+pricing.estimatedCost(180.6,1);
        fixture.getSharedPreferences("issued_invoices_v1",Context.MODE_PRIVATE).edit().clear().putString("active","preview").putString("preview",invoices.toString()).commit();
        SharedPreferences p=CostoLuceWidgetProvider.prefs(fixture);p.edit().clear().putLong("saved_at",System.currentTimeMillis()).putString("kwh","398.7").putString("estimated_euro",Double.toString(euro)).putString("source","enel").putString("rates_json",rates.toString()).putBoolean("resident",true).putInt("months",2).putInt("configured_months",2).putInt("billing_anchor",start.getMonthValue()).putString("monthly_json",monthly.toString()).putString("history_json",history.toString()).commit();
        populatedPreview=true;
        for(int[] size:new int[][]{{96,48},{130,64},{150,80},{300,64},{180,180}})checkSmallWidget(activity,fixture,size[0],size[1],0,1);
        p.edit().putString("widget_chart_mode","kwh").putString("display_unit","kwh").commit();
        for(int[] size:new int[][]{{280,260},{340,300},{400,350},{130,500},{190,420},{260,252},{410,210},{420,450}})for(int page=0;page<2;page++)checkWidget(activity,fixture,size[0],size[1],page,1);
        p.edit().putString("widget_chart_mode","bill").commit();for(int[] size:new int[][]{{280,260},{340,300},{400,350}})checkWidget(activity,fixture,size[0],size[1],1,1);
        for(String state:new String[]{"summary","bill","eur","kwh"}){
            if(!CostoLuceWidgetProvider.selectPocketView(fixture,4242,state))throw new AssertionError("Pocket view rejected");
            checkPocketWidget(activity,fixture,130,160,"summary".equals(state)?0:1,1);
        }
        if(CostoLuceWidgetProvider.selectPocketView(fixture,4242,"invalid"))throw new AssertionError("Invalid pocket state accepted");
        checkFourMetricSummaries(activity,fixture);
        checkCompactCostChoice(activity,fixture);
        populatedPreview=false;
    }

    private Bundle testArguments;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);testArguments=arguments;start();}
    private void checkAccountConfiguration(Activity activity,android.webkit.WebView web)throws Exception{
        assertScript(web,"showView('settings');if(document.querySelectorAll('#settingsMenu > .settings-entry').length!==2)throw Error('Settings menu is not two entries');if(!byId('settingsWidget').hidden||!byId('settingsAccountTools').hidden)throw Error('Settings panels leaked');byId('openAccountSettings').click()");
        Thread.sleep(650);waitForIdleSync();
        java.lang.reflect.Field dialogField=MainActivity.class.getDeclaredField("accountDialog");dialogField.setAccessible(true);
        EnelAccountDialog[] dialog=new EnelAccountDialog[1];
        java.util.Map<String,?> storedBefore=new java.util.HashMap<>(activity.getSharedPreferences("enel_secure_settings",Context.MODE_PRIVATE).getAll());
        checkedOnMainSync(()->{try{dialog[0]=(EnelAccountDialog)dialogField.get(activity);}catch(Exception e){throw new AssertionError("Missing native account screen",e);}
            if(dialog[0]==null||!dialog[0].isShowing())throw new AssertionError("Account entry did not open native form");
            View root=dialog[0].getWindow().getDecorView();android.widget.Spinner provider=root.findViewWithTag("account-provider");
            if(provider.getCount()!=1||!"Enel".equals(provider.getSelectedItem()))throw new AssertionError("Unsupported provider offered");
            int[] first=new int[2],second=new int[2];root.findViewWithTag("provider-card").getLocationOnScreen(first);root.findViewWithTag("account-card").getLocationOnScreen(second);
            if(first[1]>=second[1])throw new AssertionError("Provider must precede separate account card");
            if(((android.widget.CheckBox)root.findViewWithTag("account-remember")).isChecked())throw new AssertionError("Fresh install saved credentials without consent");
            android.widget.EditText password=root.findViewWithTag("account-password");if(!(password.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod))throw new AssertionError("Password is not masked");
        });
        android.webkit.WebView previousPreview=previewWeb;previewWeb=null;
        capture("settings-account-6.24.1");
        checkedOnMainSync(()->((android.widget.ScrollView)dialog[0].getWindow().getDecorView().findViewWithTag("account-scroll")).fullScroll(View.FOCUS_DOWN));Thread.sleep(300);capture("settings-account-security-6.24.1");
        checkedOnMainSync(()->{
            View root=dialog[0].getWindow().getDecorView();root.findViewWithTag("account-connect").performClick();
            if(!dialog[0].isShowing()||root.findViewWithTag("account-error").getVisibility()!=View.VISIBLE)throw new AssertionError("Invalid credentials dismissed form");
            android.widget.EditText password=root.findViewWithTag("account-password");root.findViewWithTag("account-reveal").performClick();if(password.getTransformationMethod()!=null)throw new AssertionError("Password reveal failed");root.findViewWithTag("account-reveal").performClick();
            android.widget.EditText user=root.findViewWithTag("account-user");user.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(user,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            if(android.os.Build.VERSION.SDK_INT>=30&&root.getWindowInsetsController()!=null)root.getWindowInsetsController().show(android.view.WindowInsets.Type.ime());
        });
        if(android.os.Build.VERSION.SDK_INT>=30){java.util.concurrent.atomic.AtomicBoolean keyboardVisible=new java.util.concurrent.atomic.AtomicBoolean();for(int attempt=0;attempt<20&&!keyboardVisible.get();attempt++){Thread.sleep(250);checkedOnMainSync(()->{View root=dialog[0].getWindow().getDecorView();keyboardVisible.set(root.getRootWindowInsets()!=null&&root.getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()));});}}
        Thread.sleep(350);waitForIdleSync();
        checkedOnMainSync(()->{View root=dialog[0].getWindow().getDecorView();if(android.os.Build.VERSION.SDK_INT>=30&&(root.getRootWindowInsets()==null||!root.getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime())))throw new AssertionError("Account keyboard not shown");View user=root.findViewWithTag("account-user");android.graphics.Rect visible=new android.graphics.Rect();if(!user.getGlobalVisibleRect(visible)||visible.height()<user.getHeight()-2)throw new AssertionError("Keyboard covers focused account field: visible="+visible+" fieldHeight="+user.getHeight()+" windowHeight="+root.getHeight()+" viewportHeight="+root.findViewWithTag("account-scroll").getHeight());});
        capture("settings-account-keyboard-6.24.1");
        checkedOnMainSync(()->{View root=dialog[0].getWindow().getDecorView();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);dialog[0].dismiss();});Thread.sleep(350);previewWeb=previousPreview;
        final EnelConnector.Credentials fixture=new EnelConnector.Credentials("preview@example.invalid","only-test-password","IT001E00000000","123456","RSSMRA80A01H501U");
        java.util.concurrent.atomic.AtomicInteger submitted=new java.util.concurrent.atomic.AtomicInteger(),tools=new java.util.concurrent.atomic.AtomicInteger();
        checkedOnMainSync(()->{dialog[0]=new EnelAccountDialog(activity,fixture,true,null,(credentials,remember)->{if(!fixture.username.equals(credentials.username)||!fixture.password.equals(credentials.password)||!fixture.pod.equals(credentials.pod)||!fixture.userNumber.equals(credentials.userNumber)||!fixture.fiscalCode.equals(credentials.fiscalCode)||!remember)throw new AssertionError("Saved account values changed");submitted.incrementAndGet();},()->tools.incrementAndGet());dialog[0].show();View root=dialog[0].getWindow().getDecorView();if(!((android.widget.CheckBox)root.findViewWithTag("account-remember")).isChecked())throw new AssertionError("Saved account opt-in lost");root.findViewWithTag("account-connect").performClick();});
        if(submitted.get()!=1)throw new AssertionError("Validated native form not submitted");
        checkedOnMainSync(()->{dialog[0]=new EnelAccountDialog(activity,fixture,true,null,(credentials,remember)->submitted.incrementAndGet(),()->tools.incrementAndGet());dialog[0].show();dialog[0].getWindow().getDecorView().findViewWithTag("account-tools").performClick();});
        if(tools.get()!=1||submitted.get()!=1)throw new AssertionError("Tools navigation submitted credentials");
        if(!storedBefore.equals(activity.getSharedPreferences("enel_secure_settings",Context.MODE_PRIVATE).getAll()))throw new AssertionError("Opening/cancelling account form modified encrypted storage");
        assertScript(web,"if(!byId('settingsMenu')||byId('settingsMenu').hidden)throw Error('Account dismissal did not retain settings menu')");
    }

    @Override public void onStart(){
        if(testArguments!=null&&"true".equals(testArguments.getString("compatibility"))){new CompatibilityChecks(this).run();return;}
        Bundle result=new Bundle();
        Bundle test=new Bundle();test.putString("id","InstrumentationTestRunner");test.putInt("numtests",1);test.putInt("current",1);test.putString("class",getClass().getName());test.putString("test","realRemoteViewsPages");sendStatus(1,test);
        try {
            Context c=getTargetContext();
            org.json.JSONObject areraFixture;
            try(InputStream input=getContext().getAssets().open("arera-2026-layout.b64");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
                areraFixture=AreraTable.parse(android.util.Base64.decode(bytes.toByteArray(),android.util.Base64.DEFAULT),2026);
            }
            if(Math.abs(areraFixture.getJSONObject("2026-08").getJSONObject("resident").getDouble("asos")-.031515)>1e-10)throw new AssertionError("ARERA parser on Android");
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2500);previewWeb=findWeb(activity.findViewById(android.R.id.content));capture("app-6.24.1");
            final android.webkit.WebView syncWeb=previewWeb;
            assertScript(syncWeb,"showView('home');window.scrollTo(0,0);if(byId('homeSync').getBoundingClientRect().bottom>document.querySelector('#page-home .result').getBoundingClientRect().top)throw Error('Sync placement');if(document.querySelectorAll('#homeRefresh').length!==1)throw Error('Duplicate refresh')");capture("sync-ready-6.24.1");
            assertScript(syncWeb,"window.onEnelStatus('Recupero consumi Enel…',false,true);if(!byId('homeRefresh').disabled||byId('syncButtonLabel').textContent!=='Aggiornamento…')throw Error('Sync busy');window.syncPreviousTime=byId('historyUpdated').textContent");capture("sync-loading-6.24.1");
            assertScript(syncWeb,"window.onEnelStatus('Connessione non disponibile. I dati precedenti sono conservati.',true,false);if(byId('homeRefresh').disabled||byId('syncButtonLabel').textContent!=='Riprova'||byId('historyUpdated').textContent!==window.syncPreviousTime)throw Error('Sync failure retention')");capture("sync-error-6.24.1");
            assertScript(syncWeb,"window.onEnelStatus('',false,false);delete window.syncPreviousTime");

            android.webkit.WebView web=findWeb(activity.findViewById(android.R.id.content));
            if(web==null)throw new AssertionError("Missing WebView");
            assertScript(web,"showView('settings')");capture("settings-menu-6.24.1");
            checkAccountConfiguration(activity,web);
            assertScript(web,"showView('settings');openSettingsPanel('widget');setCompactWidgetCost('projection');if(byId('compactWidgetCost').value!=='projection'||localStorage.getItem('costoluce_compact_widget_cost_v1')!=='projection')throw Error('Widget cost choice not persisted')");
            Thread.sleep(500);if(!"projection".equals(CostoLuceWidgetProvider.compactCostMode(c)))throw new AssertionError("Widget preference bridge failed");capture("settings-widget-6.24.1");
            assertScript(web,"setCompactWidgetCost('current');showView('home')");Thread.sleep(500);

            assertScript(web,"showView('rates');if(document.querySelectorAll('.bottom-nav button').length!==4||byId('page-period')||byId('nav-period'))throw Error('Period navigation remains');if(document.querySelectorAll('.rate-card:not([hidden])').length!==4)throw Error('Compact rows');openTariffPanel('billing');byId('billingFrequency').value='1';billingDraftChanged(true);closeTariffPanel();if(months!==2||byId('billingFrequency').value!=='2')throw Error('Cancel changed billing');if(document.querySelectorAll('.rate-card').length!==10)throw Error('Default rows');openRateEditor('energy');if(!document.getElementById('rateName').disabled)throw Error('Energy lock');closeRateEditor()");
            capture("tariffe-6.24.1");
            assertScript(web,"openRateEditor('transport','fixed');if(document.getElementById('rateAmount').value!=='')throw Error('Automatic conversion');document.getElementById('rateName').value='Quota fissa mensile';document.getElementById('rateAmount').value='8,00'");
            capture("tariffe-editor-6.24.1");
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
            String areraPayload="{\"months\":"+areraFixture+",\"checkedAt\":0,\"error\":\"\"}";
            assertScript(web,"showView('rates');window.onAreraCatalog("+areraPayload+");byId('areraMonth').value='2026-08';byId('areraResident').value='resident';byId('areraPower').value='3';openTariffPanel('arera')");capture("tariffe-arera-6.24.1");
            assertScript(web,"previewArera();if(!areraDraft||byId('areraPreview').hidden||window.tariffActivePanel!=='arera-preview')throw Error('ARERA preview')");capture("tariffe-arera-anteprima-6.24.1");
            assertScript(web,"if(!applyArera()||customKeys.length!==3)throw Error('ARERA import');openRateEditor('asos');byId('rateAmount').value='0.04';if(!saveRateEditor())throw Error('ARERA pencil');window.onAreraCatalog("+areraPayload+");if(tariffs.asos!==.04)throw Error('ARERA check overwrites manual edit')");
            Thread.sleep(1200);waitForIdleSync();saved=CostoLuceWidgetProvider.readConfiguration(c);
            if(saved==null||new org.json.JSONObject(saved.ratesJson).getJSONArray("_custom").length()!=3)throw new AssertionError("ARERA fixed network rows did not reach widget");
            double areraNativeCost=WidgetPricing.parse(saved.ratesJson,saved.resident).estimatedCost(338,2,38,2);
            assertScript(web,"if(Math.abs(calculateCosts(338,2,38,2).total-"+areraNativeCost+")>0.000001)throw Error('ARERA app/widget mismatch');if(!completeBillOffer()||customKeys.length!==5)throw Error('Offer fixed costs');if(!completeBillOffer()||customKeys.length!==5)throw Error('Offer duplicate');showView('rates');openTariffPanel('offer')");
            capture("tariffe-offerta-6.24.1");Thread.sleep(1200);waitForIdleSync();saved=CostoLuceWidgetProvider.readConfiguration(c);
            if(saved==null||new org.json.JSONObject(saved.ratesJson).getJSONArray("_custom").length()!=5)throw new AssertionError("Offer fixed costs did not reach widget");
            double offerNativeCost=WidgetPricing.parse(saved.ratesJson,saved.resident).estimatedCost(338,2,38,2);
            assertScript(web,"if(Math.abs(calculateCosts(338,2,38,2).total-"+offerNativeCost+")>0.000001)throw Error('Offer app/widget mismatch');resetRates();setRateFilter('all');showView('home')");Thread.sleep(1000);
            assertScript(web,"showView('rates');byId('billingFrequency').value='3';billingDraftChanged(true);if(saveBillingSetup())throw Error('Missing grouping example accepted');byId('billingAnchor').value='8';billingDraftChanged();if(!saveBillingSetup())throw Error('Quarter grouping rejected');openTariffPanel('billing')");
            capture("tariffe-periodo-6.24.1");Thread.sleep(1200);waitForIdleSync();saved=CostoLuceWidgetProvider.readConfiguration(c);
            if(saved==null||saved.months!=3||saved.anchor!=8)throw new AssertionError("Quarter and example did not reach widget");
            CostoLuceWidgetProvider.createViewsForSize(c,4242,280,260);
            assertScript(web,"showView('home');window.onHistoryNative([]);setChartMode('bill');if(document.querySelectorAll('#historyChart .history-col').length<5||!byId('historyTotal').textContent.includes('non disponibile'))throw Error('Missing bill data fabricated')");
            assertScript(web,"const v=byId('historyViewport');if(v.scrollWidth<=v.clientWidth)throw Error('Annual chart does not scroll');v.scrollLeft=0;rememberHistoryScroll();recalc()");Thread.sleep(250);
            assertScript(web,"const v=byId('historyViewport');if(v.scrollLeft>1)throw Error('Recalculation lost historical position');v.scrollLeft=v.scrollWidth-v.clientWidth;rememberHistoryScroll();if(byId('billPricingNotice').hidden)throw Error('Missing fixed costs warning absent')");
            capture("bollette-6.24.1");
            assertScript(web,"setMonths(2,10);setChartMode('eur');showView('home')");Thread.sleep(1000);
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c);
            byte[] pdfFixture;
            try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=new com.tom_roush.pdfbox.pdmodel.PDDocument()){
                com.tom_roush.pdfbox.pdmodel.PDPage fixturePage=new com.tom_roush.pdfbox.pdmodel.PDPage();pdf.addPage(fixturePage);
                try(com.tom_roush.pdfbox.pdmodel.PDPageContentStream stream=new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf,fixturePage)){
                    stream.beginText();stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,12);stream.newLineAtOffset(40,740);
                    for(String line:new String[]{"Enel Energia POD IT001E00000000","Fattura elettronica n. 123456789","Periodo AGO. 2026 - SET. 2026","Totale Bolletta 127,56","Totale da pagare 145,56","QUOTA FISSA E QUOTA POTENZA 39,88","IVA 10% su imponibile di euro 115,96 11,60"}){stream.showText(line);stream.newLineAtOffset(0,-20);}stream.endText();
                }
                java.io.ByteArrayOutputStream pdfBytes=new java.io.ByteArrayOutputStream();pdf.save(pdfBytes);pdfFixture=pdfBytes.toByteArray();
            }
            try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFixture)){
                IssuedInvoice invoice=IssuedInvoice.parse(new com.tom_roush.pdfbox.text.PDFTextStripper().getText(pdf));
                if(invoice.electricityCents!=12756||invoice.payableCents!=14556||!Long.valueOf(4387).equals(invoice.fixedGrossCents))throw new AssertionError("PDF invoice electricity extraction");
            }
            assertScript(web,"if(billingHistory().slice(0,-1).some(r=>r.available))throw Error('Historical consumption shown as issued invoices')");
            // Isolated forecast fixtures on the clean CI emulator only.
            assertScript(web,"window.projectionSmokeBackup={rows:historyRows,invoices:issuedInvoices,months,anchor:billingAnchor,input:byId('kwh').value,hasWidgetInput,lastEnelImport,lastEnelMonthly,widgetSource,widgetPeriod};setMonths(2,(new Date().getMonth()+11)%12+1);setChartMode('bill');const now=new Date(),fixtures=[];for(let offset=-1;offset<=0;offset++){const date=new Date(now.getFullYear(),now.getMonth()+offset,1),limit=offset===0?now.getDate()-1:new Date(now.getFullYear(),now.getMonth(),0).getDate(),daily=[];for(let day=1;day<=limit;day++)daily.push({day,kwh:8,hourSamples:24,complete:true});fixtures.push({year:date.getFullYear(),month:date.getMonth()+1,kwh:daily.length*8,daily,available:daily.length>0,partial:offset===0,lastMeasuredDay:limit});}fixtures[0].daily[0].complete=false;window.onHistoryNative(fixtures);window.onEnelConsumption({kwh:fixtures.reduce((sum,row)=>sum+row.kwh,0),months,anchor:billingAnchor,monthly:fixtures.map(row=>({...row,days:row.daily.length,expectedDays:new Date(row.year,row.month,0).getDate(),hourSamples:row.daily.length*24,future:false}))});setBillProjection(false);if(byId('billProjectionControls').hidden)throw Error('Projection toggle hidden');const tariffsBefore=JSON.stringify(serializedRates());setBillProjection(true);if(!lastBillProjection.available||lastBillProjection.sampleDays!==7||document.querySelectorAll('#historyChart .history-bar.projected').length!==1)throw Error('Projection did not replace current bar');if(JSON.stringify(serializedRates())!==tariffsBefore)throw Error('Projection changed tariffs');if(localStorage.getItem('costoluce_bill_projection_v1')!=='1')throw Error('Projection preference');byId('billPricingNotice').hidden=true;byId('historyCostScope').hidden=true;byId('historyChart').scrollIntoView({block:'center'})");
            assertScript(web,"const home=byId('page-home');if(home.querySelector('#billProjectionAmount, #billProjectionDetails, #historyRange, .history-nav, #historyTotal, #billBreakdown, #invoiceTools, #historyCostScope'))throw Error('Removed chart clutter remains in Home');if(!byId('billProjectionState').textContent.includes('Proiezione fattura completa basata sugli ultimi 7 giorni rilevati'))throw Error('Wrong full forecast caption')");
            Thread.sleep(300);capture("bolletta-proiezione-6.24.1");
            assertScript(web,"const forecast=projectedBillingRows(billingHistory(),new Date()).at(-1),actual=billingHistory().at(-1),bar=document.querySelector('.history-bar.projected'),solid=bar.querySelector('.history-projection-actual'),hatched=bar.querySelector('.history-projection-extra');if(forecast.actualValue!==actual.value)throw Error('Actual amount was recalculated for split bar');const a=solid.getBoundingClientRect(),e=hatched.getBoundingClientRect(),h=bar.clientHeight;if(!(a.height>0&&e.height>0)||Math.abs(a.height+e.height-h)>1||Math.abs(a.height/h-actual.value/forecast.value)>.025)throw Error('Wrong actual/extra forecast proportions');if(Math.abs(e.bottom-a.top)>1)throw Error('Forecast portions are not stacked');if(!byId('historyChart').getAttribute('aria-label').includes('parte piena sui consumi rilevati'))throw Error('Forecast split not explained accessibly')");

            assertScript(web,"window.stablePlotSnapshot=null;for(const [mode,projected] of [['kwh',false],['eur',false],['bill',false],['bill',true]]){setChartMode(mode);setBillProjection(projected);window.scrollTo(0,0);const geometry=JSON.stringify(['.chart-heading','.chart-badge','.plot-frame','.chart-axis','.history-track'].map(selector=>{const r=document.querySelector(selector).getBoundingClientRect();return [r.y+window.scrollY,r.height]}));if(window.stablePlotSnapshot===null)window.stablePlotSnapshot=geometry;else if(geometry!==window.stablePlotSnapshot)throw Error('Chart moved between modes '+mode+' projection '+projected);if(mode==='bill'){const toggle=byId('billProjectionControls'),segment=byId('chartBill').parentElement;if(segment.contains(toggle)||toggle.getBoundingClientRect().right>segment.getBoundingClientRect().left)throw Error('Forecast switch not separate on left');if(byId('billProjectionState').scrollHeight>byId('billProjectionState').clientHeight+1)throw Error('Forecast caption clipped');}}delete window.stablePlotSnapshot");

            assertScript(web,"window.scrollTo(0,0);window.homeSummarySnapshot=JSON.stringify(['summaryOther','total','summaryProjectedKwh','summaryProjection'].map(id=>{const e=byId(id),r=e.getBoundingClientRect();return [e.textContent,r.x+window.scrollX,r.y+window.scrollY,r.width,r.height]}));if(document.querySelector('.unit-toggle'))throw Error('Obsolete Home unit selector');if(!byId('summaryProjection').textContent.startsWith('≈'))throw Error('Home projection missing');for(const mode of ['kwh','eur','bill']){setChartMode(mode);if(JSON.stringify(['summaryOther','total','summaryProjectedKwh','summaryProjection'].map(id=>{const e=byId(id),r=e.getBoundingClientRect();return [e.textContent,r.x+window.scrollX,r.y+window.scrollY,r.width,r.height]}))!==window.homeSummarySnapshot)throw Error('Home summary moved with chart '+mode)}setChartMode('kwh');window.scrollTo(0,0)");
            capture("home-kwh-6.24.1");
            assertScript(web,"setChartMode('eur');window.scrollTo(0,0)");capture("home-eur-6.24.1");
            assertScript(web,"setBillProjection(false);showView('consumption');if(!byId('homeSync').parentElement.isSameNode(byId('page-consumption'))||byId('page-consumption').querySelector('#kwh,#enelCheck,#monthlyRows'))throw Error('Consumption cleanup');if(!byId('page-settings').querySelector('#enelCheck'))throw Error('Diagnostics not in settings');if(byId('total2').textContent!==euro(lastCalculatedCost))throw Error('Actual detail total');window.consumptionOriginalInput=byId('kwh').value");capture("consumption-actual-6.24.1");
            assertScript(web,"setBillProjection(true);if(byId('total2').textContent!==byId('summaryProjection').textContent||byId('consumptionAmount').textContent!==byId('summaryProjection').textContent)throw Error('Forecast detail differs from Home');if(byId('kwh').value!==window.consumptionOriginalInput)throw Error('Forecast replaced actual input');delete window.consumptionOriginalInput;window.onEnelStatus('Verifica sincronizzazione',false,true);showView('home');if(!byId('homeRefresh').disabled||!byId('page-home').querySelector('#homeSync'))throw Error('Shared sync busy');window.onEnelStatus('',false,false);showView('consumption')");capture("consumption-projected-6.24.1");
            assertScript(web,"showView('home')");

            assertScript(web,"setBillProjection(true);setChartMode('kwh');window.scrollTo(0,0);if(byId('summaryProjectedKwh').textContent!=='≈ '+decimal(calculateCurrentBillProjection(new Date()).kwh,1)+' kWh'||!document.querySelector('.history-bar.projected .history-projection-actual')||!byId('billProjectionToggle').checked)throw Error('Shared kWh projection missing')");capture("home-kwh-projection-6.24.1");
            assertScript(web,"setChartMode('eur');window.scrollTo(0,0);if(!document.querySelector('.history-bar.projected .history-projection-extra')||!byId('billProjectionToggle').checked)throw Error('Shared euro projection missing')");capture("home-eur-projection-6.24.1");
            assertScript(web,"setChartMode('bill');delete window.homeSummarySnapshot");

            assertScript(web,"const all=historyRows.flatMap(row=>row.daily.map(day=>({row,day,date:new Date(row.year,row.month-1,day.day)}))).sort((a,b)=>b.date-a.date);all.forEach((entry,index)=>entry.day.complete=index<6);renderHistory();if(lastBillProjection.available||document.querySelectorAll('#historyChart .history-bar.projected').length)throw Error('Six days fabricated projection');if(byId('billProjectionState').textContent!=='Proiezione non disponibile: aspettare 7 giorni rilevati')throw Error('Missing seven-day message');window.projectionWeekDays=all;byId('historyChart').scrollIntoView({block:'center'})");
            capture("bolletta-proiezione-assente-6.24.1");
            assertScript(web,"showView('consumption');if(byId('total2').textContent!=='—'||byId('breakdown').children.length||!byId('consumptionProjectionHint').textContent.includes('7 giorni'))throw Error('Unavailable forecast fabricated')");capture("consumption-unavailable-6.24.1");assertScript(web,"showView('home')");

            assertScript(web,"const all=window.projectionWeekDays;delete window.projectionWeekDays;all[6].day.complete=true;renderHistory();if(!lastBillProjection.available||lastBillProjection.sampleDays!==7)throw Error('Seven day threshold');setChartMode('kwh');if(byId('billProjectionControls').hidden)throw Error('Shared projection toggle missing in kWh');setChartMode('bill');if(!billProjectionEnabled)throw Error('Projection preference lost');all.forEach(entry=>entry.day.complete=true);setBillProjection(false);if(document.querySelectorAll('#historyChart .history-bar.projected').length)throw Error('Actual and projection both visible');byId('billPricingNotice').hidden=true;byId('historyCostScope').hidden=true;byId('historyChart').scrollIntoView({block:'center'})");
            Thread.sleep(300);capture("bolletta-attuale-6.24.1");
            assertScript(web,"historyRows[0].daily[0].complete=false;setChartMode('kwh');const columns=document.querySelectorAll('#historyChart button');if(columns.length<13)throw Error('Monthly bars not accessible buttons');columns[columns.length-2].click();if(byId('page-daily').hidden||!byId('page-home').hidden)throw Error('Daily drilldown navigation');if(!byId('dailyTitle').textContent.toLowerCase().includes(italianMonth(historyRows[0].year,historyRows[0].month)))throw Error('Wrong selected month');if(document.querySelectorAll('#dailyChart .daily-day').length!==new Date(historyRows[0].year,historyRows[0].month,0).getDate())throw Error('Wrong calendar days');selectDailyDay(1);if(!byId('dailySelectedValue').textContent.includes('8,00'))throw Error('Actual daily value');if(!byId('dailySelectedStatus').textContent.includes('parziale'))throw Error('Partial daily status');if(!byId('dailyRefresh').getAttribute('href').includes('year='+historyRows[0].year+'&month='+historyRows[0].month))throw Error('Wrong targeted month');byId('dailyChart').scrollIntoView({block:'center'})");
            capture("consumi-giornalieri-6.24.1");
            assertScript(web,"if(!closeTopTariffDialog()||!byId('page-daily').hidden||chartMode!=='kwh')throw Error('Back did not restore monthly view');setChartMode('eur');const columns=document.querySelectorAll('#historyChart button');columns[columns.length-2].click();selectDailyDay(1);if(!byId('dailySelectedValue').textContent.includes('8,00'))throw Error('Euro did not open actual daily kWh');moveDailyMonth(-1);if(document.querySelectorAll('#dailyChart .daily-bar').length||byId('dailyWarning').hidden||byId('dailySelectedValue').textContent!=='—')throw Error('Missing daily readings fabricated');byId('dailyChart').scrollIntoView({block:'center'})");
            capture("consumi-giornalieri-assenti-6.24.1");
            assertScript(web,"closeMonthDaily()");
            assertScript(web,"const backup=window.projectionSmokeBackup;setMonths(backup.months,backup.anchor);window.onHistoryNative(backup.rows);window.onInvoicesNative(backup.invoices);byId('kwh').value=backup.input;hasWidgetInput=backup.hasWidgetInput;lastEnelImport=backup.lastEnelImport;lastEnelMonthly=backup.lastEnelMonthly;widgetSource=backup.widgetSource;widgetPeriod=backup.widgetPeriod;recalc();delete window.projectionSmokeBackup;showView('home')");

            checkIntrinsicResize(c);
            checkAdaptiveEdges(activity,c);
            int[][] smallSizes={{96,48},{110,56},{130,64},{150,80},{180,96},{300,64},{130,160},{180,180},{240,220},{130,500}};
            for(int[] size:smallSizes)for(int page=0;page<2;page++)for(float scale:new float[]{1,1.3f})checkSmallWidget(activity,c,size[0],size[1],page,scale);
            int[][] sizes={{280,260},{340,300},{400,350},{280,500},{500,260},{130,500},{190,420},{260,252},{410,210},{420,450}};
            for(int[] size:sizes)for(int page=0;page<2;page++)checkWidget(activity,c,size[0],size[1],page,1);
            for(int[] size:sizes)for(int page=0;page<2;page++)checkWidget(activity,c,size[0],size[1],page,1.3f);
            new CostoLuceWidgetProvider().onReceive(c,new Intent(c,CostoLuceWidgetProvider.class).setAction(CostoLuceWidgetProvider.ACTION_UNIT).putExtra("unit","bill"));
            if(!"bill".equals(CostoLuceWidgetProvider.chartMode(c)))throw new AssertionError("Bill mode broadcast not saved");
            CostoLuceWidgetProvider.saveUnit(c,"kwh");if(!"bill".equals(CostoLuceWidgetProvider.chartMode(c)))throw new AssertionError("Summary unit reset bill chart mode");
            for(int[] size:sizes)for(float scale:new float[]{1,1.3f})checkWidget(activity,c,size[0],size[1],1,scale);
            String billDescription=MonthlyChart.description(c,CostoLuceWidgetProvider.chartRows(c));if(!billDescription.contains("Costi fissi, variabili e imposte")||!billDescription.contains("canone TV separato"))throw new AssertionError("Bill total scope missing");
            new CostoLuceWidgetProvider().onReceive(c,new Intent(c,CostoLuceWidgetProvider.class).setAction(CostoLuceWidgetProvider.ACTION_UNIT).putExtra("unit","eur"));
            if(!"eur".equals(CostoLuceWidgetProvider.chartMode(c)))throw new AssertionError("Monthly euro mode not restored");
            // Detached bitmap fixture: verify actual painted colors without saving synthetic readings.
            org.json.JSONArray allocationColors=new org.json.JSONArray();
            for(int month=8;month<=11;month++)allocationColors.put(new org.json.JSONObject().put("year",2026).put("month",month).put("available",true).put("status",month<10?"allocated":"forecast").put("billEuro",60).put("kwh",200).put("partial",month==11));
            WidgetLayoutSpec allocationSpec=new WidgetLayoutSpec(400,350);Bitmap allocationBitmap=MonthlyChart.draw(c,allocationColors,allocationSpec,180,600000);
            float allocationScale=(float)allocationBitmap.getWidth()/allocationSpec.contentWidth(),font=c.getResources().getConfiguration().fontScale;
            float allocationBottom=180-(11*font*2+12*font+11),allocationStep=(allocationSpec.contentWidth()-2-28)/4f;
            int historicalPixel=allocationBitmap.getPixel(Math.round((28+allocationStep*.5f)*allocationScale),Math.round((allocationBottom-4)*allocationScale));
            if(android.graphics.Color.blue(historicalPixel)<=android.graphics.Color.green(historicalPixel))throw new AssertionError("Allocated history not blue");
            for(int index:new int[]{2,3}){int pixel=allocationBitmap.getPixel(Math.round((28+allocationStep*(index+.5f))*allocationScale),Math.round((allocationBottom-4)*allocationScale));if(android.graphics.Color.green(pixel)<=android.graphics.Color.blue(pixel))throw new AssertionError("Both forecast months must be teal");}
            allocationBitmap.recycle();
            capturePopulatedWidgets(activity,c);

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
