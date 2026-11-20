package com.costoluce.importatore;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Clean CI only. No real account, network login, or production fixture data. */
final class CompatibilityChecks {
    private final Instrumentation instrumentation;
    private final JSONArray checks=new JSONArray();
    private final JSONObject report=new JSONObject();
    private final File evidence;
    private WebView web;
    private String stage="launch";
    CompatibilityChecks(Instrumentation instrumentation){this.instrumentation=instrumentation;evidence=new File(instrumentation.getTargetContext().getFilesDir(),"compat");evidence.mkdirs();}
    private void main(Runnable action){
        AtomicReference<Throwable> error=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(stage+": "+error.get(),error.get());
    }
    private WebView findWeb(View root){if(root instanceof WebView)return (WebView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){WebView found=findWeb(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    private String evaluate(String code)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> answer=new AtomicReference<>();
        main(()->web.evaluateJavascript("(function(){try{"+code+";return 'PASS';}catch(e){return 'FAIL: '+String(e&&e.stack||e);}})()",value->{answer.set(value);done.countDown();}));
        if(!done.await(15,TimeUnit.SECONDS))throw new AssertionError(stage+": WebView response timeout");
        if(!"\"PASS\"".equals(answer.get()))throw new AssertionError(stage+": "+answer.get());return answer.get();
    }
    private void passed(String name)throws Exception{checks.put(name);Bundle state=new Bundle();state.putString("stream","PASS: "+name);instrumentation.sendStatus(0,state);}
    private void screenshot(String name)throws Exception{
        instrumentation.waitForIdleSync();
        if(web!=null&&web.isAttachedToWindow()&&!name.contains("account")&&!stage.contains("native account")){
            CountDownLatch rendered=new CountDownLatch(1);main(()->web.postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){rendered.countDown();}}));if(!rendered.await(15,TimeUnit.SECONDS))throw new AssertionError("Screenshot WebView not rendered");
            CountDownLatch frames=new CountDownLatch(1);main(()->{web.invalidate();android.view.Choreographer.getInstance().postFrameCallback(t->android.view.Choreographer.getInstance().postFrameCallback(next->frames.countDown()));});if(!frames.await(8,TimeUnit.SECONDS))throw new AssertionError("Screenshot frames not committed");
        }
        for(int frame=0;frame<2;frame++){Thread.sleep(350);Bitmap stale=instrumentation.getUiAutomation().takeScreenshot();if(stale!=null)stale.recycle();}
        Bitmap source=instrumentation.getUiAutomation().takeScreenshot();if(source==null)throw new AssertionError("Missing screenshot");
        Bitmap image=source.copy(Bitmap.Config.ARGB_8888,true);source.recycle();Canvas canvas=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(6,24,45));canvas.drawRect(0,0,image.getWidth(),48,paint);paint.setColor(Color.WHITE);paint.setTextSize(24);canvas.drawText("TEST · Android "+Build.VERSION.SDK_INT+" · "+name,16,32,paint);
        try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private void writeReport()throws Exception{report.put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(evidence,"report.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
    private void assertWidgetBounds(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;if(child.getLeft()<0||child.getTop()<0||child.getRight()>parent.getWidth()+1||child.getBottom()>parent.getHeight()+1)throw new AssertionError("Widget child outside "+parent.getWidth()+"x"+parent.getHeight()+": "+child.getClass().getSimpleName());assertWidgetBounds(child);}}
        else if(view instanceof TextView){TextView text=(TextView)view;int id=text.getId();if(id==R.id.widget_small_kwh||id==R.id.widget_small_cost||id==R.id.widget_kwh||id==R.id.widget_euro){if(text.getLayout()==null)throw new AssertionError("Widget value has no layout");for(int line=0;line<text.getLineCount();line++)if(text.getLayout().getEllipsisCount(line)>0)throw new AssertionError("Widget numeric value clipped");}}
    }
    private void widgets(Activity activity)throws Exception{
        Context app=instrumentation.getTargetContext();
        for(float scale:new float[]{1,1.3f,2})for(int[] size:new int[][]{{96,48},{130,64},{130,160},{180,180},{280,260},{400,350},{130,500},{500,260}})for(int page=0;page<2;page++){
            stage="widget "+size[0]+"x"+size[1]+" scale="+scale+" page="+page;
            Configuration configuration=new Configuration(app.getResources().getConfiguration());configuration.fontScale=scale;
            Context sized=new android.content.ContextWrapper(activity.createConfigurationContext(configuration)){@Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return app.getSharedPreferences(name,mode);}};
            final int selected=page;
            main(()->{
                CostoLuceWidgetProvider.prefs(sized).edit().putInt("page_4242",selected).commit();
                View view=CostoLuceWidgetProvider.createViewsForSize(sized,4242,size[0],size[1]).apply(sized,null);float density=sized.getResources().getDisplayMetrics().density;
                int width=Math.round(size[0]*density),height=Math.round(size[1]*density);
                for(int settle=0;settle<2;settle++){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}assertWidgetBounds(view);
            });
        }
        passed("48 RemoteViews layouts: 8 sizes × 3 font scales × 2 pages");
    }
    private void webScreens(String orientation)throws Exception{
        for(boolean projected:new boolean[]{false,true})for(String mode:new String[]{"kwh","eur","bill"}){
            stage=orientation+" chart="+mode+" projection="+projected;
            evaluate("showView('home');setChartMode('"+mode+"');setBillProjection("+projected+");window.scrollTo(0,0);sizeProjectionStatus();"+
                "if(document.body.scrollWidth>innerWidth+2)throw Error('Horizontal overflow '+document.body.scrollWidth+'/'+innerWidth);"+
                "for(const id of ['summaryOther','total','summaryProjectedKwh','summaryProjection','billProjectionState','chartBill','chartKwh','chartEur']){const e=byId(id);if(!e.hidden&&e.getClientRects().length&&(e.scrollWidth>e.clientWidth+2||e.scrollHeight>e.clientHeight+2))throw Error(id+' clipped '+e.scrollWidth+'/'+e.clientWidth+' '+e.scrollHeight+'/'+e.clientHeight);}"+
                "if(byId('billProjectionControls').hidden||byId('billProjectionToggle').checked!=="+projected+")throw Error('Shared toggle state');"+
                "if(byId('summaryProjectedKwh').textContent!=='≈ '+decimal(calculateCurrentBillProjection(new Date()).kwh,1)+' kWh')throw Error('Summary kWh mismatch');"+
                (projected?"if(!document.querySelector('.history-bar.projected .history-projection-extra'))throw Error('Missing projection overlay');":"if(document.querySelector('.history-bar.projected'))throw Error('Forecast visible while disabled');")+
                "const toggle=byId('billProjectionControls').getBoundingClientRect(),tabs=byId('chartBill').parentElement.getBoundingClientRect();if(toggle.right>tabs.left+1&&toggle.top<tabs.bottom&&tabs.top<toggle.bottom)throw Error('Projection switch overlaps tabs');"+
                "const r=document.querySelector('.plot-frame').getBoundingClientRect(),geometry=JSON.stringify([r.y+scrollY,r.height]);if(!window.compatGeometry)window.compatGeometry=geometry;else if(geometry!==window.compatGeometry)throw Error('Plot height changed '+geometry+' vs '+window.compatGeometry);");
            passed(stage);
            if(projected&&"kwh".equals(mode))screenshot(orientation+"-kwh-projection");
        }
        evaluate("delete window.compatGeometry;showView('consumption');setBillProjection(true);if(byId('total2').textContent!==byId('summaryProjection').textContent)throw Error('Consumption total differs: '+byId('total2').textContent+' vs '+byId('summaryProjection').textContent+' renderer='+typeof renderConsumptionDetails+' history='+historyRows.length+' forecast='+JSON.stringify(calculateCurrentBillProjection(new Date())));if(document.body.scrollWidth>innerWidth+2)throw Error('Consumption overflow');showView('rates');if(document.body.scrollWidth>innerWidth+2)throw Error('Tariff overflow');showView('settings');if(document.querySelectorAll('#settingsMenu>.settings-entry').length!==2||document.body.scrollWidth>innerWidth+2)throw Error('Settings menu');openSettingsPanel('widget');if(byId('compactWidgetCost').hidden||document.body.scrollWidth>innerWidth+2)throw Error('Widget settings');closeSettingsPanel();");
        passed(orientation+" consumption/rates/settings/widget settings");
    }
    private void account(Activity activity,String orientation)throws Exception{
        stage=orientation+" native account";evaluate("showView('settings');byId('openAccountSettings').click()");Thread.sleep(500);
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("accountDialog");field.setAccessible(true);EnelAccountDialog[] dialog=new EnelAccountDialog[1];
        main(()->{try{dialog[0]=(EnelAccountDialog)field.get(activity);}catch(Exception e){throw new AssertionError(e);}if(dialog[0]==null||!dialog[0].isShowing())throw new AssertionError("Account form did not open");View root=dialog[0].getWindow().getDecorView();android.widget.Spinner provider=root.findViewWithTag("account-provider");if(provider.getCount()!=1)throw new AssertionError("Provider list");if(!( ((android.widget.EditText)root.findViewWithTag("account-password")).getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod))throw new AssertionError("Password not masked");if(root.findViewWithTag("account-scroll").getHeight()<60)throw new AssertionError("Account viewport collapsed");});
        screenshot(orientation+"-account");
        if("portrait".equals(orientation)){
            main(()->{View root=dialog[0].getWindow().getDecorView();View user=root.findViewWithTag("account-user");user.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(user,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);if(Build.VERSION.SDK_INT>=30&&root.getWindowInsetsController()!=null)root.getWindowInsetsController().show(android.view.WindowInsets.Type.ime());});Thread.sleep(1400);
            main(()->{View root=dialog[0].getWindow().getDecorView(),user=root.findViewWithTag("account-user");android.graphics.Rect rect=new android.graphics.Rect();if(!user.getGlobalVisibleRect(rect)||rect.height()<user.getHeight()-2)throw new AssertionError("Focused native field clipped with keyboard: "+rect+" height="+user.getHeight());});screenshot("portrait-account-keyboard");
        }
        main(()->{View root=dialog[0].getWindow().getDecorView();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);dialog[0].dismiss();});Thread.sleep(350);passed(stage);
    }
    void run(){
        Bundle result=new Bundle();
        try{
            report.put("api",Build.VERSION.SDK_INT);report.put("fontScale",instrumentation.getTargetContext().getResources().getConfiguration().fontScale);
            if(Build.VERSION.SDK_INT>=26){android.content.pm.PackageInfo provider=WebView.getCurrentWebViewPackage();if(provider!=null)report.put("webView",provider.versionName);}
            Activity activity=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->web=findWeb(activity.findViewById(android.R.id.content)));if(web==null)throw new AssertionError("Missing WebView");
            JSONArray consoleErrors=new JSONArray();
            main(()->{web.setWebChromeClient(new android.webkit.WebChromeClient(){@Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message){if(message.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR)consoleErrors.put(message.sourceId()+":"+message.lineNumber()+" "+message.message());return true;}});web.reload();});report.put("consoleErrors",consoleErrors);
            boolean ready=false;Throwable last=null;
            for(int attempt=0;attempt<40&&!ready;attempt++){try{evaluate("if(document.readyState!=='complete'||typeof setMonths!=='function'||typeof renderConsumptionDetails!=='function'||typeof openSettingsPanel!=='function'||typeof sizeAppChrome!=='function')throw Error('App not ready: '+document.readyState+' core='+typeof setMonths+' consumption='+typeof renderConsumptionDetails+' settings='+typeof openSettingsPanel+' responsive='+typeof sizeAppChrome)");ready=true;}catch(Throwable pending){last=pending;Thread.sleep(250);}}
            if(!ready)throw new AssertionError("Complete app scripts did not initialize",last);
            Thread.sleep(300);
            evaluate("if(typeof setBillProjection!=='function'||typeof projectedMonthlyRows!=='function')throw Error('WebView did not load app scripts');setMonths(2,(new Date().getMonth()+11)%12+1);completeBillOffer();const now=new Date(),fixtures=[];for(let offset=-1;offset<=0;offset++){const date=new Date(now.getFullYear(),now.getMonth()+offset,1),limit=offset===0?now.getDate()-1:new Date(now.getFullYear(),now.getMonth(),0).getDate(),daily=[];for(let day=1;day<=limit;day++)daily.push({day,kwh:8,hourSamples:24,complete:true});fixtures.push({year:date.getFullYear(),month:date.getMonth()+1,kwh:daily.length*8,daily,available:daily.length>0,partial:offset===0,lastMeasuredDay:limit});}window.onHistoryNative(fixtures);window.onEnelConsumption({kwh:fixtures.reduce((sum,row)=>sum+row.kwh,0),months,anchor:billingAnchor,monthly:fixtures.map(row=>({...row,days:row.daily.length,expectedDays:new Date(row.year,row.month,0).getDate(),hourSamples:row.daily.length*24,future:false}))});");
            Thread.sleep(1000);instrumentation.getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);Thread.sleep(500);webScreens("portrait");account(activity,"portrait");
            instrumentation.getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);Thread.sleep(800);webScreens("landscape");account(activity,"landscape");
            widgets(activity);report.put("passed",true);writeReport();result.putString("stream","PASS: compatibility API "+Build.VERSION.SDK_INT+" checks="+checks.length());instrumentation.finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            try{report.put("passed",false);report.put("stage",stage);report.put("failure",failure.toString()+" cause="+failure.getCause());writeReport();screenshot("failure");}catch(Exception ignored){}
            result.putString("stream","FAIL: compatibility "+stage+": "+failure);instrumentation.finish(Activity.RESULT_CANCELED,result);
        }finally{try{instrumentation.getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);}catch(Exception ignored){}}
    }
}
