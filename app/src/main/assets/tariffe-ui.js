'use strict';
// Presentation only: keep the existing tariff, billing and ARERA validation paths.
let tariffReturnFocus=null;
window.tariffActivePanel='';

function renderTariffSummaries(){
  const period=byId('tariffPeriodSummary');if(!period)return;
  const label=months===1?'Mensile':months===2?'Bimestrale':'Trimestrale';
  const names=selectedPeriod(months).map(m=>new Intl.DateTimeFormat('it-IT',{month:'short'}).format(new Date(m.year,m.month-1,1)).replace('.',''));
  period.textContent=label+' · '+names.join(' + ');
  const arera=byId('tariffAreraSummary');
  arera.textContent=areraImport?(areraImport.resident?'Residente':'Non residente')+' · '+decimal(areraImport.power,1)+' kW · '+areraMonthLabel(areraImport.period):'Profilo da verificare · Controlla i valori';
  try{const missing=missingOfferRows(billFixedRows);byId('tariffOfferSummary').textContent=missing.length?'Completa quote fisse · '+missing.length+' da verificare':'Quote fisse · verifica riferimento';}
  catch(conflict){byId('tariffOfferSummary').textContent='Quote fisse · controlla le tue voci';}
}

function renderBillingChoices(){
  const count=Number(byId('billingFrequency').value),anchor=Number(byId('billingAnchor').value);
  document.querySelectorAll('[data-billing-choice]').forEach(button=>button.setAttribute('aria-pressed',String(Number(button.dataset.billingChoice)===count)));
  const chips=byId('billingChips');chips.textContent='';
  if(count===1){const chip=document.createElement('span');chip.textContent='Ogni mese';chips.appendChild(chip);return;}
  if(anchor<1||anchor>12){byId('billingExplanation').open=true;return;}
  for(let i=0;i<count;i++){const chip=document.createElement('span');chip.textContent=new Intl.DateTimeFormat('it-IT',{month:'long'}).format(new Date(2026,anchor-1+i,1));chips.appendChild(chip);}
}

function openTariffPanel(kind){
  if(!['billing','arera','arera-preview','offer'].includes(kind))return;
  if(kind==='arera-preview'&&!areraDraft)kind='arera';
  if(!window.tariffActivePanel)tariffReturnFocus=document.activeElement;
  if(kind==='billing'&&window.tariffActivePanel!=='billing')renderBillingSetup();
  document.querySelectorAll('#tariffSettings .tariff-panel').forEach(panel=>panel.hidden=panel.dataset.tariffPanel!==kind);
  window.tariffActivePanel=kind;byId('tariffSettings').hidden=false;document.body.style.overflow='hidden';
  if(kind==='billing')renderBillingChoices();
  if(kind==='arera-preview')byId('areraPreview').hidden=false;
  const panel=document.querySelector('#tariffSettings .tariff-panel:not([hidden])');panel.scrollTop=0;panel.querySelector('.tariff-panel-close').focus();
}

function closeTariffPanel(){
  const holder=byId('tariffSettings');if(!holder||holder.hidden)return false;
  const prior=window.tariffActivePanel;window.tariffActivePanel='';holder.hidden=true;
  document.querySelectorAll('#tariffSettings .tariff-panel').forEach(panel=>panel.hidden=true);
  if(prior==='arera-preview'){areraDraft=null;byId('areraPreview').hidden=true;}
  if(byId('rateEditor').hidden)document.body.style.overflow='';
  renderBillingSetup();renderTariffSummaries();
  if(tariffReturnFocus&&tariffReturnFocus.isConnected)tariffReturnFocus.focus();tariffReturnFocus=null;
  return true;
}

function cancelAreraPreview(){areraDraft=null;byId('areraPreview').hidden=true;openTariffPanel('arera');}

function closeTopTariffDialog(){
  if(!byId('rateEditor').hidden){closeRateEditor();if(window.tariffActivePanel)document.body.style.overflow='hidden';return true;}
  if(closeTariffPanel())return true;
  return typeof closeMonthDaily==='function'?closeMonthDaily():false;
}

