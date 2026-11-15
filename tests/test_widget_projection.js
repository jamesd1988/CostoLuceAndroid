// Cross-language fixtures use the actual app tariff engine and projection.js as the reference.
'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),projection=require('../app/src/main/assets/projection.js');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function element(){const e={children:[],style:{},value:'',checked:false,textContent:'',classList:{toggle:()=>{}},setAttribute:()=>{},addEventListener:()=>{}};e.appendChild=c=>e.children.push(c);Object.defineProperty(e,'innerHTML',{get(){return ''},set(v){this.children=v.includes('labeltext')?[element(),element()]:[]}});return e;}
const nodes={},get=id=>nodes[id]||(nodes[id]=element()),store=new Map();get('kwh').value='0';
const ctx={window:{location:{}},location:{hash:''},document:{getElementById:get,createElement:element},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},Date,Intl,Number,Object,Math,String,JSON,isFinite,console};vm.createContext(ctx);vm.runInContext(code,ctx);
const rates={energy:.135,losses:10,dispatch:.021115752,balancing:.000786979,transport:.0119,equalization:.00283,asos:.031515,arim:.001638,excise:.0227,vat:10,_custom:[{id:'custom_fixed',name:'Fisso',type:'fixed',value:10},{id:'custom_variable',name:'Servizio',type:'variable',value:.008}]};
const clone=x=>JSON.parse(JSON.stringify(x)),records=(days,kwh=8)=>days.map(day=>({day,kwh,hourSamples:24,complete:true})),range=(from,to)=>Array.from({length:to-from+1},(_,i)=>i+from);
const row=(year,month,daily,partial=true)=>({year,month,kwh:daily.reduce((s,d)=>s+d.kwh,0),available:true,partial,daily});
const october={year:2026,month:10,kwh:218.1,available:true,partial:false},november=row(2026,11,records(range(1,13)));
const cases=[];function add(name,changes={}){cases.push({name,today:'2026-11-14',months:2,anchor:10,rates:clone(rates),resident:true,rows:clone([october,november]),...changes});}
add('resident-bimestre');add('nonresident-bimestre',{resident:false});add('monthly',{months:1});
add('quarter',{months:3,anchor:9,rows:[{year:2026,month:9,kwh:177.9,available:true,partial:false},october,november]});
add('quarter-year-crossing',{today:'2027-01-14',months:3,anchor:12,rows:[{year:2026,month:12,kwh:200,available:true,partial:false},row(2027,1,records(range(1,13)))]});
add('leap-February',{today:'2028-02-14',months:1,rows:[row(2028,2,records(range(1,13)))]});
add('first-day-previous-week',{today:'2026-10-01',rows:[row(2026,9,records(range(24,30)),true)]});
add('five-days',{rows:[october,row(2026,11,records(range(9,13)))]});
add('six-days',{rows:[october,row(2026,11,records(range(8,13)))]});
add('four-days',{rows:[october,row(2026,11,records(range(10,13)))]});
add('no-daily-detail',{rows:[october,{...november,daily:[]}]});
add('stale',{rows:[october,row(2026,11,records(range(1,5)))]});
add('real-zero',{rows:[october,row(2026,11,records(range(1,13),0))]});
add('missing-closed-month',{rows:[november]});
const historic=row(2026,10,records(range(1,31)),false);historic.daily[0].complete=false;
add('closed-month-coverage-flag',{rows:[historic,november]});
const missing=clone(historic);missing.daily.pop();add('closed-month-missing-day',{rows:[missing,november]});
const mismatch=clone(november);mismatch.kwh++;add('monthly-mismatch',{rows:[october,mismatch]});
const duplicate=clone(november);duplicate.daily.push({...duplicate.daily[0]});add('duplicate-day',{rows:[october,duplicate]});
add('duplicate-month',{rows:[october,november,november]});
const future=clone(november);future.daily.push({day:15,kwh:8,hourSamples:24,complete:true});future.kwh+=8;add('future-day',{rows:[october,future]});
const partial=clone(november);partial.daily.push({day:14,kwh:20,hourSamples:10,complete:false});partial.kwh+=20;add('partial-today-preserved',{rows:[october,partial]});
const noVat=clone(rates);noVat._meta={vat:{name:'IVA',type:'percent',enabled:false}};add('manual-disabled-VAT',{rates:noVat});
function expected(c){
 ctx.fixtureRates=c.rates;ctx.fixtureResident=c.resident;
 vm.runInContext('for(const key of Object.keys(initial))tariffs[key]=fixtureRates[key];loadRateMeta(fixtureRates._meta);loadCustomRates(fixtureRates._custom);byId("resident").checked=fixtureResident',ctx);
 const today=new Date(c.today+'T12:00:00'),phase=((today.getMonth()+1-c.anchor)%c.months+c.months)%c.months,start=today.getFullYear()*12+today.getMonth()-phase;
 const period=Array.from({length:c.months},(_,i)=>({year:Math.floor((start+i)/12),month:(start+i)%12+1}));
 const result=projection.calculate({rows:c.rows,period,today,price:k=>{ctx.fixtureKwh=k;return vm.runInContext('calculateCosts(fixtureKwh,1,byId("resident").checked?taxableMonthly(fixtureKwh):fixtureKwh,1)',ctx)}});
 return result.available?{available:true,value:result.value,kwh:result.kwh,observedKwh:result.observedKwh,average:result.average,sampleDays:result.sampleDays,from:result.from,to:result.to}:{available:false};
}
const path=__dirname+'/../app/src/test/resources/widget-projection-cases.json';
if(process.argv.includes('--write'))fs.writeFileSync(path,JSON.stringify(cases.map(c=>({...c,expected:expected(c)})))+'\n');
const saved=JSON.parse(fs.readFileSync(path,'utf8'));for(const c of saved)assert.deepStrictEqual(expected(c),c.expected,c.name);
// The preference is independent of graph modes, Home totals, prices and its forecast toggle.
const before=vm.runInContext('JSON.stringify([serializedRates(),chartMode,months,billingAnchor])',ctx);
ctx.setCompactWidgetCost('projection');assert.equal(store.get('costoluce_compact_widget_cost_v1'),'projection');assert.equal(ctx.window.location.href,'costoluce://widget-cost?mode=projection');
assert.equal(vm.runInContext('JSON.stringify([serializedRates(),chartMode,months,billingAnchor])',ctx),before);
ctx.window.onWidgetNativeState({compactCost:'current'},null);assert.equal(get('compactWidgetCost').value,'current');assert.equal(store.get('costoluce_compact_widget_cost_v1'),'current');
assert(html.includes('id="compactWidgetCost"'));console.log('PASS:',saved.length,'app/native projection reference cases and persistent compact widget cost selection');
