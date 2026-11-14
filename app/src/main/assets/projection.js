(function(scope){
  'use strict';
  const DAY=86400000;
  const ordinal=(y,m,d)=>Date.UTC(y,m-1,d)/DAY;
  const iso=index=>new Date(index*DAY).toISOString().slice(0,10);
  const monthDays=(y,m)=>new Date(Date.UTC(y,m,0)).getUTCDate();
  function calculate({rows,period,today=new Date(),price}){
    const unavailable=reason=>({available:false,reason});
    if(!Array.isArray(rows)||!Array.isArray(period)||period.length<1||period.length>3||typeof price!=='function')return unavailable('Dati di proiezione non disponibili.');
    const now=ordinal(today.getFullYear(),today.getMonth()+1,today.getDate());
    if(!Number.isFinite(now))return unavailable('Data di riferimento non valida.');
    const daily=new Map(),monthly=new Map();
    for(const row of rows){
      if(!row||!Number.isInteger(row.year)||!Number.isInteger(row.month)||row.year<2000||row.year>2100||row.month<1||row.month>12)continue;
      const key=row.year*12+row.month;
      if(monthly.has(key))return unavailable('Consumi mensili duplicati: aggiorna Enel.');
      monthly.set(key,row);
      if(!Array.isArray(row.daily))continue;
      if(row.daily.length>31)return unavailable('Dati giornalieri non validi: aggiorna Enel.');
      for(const day of row.daily){
        if(!day||!Number.isInteger(day.day)||day.day<1||day.day>monthDays(row.year,row.month)||!Number.isFinite(day.kwh)||day.kwh<0||day.kwh>250000)return unavailable('Dati giornalieri non validi: aggiorna Enel.');
        const date=ordinal(row.year,row.month,day.day);
        if(daily.has(date)||date>now)return unavailable('Dati giornalieri duplicati o futuri: aggiorna Enel.');
        daily.set(date,{...day,complete:day.complete===true&&Number.isInteger(day.hourSamples)&&day.hourSamples>=23&&day.hourSamples<=25});
      }
    }
    const complete=Array.from(daily.keys()).filter(date=>date<now&&daily.get(date).complete).sort((a,b)=>b-a),last=complete[0];
    if(last===undefined)return unavailable('Servono almeno 5 giorni completi registrati. Premi Aggiorna per acquisire le letture giornaliere Enel.');
    if(now-last>7)return unavailable('Le letture giornaliere sono ferme da oltre 7 giorni. Aggiorna Enel per una proiezione recente.');
    const sample=complete.filter(date=>date>=last-6);
    if(sample.length<5)return unavailable('Servono almeno 5 giorni completi registrati nella settimana di riferimento ('+sample.length+' disponibili).');
    const average=sample.reduce((sum,date)=>sum+daily.get(date).kwh,0)/sample.length;
    let value=0,kwh=0,variable=0,fixed=0,observed=0,remainingDays=0;
    const output=[];
    for(let i=0;i<period.length;i++){
      const item=period[i];
      if(!item||!Number.isInteger(item.year)||!Number.isInteger(item.month)||item.month<1||item.month>12||item.year<2000||item.year>2100||(i&&item.year*12+item.month!==period[i-1].year*12+period[i-1].month+1))return unavailable('Periodo di fatturazione non valido.');
      const first=ordinal(item.year,item.month,1),days=monthDays(item.year,item.month),end=first+days-1,row=monthly.get(item.year*12+item.month);
      let actual=0,projected=0,estimatedDays=0;
      if(end<now&&row?.available&&!row.partial&&Number.isFinite(row.kwh)&&row.kwh>=0){
        if(Array.isArray(row.daily)&&row.daily.length){
          let verified=0;for(let d=0;d<days;d++){const day=daily.get(first+d);if(!day?.complete)return unavailable('Un mese concluso contiene giornate incomplete. Aggiorna Enel prima di calcolare la proiezione.');verified+=day.kwh;}
          if(Math.abs(verified-row.kwh)>.02)return unavailable('Totale mensile e dettaglio giornaliero non coincidono. Aggiorna Enel.');
        }
        actual=projected=row.kwh;
      }
      else if(end<now)return unavailable('Manca un mese concluso completo nel periodo. Aggiorna Enel prima di calcolare la proiezione.');
      else{
        if(row?.available&&(!Array.isArray(row.daily)||!row.daily.length))return unavailable('Premi Aggiorna: i consumi salvati non contengono ancora il dettaglio giornaliero.');
        for(let d=0;d<days;d++){
          const date=first+d,day=daily.get(date),measured=day?.kwh||0;
          actual+=measured;
          if(date<now&&day?.complete)projected+=measured;
          else {projected+=Math.max(measured,average);estimatedDays++;}
        }
        if(row?.available&&(!Number.isFinite(row.kwh)||row.kwh<0||Math.abs(row.kwh-actual)>.02))return unavailable('Totale mensile e dettaglio giornaliero non coincidono. Aggiorna Enel.');
      }
      const parts=price(projected);
      if(!parts||!['total','variable','fixed'].every(key=>Number.isFinite(parts[key])&&parts[key]>=0))return unavailable('Verifica le tariffe per calcolare la proiezione.');
      kwh+=projected;observed+=actual;remainingDays+=estimatedDays;value+=parts.total;variable+=parts.variable;fixed+=parts.fixed;
      output.push({...item,kwh:projected,observedKwh:actual,estimatedDays});
    }
    const start=ordinal(period[0].year,period[0].month,1),endItem=period[period.length-1],end=ordinal(endItem.year,endItem.month,monthDays(endItem.year,endItem.month));
    if(now<start||now>end)return unavailable('Il periodo scelto non è quello corrente.');
    return {available:true,value:Math.round((value+Number.EPSILON)*100)/100,kwh,variable,fixed,observedKwh:observed,extraKwh:Math.max(0,kwh-observed),average,sampleDays:sample.length,sampleFrom:iso(sample[sample.length-1]),sampleTo:iso(last),from:iso(start),to:iso(end),remainingDays,monthly:output};
  }
  scope.BillProjection={calculate};
  if(typeof module==='object'&&module.exports)module.exports=scope.BillProjection;
})(typeof window==='object'?window:globalThis);
