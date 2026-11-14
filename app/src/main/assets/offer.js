'use strict';
// These are contract reference amounts, not ARERA tariffs or future price guarantees.
const offerFixedRows=[
  {id:'custom_offer_commercialization',name:'Commercializzazione vendita',value:12,aliases:['commercializzazione','commercializzazione vendita','commercializzazione e vendita','ccv','ccv luce']},
  {id:'custom_offer_dispbt',name:'DISPbt · parte fissa',value:.092250,aliases:['dispbt','dispbt parte fissa','componente di dispacciamento dispbt parte fissa','dispacciamento parte fissa','dispacciamento fisso']}
];
const billFixedRows=[...offerFixedRows,
  {id:'custom_arera_rete_fissa',name:'Rete · quota fissa',value:1.92,aliases:['rete quota fissa','rete fissa','quota fissa rete']},
  {id:'custom_arera_rete_potenza',name:'Rete · quota potenza',value:5.88,aliases:['rete quota potenza','rete potenza','quota potenza rete']},
  {id:'custom_arera_qualita_potenza',name:'Rete · qualità quota potenza',value:.0497,aliases:['rete qualita quota potenza','rete qualita potenza','qualita quota potenza']}
];
function normalizedOfferName(name){return String(name).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}
function missingOfferRows(reference=offerFixedRows){
  const keys=[...Object.keys(initial),...customKeys].filter(k=>rateEnabled(k));
  const missing=[];
  for(const row of reference){
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
  try{
    const missing=missingOfferRows(billFixedRows);el.hidden=!missing.length;
    byId('historyCostScope').textContent=missing.length?'Stima corrente incompleta: verifica le quote fisse. Le fatture emesse restano agli importi originali. Canone TV escluso.':'Storico fatturato: totale elettricità originale. Periodo corrente: stima con costi fissi, variabili, accisa e IVA configurate. Canone TV separato.';
    byId('billPricingText').textContent=missing.length?'Quote non riconosciute: '+missing.map(r=>r.name+' '+decimal(r.value,6)+' €/mese').join('; ')+'. Riferimento E-Light agosto–settembre 2026, residente 3 kW. Il pulsante aggiunge solo le quote mancanti: controlla prima eventuali voci equivalenti rinominate e la potenza del tuo contratto.':'';
    byId('billFixedPreview').textContent=missing.length?'Aggiunta al bimestre di riferimento: '+euro(missing.reduce((sum,r)=>sum+r.value,0)*2*(1+(rateEnabled('vat')?tariffs.vat:0)/100))+' di quote fisse, IVA configurata inclusa.':'';
  }
  catch(e){el.hidden=false;byId('historyCostScope').textContent='Verifica quote fisse: totale da controllare. Canone TV escluso.';byId('billPricingText').textContent='Verifica le quote fisse in Tariffe: '+e.message;byId('billFixedPreview').textContent=''}
}
function completeBillOffer(){
  try{
    const rows=missingOfferRows(billFixedRows); // Validate all five before making any change.
    for(const row of rows){customKeys.push(row.id);tariffs[row.id]=row.value;rateMeta[row.id]={name:row.name,type:'fixed',enabled:true}}
    save();fillRates();recalc();
    byId('billTariffStatus').hidden=false;byId('billTariffStatus').textContent=rows.length+' quote fisse mancanti aggiunte. Rete, potenza, qualità, commercializzazione e DISPbt ora sono riconosciute. Importi già presenti conservati; verifica i prezzi validi per il periodo.';
    byId('offerMessage').textContent=byId('billTariffStatus').textContent;
    return true;
  }catch(e){byId('offerMessage').textContent=e.message;byId('billPricingNotice').hidden=false;byId('billPricingText').textContent=e.message;return false}
}
function renderBillBreakdown(rows){
  const dest=byId('billBreakdown');dest.innerHTML='';dest.hidden=chartMode!=='bill';if(dest.hidden)return;
  const title=document.createElement('summary');title.textContent='Dettaglio fatture e '+(rows.some(r=>r.projection)?'proiezione finale':'stima corrente')+(rateEnabled('vat')?' (IVA inclusa)':' (IVA esclusa)');dest.appendChild(title);
  for(const row of rows.filter(r=>r.available)){
    const p=document.createElement('p');p.textContent=row.status==='issued'?row.label+' '+row.years+': '+euro(row.value)+' elettricità fatturata · '+euro(row.other)+' fuori totale elettricità (canone TV/altre voci)':row.label+' '+row.years+': '+euro(row.variable)+' variabile + '+euro(row.fixed)+' fisso = '+euro(row.value)+(row.projection?' · proiezione finale':' · stima corrente');dest.appendChild(p);
  }
}

renderBillPricingNotice();
