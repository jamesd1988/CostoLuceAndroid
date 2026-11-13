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
assert.equal(byId('historyCostScope').hidden,false);assert.equal(byId('billPricingNotice').hidden,false);assert(ctx.completeBillOffer());assert.equal(byId('billPricingNotice').hidden,true);assert(Math.abs(ctx.recalc()-before-26.60295)<1e-7,'Both fixed offer charges and VAT contribute to the total');
assert(ctx.completeBillOffer());assert.equal(vm.runInContext('customKeys.length',ctx),2,'Completing a bill cannot duplicate fixed costs');
const provider=fs.readFileSync(__dirname+'/../app/src/main/java/com/costoluce/importatore/CostoLuceWidgetProvider.java','utf8');
assert(provider.includes('R.id.widget_unit_bill'));assert(provider.includes('saveChartMode(context,mode)'));assert(provider.includes('BillHistory.rows(archive(context)'));assert(provider.includes('PendingIntent.getBroadcast(context,2100+i,unit'));
const xml=fs.readFileSync(__dirname+'/../app/src/main/res/layout/costoluce_widget.xml','utf8');assert(xml.includes('widget_unit_bill'));assert(xml.includes('canone TV escluso'));
console.log('PASS: complete electricity scope, fixed fees including VAT, duplicate-safe shortcut and native bill broadcast wiring');
