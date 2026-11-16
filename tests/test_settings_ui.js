// Cross-language fixtures use the actual app tariff engine and projection.js as the reference.
'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),projection=require('../app/src/main/assets/projection.js');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8'),code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function element(){const e={children:[],style:{},value:'',checked:false,textContent:'',classList:{toggle:()=>{}},setAttribute:()=>{},addEventListener:()=>{}};e.appendChild=c=>e.children.push(c);Object.defineProperty(e,'innerHTML',{get(){return ''},set(v){this.children=v.includes('labeltext')?[element(),element()]:[]}});return e;}
const nodes={},get=id=>nodes[id]||(nodes[id]=element()),store=new Map();get('kwh').value='0';
const ctx={window:{location:{}},location:{hash:''},document:{getElementById:get,createElement:element},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},Date,Intl,Number,Object,Math,String,JSON,isFinite,console};vm.createContext(ctx);vm.runInContext(code,ctx);

vm.runInContext(fs.readFileSync('app/src/main/assets/settings-ui.js','utf8'),ctx);
assert.equal(get('settingsMenu').hidden,false);assert.equal(get('settingsWidget').hidden,true);assert.equal(get('settingsAccountTools').hidden,true);
assert.equal(ctx.openSettingsPanel('unsupported'),false);
ctx.openSettingsPanel('widget');assert.equal(get('settingsMenu').hidden,true);assert.equal(get('settingsWidget').hidden,false);assert.equal(get('settingsAccountTools').hidden,true);assert.equal(get('settingsBack').hidden,false);
vm.runInContext("showView('settings');openSettingsPanel('widget');setCompactWidgetCost('projection')",ctx);
assert.equal(store.get('costoluce_compact_widget_cost_v1'),'projection');assert.equal(ctx.closeSettingsPanel(),true);assert.equal(ctx.closeSettingsPanel(),false);
ctx.openSettingsPanel('account');assert.equal(get('settingsAccountTools').hidden,false);assert.equal(get('settingsWidget').hidden,true);
vm.runInContext("showView('home')",ctx);assert.equal(ctx.closeSettingsPanel(),false);
vm.runInContext("showView('settings')",ctx);assert.equal(get('settingsMenu').hidden,false);assert.equal(get('settingsAccountTools').hidden,true);assert.equal(get('compactWidgetCost').value,'projection');
console.log('PASS settings navigation: two panels, back handling, entry reset, preserved widget preference and account tools isolation');
