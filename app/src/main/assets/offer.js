'use strict';
// These are contract reference amounts, not ARERA tariffs or future price guarantees.
const offerFixedRows=[
  {id:'custom_offer_commercialization',name:'Commercializzazione vendita',value:12,aliases:['commercializzazione','commercializzazione vendita','commercializzazione e vendita','ccv','ccv luce']},
  {id:'custom_offer_dispbt',name:'DISPbt · parte fissa',value:.092250,aliases:['dispbt','dispbt parte fissa','componente di dispacciamento dispbt parte fissa','dispacciamento parte fissa','dispacciamento fisso']}
];
function normalizedOfferName(name){return String(name).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}
function missingOfferRows(){
  const keys=[...Object.keys(initial),...customKeys].filter(k=>rateEnabled(k));
  const missing=[];
  for(const row of offerFixedRows){
    const matches=keys.filter(k=>k===row.id||row.aliases.includes(normalizedOfferName(rateMeta[k].name)));
    if(matches.length>1)throw Error('Sono già presenti più voci per “'+row.name+'”. Controllale con la matita per evitare doppioni.');
    if(matches.length===1){
      if(rateMeta[matches[0]].type!=='fixed')throw Error('La voce “'+rateMeta[matches[0]].name+'” esiste come variabile. Verifica il tipo con la matita prima di completare le quote fisse.');
      continue; // Keep existing amounts and user labels, even when different from the reference.
    }
    missing.push(row);
  }
  if(customKeys.length+missing.length>MAX_CUSTOM_RATES)throw Error('Spazio insufficiente: elimina alcune voci personalizzate prima di completare le quote fisse.');
  return missing;
}
function completeOfferFixed(){
  try{
    const rows=missingOfferRows();
    if(!rows.length){byId('offerMessage').textContent='Le due voci sono già configurate. Importi e nomi personali conservati.';return true}
    for(const row of rows){customKeys.push(row.id);tariffs[row.id]=row.value;rateMeta[row.id]={name:row.name,type:'fixed',enabled:true}}
    save();fillRates();recalc();
    byId('offerMessage').textContent=rows.length+' voci fisse aggiunte e sincronizzate con il widget. Le voci già presenti sono rimaste invariate.';
    return true;
  }catch(e){byId('offerMessage').textContent=e.message;return false}
}
function renderBillPricingNotice(){
  const el=byId('billPricingNotice');el.hidden=chartMode!=='bill';if(el.hidden)return;
  try{const missing=missingOfferRows();el.hidden=!missing.length;byId('billPricingText').textContent=missing.length?'Stima incompleta se queste quote non sono già presenti con altri nomi. Riferimento E-Light agosto–settembre 2026: '+missing.map(r=>r.name+' '+decimal(r.value,6)+' €/mese').join('; ')+'. Puoi aggiungere le quote di riferimento o verificare le tue Tariffe.':''}
  catch(e){el.hidden=false;byId('billPricingText').textContent='Verifica le quote fisse in Tariffe: '+e.message}
}
function completeBillOffer(){
  const ok=completeOfferFixed();
  if(ok){byId('billTariffStatus').hidden=false;byId('billTariffStatus').textContent='Quote fisse riconosciute e importi ricalcolati. Restano le tariffe salvate: verifica i prezzi validi per il periodo della tua bolletta.'}
  else{byId('billPricingNotice').hidden=false;byId('billPricingText').textContent=byId('offerMessage').textContent}
  return ok;
}
renderBillPricingNotice();
