'use strict';
let billProjectionEnabled=false,lastBillProjection=null;
try{billProjectionEnabled=localStorage.getItem('costoluce_bill_projection_v1')==='1'}catch(e){}
function setBillProjection(enabled){
  billProjectionEnabled=Boolean(enabled);
  try{localStorage.setItem('costoluce_bill_projection_v1',billProjectionEnabled?'1':'0')}catch(e){}
  renderHistory();
}
function projectedBillingRows(rows,today){
  lastBillProjection=window.BillProjection.calculate({rows:historyRows,period:selectedPeriod(months,today),today,price:k=>calculateCosts(k,1,byId('resident').checked?taxableMonthly(k):k,1)});
  if(!billProjectionEnabled)return rows;
  return rows.map((row,i)=>i===rows.length-1?{...row,...lastBillProjection,projection:true,status:'forecast',partial:true}:row);
}
function projectionDate(iso){return new Intl.DateTimeFormat('it-IT',{day:'numeric',month:'short'}).format(new Date(iso+'T12:00:00'));}
function renderProjectionControls(bill,rows){
  byId('billProjectionControls').hidden=!bill;byId('billProjectionAmount').hidden=!bill;byId('billProjectionDetails').hidden=!bill;
  byId('billProjectionToggle').checked=billProjectionEnabled;
  if(!bill)return;
  const current=rows[rows.length-1],result=lastBillProjection,projected=billProjectionEnabled;
  byId('billProjectionState').textContent=projected?'Fine periodo':'Periodo corrente';
  const recent=historyRows.filter(r=>Number.isInteger(r.lastMeasuredDay)&&r.lastMeasuredDay>0&&r.available).sort((a,b)=>b.year*12+b.month-(a.year*12+a.month))[0];
  const last=recent?new Date(recent.year,recent.month-1,recent.lastMeasuredDay):null;
  byId('billProjectionHint').textContent=projected?(result.available?'Al '+projectionDate(result.to)+' · media di '+result.sampleDays+' giorni':result.reason):(last?'Letture fino al '+new Intl.DateTimeFormat('it-IT',{day:'numeric',month:'short'}).format(last):'Consumi disponibili · premi Aggiorna per acquisire Enel');
  byId('billProjectionLabel').textContent=current.label+' · '+(projected?'fine periodo':'stima attuale');
  byId('billProjectionValue').textContent=current.available?(projected?'≈ ':'')+euro(current.value):'—';
  byId('billProjectionDetailsTitle').textContent=projected?'Come viene calcolata':'Dettagli della stima';
  byId('billProjectionExplanation').textContent=projected?(result.available?'Media '+decimal(result.average,2)+' kWh/giorno su '+result.sampleDays+' giorni completi tra '+projectionDate(result.sampleFrom)+' e '+projectionDate(result.sampleTo)+'. Consumi disponibili '+decimal(result.observedKwh,1)+' kWh; aggiunta prevista '+decimal(result.extraKwh,1)+' kWh, totale '+decimal(result.kwh,1)+' kWh. Il calcolo include le quote fisse di tutti i '+months+' mesi, componenti variabili, accisa mese per mese e IVA configurati. Le giornate mancanti o incomplete sono stimate con la stessa media; i consumi già misurati vengono conservati. Canone TV separato. È una proiezione al ritmo recente, soggetta ai consumi futuri e alle tariffe.':result.reason):'Stima sui consumi disponibili: costi variabili, quote fisse dei mesi iniziati, accisa e IVA configurati. Il grafico mostra un solo importo per il periodo corrente. Le fatture emesse non vengono ricalcolate. Canone TV separato.';
  byId('historyBadge').textContent=projected?(result.available?'Proiezione fine periodo':'Proiezione non disponibile'):'Periodo in corso';
  if(projected&&byId('billPricingNotice').hidden)byId('historyCostScope').textContent=result.available?'Storico: fatture emesse. Periodo corrente: proiezione completa con le tariffe configurate. Canone TV separato.':'Proiezione non disponibile. Lo storico mantiene gli importi delle fatture emesse. Canone TV separato.';
  if(projected)byId('historyTotal').textContent=byId('historyTotal').textContent.replace(' · include il periodo parziale',' · include una proiezione');
  byId('historyTotal').textContent=byId('historyTotal').textContent.replace('Somma fatturate e stima corrente:','Somma fatturate e '+(projected?'proiezione finale:':'stima corrente:'));
}
renderHistory();
