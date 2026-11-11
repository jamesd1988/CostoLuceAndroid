'use strict';
const assert=require('assert'),fs=require('fs'),path=require('path');
const app=p=>fs.readFileSync(path.join(__dirname,'../app/src/main',p),'utf8');
const manifest=app('AndroidManifest.xml');
const provider=app('java/com/costoluce/importatore/CostoLuceWidgetProvider.java');
const refreshActivity=app('java/com/costoluce/importatore/WidgetRefreshActivity.java');
const widgetLayout=app('res/layout/costoluce_widget.xml');
const jobService=app('java/com/costoluce/importatore/WidgetUpdateJobService.java');
assert.match(manifest,/uses-permission android:name="android\.permission\.ACCESS_NETWORK_STATE"/,
  'ACCESS_NETWORK_STATE is mandatory for Android 14+ network-constrained JobScheduler');
assert.match(manifest,/android:name="\.WidgetRefreshActivity"/);
assert.match(manifest,/android:name="\.WidgetUpdateJobService"/);
assert.match(provider,/setOnClickPendingIntent\(R\.id\.widget_refresh,\s*refreshPending\)/);
assert.match(provider,/setOnClickPendingIntent\(R\.id\.widget_status,\s*refreshPending\)/);
assert.match(provider,/PendingIntent\.getActivity\(context,\s*1002,\s*refreshIntent/);
assert.doesNotMatch(provider,/setOnClickPendingIntent\(R\.id\.widget_root/,
 'Parent root must not steal the refresh-button click');
assert.match(refreshActivity,/CostoLuceWidgetProvider\.requestDataRefresh\(this\)/);
assert.match(refreshActivity,/finish\(\)/);
assert.match(provider,/Tasto premuto alle/,'Every tap must show an immediate visible acknowledgement');
assert.match(jobService,/EnelConnector\.fetch\(credentials,\s*config\.months,\s*phase\s*->/,
 'Background sync must report its stage');
assert.match(widgetLayout,/android:id="@\+id\/widget_refresh"/);
assert.match(widgetLayout,/android:layout_width="48dp"/);
console.log('PASS: Android14 permission, clickable refresh, Activity launch, prompt status, phase feedback');
