'use strict';
const assert=require('assert'),fs=require('fs'),path=require('path');
const app=p=>fs.readFileSync(path.join(__dirname,'../app/src/main',p),'utf8');
const manifest=app('AndroidManifest.xml');
const provider=app('java/com/costoluce/importatore/CostoLuceWidgetProvider.java');

const widgetLayout=app('res/layout/costoluce_widget.xml');
const jobService=app('java/com/costoluce/importatore/WidgetUpdateJobService.java');
assert.match(manifest,/uses-permission android:name="android\.permission\.ACCESS_NETWORK_STATE"/,
  'ACCESS_NETWORK_STATE is mandatory for Android 14+ network-constrained JobScheduler');
assert.doesNotMatch(manifest,/android:name="\.WidgetRefreshActivity"/,
  'A click on the refresh button must never launch an activity');
assert.match(manifest,/android:name="\.WidgetUpdateJobService"/);
assert.match(provider,/setOnClickPendingIntent\(R\.id\.widget_refresh,\s*refreshPending\)/);
assert.match(provider,/setOnClickPendingIntent\(R\.id\.widget_status,\s*refreshPending\)/);
assert.match(provider,/PendingIntent\.getBroadcast\(context,\s*1002,\s*refreshIntent/,
  'Refresh tap must dispatch a broadcast, not start an activity');
assert.match(provider,/new Intent\(context,\s*CostoLuceWidgetProvider\.class\)/);
assert.match(provider,/FLAG_RECEIVER_FOREGROUND/);
assert.doesNotMatch(provider,/PendingIntent\.getActivity\(context,\s*1002/,
  'Do not use getActivity for refresh');
assert.doesNotMatch(provider,/setOnClickPendingIntent\(R\.id\.widget_root/,
 'Parent root must not steal the refresh-button click');

assert.match(provider,/Tasto premuto alle/,'Every tap must show an immediate visible acknowledgement');
assert.match(jobService,/EnelConnector\.fetch\(credentials,\s*config\.months,\s*phase\s*->/,
 'Background sync must report its stage');
assert.match(widgetLayout,/android:id="@\+id\/widget_refresh"/);
assert.match(widgetLayout,/android:layout_width="48dp"/);
console.log('PASS: refresh via foreground broadcast (no Activity), background JobScheduler, clear status and network permission');
