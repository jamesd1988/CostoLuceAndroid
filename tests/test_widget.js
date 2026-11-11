'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(code);
function make(tag='div'){
  const x={tag,children:[],style:{},className:'',checked:false,value:'',textContent:'',href:'',_inner:''};
  x.appendChild=c=>x.children.push(c);
  x.addEventListener=()=>{};
  x.setAttribute=()=>{};
  x.classList={toggle:()=>true};
  Object.defineProperty(x,'innerHTML',{get(){return this._inner},set(v){this._inner=v;this.children=v.includes('labeltext')?[make(),make()]:[]}});
  return x;
}
const nodes={};const byId=id=>nodes[id]||(nodes[id]=make());
byId('kwh').value='479';byId('resident').checked=true;
const ctx={
  document:{getElementById:byId,createElement:make},
  location:{hash:''},window:{},
  localStorage:{getItem:()=>null,setItem:()=>{}},
  Number,Math,Intl,Date,Object,String,JSON,isFinite,encodeURIComponent,console
};
vm.createContext(ctx);vm.runInContext(code,ctx);
assert.strictEqual(ctx.prepareWidgetSave(),true,'Initial setup must allow tariff-only configuration');
assert.match(byId('widgetSave').href,/^costoluce:\/\/widget-config\?months=2/);
assert.doesNotMatch(byId('widgetSave').href,/kwh=479/,'Never use initial example consumption');
assert.strictEqual(new URL(byId('widgetSave').href).searchParams.get('rates')!==null,true);
byId('kwh').value='238';
ctx.markManualInput();
assert.strictEqual(ctx.prepareWidgetSave(),true);
assert.match(byId('widgetSave').href,/^costoluce:\/\/widget-save\?kwh=238\.000/);
assert.match(byId('widgetSave').href,/source=manuale/);
assert.match(byId('widgetSave').href,/months=2/);
assert.match(byId('widgetSave').href,/euro=/);
const savedUrl=new URL(byId('widgetSave').href);
assert(savedUrl.searchParams.has('rates'),'Tariffe non incluse per il widget');
assert(savedUrl.searchParams.has('resident'),'Residenza non inclusa per il widget');
assert.strictEqual(JSON.parse(savedUrl.searchParams.get('rates')).energy,0.135);
ctx.window.onWidgetRestored({kwh:123,months:1,source:'manuale',period:'ottobre 2026'});
assert.strictEqual(byId('kwh').value,'123');
assert.strictEqual(ctx.prepareWidgetSave(),true);
assert.match(byId('widgetSave').href,/months=1/);
ctx.setMonths(2);

const amount=Number(new URL(byId('widgetSave').href).searchParams.get('euro'));
assert(Number.isFinite(amount)&&amount>0);
ctx.applyImportedText('Totale 240 kWh');
assert.strictEqual(ctx.prepareWidgetSave(),true);
assert.match(byId('widgetSave').href,/source=file/);
ctx.window.onEnelConsumption({months:2,kwh:125.75,monthly:[
 {year:2026,month:9,kwh:50.25,days:30,expectedDays:30,hourSamples:720},
 {year:2026,month:10,kwh:75.50,days:31,expectedDays:31,hourSamples:744}
]});
assert.strictEqual(ctx.prepareWidgetSave(),true);
const url=new URL(byId('widgetSave').href);
assert.strictEqual(url.searchParams.get('source'),'enel');
assert.match(url.searchParams.get('period'),/settembre 2026/);
assert.strictEqual(url.searchParams.get('kwh'),'125.750');
ctx.setMonths(1);
assert.strictEqual(ctx.prepareWidgetSave(),true,'Changing Enel period allows tariff-only setup');
assert.match(byId('widgetSave').href,/^costoluce:\/\/widget-config\?/);
assert.doesNotMatch(byId('widgetSave').href,/kwh=/,'Stale consumption must not be carried into a new period');
ctx.window.onWidgetSaveStatus('Salvato correttamente',true);
assert.match(byId('widgetStatus').textContent,/Salvato correttamente/);
console.log('PASS: widget config-only first setup without demo kWh, real manual/import/Enel snapshots, period change and statuses');
