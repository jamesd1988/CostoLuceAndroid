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
const nodes={};const byId=id=>nodes[id]||(nodes[id]=make());
byId('kwh').value='479';byId('resident').checked=true;
const timers=new Map();let timerId=0;
const mockWindow={location:{href:''}};
const ctx={
  document:{getElementById:byId,createElement:make},
  location:{hash:''},window:mockWindow,
  localStorage:{getItem:()=>null,setItem:()=>{}},
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
assert.match(ctx.buildWidgetSyncUrl(),/^costoluce:\/\/widget-config\?months=2/);
assert.doesNotMatch(ctx.buildWidgetSyncUrl(),/kwh=479/,'Never publish the initial demonstration');
flush();
assert.match(mockWindow.location.href,/^costoluce:\/\/widget-config\?/);
assert.strictEqual(new URL(mockWindow.location.href).searchParams.get('auto'),'1');
assert(new URL(mockWindow.location.href).searchParams.has('rates'));

byId('kwh').value='238';ctx.markManualInput();flush();
let url=new URL(mockWindow.location.href);
assert.strictEqual(url.host,'widget-save');
assert.strictEqual(url.searchParams.get('kwh'),'238.000');
assert.strictEqual(url.searchParams.get('source'),'manuale');

ctx.applyImportedText('Totale 240 kWh');flush();
url=new URL(mockWindow.location.href);
assert.strictEqual(url.searchParams.get('kwh'),'240.000');
assert.strictEqual(url.searchParams.get('source'),'file');

ctx.window.onEnelConsumption({months:2,kwh:125.75,monthly:[
  {year:2026,month:10,kwh:50.25,days:30,expectedDays:31,hourSamples:720},
  {year:2026,month:11,kwh:75.50,days:10,expectedDays:11,hourSamples:240}
]});flush();
url=new URL(mockWindow.location.href);
assert.strictEqual(url.searchParams.get('source'),'enel');
assert.match(url.searchParams.get('period'),/ottobre 2026.*novembre 2026/);
assert(url.searchParams.has('monthly'),'Monthly data must be included in native widget calculation');
assert.strictEqual(url.searchParams.get('kwh'),'125.750');

ctx.setMonths(1);flush();
url=new URL(mockWindow.location.href);
assert.strictEqual(url.host,'widget-config');
assert.strictEqual(url.searchParams.get('months'),'1');
assert.strictEqual(url.searchParams.get('kwh'),null,
  'Never reuse kWh from the previous period');

byId('resident').checked=false;byId('resident').handlers.change();flush();
url=new URL(mockWindow.location.href);
assert.strictEqual(url.searchParams.get('resident'),'0');

ctx.window.onWidgetNativeState({months:1,resident:false},{months:2,kwh:999,source:'enel',period:'2026'});
flush();
assert.notStrictEqual(byId('kwh').value,'999',
  'The previous month snapshot must not overwrite new-period settings');

ctx.window.onWidgetSaveStatus('Sincronizzato',true);
assert.match(byId('widgetStatus').textContent,/Sincronizzato/);

console.log('PASS: automatic widget configuration, real data updates, no demo consumption, period isolation and resident pricing');
