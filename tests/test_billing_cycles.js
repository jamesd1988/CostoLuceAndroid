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


assert(html.indexOf('id="billingTitle"')>html.indexOf('id="page-rates"'));assert(!html.includes('id="m2"'),'Grouping controls live in Tariffe');
getId('billingFrequency').value='3';ctx.billingDraftChanged(true);assert.equal(ctx.saveBillingSetup(),false,'An example is mandatory for a new grouping');
getId('billingAnchor').value='8';ctx.billingDraftChanged();assert.match(getId('billingExample').textContent,/agosto.*settembre.*ottobre/);assert(ctx.saveBillingSetup());
assert.equal(vm.runInContext('months',ctx),3);assert.equal(vm.runInContext('billingAnchor',ctx),8);assert.equal(saved.get('costoluce_months_v1'),'3');assert.equal(saved.get('costoluce_billing_anchor_v1'),'8');
assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(selectedPeriod(3,new Date(2027,0,5),12))',ctx)),[{year:2026,month:12},{year:2027,month:1},{year:2027,month:2}]);
ctx.setMonths(2,8);
ctx.window.onHistoryNative([{year:2026,month:8,kwh:178,available:true,partial:false},{year:2026,month:9,kwh:160,available:true,partial:false},{year:2026,month:10,kwh:200,available:true,partial:false},{year:2026,month:11,kwh:100,available:true,partial:true}]);
ctx.setChartMode('bill');let groups=JSON.parse(vm.runInContext('JSON.stringify(billingHistory())',ctx));
assert.equal(groups.length,7);groups=groups.slice(-4);assert.equal(groups[2].label,'ago–set');assert.equal(groups[2].available,false);assert.equal(groups[0].available,false);
ctx.window.onInvoicesNative([{id:'example',from:'2026-08-01',to:'2026-09-30',electricityCents:12756,payableCents:14556}]);
groups=ctx.billingHistory().slice(-4);assert.equal(groups[2].status,'issued');assert.equal(groups[2].value,127.56);
assert.equal(ctx.monthlyEuroHistory(new Date(2026,10,13)).find(r=>r.year===2026&&r.month===8).status,'allocated','A monthly attribution must be explicitly distinguished from an issued bill');vm.runInContext('tariffs.energy=.25',ctx);assert.equal(ctx.billingHistory().slice(-4)[2].value,127.56,'Issued invoices never change when tariffs change');
const expected=vm.runInContext('calculateCosts(300,2,taxableMonthly(200)+taxableMonthly(100),2).total',ctx);assert(Math.abs(ctx.billingHistory().slice(-1)[0].value-Math.round(expected*100)/100)<1e-8,'Only current bill is calculated');
assert.match(getId('historyTotal').textContent,/esclusi.*mancanti/);assert.match(getId('historyTotal').textContent,/parziale/);
ctx.setChartMode('kwh');assert.equal(getId('historyChart').children.slice(-4)[0].children[0].children[0].textContent,'178,0');ctx.setChartMode('eur');assert.equal(saved.get('costoluce_chart_mode_v1'),'eur');
ctx.setMonths(3,11);assert.equal(ctx.billingHistory().find(r=>r.status==='issued').value,127.56,'Changing grouping preserves actual invoices');ctx.setChartMode('bill');groups=JSON.parse(vm.runInContext('JSON.stringify(billingHistory())',ctx)).slice(-4);assert.equal(groups[3].label,'nov–gen');assert.equal(groups[3].years,'2026–2027');assert(groups[3].partial);assert(groups[3].available);assert(Math.abs(groups[3].value-Math.round(vm.runInContext('monthlyCost(100)',ctx)*100)/100)<1e-8,'Future months do not accrue consumption or fixed costs');
ctx.window.onInvoicesNative([]);ctx.window.onHistoryNative([]);assert.match(getId('historyTotal').textContent,/non disponibile/);assert(getId('historyChart').children.every(c=>c.children[0].children[0].textContent==='—'));
const url=ctx.buildWidgetSyncUrl();assert.match(url,/months=3/);assert.match(url,/anchor=11/);
console.log('PASS: Tariffe grouping examples, 1/2/3-month periods, year boundary, bill totals, monthly excise, missing/future data and three chart modes');
