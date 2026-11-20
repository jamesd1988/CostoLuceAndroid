'use strict';
// A catalogue check only refreshes source data; importing is an explicit user action.
let areraCatalog={},areraCheckedAt=0,areraImport=null,areraDraft=null;
try{areraImport=JSON.parse(localStorage.getItem('costoluce_arera_import_v1')||'null')}catch(e){}
function areraMonthLabel(key){const [year,month]=key.split('-').map(Number);return italianMonth(year,month)}
function areraStatus(){
  const keys=Object.keys(areraCatalog).sort(),last=keys[keys.length-1];
  byId('areraCatalogStatus').textContent=areraCheckedAt?'Ultimo controllo riuscito: '+new Date(areraCheckedAt).toLocaleString('it-IT')+(last?' · Ultimo mese disponibile: '+areraMonthLabel(last):''):'Nessuna tabella ARERA scaricata.';
  if(areraImport){
    const edits=Object.keys(areraImport.values).filter(k=>!rateMeta[k]||!rateEnabled(k)||rateMeta[k].type!==areraImport.values[k].type||Math.abs(tariffs[k]-areraImport.values[k].value)>1e-10);
    byId('areraApplied').textContent='Importazione: '+areraMonthLabel(areraImport.period)+' · '+(areraImport.resident?'residente':'non residente')+' · '+decimal(areraImport.power,1)+' kW.'+(edits.length?' '+edits.length+' voci successivamente modificate o eliminate.':'');
  }else byId('areraApplied').textContent='Nessuna importazione effettuata.';
}
let areraCheckPending=false, areraSelectedMonth='';
byId('areraMonth').addEventListener('change',()=>{
  areraSelectedMonth=byId('areraMonth').value;
});
window.onAreraCatalog=function(payload,checked=false){
  if(!payload||!payload.months||typeof payload.months!=='object')return;
  // Restoring a cached catalogue can happen while a network request is pending.
  if(areraCheckPending&&!checked)return;
  const showComparison=areraCheckPending&&checked;
  areraCheckPending=false;
  areraCatalog=payload.months;
  areraCheckedAt=Number(payload.checkedAt)||0;
  const select=byId('areraMonth');
  select.innerHTML='';
  const today=new Date();
  const current=today.getFullYear()+'-'+String(today.getMonth()+1).padStart(2,'0');
  const keys=Object.keys(areraCatalog).filter(key=>/^\d{4}-(0[1-9]|1[0-2])$/.test(key)&&key<=current).sort().reverse();
  for(const key of keys){
    const option=document.createElement('option');
    option.value=key;
    option.textContent=areraMonthLabel(key);
    select.appendChild(option);
  }
  select.value=keys.includes(areraSelectedMonth)?areraSelectedMonth:keys[0]||'';
  const button=byId('areraPreviewButton');
  button.disabled=false;
  button.textContent='Aggiorna valori';
  byId('areraMessage').textContent=String(payload.error||(!keys.length?'Nessuna tariffa ARERA disponibile.':''));
  areraDraft=null;
  byId('areraPreview').hidden=true;
  areraStatus();
  if(showComparison&&!payload.error&&keys.length&&(window.tariffActivePanel==='arera'||typeof openTariffPanel!=='function'))previewArera();
};
function checkAreraCatalog(){
  if(areraCheckPending)return;
  areraCheckPending=true;
  const button=byId('areraPreviewButton');
  button.disabled=true;
  button.textContent='Controllo in corso…';
  byId('areraMessage').textContent='Controllo delle tabelle ufficiali ARERA in corso…';
  window.location.href='costoluce://arera-check';
}
function areraPlan(){
  const period=byId('areraMonth').value,resident=byId('areraResident').value==='resident';
  const raw=String(byId('areraPower').value).trim();
  if(!/^\d+(?:[.,]\d+)?$/.test(raw))throw Error('Inserisci la potenza impegnata riportata nella bolletta.');
  const power=Number(raw.replace(',','.'));if(!Number.isFinite(power)||power<0.5||power>36)throw Error('La potenza deve essere tra 0,5 e 36 kW.');
  const profile=(areraCatalog[period]||{})[resident?'resident':'nonresident'];
  if(!profile)throw Error('Tariffe del mese non disponibili. Nessun valore è stato sostituito.');
  for(const key of ['transport','equalization','asos','arim','networkAnnual','powerAnnual','qualityPowerAnnual','oneriAnnual'])if(typeof profile[key]!=='number'||!Number.isFinite(profile[key])||profile[key]<0||profile[key]>1000)throw Error('Tabella ARERA incompleta. Nessuna tariffa modificata.');
  const rows=[
    ['transport','Trasporto','variable',profile.transport],['equalization','Perequazione','variable',profile.equalization],
    ['asos','ASOS','variable',profile.asos],['arim','ARIM','variable',profile.arim],
    ['custom_arera_rete_fissa','Rete · quota fissa','fixed',profile.networkAnnual/12],
    ['custom_arera_rete_potenza','Rete · quota potenza','fixed',profile.powerAnnual*power/12],
    ['custom_arera_qualita_potenza','Rete · qualità quota potenza','fixed',profile.qualityPowerAnnual*power/12],
    ['custom_arera_oneri_fissi','Oneri · quota fissa','fixed',profile.oneriAnnual/12]
  ].filter(row=>row[3]>0||rateMeta[row[0]]);
  const missing=rows.filter(r=>r[0].startsWith('custom_')&&!customKeys.includes(r[0]));
  if(customKeys.length+missing.length>MAX_CUSTOM_RATES)throw Error('Spazio insufficiente: elimina alcune voci personalizzate prima di importare.');
  return {period,resident,power,rows};
}
const areraNumber=new Intl.NumberFormat('it-IT',{maximumFractionDigits:9});
function areraComparisonCell(amount,type,status){
  const cell=document.createElement('td');
  const value=document.createElement('strong');
  if(Number.isFinite(amount)){
    value.textContent=areraNumber.format(amount);
    const unit=document.createElement('small');
    unit.textContent=type==='fixed'?'€/mese':'€/kWh';
    cell.appendChild(value);
    cell.appendChild(unit);
  }else{
    value.textContent='—';
    cell.appendChild(value);
  }
  if(status){
    cell.className='arera-value-'+status;
    const indicator=document.createElement('small');
    indicator.textContent=status==='same'?'Coincide':'Da aggiornare';
    cell.appendChild(indicator);
  }
  return cell;
}
function previewArera(){
  try{
    areraDraft=areraPlan();
    const dest=byId('areraPreviewRows');
    dest.innerHTML='';
    for(const [key,name,type,value] of areraDraft.rows){
      const meta=rateMeta[key];
      const active=meta&&rateEnabled(key)&&Number.isFinite(tariffs[key]);
      const matches=active&&meta.type===type&&areraNumber.format(tariffs[key])===areraNumber.format(value);
      const row=document.createElement('tr');
      const title=document.createElement('th');
      title.setAttribute('scope','row');
      title.textContent=meta&&meta.name?meta.name:name;
      row.appendChild(title);
      row.appendChild(areraComparisonCell(value,type,matches?'same':'different'));
      row.appendChild(areraComparisonCell(active?tariffs[key]:null,meta&&meta.type));
      dest.appendChild(row);
    }
    byId('areraPreviewTitle').textContent='Valori di '+areraMonthLabel(areraDraft.period)+' · '+decimal(areraDraft.power,1)+' kW';
    byId('areraPreview').hidden=false;
  }catch(e){
    areraDraft=null;
    byId('areraPreview').hidden=true;
    byId('areraMessage').textContent=e.message;
  }
}
function applyArera(){
  if(!areraDraft)return false;
  // Revalidate: do not import a stale preview after changing month, power or profile.
  let plan;try{plan=areraPlan();if(JSON.stringify(plan)!==JSON.stringify(areraDraft)){previewArera();byId('areraMessage').textContent='Impostazioni cambiate: verifica il nuovo riepilogo e premi di nuovo Imposta valori ARERA.';return false}}catch(e){byId('areraMessage').textContent=e.message;return false}
  const values={};
  for(const [key,name,type,value] of plan.rows){
    if(key.startsWith('custom_')&&!customKeys.includes(key))customKeys.push(key);
    tariffs[key]=value;rateMeta[key]={name:(rateMeta[key]||{}).name||name,type,enabled:true};values[key]={value,type};
  }
  areraImport={period:plan.period,resident:plan.resident,power:plan.power,values};
  try{localStorage.setItem('costoluce_arera_import_v1',JSON.stringify(areraImport))}catch(e){}
  save();fillRates();recalc();areraStatus();byId('areraPreview').hidden=true;areraDraft=null;
  byId('areraMessage').textContent='Valori importati e sincronizzati con il widget. La matita resta disponibile per ogni voce.';return true;
}
const originalFillRates=fillRates;
fillRates=function(){originalFillRates();areraStatus()};
byId('areraPower').value=areraImport?String(areraImport.power):'3';
byId('areraResident').value=areraImport?(areraImport.resident?'resident':'nonresident'):(byId('resident').checked?'resident':'nonresident');
areraStatus();
