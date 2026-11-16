/* Synchronization state follows native network lifecycle, not a cosmetic timer. */
(function(){
  'use strict';
  const el=id=>document.getElementById(id);
  let busy=false,lastAt=0,kind='idle';
  const root=el('homeSync'),button=el('homeRefresh'),label=el('syncButtonLabel');
  function timestamp(at){
    const date=new Date(at),today=new Date();
    const time=new Intl.DateTimeFormat('it-IT',{hour:'2-digit',minute:'2-digit'}).format(date);
    return date.toDateString()===today.toDateString()?'Aggiornati oggi alle '+time:
      'Aggiornati il '+new Intl.DateTimeFormat('it-IT',{day:'2-digit',month:'2-digit',year:'numeric'}).format(date)+' alle '+time;
  }
  function render(message){
    root.dataset.state=kind;root.setAttribute('aria-busy',String(busy));
    el('syncStatusIcon').innerHTML='<circle cx="12" cy="12" r="9"/>'+(kind==='error'?'<path d="M12 7v6m0 3v1"/>':'<path d="m8 12 3 3 5-6"/>');
    button.disabled=busy;label.textContent=busy?'Aggiornamento…':kind==='error'?'Riprova':'Aggiorna';
    el('historyUpdated').textContent=lastAt?timestamp(lastAt):'Nessuna sincronizzazione';
    const status=el('homeStatus');status.hidden=!message;status.textContent=message||'';
    const settings=el('settingsEnelStatus');if(settings){settings.hidden=!message;settings.textContent=message||'';settings.className='settings-enel-status'+(kind==='error'?' err':'');}
  }
  const oldStatus=window.onEnelStatus;
  window.onEnelStatus=function(message,error,nativeBusy){
    oldStatus(message,error);
    if(typeof nativeBusy==='boolean')busy=nativeBusy;
    kind=error?'error':busy?'loading':'info';render(message);
  };
  const oldHistory=window.onHistoryNative;
  window.onHistoryNative=function(rows,at){
    oldHistory(rows,at);
    // Initial restore only. A failed/partial attempt must not replace the last successful time.
    if(!lastAt&&!busy&&kind==='idle'&&Number(at)>0)lastAt=Number(at);
    render(el('homeStatus').hidden?'':el('homeStatus').textContent);
  };
  const oldConsumption=window.onEnelConsumption;
  window.onEnelConsumption=function(data){
    const accepted=oldConsumption(data);
    if(data&&data.syncResult===true){
      busy=false;
      if(accepted===true&&Number(data.syncedAt)>0){
        lastAt=Number(data.syncedAt);kind='success';
        render('Sincronizzazione completata.');
      }else{kind='error';render(el('importStatus').textContent||'Dati non importati. Riprova.');}
    }else render(el('homeStatus').hidden?'':el('homeStatus').textContent);
    return accepted;
  };
  button.addEventListener('click',()=>{
    if(busy)return;
    busy=true;kind='loading';render('Collegamento a Enel…');
    window.location.href=button.href||'costoluce://enel-sync?months='+months+'&anchor='+billingAnchor;
  });
  render('');
})();
