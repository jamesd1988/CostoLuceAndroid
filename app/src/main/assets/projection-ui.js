'use strict';
let billProjectionEnabled=false,lastBillProjection=null;
try{billProjectionEnabled=localStorage.getItem('costoluce_bill_projection_v1')==='1'}catch(e){}
function setBillProjection(enabled){
  billProjectionEnabled=Boolean(enabled);
  try{localStorage.setItem('costoluce_bill_projection_v1',billProjectionEnabled?'1':'0')}catch(e){}
  renderHistory();
}
function calculateCurrentBillProjection(today){
  return window.BillProjection.calculate({rows:historyRows,period:selectedPeriod(months,today),today,price:k=>calculateCosts(k,1,byId('resident').checked?taxableMonthly(k):k,1)});
}
function renderSummaryProjection(today=new Date()){
  const result=calculateCurrentBillProjection(today);
  byId('summaryProjection').textContent=result.available?'≈ '+euro(result.value):'—';
  byId('summaryProjectionHint').textContent=result.available?'Proiezione al '+projectionDate(result.to)+' · media di '+result.sampleDays+' giorni completi.':'Proiezione non disponibile: '+result.reason;
  byId('summaryProjection').setAttribute('title',result.available?'Bolletta prevista a fine periodo, canone TV separato.':result.reason);
  return result;
}
function projectedBillingRows(rows,today){
  lastBillProjection=calculateCurrentBillProjection(today);
  if(!billProjectionEnabled)return rows;
  return rows.map((row,i)=>i===rows.length-1?{...row,...lastBillProjection,projection:true,status:'forecast',partial:true}:row);
}
function projectionDate(iso){return new Intl.DateTimeFormat('it-IT',{day:'numeric',month:'short'}).format(new Date(iso+'T12:00:00'));}
function latestRecordedConsumptionDate(today=new Date()){
  const keys=new Set(selectedPeriod(months,today).map(row=>row.year*12+row.month));let latest=null;
  const candidate=(row,day)=>{
    const limit=new Date(row.year,row.month,0).getDate();if(!Number.isInteger(day)||day<1||day>limit)return;
    const date=new Date(row.year,row.month-1,day);if(date>today)return;if(!latest||date>latest)latest=date;
  };
  for(const row of historyRows){
    if(!row||!row.available||!Number.isInteger(row.year)||!Number.isInteger(row.month)||row.month<1||row.month>12||!keys.has(row.year*12+row.month))continue;
    candidate(row,row.lastMeasuredDay);
    if(Array.isArray(row.daily))for(const day of row.daily)if(day&&Number.isFinite(day.kwh)&&day.kwh>=0)candidate(row,day.day);
  }
  return latest;
}
function renderProjectionControls(bill,rows){
  byId('billProjectionControls').hidden=!bill;byId('billProjectionToggle').checked=billProjectionEnabled;
  byId('billProjectionState').hidden=!bill;byId('historyBadge').hidden=bill;
  if(!bill)return;
  const result=lastBillProjection,projected=billProjectionEnabled,last=latestRecordedConsumptionDate();
  const missingDays=projected&&!result.available&&result.code==='recent_days';
  byId('billProjectionState').textContent=projected?(result.available?'Proiezione fattura completa basata sugli ultimi 7 giorni rilevati':missingDays?'Proiezione non disponibile: aspettare 7 giorni rilevati':'Proiezione non disponibile'):'Stima bolletta al '+(last?new Intl.DateTimeFormat('it-IT',{day:'2-digit',month:'2-digit',year:'numeric'}).format(last):'—');
  const hint=byId('billProjectionHint');hint.hidden=projected?(result.available||missingDays):Boolean(last);
  hint.textContent=projected?(result.available?'':result.reason):(last?'':'Aggiorna Enel per acquisire l’ultimo giorno rilevato.');
  byId('billProjectionState').setAttribute('title',projected&&result.available?'Proiezione al '+projectionDate(result.to)+'. Riferimento: '+projectionDate(result.sampleFrom)+' – '+projectionDate(result.sampleTo)+'. Canone TV separato.':'');
  byId('historyBadge').textContent=projected?(result.available?'Proiezione fine periodo':'Proiezione non disponibile'):'Periodo in corso';
}
renderHistory();
