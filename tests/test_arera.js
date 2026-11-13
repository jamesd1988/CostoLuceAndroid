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



vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/arera.js','utf8'),ctx);
const sample={transport:.0119,equalization:.00283,asos:.031515,arim:.001638,networkAnnual:23.04,powerAnnual:23.52,qualityPowerAnnual:.1988,oneriAnnual:0};
const before=vm.runInContext('JSON.stringify(serializedRates())',ctx),oldDispatch=vm.runInContext('tariffs.dispatch',ctx);
const data={months:{'2026-08':{resident:sample,nonresident:{...sample,oneriAnnual:95.0916}}},checkedAt:1794528000000,error:''};
ctx.window.onAreraCatalog(data);assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx),before,'Checking must never write rates');
byId('areraMonth').value='2026-08';byId('areraResident').value='resident';byId('areraPower').value='3';
vm.runInContext('rateMeta.asos.name="Oneri rinnovabili"',ctx);
ctx.previewArera();assert.equal(byId('areraPreview').hidden,false);assert.equal(ctx.applyArera(),true);
assert.equal(vm.runInContext('tariffs.dispatch',ctx),oldDispatch,'Do not import the non-matching dispacciamento');
assert.equal(vm.runInContext('rateMeta.asos.name',ctx),'Oneri rinnovabili');
assert(Math.abs(vm.runInContext('tariffs.custom_arera_rete_potenza',ctx)-5.88)<1e-9);
assert.equal(vm.runInContext('customKeys.length',ctx),3);ctx.previewArera();ctx.applyArera();assert.equal(vm.runInContext('customKeys.length',ctx),3,'Repeated imports must not duplicate fixed rows');
ctx.openRateEditor('asos');byId('rateAmount').value='0.04';assert(ctx.saveRateEditor());ctx.window.onAreraCatalog(data);
assert.equal(vm.runInContext('tariffs.asos',ctx),.04,'A later automatic check must preserve pencil edits');
ctx.previewArera();byId('areraPower').value='4.5';assert.equal(ctx.applyArera(),false,'Changed settings require a new preview');
assert(Math.abs(vm.runInContext('tariffs.custom_arera_rete_potenza',ctx)-5.88)<1e-9);
assert.equal(ctx.applyArera(),true);assert(Math.abs(vm.runInContext('tariffs.custom_arera_rete_potenza',ctx)-8.82)<1e-9);
byId('areraResident').value='nonresident';ctx.previewArera();ctx.applyArera();assert.equal(vm.runInContext('customKeys.length',ctx),4);
assert(Math.abs(vm.runInContext('tariffs.custom_arera_oneri_fissi',ctx)-95.0916/12)<1e-9);
byId('areraResident').value='resident';ctx.previewArera();ctx.applyArera();assert.equal(vm.runInContext('tariffs.custom_arera_oneri_fissi',ctx),0,'Changing to resident must clear the previously imported nonresident charge');
byId('areraMonth').value='2026-12';const stored=vm.runInContext('JSON.stringify(serializedRates())',ctx);ctx.previewArera();assert.equal(ctx.applyArera(),false);assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx),stored);
byId('areraMonth').value='2026-08';byId('areraPower').value='NaN';ctx.previewArera();assert.equal(ctx.applyArera(),false);
const protectedRates=vm.runInContext('JSON.stringify(serializedRates())',ctx);ctx.window.onAreraCatalog({...data,error:'Offline'});assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx),protectedRates);
console.log('PASS: ARERA preview/import, power, profiles, immutable checks, manual edits, duplicate prevention, unavailable periods and stale-preview protection');
