'use strict';
const assert=require('assert'),fs=require('fs');
const daily=require('../app/src/main/assets/daily.js');
const today=new Date(2026,10,14,12);
const row={year:2026,month:11,kwh:12.34,available:true,partial:true,daily:[{day:1,kwh:0,hourSamples:24,complete:true},{day:2,kwh:8.12,hourSamples:24,complete:true},{day:3,kwh:4.22,hourSamples:12,complete:false}]};
let result=daily.build([row],2026,11,today);
assert(result.valid);assert.equal(result.days.length,30);assert.equal(result.days[0].status,'complete');assert.equal(result.days[0].kwh,0,'Measured zero must remain an actual value');assert.equal(result.days[2].status,'partial');assert.equal(result.days[3].status,'missing');assert.equal(result.days[3].kwh,null);assert.equal(result.days[14].status,'future');assert.equal(result.availableDays,3);assert(Math.abs(result.recordedKwh-12.34)<1e-9);assert.equal(result.reason,'');
result=daily.build([{year:2026,month:10,kwh:218.1,available:true,partial:false}],2026,10,today);assert.equal(result.monthKwh,218.1);assert.equal(result.availableDays,0);assert(result.days.every(d=>d.kwh===null),'Never divide an old monthly total into invented daily readings');assert(result.reason.includes('Aggiorna'));
result=daily.build([{...row,kwh:20}],2026,11,today);assert(result.reason.includes('non coincidono'));assert.equal(result.days[1].kwh,8.12,'Keep individually validated observations when sums differ');
result=daily.build([{...row,daily:[...row.daily,row.daily[0]]}],2026,11,today);assert(result.reason.includes('non valido'));assert.equal(result.availableDays,0);
result=daily.build([row,{...row}],2026,11,today);assert(result.reason.includes('duplicati'));assert.equal(result.availableDays,0);
for(const invalid of [NaN,-1,'8']){result=daily.build([{...row,daily:[{day:1,kwh:invalid,hourSamples:24,complete:true}]}],2026,11,today);assert.equal(result.availableDays,0);}
result=daily.build([{...row,daily:[{day:14,kwh:1,hourSamples:24,complete:true}]}],2026,11,today);assert.equal(result.days[13].status,'partial','The ongoing day cannot be marked definitive');
result=daily.build([{...row,daily:[{day:15,kwh:1,hourSamples:24,complete:true}]}],2026,11,today);assert.equal(result.availableDays,0,'Future readings are not displayed as observations');
assert.equal(daily.build([],2028,2,new Date(2028,2,1)).days.length,29);assert.equal(daily.build([],2027,2,new Date(2027,2,1)).days.length,28);
assert(!daily.build([],2026,12,today).valid);assert(!daily.build([],2026,13,today).valid);
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),ui=fs.readFileSync(__dirname+'/../app/src/main/assets/daily-ui.js','utf8');
assert(html.includes("const drilldown=!bill&&typeof openMonthDaily==='function'"));assert(html.includes('id="page-daily"'));assert(ui.includes('costoluce://enel-month?year='));
console.log('PASS: daily month model, true zero versus absent/future data, partial coverage, duplicate/invalid data, monthly-only migration, leap years and monthly drill-down wiring');
