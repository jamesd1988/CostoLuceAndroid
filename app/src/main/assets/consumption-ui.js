'use strict';
function consumptionDetailModel(today=new Date()){
  const projected=billProjectionEnabled;
  if(projected){
    const forecast=calculateCurrentBillProjection(today);
    if(!forecast.available)return {available:false,projected,reason:forecast.code==='recent_days'?'Proiezione non disponibile: aspettare 7 giorni rilevati.':forecast.reason};
    const tax=forecast.monthly.reduce((sum,row)=>sum+(byId('resident').checked?taxableMonthly(row.kwh):row.kwh),0);
    return {available:true,projected,kwh:forecast.kwh,tax,parts:calculateCosts(forecast.kwh,months,tax,months),value:forecast.value,scope:'Fine periodo · '+projectionDate(forecast.to),hint:'Media degli ultimi 7 giorni rilevati. Costi completi; canone TV separato.'};
  }
  if(String(byId('kwh').value).trim()==='')return {available:false,projected,reason:'Premi Aggiorna per acquisire i consumi del periodo corrente.'};
  const kwh=safeNumber(byId('kwh').value),measured=lastEnelImport&&lastEnelMonthly.length===months&&Math.abs(lastEnelMonthly.reduce((sum,m)=>sum+m.kwh,0)-kwh)<.02;
  const tax=byId('resident').checked?(measured?lastEnelMonthly.reduce((sum,m)=>sum+taxableMonthly(m.kwh),0):months*taxableMonthly(kwh/months)):kwh;
  const parts=calculateCosts(kwh,months,tax,measured?lastEnelMonthly.filter(m=>!m.future).length:months),last=latestRecordedConsumptionDate(today);
  return {available:true,projected,kwh,tax,parts,value:parts.total,scope:lastEnelImport&&last?'Stima al '+new Intl.DateTimeFormat('it-IT',{day:'2-digit',month:'2-digit',year:'numeric'}).format(last):'Stima attuale',hint:'Costi fissi, variabili, accisa e IVA configurate. Canone TV separato.'};
}
function renderConsumptionDetails(){
  const model=consumptionDetailModel(),prefix=model.projected?'≈ ':'';
  byId('consumptionProjectionToggle').checked=model.projected;
  byId('detail-title').textContent=model.projected?'Dettaglio della proiezione':'Dettaglio del consumo';
  byId('consumptionScope').textContent=model.available?model.scope:model.projected?'Proiezione non disponibile':'Consumi non disponibili';
  byId('consumptionProjectionHint').textContent=model.available?model.hint:model.reason;
  byId('consumptionUsageLabel').textContent=model.projected?'Consumo previsto':'Consumo rilevato';
  byId('consumptionAmountLabel').textContent=model.projected?'Bolletta prevista':'Costo stimato';
  byId('consumptionUsage').textContent=model.available?prefix+decimal(model.kwh,1)+' kWh':'—';
  byId('consumptionAmount').textContent=model.available?prefix+euro(model.value):'—';
  byId('consumptionTotalLabel').textContent=model.projected?'Totale elettricità previsto':'Totale elettricità stimato';
  byId('total2').textContent=model.available?prefix+euro(model.value):'—';
  byId('costParts').textContent=model.available?'Variabile '+euro(model.parts.variable)+' · Fisso '+euro(model.parts.fixed)+(rateEnabled('vat')?' · IVA inclusa':' · IVA non applicata'):'';
  byId('perKwh').textContent=model.available&&model.kwh?decimal(model.value/model.kwh,3)+' €/kWh':'—';
  byId('taxKwh').textContent=model.available?decimal(model.tax,1)+' kWh':'—';
  const dest=byId('breakdown');dest.innerHTML='';
  if(model.available)for(const [name,value,desc] of model.parts.items){
    const row=document.createElement('div');row.className='lineitem';
    const text=document.createElement('div'),label=document.createElement('div'),detail=document.createElement('div'),money=document.createElement('span');
    label.className='labeltext';label.textContent=name;detail.className='detail';detail.textContent=desc;money.className='money';money.textContent=euro(value);
    text.appendChild(label);text.appendChild(detail);row.appendChild(text);row.appendChild(money);dest.appendChild(row);
  }
}
(()=>{
  const originalRecalc=recalc;recalc=function(){const result=originalRecalc();renderConsumptionDetails();return result;};
  const originalHistory=renderHistory;renderHistory=function(){const result=originalHistory();renderConsumptionDetails();return result;};
  renderConsumptionDetails();
})();
