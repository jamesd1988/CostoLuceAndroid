'use strict';
const vm=require('vm'),fs=require('fs'),assert=require('assert');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const source=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function node(tag='div'){
 const e={tagName:tag.toUpperCase(),children:[],style:{},className:'',value:'',checked:false,textContent:'',href:''};
 e.appendChild=c=>e.children.push(c);
 e.addEventListener=()=>{};
 e.setAttribute=()=>{};
 e.classList={toggle:()=>{}};
 Object.defineProperty(e,'innerHTML',{get(){return this._inner||''},set(v){this._inner=v;this.children=(v.includes('labeltext')?[node('div'),node('div')]:[])}});
 return e;
}
const ids={}; const el=id=>ids[id]||(ids[id]=node());
el('kwh').value='479';el('resident').checked=true;
class FixedDate extends Date {
 constructor(...args){if(!args.length)super('2026-11-11T12:00:00+01:00');else super(...args);}
}
const ctx={window:{},document:{getElementById:el,createElement:node},location:{hash:''},
 localStorage:{getItem:()=>null,setItem:()=>{}},Date:FixedDate,Intl,Number,Math,String,Object,JSON,isFinite,console};
vm.createContext(ctx);vm.runInContext(source,ctx);
assert.match(el('requestedPeriod').textContent,/settembre 2026.*ottobre 2026/);
assert.strictEqual(el('monthlyRows').children.length,2);
assert.strictEqual(el('monthlyRows').children[0].children[1].textContent,'—');
ctx.setMonths(1);
assert.match(el('requestedPeriod').textContent,/ottobre 2026/);
assert.doesNotMatch(el('requestedPeriod').textContent,/settembre/);
assert.strictEqual(el('enelSync').href,'costoluce://enel-sync?months=1');
ctx.setMonths(2);
ctx.window.onEnelConsumption({months:2,kwh:125.75,monthly:[
 {year:2026,month:9,kwh:50.25,days:30,expectedDays:30,hourSamples:720},
 {year:2026,month:10,kwh:75.5,days:20,expectedDays:31,hourSamples:480}
]});
assert.strictEqual(el('monthlyRows').children.length,2);
assert.strictEqual(el('kwh').value,'125.75');
assert.match(el('monthlyRows').children[0].children[0].children[0].textContent,/settembre 2026/);
assert.match(el('monthlyRows').children[0].children[1].textContent,/50,250 kWh/);
assert.match(el('monthlyRows').children[1].children[1].textContent,/75,500 kWh/);
assert.match(el('monthlyRows').children[1].children[0].children[1].textContent,/20\/31/);
assert.match(el('monthlyWarning').textContent,/parziali|mancanti/);
ctx.window.onEnelConsumption({months:2,kwh:50.25,monthly:[
 {year:2026,month:9,kwh:50.25,days:1,expectedDays:30,hourSamples:24},
 {year:2026,month:10,kwh:0,days:0,expectedDays:31,hourSamples:0}
]});
assert.match(el('monthlyRows').children[1].children[1].textContent,/Non disponibile/);
assert.strictEqual(el('kwh').value,'50.25');
const before=el('kwh').value;
ctx.window.onEnelConsumption({months:2,kwh:900,monthly:[
 {year:2026,month:9,kwh:50.25,days:1,expectedDays:30},
 {year:2026,month:10,kwh:0,days:0,expectedDays:31}
]});
assert.strictEqual(el('kwh').value,before);
assert.match(el('importStatus').textContent,/non corrisponde/);
ctx.setMonths(1);
assert.strictEqual(el('kwh').value,'');
assert.strictEqual(el('monthlyRows').children.length,1);
console.log('PASS: periodi settembre/ottobre 2026, selezione 1 mese, riepilogo, parziali, assenti, controllo somma, reset');
