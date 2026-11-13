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



vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/offer.js','utf8'),ctx);
const baseline=ctx.recalc();assert(ctx.completeOfferFixed());
assert.equal(vm.runInContext('customKeys.length',ctx),2);assert(Math.abs(ctx.recalc()-baseline-2*(12+.092250)*1.1)<1e-8);
assert(ctx.completeOfferFixed());assert.equal(vm.runInContext('customKeys.length',ctx),2,'No duplicates on repeated completion');
ctx.openRateEditor('custom_offer_commercialization');byId('rateAmount').value='15';byId('rateName').value='Canone personale';assert(ctx.saveRateEditor());
assert(ctx.completeOfferFixed());assert.equal(vm.runInContext('tariffs.custom_offer_commercialization',ctx),15);assert.equal(vm.runInContext('rateMeta.custom_offer_commercialization.name',ctx),'Canone personale');
const state=JSON.parse(saved.get('costoluce_rates_v1'));assert.equal(state._custom.length,2);assert.equal(state._custom[0].type,'fixed');
const ctx2={...ctx,window:{location:{href:''}}};vm.createContext(ctx2);vm.runInContext(code,ctx2);vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/offer.js','utf8'),ctx2);assert(ctx2.completeOfferFixed());assert.equal(vm.runInContext('customKeys.length',ctx2),2,'Persistence prevents duplicates after reopening');
ctx2.resetRates();ctx2.openNewRate();byId('rateName').value='Commercializzazione vendita';ctx2.setDraftType('fixed');byId('rateAmount').value='14';assert(ctx2.saveRateEditor());assert(ctx2.completeOfferFixed());assert.equal(vm.runInContext('customKeys.length',ctx2),2,'Recognize manually added rows');assert.equal(vm.runInContext('tariffs[customKeys[0]]',ctx2),14);
ctx2.resetRates();ctx2.openNewRate();byId('rateName').value='Commercializzazione vendita';byId('rateAmount').value='0.02';assert(ctx2.saveRateEditor());const before=vm.runInContext('JSON.stringify(serializedRates())',ctx2);assert.equal(ctx2.completeOfferFixed(),false,'Conflicting variable row needs explicit review');assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx2),before,'No partial insertion on conflict');
ctx2.resetRates();for(let i=0;i<29;i++){ctx2.openNewRate();byId('rateName').value='Voce '+i;byId('rateAmount').value='1';assert(ctx2.saveRateEditor())}const full=vm.runInContext('JSON.stringify(serializedRates())',ctx2);assert.equal(ctx2.completeOfferFixed(),false);assert.equal(vm.runInContext('JSON.stringify(serializedRates())',ctx2),full,'Capacity check is atomic');
console.log('PASS: missing offer fixed costs, VAT, no duplicates, pencil edits, recognized existing rows, persistence, conflict and capacity checks');
