'use strict';
function sizeSyncBar(){
  const root=byId('homeSync'),label=byId('syncButtonLabel');
  if(root&&label&&label.getBoundingClientRect)root.classList.toggle('sync-stacked',label.getBoundingClientRect().height>27);
}
function sizeHistoryLayout(){
  const chart=byId('historyChart'),viewport=byId('historyViewport');
  if(!chart||!viewport||!viewport.clientWidth||typeof chart.querySelectorAll!=='function')return;
  const columns=Array.from(chart.children),labels=Array.from(chart.querySelectorAll('.history-track strong'));
  if(!columns.length)return;
  const oldWidth=chart.scrollWidth,oldLeft=viewport.scrollLeft,keepEnd=typeof historyScrollAtEnd==='undefined'||historyScrollAtEnd;
  const visible=parseFloat(getComputedStyle(labels[0]||chart).fontSize)>18?2:4;
  chart.style.width='calc('+(columns.length/visible*100)+'% + '+(10/visible*columns.length-10)+'px)';
  const valueHeight=Math.max(0,...labels.map(e=>e.getBoundingClientRect().height));
  const first=columns[0],name=first.children[1],status=first.children[2],track=first.children[0];
  const probe=name.cloneNode(false),year=name.children[0]?name.children[0].cloneNode(false):document.createElement('span');probe.textContent='mag–mag';year.textContent='2025–2026';probe.appendChild(year);
  Object.assign(probe.style,{position:'absolute',visibility:'hidden',pointerEvents:'none',width:track.getBoundingClientRect().width+'px'});first.appendChild(probe);
  const nameHeight=Math.max(32,Math.ceil(probe.getBoundingClientRect().height));probe.remove();
  const small=status.cloneNode(false);small.textContent='Proiezione assente';Object.assign(small.style,{position:'absolute',visibility:'hidden',pointerEvents:'none',width:track.getBoundingClientRect().width+'px',height:'auto'});first.appendChild(small);
  const statusHeight=Math.max(12,Math.ceil(small.getBoundingClientRect().height));small.remove();
  chart.style.setProperty('--history-track-height',Math.max(150,Math.ceil(valueHeight+123))+'px');
  chart.style.setProperty('--history-name-height',nameHeight+'px');chart.style.setProperty('--history-status-height',statusHeight+'px');
  const plot=viewport.parentElement;plot.style.setProperty('--history-track-height',Math.max(150,Math.ceil(valueHeight+123))+'px');
  viewport.scrollLeft=keepEnd?Math.max(0,viewport.scrollWidth-viewport.clientWidth):(oldWidth?oldLeft*chart.scrollWidth/oldWidth:0);
}
function sizeAppChrome(){
  const nav=document.querySelector('.bottom-nav');if(nav)document.documentElement.style.setProperty('--navigation-height',Math.ceil(nav.getBoundingClientRect().height)+'px');
  sizeSyncBar();sizeHistoryLayout();if(typeof sizeProjectionStatus==='function')sizeProjectionStatus();
}
(()=>{
  const probe=document.createElement('div');Object.assign(probe.style,{display:'flex',flexDirection:'column',rowGap:'1px',position:'absolute',visibility:'hidden'});
  probe.appendChild(document.createElement('div'));probe.appendChild(document.createElement('div'));probe.firstChild.style.height='1px';probe.lastChild.style.height='1px';document.body.appendChild(probe);
  document.body.classList.toggle('legacy-flex-gap',probe.scrollHeight!==3);probe.remove();
  sizeAppChrome();window.addEventListener('resize',sizeAppChrome);
  if(typeof ResizeObserver==='function'){const observer=new ResizeObserver(sizeAppChrome),nav=document.querySelector('.bottom-nav');if(nav)observer.observe(nav);}
})();
