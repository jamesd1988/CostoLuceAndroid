// Cross-language fixtures use the actual app tariff engine and projection.js as the reference.
'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),projection=require('../app/src/main/assets/projection.js');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function element(){const e={children:[],style:{},value:'',checked:false,textContent:'',classList:{toggle:()=>{}},setAttribute:()=>{},addEventListener:()=>{}};e.appendChild=c=>e.children.push(c);Object.defineProperty(e,'innerHTML',{get(){return ''},set(v){this.children=v.includes('labeltext')?[element(),element()]:[]}});return e;}
const nodes={},get=id=>nodes[id]||(nodes[id]=element()),store=new Map();get('kwh').value='0';
const ctx={window:{location:{}},location:{hash:''},document:{getElementById:get,createElement:element},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},Date,Intl,Number,Object,Math,String,JSON,isFinite,console};vm.createContext(ctx);vm.runInContext(code,ctx);

ctx.window.BillProjection=projection;vm.runInContext(fs.readFileSync('app/src/main/assets/projection-ui.js','utf8'),ctx);
const make=(year,month,days,partial)=>({year,month,available:true,partial,lastMeasuredDay:days,kwh:days*8,daily:Array.from({length:days},(_,i)=>({day:i+1,kwh:8,complete:true,hourSamples:24}))});
for(const scenario of [{count:1,anchor:11,date:'2026-11-20'},{count:2,anchor:10,date:'2026-11-20'},{count:3,anchor:11,date:'2026-11-20'},{count:3,anchor:12,date:'2027-01-20'}]){
 ctx.scenario=scenario;const today=new Date(scenario.date+'T12:00:00');ctx.today=today;
 vm.runInContext('setMonths(scenario.count,scenario.anchor);billProjectionEnabled=true',ctx);
 const period=vm.runInContext('selectedPeriod(months,today)',ctx),rows=[];
 for(const m of period){const start=new Date(m.year,m.month-1,1);if(start>today)continue;rows.push(make(m.year,m.month,start.getMonth()===today.getMonth()&&start.getFullYear()===today.getFullYear()?today.getDate()-1:new Date(m.year,m.month,0).getDate(),start.getMonth()===today.getMonth()&&start.getFullYear()===today.getFullYear()));}
 ctx.rows=rows;vm.runInContext('historyRows=rows',ctx);const forecast=ctx.calculateCurrentBillProjection(today);assert(forecast.available,forecast.reason);
 for(const kwhMode of [true,false]){
  ctx.kwhMode=kwhMode;const original=vm.runInContext('kwhMode?monthlyHistory(today):monthlyEuroHistory(today)',ctx),snapshot=JSON.stringify(original),result=ctx.projectedMonthlyRows(original,today,kwhMode);
  assert.equal(JSON.stringify(original),snapshot,'Projection mutated the measured source');
  for(const m of forecast.monthly){const target=result.find(r=>r.year===m.year&&r.month===m.month),source=original.find(r=>r.year===m.year&&r.month===m.month);if(m.estimatedDays){assert(target.projection);assert(target.available);assert(Math.abs((kwhMode?target.kwh:target.value)-(kwhMode?m.kwh:ctx.monthlyCost(m.kwh)))<.02);assert.equal(target.actualValue,source&&source.available?(kwhMode?source.kwh:source.value):null);}else assert(!target.projection,'Concluded month became a forecast');}
  if(kwhMode){const total=period.reduce((s,m)=>s+result.find(r=>r.year===m.year&&r.month===m.month).kwh,0);assert(Math.abs(total-forecast.kwh)<1e-8);}else{const cents=period.reduce((s,m)=>s+Math.round(result.find(r=>r.year===m.year&&r.month===m.month).value*100),0);assert.equal(cents,Math.round(forecast.value*100),'Displayed monthly euro amounts must sum to full forecast');}
 }
 const summary=ctx.renderSummaryProjection(today);assert.equal(get('summaryProjectedKwh').textContent,vm.runInContext("'≈ '+decimal("+summary.kwh+",1)+' kWh'",ctx));
 vm.runInContext('billProjectionEnabled=false',ctx);const measured=ctx.monthlyHistory(today);assert.equal(ctx.projectedMonthlyRows(measured,today,true),measured);
}
ctx.today=new Date(2026,10,20,12);ctx.rows=[make(2026,10,31,false),make(2026,11,19,true)];vm.runInContext('setMonths(2,10);historyRows=rows;billProjectionEnabled=true;historyRows[1].daily.forEach(d=>d.complete=d.day>=14)',ctx);
const missing=ctx.projectedMonthlyRows(ctx.monthlyHistory(ctx.today),ctx.today,true);assert.equal(missing[missing.length-1].available,false);assert.equal(missing[missing.length-2].available,true);assert.equal(get('kwh').value,'0');
console.log('PASS shared forecasts: kWh/euro overlays, monthly/bimonthly/quarterly periods, future months, year boundary, observed preservation, summary kWh and missing-week behavior');
