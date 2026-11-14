(function(scope){
  'use strict';
  function build(rows,year,month,today=new Date()){
    const count=new Date(year,month,0).getDate(),current=today.getFullYear()*12+today.getMonth(),key=year*12+month-1;
    if(!Number.isInteger(year)||!Number.isInteger(month)||year<2000||year>2100||month<1||month>12||key>current)return {valid:false,reason:'Mese non valido.'};
    const matches=Array.isArray(rows)?rows.filter(r=>r.year===year&&r.month===month):[],source=matches[0],records=new Map();
    let reason='',recordedKwh=0;
    if(matches.length>1)reason='Dati mensili duplicati: aggiorna questo mese da Enel.';
    if(!reason&&Array.isArray(source?.daily))for(const record of source.daily){
      if(!record||!Number.isInteger(record.day)||record.day<1||record.day>count||records.has(record.day)||!Number.isFinite(record.kwh)||record.kwh<0||!Number.isInteger(record.hourSamples)||record.hourSamples<1||record.hourSamples>25||(key===current&&record.day>today.getDate())){reason='Dettaglio giornaliero non valido: aggiorna questo mese da Enel.';break;}
      records.set(record.day,record);recordedKwh+=record.kwh;
    }
    if(reason){records.clear();recordedKwh=0;}
    const days=[];for(let day=1;day<=count;day++){
      const future=key===current&&day>today.getDate(),record=records.get(day),available=!future&&Boolean(record);
      days.push({day,kwh:available?record.kwh:null,status:future?'future':!available?'missing':record.complete===true&&(key!==current||day<today.getDate())?'complete':'partial'});
    }
    const monthKwh=source?.available&&Number.isFinite(source.kwh)&&source.kwh>=0?source.kwh:null;
    if(!reason&&!records.size)reason='Dettaglio giornaliero non ancora disponibile. Aggiorna questo mese da Enel per recuperare le letture reali.';
    else if(!reason&&monthKwh!==null&&Math.abs(monthKwh-recordedKwh)>.02)reason='Il dettaglio giornaliero e il totale mensile non coincidono. Aggiorna questo mese da Enel; le letture mostrate non sono ripartizioni stimate.';
    return {valid:true,year,month,days,recordedKwh,monthKwh,availableDays:records.size,reason};
  }
  scope.DailyUsage={build};if(typeof module==='object'&&module.exports)module.exports=scope.DailyUsage;
})(typeof window==='object'?window:globalThis);
