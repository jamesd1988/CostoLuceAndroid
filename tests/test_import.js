const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)[1];
const script=js.replace(/  byId\('kwh'\)\.addEventListener\('input',recalc\);[\s\S]*$/,'');
const cx={console,Intl,localStorage:{getItem:()=>null},document:{getElementById:()=>({})},window:{},location:{hash:''}};
vm.createContext(cx);vm.runInContext(script,cx);
const sample=[
 ['479',479],
 ['Totale 479 kWh',479],
 ['Totale kWh;479,5',479.5],
 ['Data;Consumo (kWh)\n2026-07-01;100,5\n2026-07-02;250,25',350.75],
 ['Mese;F1 (kWh);F2 (kWh);F3 (kWh)\n06-2026;10,5;21;3\n07-2026;11;25;2',72.5],
 ['Mese;Consumo (kWh)\nGiugno;238\nLuglio;241\nTotale;479',479],
 ['Mese,Consumo (kWh)\n2026-06,"1.234,56"\n2026-07,245',1479.56],
];
for(let [raw,expected] of sample){const result=vm.runInContext('parseConsumption('+JSON.stringify(raw)+')',cx);assert(Math.abs(result.kwh-expected)<1e-8,JSON.stringify({raw,expected,result}));console.log('PASS',expected,result.origin);}
for (let bad of ['Gas naturale;Smc\nLuglio;25','POD;lettura contatore\nABC;24345','Consumo elettrico kW;23']) {
 let threw=false;
 try {vm.runInContext('parseConsumption('+JSON.stringify(bad)+')',cx)} catch(e){threw=true}
 assert(threw,'Should refuse '+bad);
}
console.log('PASS invalid CSV refusal');