(()=>{
  const page=byId('page-rates');
  const setup=document.createElement('div');setup.className='tariff-setup';
  setup.innerHTML='<button type="button" id="openBilling" aria-haspopup="dialog" onclick="openTariffPanel(\'billing\')"><span class="tariff-setup-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v5m10-5v5M3 11h18"/></svg></span><span class="tariff-setup-copy"><strong>Periodo di fatturazione</strong><small id="tariffPeriodSummary"></small></span><span class="tariff-setup-arrow" aria-hidden="true">›</span></button><button type="button" id="openArera" aria-haspopup="dialog" onclick="openTariffPanel(\'arera\')"><span class="tariff-setup-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m3 7 9-4 9 4-9 4-9-4Zm0 5 9 4 9-4M3 17l9 4 9-4"/></svg></span><span class="tariff-setup-copy"><strong>Rete e oneri ARERA</strong><small id="tariffAreraSummary"></small></span><span class="tariff-setup-arrow" aria-hidden="true">›</span></button>';
  page.insertBefore(setup,page.querySelector('.arera-card'));

  const holder=document.createElement('div');holder.id='tariffSettings';holder.hidden=true;
  const backdrop=document.createElement('button');backdrop.className='tariff-panel-backdrop';backdrop.type='button';backdrop.setAttribute('aria-label','Chiudi impostazioni tariffa');backdrop.addEventListener('click',closeTariffPanel);holder.appendChild(backdrop);
  page.appendChild(holder);
  function panel(section,kind,title){
    const heading=section.querySelector('h3');heading.textContent=title;
    const header=document.createElement('div');header.className='tariff-panel-header';header.appendChild(heading);
    const close=document.createElement('button');close.type='button';close.className='tariff-panel-close';close.textContent='×';close.setAttribute('aria-label','Chiudi '+title);close.addEventListener('click',closeTariffPanel);header.appendChild(close);
    section.className='tariff-panel';section.dataset.tariffPanel=kind;section.hidden=true;section.setAttribute('role','dialog');section.setAttribute('aria-modal','true');section.setAttribute('aria-labelledby',heading.id);section.insertBefore(header,section.firstChild);holder.appendChild(section);return section;
  }
  const billing=panel(page.querySelector('[aria-labelledby="billingTitle"]'),'billing','Periodo di fatturazione');
  const arera=panel(page.querySelector('[aria-labelledby="areraTitle"]'),'arera','Rete e oneri ARERA');
  const offer=panel(page.querySelector('[aria-labelledby="offerTitle"]'),'offer','Completa quote fisse');

  const frequency=byId('billingFrequency');frequency.hidden=true;
  const choices=document.createElement('div');choices.className='billing-choices';choices.setAttribute('role','group');choices.setAttribute('aria-label','Periodicità della bolletta');
  [1,2,3].forEach(n=>{const button=document.createElement('button');button.type='button';button.dataset.billingChoice=String(n);button.textContent=n+(n===1?' mese':' mesi');button.addEventListener('click',()=>{if(Number(frequency.value)!==n){frequency.value=String(n);billingDraftChanged(true);}});choices.appendChild(button);});frequency.parentNode.insertBefore(choices,frequency.nextSibling);
  billing.querySelector('label[for="billingAnchor"]').textContent='Primo mese del gruppo di esempio';
  const chips=document.createElement('div');chips.className='billing-chips';chips.id='billingChips';byId('billingExample').parentNode.insertBefore(chips,byId('billingExample'));
  const explanation=document.createElement('details');explanation.id='billingExplanation';const explanationTitle=document.createElement('summary');explanationTitle.textContent='Come si ripete lo schema';explanation.appendChild(explanationTitle);explanation.appendChild(byId('billingExample'));
  const exampleHelp=byId('billingExampleFields').querySelector('p');if(exampleHelp)explanation.appendChild(exampleHelp);
  explanation.appendChild(byId('billingActive'));billing.appendChild(explanation);
  const save=billing.querySelector('button[onclick="saveBillingSetup()"]');save.textContent='Salva periodo';billing.appendChild(save);billing.appendChild(byId('billingSetupStatus'));
  // Long explanations remain available on demand instead of preceding every tariff.
  Array.from(billing.children).filter(el=>el.tagName==='P'&&el.id!=='billingSetupStatus').forEach(el=>explanation.appendChild(el));

  const preview=document.createElement('section');preview.innerHTML='<h3 id="tariffAreraPreviewTitle">Verifica prima di importare</h3>';panel(preview,'arera-preview','Verifica prima di importare');preview.appendChild(byId('areraPreview'));
  const previewActions=document.createElement('div');previewActions.className='tariff-preview-actions';
  const actions=Array.from(byId('areraPreview').querySelectorAll(':scope > button'));
  actions[0].removeAttribute('onclick');actions[0].addEventListener('click',cancelAreraPreview);actions[0].textContent='Indietro';actions.forEach(button=>previewActions.appendChild(button));byId('areraPreview').appendChild(previewActions);

  const header=arera.querySelector('.tariff-panel-header'),profile=arera.querySelector('.arera-profile'),monthLabel=arera.querySelector('label[for="areraMonth"]'),month=byId('areraMonth'),verify=byId('areraPreviewButton'),check=byId('areraCheck'),message=byId('areraMessage');
  const originalInfo=Array.from(arera.children).filter(el=>el.tagName==='P'&&el!==message);
  const form=document.createElement('div');const introduction=document.createElement('p');introduction.textContent='Verifica le quote di rete e gli oneri della tua fornitura.';form.appendChild(introduction);form.appendChild(profile);form.appendChild(monthLabel);form.appendChild(month);
  verify.textContent='Verifica valori';form.appendChild(verify);check.textContent='↻ Controlla disponibilità';check.className='tariff-check';form.appendChild(check);form.appendChild(message);
  const info=document.createElement('details');const infoTitle=document.createElement('summary');infoTitle.textContent='Aggiornamenti, fonte e voci importate';info.appendChild(infoTitle);originalInfo.forEach(el=>info.appendChild(el));form.appendChild(info);
  const tax=document.createElement('button');tax.type='button';tax.className='tariff-check';tax.textContent='Imposte e agevolazioni accisa';tax.addEventListener('click',()=>{closeTariffPanel();byId('taxSettings').open=true;byId('taxSettings').scrollIntoView({block:'nearest'});});form.appendChild(tax);
  while(arera.firstChild)arera.removeChild(arera.firstChild);arera.appendChild(header);arera.appendChild(form);

  const ratesHeading=document.createElement('div');ratesHeading.className='tariff-rates-heading';const title=document.createElement('h3');title.textContent='Voci della bolletta';ratesHeading.appendChild(title);
  const filter=page.querySelector('.tariff-filter');page.insertBefore(ratesHeading,filter);byId('addRateButton').textContent='＋ Aggiungi';ratesHeading.appendChild(byId('addRateButton'));
  const offerLink=document.createElement('button');offerLink.type='button';offerLink.className='tariff-offer-link';offerLink.innerHTML='<span id="tariffOfferSummary">Completa quote fisse</span><span aria-hidden="true">›</span>';offerLink.addEventListener('click',()=>openTariffPanel('offer'));page.insertBefore(offerLink,byId('taxSettings'));
  const advanced=document.createElement('details');advanced.className='tariff-secondary';advanced.innerHTML='<summary>Informazioni e ripristino tariffe</summary>';
  Array.from(page.children).filter(el=>el.matches('.tariff-help,.reset-tariffs,#rateResetConfirm')).forEach(el=>advanced.appendChild(el));page.appendChild(advanced);page.appendChild(holder);

  const previousDraft=billingDraftChanged;billingDraftChanged=function(reset=false){previousDraft(reset);renderBillingChoices();};
  const previousSave=saveBillingSetup;saveBillingSetup=function(){const saved=previousSave();if(saved){closeTariffPanel();byId('rateStatus').textContent='Periodo salvato e sincronizzato con il widget.';}return saved;};
  const previousAreraStatus=areraStatus;areraStatus=function(){previousAreraStatus();renderTariffSummaries();};
  const previousCatalog=window.onAreraCatalog;window.onAreraCatalog=function(payload){previousCatalog(payload);if(window.tariffActivePanel==='arera-preview'&&!areraDraft)openTariffPanel('arera');};
  const previousPreview=previewArera;previewArera=function(){previousPreview();if(areraDraft)openTariffPanel('arera-preview');};
  const previousApply=applyArera;applyArera=function(){const applied=previousApply();if(applied){closeTariffPanel();byId('rateStatus').textContent='Valori ARERA importati e sincronizzati con il widget.';}return applied;};
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'&&closeTopTariffDialog()){event.preventDefault();return;}
    const dialog=!byId('rateEditor').hidden?byId('rateEditor'):window.tariffActivePanel?document.querySelector('#tariffSettings .tariff-panel:not([hidden])'):null;
    if(event.key!=='Tab'||!dialog)return;const controls=Array.from(dialog.querySelectorAll('button,input,select,textarea,a[href],summary')).filter(el=>!el.disabled&&!el.hidden&&el.getClientRects().length);
    if(!controls.length)return;const first=controls[0],last=controls[controls.length-1];if(event.shiftKey&&document.activeElement===first){last.focus();event.preventDefault();}else if(!event.shiftKey&&document.activeElement===last){first.focus();event.preventDefault();}
  });
  renderBillingSetup();fillRates();renderTariffSummaries();
})();
