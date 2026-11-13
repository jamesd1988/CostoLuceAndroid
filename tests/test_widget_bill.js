'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(code);
assert.doesNotMatch(html,/id="widgetSave"/,
  'No separate Save widget button: configuration must be automatic');

function make(tag='div'){
  const x={tag,children:[],style:{},className:'',checked:false,value:'',textContent:'',href:'',_inner:'',handlers:{}};
  x.appendChild=c=>x.children.push(c);
  x.addEventListener=(event,callback)=>x.handlers[event]=callback;
  x.setAttribute=()=>{};
  x.classList={toggle:()=>true};
  Object.defineProperty(x,'innerHTML',{get(){return this._inner},set(v){this._inner=v;this.children=v.includes('labeltext')?[make(),make()]:[]}});
  return x;
}
const saved=new Map();
const nodes={};const byId=id=>nodes[id]||(nodes[id]=make());
byId('kwh').value='479';byId('resident').checked=true;
const timers=new Map();let timerId=0;
const mockWindow={location:{href:''}};
const ctx={
  document:{getElementById:byId,createElement:make},
  location:{hash:''},window:mockWindow,
  localStorage:{getItem:key=>saved.get(key)??null,setItem:(key,value)=>saved.set(key,value),removeItem:key=>saved.delete(key)},
  Number,Math,Intl,Date,Object,String,JSON,isFinite,encodeURIComponent,console,
  setTimeout:(fn)=>{const id=++timerId;timers.set(id,fn);return id},
  clearTimeout:(id)=>timers.delete(id)
};
const flush=()=>{
  for(let i=0;i<20&&timers.size;i++){
    const queued=[...timers.values()];timers.clear();
    for(const callback of queued)callback();
  }
  assert.strictEqual(timers.size,0,'No infinite autosync loop');
};
vm.createContext(ctx);vm.runInContext(code,ctx);



vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/offer.js','utf8'),ctx);ctx.setChartMode('bill');const before=ctx.recalc();
assert.equal(byId('historyCostScope').hidden,false);assert.equal(byId('billPricingNotice').hidden,false);assert(ctx.completeBillOffer());assert.equal(byId('billPricingNotice').hidden,true);assert(Math.abs(ctx.recalc()-before-43.87229)<1e-7,'Both fixed offer charges and VAT contribute to the total');
assert(ctx.completeBillOffer());assert.equal(vm.runInContext('customKeys.length',ctx),5,'Completing a bill cannot duplicate fixed costs');
assert.equal(vm.runInContext('customKeys.length',ctx),5);
assert.equal(JSON.parse(saved.get('costoluce_rates_v1'))._custom.filter(r=>r.type==='fixed').length,5,'All fixed charges saved for the native widget');
// The screenshot reported only variable charges: all missing fixed charges must enter each month.
vm.runInContext("tariffs.dispatch=.025043;tariffs.balancing=.001042598;tariffs.asos=.031515;historyRows=[{year:2026,month:8,kwh:177.9,available:true},{year:2026,month:9,kwh:160.2,available:true}]",ctx);
const bill=ctx.billingHistory(new Date(2026,8,13)).find(r=>r.month===8&&r.year===2026);
assert.equal(bill.value,127.56);assert(Math.abs(bill.fixed-43.87229)<1e-7);assert(Math.abs(bill.variable+bill.fixed-127.56194328218)<1e-7);
ctx.renderBillBreakdown([bill]);assert.match(byId('billBreakdown').children[1].textContent,/variabile.*fisso/);
// Existing ARERA prices and renamed stable IDs must survive a completion.
vm.runInContext("tariffs.custom_arera_rete_potenza=8;rateMeta.custom_arera_rete_potenza.name='Potenza personale'",ctx);
assert(ctx.completeBillOffer());assert.equal(vm.runInContext('tariffs.custom_arera_rete_potenza',ctx),8);assert.equal(vm.runInContext('customKeys.length',ctx),5);
ctx.removeRate('custom_arera_rete_fissa');assert.equal(byId('billPricingNotice').hidden,false,'Missing network fees are flagged too');assert(ctx.completeBillOffer());
assert.equal(vm.runInContext('customKeys.length',ctx),5);
// A conflict must block all insertions, including the three network rows.
ctx.resetRates();ctx.openNewRate();byId('rateName').value='Rete quota potenza';byId('rateAmount').value='0.02';assert(ctx.saveRateEditor());
const conflictState=vm.runInContext('JSON.stringify(serializedRates())',ctx);assert.equal(ctx.completeBillOffer(),false);assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx),conflictState);
const provider=fs.readFileSync(__dirname+'/../app/src/main/java/com/costoluce/importatore/CostoLuceWidgetProvider.java','utf8');
assert(provider.includes('R.id.widget_unit_bill'));assert(provider.includes('saveChartMode(context,mode)'));assert(provider.includes('BillHistory.rows(archive(context)'));assert(provider.includes('PendingIntent.getBroadcast(context,2100+i,unit'));
const xml=fs.readFileSync(__dirname+'/../app/src/main/res/layout/costoluce_widget.xml','utf8');assert(xml.includes('widget_unit_bill'));assert(xml.includes('canone TV escluso'));
console.log('PASS: complete electricity scope, fixed fees including VAT, duplicate-safe shortcut and native bill broadcast wiring');

const activity=fs.readFileSync(__dirname+'/../app/src/main/java/com/costoluce/importatore/MainActivity.java','utf8');
const job=fs.readFileSync(__dirname+'/../app/src/main/java/com/costoluce/importatore/WidgetUpdateJobService.java','utf8');
for(const source of [activity,job]){assert(source.includes('InvoiceDocuments.read(this,pdf)'));assert(source.includes('InvoiceStore.accept(this,result.invoiceOutcome)'));assert(source.includes('InvoiceStore.forPod(this,credentials.pod)'));}
assert(!html.includes('Download automatico delle fatture non ancora disponibile'));
assert(!html.includes('href="costoluce://invoice-import"'),'No manual PDF upload in the user flow');

const allocationCases=JSON.parse(fs.readFileSync(__dirname+'/../app/src/test/resources/invoice-allocation.json','utf8'));
for(const test of allocationCases){
 const invoice={from:test.from,to:test.to,electricityCents:test.total};if(test.fixed!==undefined)invoice.fixedGrossCents=test.fixed;
 const first=new Date(test.from+'T12:00:00'),archive=test.usage.map((kwh,i)=>{const d=new Date(first.getFullYear(),first.getMonth()+i,1);return {year:d.getFullYear(),month:d.getMonth()+1,kwh,available:kwh!==null,partial:Boolean(test.partial&&i===1)}});
 const rows=ctx.splitInvoiceMonths(invoice,archive);assert.deepEqual(Array.from(rows,r=>r.allocatedCents),test.expected,test.name);if(rows.length)assert.equal(rows.reduce((s,r)=>s+r.allocatedCents,0),test.total);
}
console.log('PASS: monthly invoice attribution, fixed shares, exact cents, missing/partial/zero data, quarters and year boundaries');
