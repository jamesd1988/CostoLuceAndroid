const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(code,{filename:'costoluce.js'});
function fakeElement(tag='div'){
  const e={tag,children:[],style:{},className:'',value:'',checked:false,textContent:'',attributes:{},_inner:''};
  e.appendChild=(c)=>{e.children.push(c)};
  e.addEventListener=()=>{};
  e.setAttribute=(k,v)=>{e.attributes[k]=v};
  e.classList={toggle:(c,v)=>typeof v==='boolean'?v:!e.className.includes(c)};
  Object.defineProperty(e,'innerHTML',{get(){return this._inner},set(v){this._inner=v;this.children=(v.includes('labeltext')?[fakeElement('div'),fakeElement('div')]:[])}});
  return e;
}
class FixedDate extends Date{constructor(...args){super(...(args.length?args:['2026-11-12T12:00:00+01:00']))}}
const nodes={};
const getId=id=>nodes[id]??(nodes[id]=fakeElement());
getId('kwh').value='479';getId('resident').checked=true;
const saved=new Map();const localStorage={getItem:k=>saved.get(k)??null,setItem:(k,v)=>saved.set(k,v)};
const ctx={window:{location:{}},location:{hash:''},document:{getElementById:getId,createElement:fakeElement},localStorage,Date:FixedDate,Intl,Number,Object,Math,String,JSON,isFinite,console};
vm.createContext(ctx);vm.runInContext(code,ctx);

assert.strictEqual(getId('historyChart').children.length,13);
assert.ok(getId('historyChart').children.every(c=>/assente|Non disponibile/.test(c.children[2].textContent)));
ctx.window.onHistoryNative([
 {year:2026,month:8,kwh:0,available:false},
 {year:2026,month:9,kwh:0,available:true,partial:false},
 {year:2026,month:10,kwh:238,available:true,partial:false},
 {year:2026,month:11,kwh:241,available:true,partial:true}
]);
ctx.setDisplayUnit('kwh');
let columns=getId('historyChart').children.slice(-4);
assert.strictEqual(columns[0].children[0].children[0].textContent,'—');
assert.strictEqual(columns[0].children[0].children.length,1,'Missing month has no bar');
assert.strictEqual(columns[1].children[0].children[0].textContent,'0,0');
assert.strictEqual(columns[1].children[2].textContent,'Completo','Measured zero remains available');
assert.strictEqual(columns[3].children[2].textContent,'Parziale');
assert.strictEqual(getId('total').textContent,'479,0 kWh');
assert.strictEqual(saved.get('costoluce_display_unit_v1'),'kwh');
assert.strictEqual(ctx.window.location.href,'costoluce://display-unit?unit=kwh');
ctx.setDisplayUnit('eur');
assert.match(getId('total').textContent,/119,71/);
assert.match(getId('historyChart').children.slice(-4)[2].children[0].children[0].textContent,/59,42/);
assert.strictEqual(saved.get('costoluce_display_unit_v1'),'eur');
console.log('PASS: four real-data slots, missing versus measured zero, partial current month, persistent euro/kWh toggle and monthly excise');

let openedPanel='';ctx.openTariffPanel=kind=>{openedPanel=kind};ctx.showView('period');assert.strictEqual(getId('page-rates').hidden,false);assert.strictEqual(getId('page-home').hidden,true);assert.equal(openedPanel,'billing');assert(!html.includes('id="nav-period"'));assert(!html.includes('id="page-period"'));
ctx.showView('home');assert.strictEqual(getId('page-rates').hidden,true);assert.strictEqual(getId('page-home').hidden,false);
console.log('PASS: functional Home and Period navigation');
