'use strict';
let dailySelection=null,dailyOriginY=0,dailySelectedDay=0,dailyKeepScroll=false;
function openMonthDaily(year,month){
  if(!Number.isInteger(year)||!Number.isInteger(month)||month<1||month>12)return;
  rememberHistoryScroll();dailyOriginY=window.scrollY||0;dailySelection={year,month};dailySelectedDay=0;dailyKeepScroll=false;
  showView('daily');renderDailyMonth();byId('dailyBack').focus();
}
function closeMonthDaily(){
  if(byId('page-daily').hidden)return false;
  showView('home');if(typeof requestAnimationFrame==='function')requestAnimationFrame(()=>window.scrollTo(0,dailyOriginY));return true;
}
function moveDailyMonth(direction){
  if(!dailySelection)return;const now=new Date(),current=now.getFullYear()*12+now.getMonth(),index=dailySelection.year*12+dailySelection.month-1+direction;
  if(index>current||index<current-23)return;dailySelection={year:Math.floor(index/12),month:index%12+1};dailySelectedDay=0;dailyKeepScroll=false;renderDailyMonth();
}
function selectDailyDay(day){const focused=byId('dailyChart').contains(document.activeElement);dailySelectedDay=day;dailyKeepScroll=true;renderDailyMonth();if(focused)byId('dailyChart').children[day-1]?.focus({preventScroll:true});}
function scrollDailyDays(direction){byId('dailyViewport').scrollBy({left:direction*byId('dailyViewport').clientWidth*.8,behavior:'smooth'});}
function dailyStatus(status){return status==='complete'?'Lettura completa':status==='partial'?'Lettura parziale / copertura oraria non confermata':status==='future'?'Giorno non ancora iniziato':'Lettura mancante';}
function renderDailyMonth(){
  if(!dailySelection||byId('page-daily').hidden)return;
  const {year,month}=dailySelection,today=new Date(),data=window.DailyUsage.build(historyRows,year,month,today);if(!data.valid)return;
  const label=italianMonth(year,month),viewport=byId('dailyViewport'),savedLeft=viewport.scrollLeft||0;
  byId('dailyTitle').textContent=label.charAt(0).toUpperCase()+label.slice(1);byId('dailyTotal').textContent=data.monthKwh===null?'—':decimal(data.monthKwh,2)+' kWh';
  byId('dailyCoverage').textContent=data.availableDays+' / '+data.days.length;byId('dailyWarning').textContent=data.reason;byId('dailyWarning').hidden=!data.reason;
  byId('dailyRecorded').textContent=data.availableDays?'Somma letture giornaliere: '+decimal(data.recordedKwh,2)+' kWh.':'Nessuna lettura giornaliera acquisita.';
  byId('dailyRefresh').href='costoluce://enel-month?year='+year+'&month='+month;
  const current=today.getFullYear()*12+today.getMonth(),index=year*12+month-1;byId('dailyPrevious').disabled=index<=current-23;byId('dailyNext').disabled=index>=current;
  const max=chartScale(Math.max(0,...data.days.map(d=>d.kwh||0))),axis=byId('dailyAxis');axis.innerHTML='';
  for(let tick=4;tick>=0;tick--){const text=document.createElement('span');text.style.top=((4-tick)/4*100)+'%';text.textContent=decimal(max*tick/4,max<.1?3:max<4?2:0);axis.appendChild(text);}
  const records=data.days.filter(d=>d.kwh!==null);if(!data.days.some(d=>d.day===dailySelectedDay))dailySelectedDay=0;if(!dailySelectedDay)dailySelectedDay=records.length?records[records.length-1].day:1;
  const chart=byId('dailyChart');chart.innerHTML='';const table=byId('dailyTableBody');table.innerHTML='';
  for(const entry of data.days){
    const button=document.createElement('button');button.type='button';button.className='daily-day '+entry.status+(dailySelectedDay===entry.day?' selected':'');button.setAttribute('aria-pressed',String(dailySelectedDay===entry.day));button.setAttribute('aria-label',entry.day+' '+label+': '+(entry.kwh===null?dailyStatus(entry.status):decimal(entry.kwh,2)+' kWh, '+dailyStatus(entry.status)));button.addEventListener('click',()=>selectDailyDay(entry.day));
    const track=document.createElement('span');track.className='daily-track';const value=document.createElement('strong');value.textContent=entry.kwh===null?'—':decimal(entry.kwh,2);track.appendChild(value);
    if(entry.kwh!==null){const bar=document.createElement('span');bar.className='daily-bar';bar.style.height=entry.kwh/max*145+'px';track.appendChild(bar);}
    button.appendChild(track);const day=document.createElement('span');day.className='daily-day-label';day.textContent=String(entry.day);button.appendChild(day);chart.appendChild(button);
    const line=document.createElement('tr');for(const text of [String(entry.day),entry.kwh===null?'—':decimal(entry.kwh,2),dailyStatus(entry.status)]){const cell=document.createElement('td');cell.textContent=text;line.appendChild(cell);}table.appendChild(line);
  }
  const selected=data.days[dailySelectedDay-1];byId('dailySelectedDate').textContent=selected.day+' '+label;byId('dailySelectedValue').textContent=selected.kwh===null?'—':decimal(selected.kwh,2)+' kWh';byId('dailySelectedStatus').textContent=dailyStatus(selected.status);
  const place=()=>{viewport.scrollLeft=dailyKeepScroll?savedLeft:Math.max(0,Math.min((dailySelectedDay-4)*48,viewport.scrollWidth-viewport.clientWidth));};if(typeof requestAnimationFrame==='function')requestAnimationFrame(place);else place();
}
(()=>{
  byId('dailyViewport').addEventListener('scroll',()=>dailyKeepScroll=true);
  const oldStatus=window.onEnelStatus;window.onEnelStatus=function(message,error){oldStatus(message,error);byId('dailySyncStatus').textContent=message;byId('dailySyncStatus').classList.toggle('error',Boolean(error));};
  const oldHistory=window.onHistoryNative;window.onHistoryNative=function(rows,at){oldHistory(rows,at);if(dailySelection&&!byId('page-daily').hidden){byId('dailySyncStatus').textContent='Letture aggiornate.';renderDailyMonth();}};
})();
