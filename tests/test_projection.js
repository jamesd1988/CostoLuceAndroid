'use strict';
const assert=require('assert'),vm=require('vm'),fs=require('fs');
const projection=require('../app/src/main/assets/projection.js');
const price=k=>({total:k*.3+10,variable:k*.3,fixed:10});
const today=new Date(2026,10,14,12),period=[{year:2026,month:10},{year:2026,month:11}];
const daily=(days,kwh=8)=>days.map(day=>({day,kwh,hourSamples:24,complete:true}));
const row=(year,month,records,partial=true)=>({year,month,daily:records,kwh:records.reduce((s,d)=>s+d.kwh,0),available:true,partial});
const october={year:2026,month:10,kwh:200,available:true,partial:false};
function calc(records,options={}){return projection.calculate({rows:[october,row(2026,11,records)],period,today,price,...options});}
assert(!calc(daily([10,11,12,13])).available,'Four days must not produce a forecast');
let result=calc(daily([9,10,11,12,13]));assert(result.available);assert.equal(result.sampleDays,5);assert.equal(result.average,8);assert.equal(result.kwh,440);assert.equal(result.monthly[0].kwh,200);assert.equal(result.value,152);assert.equal(result.fixed,20);
result=calc(daily([7,8,9,10,11,12,13]));assert.equal(result.sampleDays,7);assert.equal(result.kwh,440);
assert(calc(daily([7,8,10,12,13])).available,'Five complete days inside seven calendar days are sufficient');
assert(!calc(daily([1,2,3,12,13])).available,'Five scattered days are not a recent week');
assert(!calc(daily([1,2,3,4,5])).available,'Stale readings need a refresh');
assert(!calc([...daily([9,10,11,12]),{day:13,kwh:4,hourSamples:12,complete:false}]).available,'Partial days do not count toward minimum');
assert(!calc([...daily([10,11,12,13]),{day:14,kwh:90,hourSamples:24,complete:true}]).available,'Today is never a completed reference day');
result=calc([...daily([7,8,9,10,11,12,13]),{day:14,kwh:20,hourSamples:2,complete:false}]);assert.equal(result.kwh,452,'Never reduce measured consumption when a partial day already exceeds the average');assert.equal(result.observedKwh,276);
result=calc(daily([7,8,9,10,11,12,13],0));assert(result.available);assert.equal(result.average,0);assert.equal(result.kwh,200);assert.equal(result.value,80,'Measured zero is valid and fixed fees remain');
assert(!calc([...daily([9,10,11,12,13]),...daily([13])]).available,'Duplicate daily dates rejected');
assert(!calc(daily([9,10,11,12,13]),{rows:[october,{year:2026,month:11,kwh:50,available:true,partial:true}]}).available,'Old monthly-only snapshots cannot invent a daily average');
assert(!calc(daily([9,10,11,12,13]),{rows:[{...october,partial:true},row(2026,11,daily([9,10,11,12,13]))]}).available,'Incomplete concluded months must be disclosed');
const records=daily([9,10,11,12,13]);assert(!calc(records,{rows:[october,{...row(2026,11,records),kwh:100}]}).available,'Monthly and daily amounts must reconcile');
// Regression for the user's October/November account: complete monthly coverage,
// but one October day is excluded from the daily reference sample (e.g. 24 bins on a 25-hour day).
const concluded=daily(Array.from({length:31},(_,i)=>i+1));concluded[24].complete=false;
const octoberDaily=row(2026,10,concluded,false);
const recovered=calc(records,{rows:[octoberDaily,row(2026,11,records)]});
assert(recovered.available,'A valid concluded monthly total must not be rejected by hourly reference-day flags');
assert.equal(recovered.monthly[0].kwh,octoberDaily.kwh);assert.equal(recovered.monthly[0].estimatedDays,0);assert.equal(recovered.sampleDays,5);
assert(!calc(daily([10,11,12,13]),{rows:[octoberDaily,row(2026,11,daily([10,11,12,13]))]}).available,'Historical days must not bypass the five recent-day threshold');
const mismatched=JSON.parse(JSON.stringify(octoberDaily));mismatched.kwh+=1;assert(!calc(records,{rows:[mismatched,row(2026,11,records)]}).available,'Keep the monthly/daily total reconciliation');
// First day of a new cycle: use real daily readings from the preceding month.
result=projection.calculate({rows:[row(2026,9,daily([24,25,26,27,28,29,30]),false)],period:[{year:2026,month:10},{year:2026,month:11}],today:new Date(2026,9,1,12),price});assert(result.available);assert.equal(result.observedKwh,0);assert.equal(result.kwh,61*8);assert.equal(result.sampleDays,7);
// Monthly, quarterly, leap-year and year-crossing windows use actual calendar lengths.
result=calc(daily([9,10,11,12,13]),{period:[{year:2026,month:11}]});assert.equal(result.kwh,240);assert.equal(result.fixed,10);
result=projection.calculate({rows:[row(2026,11,daily([24,25,26,27,28,29,30]),false)],period:[{year:2026,month:12},{year:2027,month:1},{year:2027,month:2}],today:new Date(2026,11,1,12),price});assert.equal(result.kwh,90*8);assert.equal(result.fixed,30);
result=projection.calculate({rows:[row(2028,1,daily([25,26,27,28,29,30,31]),false)],period:[{year:2028,month:2}],today:new Date(2028,1,1,12),price});assert.equal(result.kwh,29*8);
// Exercise the app's real tariff engine (monthly excise, VAT and fixed/custom rates).
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function element(){const x={children:[],style:{},value:'',checked:false,textContent:'',classList:{toggle:()=>{}},setAttribute:()=>{},addEventListener:()=>{}};x.appendChild=c=>x.children.push(c);Object.defineProperty(x,'innerHTML',{set(v){this.children=v.includes('labeltext')?[element(),element()]:[]},get(){return ''}});return x;}
const nodes={},get=id=>nodes[id]||(nodes[id]=element());get('resident').checked=true;get('kwh').value='0';
const store=new Map(),ctx={window:{location:{}},location:{hash:''},document:{getElementById:get,createElement:element},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},Date,Intl,Number,Object,Math,String,JSON,isFinite,console};vm.createContext(ctx);vm.runInContext(code,ctx);
vm.runInContext("customKeys.push('custom_test');tariffs.custom_test=12;rateMeta.custom_test={name:'Fisso',type:'fixed',enabled:true}",ctx);
const appPrice=k=>{ctx.testKwh=k;return vm.runInContext('calculateCosts(testKwh,1,taxableMonthly(testKwh),1)',ctx);};
result=calc(daily([9,10,11,12,13]),{price:appPrice});const expected=appPrice(200).total+appPrice(240).total;assert.equal(result.value,Math.round(expected*100)/100);assert(Math.abs(result.fixed-26.4)<1e-9);
// UI controls replace only the final bar and persist independently of prices and chart mode.
vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/projection.js','utf8'),ctx);vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/projection-ui.js','utf8'),ctx);
ctx.historyFixture=[october,row(2026,11,daily([9,10,11,12,13]))];vm.runInContext('historyRows=historyFixture',ctx);
const source=ctx.billingHistory(today),pricesBefore=vm.runInContext('JSON.stringify(serializedRates())',ctx);
ctx.setBillProjection(true);const projected=ctx.projectedBillingRows(source,today);assert.equal(projected.at(-1).projection,true);assert.equal(projected.at(-1).value,result.value);assert.equal(projected[0],source[0]);assert.equal(store.get('costoluce_bill_projection_v1'),'1');assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx),pricesBefore);
ctx.setBillProjection(false);assert.equal(ctx.projectedBillingRows(source,today),source);assert.equal(store.get('costoluce_bill_projection_v1'),'0');
console.log('PASS: 5/7 complete-day threshold, stale/missing/partial/zero data, monthly/bimonthly/quarterly forecasts, actual preservation, fixed fees/excise/VAT, exclusive graph and persistence');
