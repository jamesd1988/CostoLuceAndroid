'use strict';
const vm=require('vm'),fs=require('fs'),assert=require('assert'),path=require('path');
const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/portal_capture.js'),'utf8');
function makePage(controls, text, months=2){
  const clicks=[];
  const nodes=controls.map(label=>({
    innerText:label,textContent:label,tagName:'BUTTON',className:'',
    getAttribute:()=>null,closest:()=>null,getBoundingClientRect:()=>({width:50,height:20}),
    click(){clicks.push(label)}
  }));
  const panel={style:{},textContent:'',addEventListener(){}};
  const window={location:{href:'https://www.consumienergia.it/portaleConsumi/'},__costoLuceMonths:months,
    sessionStorage:{_map:{},getItem(k){return this._map[k]||null},setItem(k,v){this._map[k]=v}}};
  const document={body:{innerText:text,appendChild(){}},getElementById(id){return id==='costoluce-panel'?{style:{}}:panel},querySelectorAll(){return nodes}};
  const context={window,document,MutationObserver:class{observe(){}},setTimeout(){return 1},clearTimeout(){},console,isFinite,Date,URL};
  vm.createContext(context);
  vm.runInContext(js,context);
  return {clicks,run:()=>window.__costoLuceNavigateForTests(text),extract:window.__costoLuceExtractTotalForTests};
}
let p=makePage(['I MIEI CONSUMI','Chi siamo'],'Benvenuto. Le tue forniture');
p.run();assert.deepStrictEqual(p.clicks,['I MIEI CONSUMI']);
p=makePage(['I MIEI CONSUMI'],'Entra con SPID. I MIEI CONSUMI');
p.run();assert.deepStrictEqual(p.clicks,[]);
p=makePage(['Ultimi 2 mesi'],'I miei consumi. Dati elettrici',2);
p.run();assert.deepStrictEqual(p.clicks,['Ultimi 2 mesi']);
p=makePage(['Ultimi 2 mesi'],'I miei consumi. Dati elettrici',1);
p.run();assert.deepStrictEqual(p.clicks,[]);
p=makePage(['Esci','Entra con SPID','Accetta consenso'],'Benvenuto');
p.run();assert.deepStrictEqual(p.clicks,[]);
p=makePage([],'Consumi\n852,6 kWh\nConsumi nel periodo');
assert.strictEqual(p.extract('Consumi\n852,6 kWh\nConsumi nel periodo'),852.6);
assert.strictEqual(p.extract('Consumi\nConsumi nel periodo\n852,6 kWh'),852.6);
assert.strictEqual(p.extract('Gas\nConsumo totale\n100 Smc'),null);
console.log('Test navigazione ed estrazione: 8 verifiche superate');
