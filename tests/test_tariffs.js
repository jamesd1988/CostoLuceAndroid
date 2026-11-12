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
  localStorage:{getItem:key=>saved.get(key)??null,setItem:(key,value)=>saved.set(key,value)},
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

const initial=vm.runInContext('({...initial})',ctx);const baseline=ctx.recalc();
assert.strictEqual(byId('rateFields').children.length,10);
ctx.openRateEditor('energy');assert.strictEqual(byId('rateName').disabled,true);
byId('rateName').value='Tentativo';assert.strictEqual(ctx.saveRateEditor(),true);
assert.strictEqual(vm.runInContext('rateMeta.energy.name',ctx),'Energia €/kWh');
ctx.openRateEditor('dispatch','fixed');assert.strictEqual(byId('rateAmount').value,'','Never reinterpret a per-kWh number as a monthly fee');
assert.strictEqual(ctx.saveRateEditor(),false);byId('rateName').value='Quota fissa mensile';byId('rateAmount').value='8,00';assert.strictEqual(ctx.saveRateEditor(),true);
const total=ctx.recalc();assert(Math.abs(total-(baseline+(16-479*initial.dispatch)*1.1))<1e-8);
assert.match(byId('costParts').textContent,/Fisso 17,60/);
assert.strictEqual(JSON.parse(saved.get('costoluce_rates_v1'))._meta.dispatch.name,'Quota fissa mensile');
ctx.setRateFilter('fixed');assert.strictEqual(byId('rateFields').children.length,1);
ctx.setRateFilter('variable');assert.strictEqual(byId('rateFields').children.length,7);
ctx.setRateFilter('all');assert.strictEqual(byId('rateFields').children.length,10);
const serialized=vm.runInContext('JSON.stringify(serializedRates())',ctx);
ctx.openRateEditor('dispatch','variable');assert.strictEqual(byId('rateAmount').value,'');ctx.closeRateEditor();assert.strictEqual(vm.runInContext('JSON.stringify(serializedRates())',ctx),serialized,'Cancel must retain the old rate and mode');
ctx.openRateEditor('dispatch');byId('rateAmount').value='-1';assert.strictEqual(ctx.saveRateEditor(),false);byId('rateAmount').value='1001';assert.strictEqual(ctx.saveRateEditor(),false);byId('rateAmount').value='8';byId('rateName').value=' ';assert.strictEqual(ctx.saveRateEditor(),false);ctx.closeRateEditor();
ctx.openRateEditor('vat');assert.strictEqual(byId('rateTypeSection').hidden,true);ctx.setDraftType('fixed');assert.strictEqual(vm.runInContext('rateDraft.type',ctx),'percent');byId('rateAmount').value='101';assert.strictEqual(ctx.saveRateEditor(),false);ctx.closeRateEditor();
const withTwo=ctx.calculateCosts(479,2,218,2).total;const withOne=ctx.calculateCosts(479,2,218,1).total;assert(Math.abs(withTwo-withOne-8.8)<1e-8,'Future billing month must not accrue a monthly fee');
// Relaunch with the persisted v1 numeric prices plus new optional metadata.
const ctx2={...ctx,window:{location:{href:''}}};vm.createContext(ctx2);vm.runInContext(code,ctx2);assert.strictEqual(vm.runInContext('rateMeta.dispatch.name',ctx2),'Quota fissa mensile');assert.strictEqual(vm.runInContext('rateMeta.dispatch.type',ctx2),'fixed');assert(Math.abs(ctx2.recalc()-total)<1e-8);
ctx2.resetRates();assert(Math.abs(ctx2.recalc()-baseline)<1e-8);assert.strictEqual(vm.runInContext('rateMeta.dispatch.type',ctx2),'variable');
console.log('PASS: locked Energy name, editable labels, filter, explicit unit conversion, cancel, validation, persistence, fixed VAT and future months');
