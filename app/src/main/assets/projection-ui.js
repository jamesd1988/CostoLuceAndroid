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
  byId('summaryProjectedKwh').textContent=result.available?'≈ '+decimal(result.kwh,1)+' kWh':'—';
  byId('summaryProjectedKwh').setAttribute('title',result.available?'Consumo previsto dell’intero periodo di fatturazione.':result.reason);
  byId('summaryProjectionHint').textContent=result.available?'Proiezione al '+projectionDate(result.to)+' · media di '+result.sampleDays+' giorni completi.':'Proiezione non disponibile: '+result.reason;
  byId('summaryProjection').setAttribute('title',result.available?'Bolletta prevista a fine periodo, canone TV separato.':result.reason);
  return result;
}
function projectedBillingRows(rows,today){
  lastBillProjection=calculateCurrentBillProjection(today);
  if(!billProjectionEnabled)return rows;
  return rows.map((row,i)=>i===rows.length-1?{...row,...lastBillProjection,actualValue:row.available&&Number.isFinite(row.value)?row.value:null,projection:true,status:'forecast',partial:true}:row);
}
function projectedMonthlyRows(rows,today,kwhMode){
  lastBillProjection=calculateCurrentBillProjection(today);
  if(!billProjectionEnabled)return rows;
  const now=today.getFullYear()*12+today.getMonth()+1,period=selectedPeriod(months,today);
  const output=rows.map(row=>({...row}));
  const monthlyPrices=new Map();
  if(lastBillProjection.available&&!kwhMode){
    let cents=0;for(const month of lastBillProjection.monthly){const value=Math.round((monthlyCost(month.kwh)+Number.EPSILON)*100);monthlyPrices.set(month.year*12+month.month,value);cents+=value;}
    const last=period[period.length-1],key=last.year*12+last.month;
    monthlyPrices.set(key,monthlyPrices.get(key)+Math.round(lastBillProjection.value*100)-cents);
  }
  for(const month of period){
    const key=month.year*12+month.month;if(key<now)continue;
    let index=output.findIndex(row=>row.year===month.year&&row.month===month.month);
    if(index<0){output.push({...month,available:false,partial:true,future:true});index=output.length-1;}
    const original=output[index],forecast=lastBillProjection.available?lastBillProjection.monthly.find(row=>row.year===month.year&&row.month===month.month):null;
    const actual=original.available?(kwhMode?original.kwh:original.value):null;
    const value=forecast?(kwhMode?forecast.kwh:monthlyPrices.get(key)/100):0;
    output[index]={...original,projection:true,status:'forecast',partial:true,actualValue:Number.isFinite(actual)?actual:null,available:Boolean(forecast),kwh:kwhMode?value:original.kwh,value:kwhMode?original.value:value};
  }
  return output.sort((a,b)=>a.year*12+a.month-b.year*12-b.month);
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
function sizeProjectionStatus(){
  const state=byId('billProjectionState'),row=state&&state.parentElement;
  if(!row||!row.clientWidth||typeof getComputedStyle!=='function')return;
  const font=getComputedStyle(state),measure=document.createElement('span');
  measure.setAttribute('aria-hidden','true');
  Object.assign(measure.style,{position:'absolute',visibility:'hidden',pointerEvents:'none',display:'block',whiteSpace:'normal',width:row.clientWidth+'px',fontFamily:font.fontFamily,fontSize:font.fontSize,lineHeight:font.lineHeight,fontWeight:font.fontWeight,letterSpacing:font.letterSpacing});
  row.appendChild(measure);let height=24;
  for(const text of ['Proiezione fattura completa basata sugli ultimi 7 giorni rilevati','Proiezione mensile basata sugli ultimi 7 giorni rilevati','Proiezione non disponibile: aspettare 7 giorni rilevati','Stima bolletta al 30/12/2026']){measure.textContent=text;height=Math.max(height,Math.ceil(measure.getBoundingClientRect().height));}
  measure.remove();row.style.height=height+'px';
}
function renderProjectionControls(bill,rows){
  byId('billProjectionControls').hidden=false;byId('billProjectionToggle').checked=billProjectionEnabled;
  byId('billProjectionState').hidden=!bill&&!billProjectionEnabled;byId('historyBadge').hidden=bill||billProjectionEnabled;
  if(!bill&&!billProjectionEnabled){byId('billProjectionHint').hidden=true;sizeProjectionStatus();return;}
  if(!lastBillProjection)lastBillProjection=calculateCurrentBillProjection(new Date());
  const result=lastBillProjection,projected=billProjectionEnabled,last=latestRecordedConsumptionDate();
  const missingDays=projected&&!result.available&&result.code==='recent_days';
  byId('billProjectionState').textContent=projected?(result.available?(bill?'Proiezione fattura completa basata sugli ultimi 7 giorni rilevati':'Proiezione mensile basata sugli ultimi 7 giorni rilevati'):missingDays?'Proiezione non disponibile: aspettare 7 giorni rilevati':'Proiezione non disponibile'):'Stima bolletta al '+(last?new Intl.DateTimeFormat('it-IT',{day:'2-digit',month:'2-digit',year:'numeric'}).format(last):'—');
  const hint=byId('billProjectionHint');hint.hidden=projected?(result.available||missingDays):Boolean(last);
  hint.textContent=projected?(result.available?'':result.reason):(last?'':'Aggiorna Enel per acquisire l’ultimo giorno rilevato.');
  byId('billProjectionState').setAttribute('title',projected&&result.available?'Proiezione al '+projectionDate(result.to)+'. Riferimento: '+projectionDate(result.sampleFrom)+' – '+projectionDate(result.sampleTo)+'. Canone TV separato.':'');
  byId('historyBadge').textContent=projected?(result.available?'Proiezione fine periodo':'Proiezione non disponibile'):'Periodo in corso';
  sizeProjectionStatus();
}
renderHistory();

if(typeof window.addEventListener==='function')window.addEventListener('resize',sizeProjectionStatus);
