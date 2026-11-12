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


const original=vm.runInContext('JSON.stringify(serializedRates())',ctx),baseline=ctx.recalc();
assert.strictEqual(ctx.removeRate('energy'),false);ctx.openRateEditor('energy');assert.strictEqual(byId('rateDeleteButton').hidden,true);assert.strictEqual(ctx.requestDeleteRate(),false);ctx.closeRateEditor();
ctx.openNewRate();assert.strictEqual(byId('rateEditorTitle').textContent,'Nuova voce');assert.strictEqual(ctx.saveRateEditor(),false);ctx.closeRateEditor();assert.strictEqual(vm.runInContext('JSON.stringify(serializedRates())',ctx),original);
ctx.openNewRate();byId('rateName').value='Canone offerta';ctx.setDraftType('fixed');byId('rateAmount').value='8,00';assert.strictEqual(ctx.saveRateEditor(),true);
const fixedId=vm.runInContext('customKeys[0]',ctx);assert(Math.abs(ctx.recalc()-baseline-17.6)<1e-8);assert.strictEqual(byId('rateFields').children.length,11);
ctx.openNewRate();byId('rateName').value='Servizio a consumo';byId('rateAmount').value='0,02';assert.strictEqual(ctx.saveRateEditor(),true);const full=ctx.recalc();assert(Math.abs(full-baseline-17.6-479*.02*1.1)<1e-8);
ctx.openRateEditor('dispatch');assert.strictEqual(ctx.requestDeleteRate(),true);assert.strictEqual(ctx.recalc(),full,'Confirmation must precede removal');assert.strictEqual(ctx.confirmDeleteRate(),true);assert.strictEqual(vm.runInContext('rateMeta.dispatch.enabled',ctx),false);
assert.strictEqual(JSON.parse(saved.get('costoluce_rates_v1'))._meta.dispatch.enabled,false);assert(Math.abs(ctx.recalc()-full+479*vm.runInContext('initial.dispatch',ctx)*1.1)<1e-8);assert(ctx.undoRateDeletion());assert(Math.abs(ctx.recalc()-full)<1e-8);
assert(ctx.removeRate(fixedId));assert(Math.abs(ctx.recalc()-full+17.6)<1e-8);assert.strictEqual(JSON.parse(saved.get('costoluce_rates_v1'))._custom.length,1);
const ctx2={...ctx,window:{location:{href:''}}};vm.createContext(ctx2);vm.runInContext(code,ctx2);assert.strictEqual(vm.runInContext('customKeys.length',ctx2),1,'Deleted rows must stay deleted after relaunch');assert(ctx2.undoRateDeletion(),'Undo must survive relaunch');assert(Math.abs(ctx2.recalc()-full)<1e-8);assert.strictEqual(ctx2.undoRateDeletion(),false);
assert(ctx2.removeRate('vat'));assert(Math.abs(ctx2.recalc()-full/1.1)<1e-8);assert.match(byId('costParts').textContent,/IVA non applicata/);assert(ctx2.undoRateDeletion());assert(Math.abs(ctx2.recalc()-full)<1e-8);
const before=vm.runInContext('JSON.stringify(serializedRates())',ctx2);ctx2.requestResetRates();assert.strictEqual(byId('rateResetConfirm').hidden,false);ctx2.cancelResetRates();assert.strictEqual(vm.runInContext('JSON.stringify(serializedRates())',ctx2),before);
ctx2.resetRates();assert.strictEqual(vm.runInContext('customKeys.length',ctx2),0);assert.strictEqual(byId('rateFields').children.length,10);assert(Math.abs(ctx2.recalc()-baseline)<1e-8);
ctx2.loadCustomRates([{id:'custom_ok',name:'<b>Testo</b>',type:'fixed',value:4},{id:'custom_ok',name:'Duplicato',type:'variable',value:9},{id:'custom_bad',name:'Negativo',type:'fixed',value:-1}]);assert.strictEqual(vm.runInContext('customKeys.length',ctx2),1);assert.strictEqual(vm.runInContext('rateMeta.custom_ok.name',ctx2),'<b>Testo</b>');
console.log('PASS: create fixed/variable rows, delete with confirmation, protected Energy, exclusions in totals, persistent removal and undo, reset confirmation and validation');
