// Cross-language fixtures use the actual app tariff engine and projection.js as the reference.
'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),projection=require('../app/src/main/assets/projection.js');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function element(){const e={children:[],style:{},value:'',checked:false,textContent:'',classList:{toggle:()=>{}},setAttribute:()=>{},addEventListener:()=>{}};e.appendChild=c=>e.children.push(c);Object.defineProperty(e,'innerHTML',{get(){return ''},set(v){this.children=v.includes('labeltext')?[element(),element()]:[]}});return e;}
const nodes={},get=id=>nodes[id]||(nodes[id]=element()),store=new Map();get('kwh').value='0';
const ctx={window:{location:{}},location:{hash:''},document:{getElementById:get,createElement:element},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},Date,Intl,Number,Object,Math,String,JSON,isFinite,console};vm.createContext(ctx);vm.runInContext(code,ctx);

ctx.window.BillProjection=projection;
vm.runInContext(fs.readFileSync('app/src/main/assets/projection-ui.js','utf8'),ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/consumption-ui.js','utf8'),ctx);
ctx.today=new Date(2026,10,14,12);
ctx.rows=[{year:2026,month:9,kwh:160,available:true,partial:false},{year:2026,month:10,kwh:218.1,available:true,partial:false},{year:2026,month:11,kwh:104,available:true,partial:true,lastMeasuredDay:13,daily:Array.from({length:13},(_,i)=>({day:i+1,kwh:8,hourSamples:24,complete:true}))}];
for(const cycle of [1,2,3]){
 ctx.cycle=cycle;
 vm.runInContext("setMonths(cycle,cycle===3?9:10);historyRows=rows;byId('resident').checked=true;billProjectionEnabled=true",ctx);
 const model=vm.runInContext('consumptionDetailModel(today)',ctx),forecast=vm.runInContext('calculateCurrentBillProjection(today)',ctx);
 assert(model.available,model.reason);assert.equal(model.value,forecast.value);assert(Math.abs(model.parts.total-model.value)<.005);
 const expectedTax=vm.runInContext('calculateCurrentBillProjection(today).monthly.reduce((s,m)=>s+taxableMonthly(m.kwh),0)',ctx);
 assert.equal(model.tax,expectedTax,'Excise must be monthly');
 const sum=model.parts.items.reduce((s,item)=>s+item[1],0);assert(Math.abs(sum-model.value)<.005,'Breakdown must sum to Home forecast including fixed fees and VAT');
}
vm.runInContext("setMonths(2,10);historyRows=rows;lastEnelImport=true;lastEnelMonthly=rows.slice(1).map(r=>({...r,future:false}));byId('kwh').value='322.1';billProjectionEnabled=false;recalc()",ctx);
const actual=vm.runInContext('consumptionDetailModel(today)',ctx);assert(actual.available);assert(!actual.projected);assert.equal(actual.value,vm.runInContext('lastCalculatedCost',ctx));
vm.runInContext('billProjectionEnabled=true',ctx);const forecast=vm.runInContext('consumptionDetailModel(today)',ctx);assert(forecast.value>actual.value);assert.equal(get('kwh').value,'322.1');assert.equal(vm.runInContext('lastCalculatedCost',ctx),actual.value,'Forecast must not overwrite measured widget input');
vm.runInContext('historyRows[2].daily.forEach(d=>d.complete=d.day>=8)',ctx);assert(!vm.runInContext('consumptionDetailModel(today).available',ctx));
assert(html.indexOf('id="enelCheck"')>html.indexOf('id="page-settings"'));
const consumption=html.slice(html.indexOf('id="page-consumption"'),html.indexOf('id="page-rates"'));assert(!consumption.includes('id="kwh"'));assert(!consumption.includes('id="monthlyRows"'));assert(consumption.includes('consumptionProjectionToggle'));
console.log('PASS consumption details: monthly/bimonthly/quarterly forecast parity, monthly excise, all fixed/variable/VAT rows, actual input preserved, insufficient week and relocated controls');
