'use strict';
const vm = require('vm'), fs=require('fs'), assert=require('assert'), path=require('path');
let timerCall;
const context = { window: {location:{href:''}}, document: {body:{innerText:'', appendChild(){}}, getElementById:(id)=> id==='costoluce-panel' ? null : ({textContent:'',style:{},addEventListener(){}}), createElement:()=>({style:{},innerHTML:'',addEventListener(){}})}, MutationObserver: class {observe(){}}, setTimeout:(fn)=>{timerCall=fn;return 1;}, clearTimeout() {}, console: console, isFinite: isFinite };
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/portal_capture.js'),'utf8'),context);
const scan=context.window.__costoLuceExtractTotalForTests;
const cases=[
  ['I MIEI CONSUMI\nConsumo totale\n479,00 kWh',479],
  ['Consumi energia elettrica\nTotale consumi: 1.279,5 kWh\nPeriodo marzo–ottobre',1279.5],
  ['Consumi\nEnergia complessiva: 245 kWh',245],
  ['Consumi\nF1 90 kWh\nF2 80 kWh\nF3 75 kWh',null],
  ['Forniture gas\nConsumo totale\n123 Smc',null],
  ['Lettura contatore\nConsumo totale 123 kWh\nConsumo totale 456 kWh',null],
  ['Consumo totale: 479 kWh\nPrezzo energia 0,15 €/kWh',479],
  ['Consumo totale: 0 kWh',null],
];
for(const [txt,result] of cases){const got=scan(txt); assert.strictEqual(got,result,JSON.stringify(txt)+ ' got '+got);}
console.log('Test estrazione portale: '+cases.length+' casi superati');
