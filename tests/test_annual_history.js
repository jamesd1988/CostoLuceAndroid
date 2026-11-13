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
assert(vm.runInContext('monthlyHistory().length>=13',ctx));
const events=[];Object.defineProperty(mockWindow.location,'href',{set(value){events.push(value)},get(){return events[events.length-1]||''},configurable:true});
ctx.setChartMode('bill');ctx.setChartMode('bill');flush();assert.equal(events.filter(x=>x==='costoluce://enel-history').length,1,'Entering bills requests annual data once per grouping/session');
assert.equal(byId('billPricingNotice').hidden,false,'Warn when fixed offer charges are not recognized');assert(ctx.completeBillOffer());assert.equal(byId('billPricingNotice').hidden,true,'Adding fixed costs clears warning');
ctx.setChartMode('eur');ctx.setChartMode('bill');flush();assert.equal(events.filter(x=>x==='costoluce://enel-history').length,1);
ctx.setMonths(3,8);ctx.showView('home');flush();assert.equal(events.filter(x=>x==='costoluce://enel-history').length,2,'A new grouping may require a different historical boundary');
ctx.window.onHistoryNative([{year:2024,month:1,kwh:10,available:true,partial:false}]);assert.equal(vm.runInContext('monthlyHistory().length',ctx),24,'Archive is bounded at two years');
const originalBills=ctx.billingHistory;
ctx.billingHistory=()=>[{year:2026,month:4,label:'apr–mag',years:'2026',available:false},{year:2026,month:6,label:'giu–lug',years:'2026',available:false},{year:2026,month:8,label:'ago–set',years:'2026',available:true,partial:false,value:99.395},{year:2026,month:10,label:'ott–nov',years:'2026',available:true,partial:true,value:111.429}];
ctx.renderHistory();assert.match(byId('historyTotal').textContent,/210,83/,'Total must equal 99.40 + 111.43 displayed, not 210.82 from unrounded values');
assert.match(byId('historyChart').children[2].children[0].children[0].textContent,/99,40/);assert.match(byId('historyChart').children[3].children[0].children[0].textContent,/111,43/);
ctx.billingHistory=originalBills;
const htmlSource=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');assert.match(htmlSource,/overflow-x:auto/);assert.match(htmlSource,/id="historyViewport"/);assert(htmlSource.includes('scrollHistory(-1)'));
console.log('PASS: annual history request throttling, cost completeness notice, two-year bound, scroll controls and displayed-cent total regression');

