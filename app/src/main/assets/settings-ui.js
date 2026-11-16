'use strict';
let settingsActivePanel=null;
function openSettingsPanel(panel){
  if(!['widget','account'].includes(panel))return false;
  settingsActivePanel=panel;
  byId('settingsMenu').hidden=true;byId('settingsBack').hidden=false;
  byId('settingsWidget').hidden=panel!=='widget';byId('settingsAccountTools').hidden=panel!=='account';
  byId('settingsTitle').textContent=panel==='widget'?'Impostazioni widget':'Strumenti account Enel';
  if(typeof window.scrollTo==='function')window.scrollTo(0,0);
  return true;
}
function resetSettingsPanel(){
  settingsActivePanel=null;byId('settingsMenu').hidden=false;byId('settingsBack').hidden=true;
  byId('settingsWidget').hidden=true;byId('settingsAccountTools').hidden=true;byId('settingsTitle').textContent='Impostazioni';
}
function closeSettingsPanel(){
  if(!settingsActivePanel||byId('page-settings').hidden)return false;
  resetSettingsPanel();if(typeof window.scrollTo==='function')window.scrollTo(0,0);return true;
}
resetSettingsPanel();
