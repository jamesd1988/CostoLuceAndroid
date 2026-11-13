(function () {
  'use strict';
  // Script eseguito soltanto nelle pagine ufficiali consumienergia.it.
  // Non interagisce mai con pagine, PIN o pulsanti SPID/CIE.
  if (window.__costoLuceCaptureInstalled) return;
  window.__costoLuceCaptureInstalled = true;
  var selectedMonths = Number(window.__costoLuceMonths) === 1 ? 1 : Number(window.__costoLuceMonths) === 3 ? 3 : 2;
  var lastSent = '';
  var scanTimer = 0;
  var lastAction = 0;
  var actionAttempts = 0;
  var maxActions = 4;
  var periodAttempted = false;

  function numberFromItalian(raw) {
    var s = String(raw || '').replace(/[\s\u00a0\u202f]/g, '');
    if (!/^\d[\d.,]*$/.test(s)) return NaN;
    var comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
    if (comma >= 0 && dot >= 0) {
      s = comma > dot ? s.replace(/\./g, '').replace(',', '.') : s.replace(/,/g, '');
    } else if (comma >= 0) s = s.replace(',', '.');
    else if (/^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, '');
    var n = Number(s);
    return isFinite(n) && n > 0 && n < 1000000 ? n : NaN;
  }

  function extractFromText(input) {
    var body = String(input || '').replace(/\u00a0/g, ' ');
    if (!/consum|energia elettrica|preliev/i.test(body)) return null;
    var rows = body.split(/[\n\r]+/).map(function(x) { return x.replace(/\s+/g, ' ').trim(); }).filter(Boolean);
    var expressions = [
      /(?:consum[oi](?:\s+di\s+energia)?|energia(?:\s+elettrica)?|preliev[oi])\s+(?:totale|complessiv[oa])[^\n\r]{0,48}?([\d][\d.,]*)\s*kwh\b/i,
      /(?:totale|complessiv[oa])\s+(?:dei\s+)?(?:consum[oi]|energia(?:\s+prelevata)?|preliev[oi])[^\n\r]{0,48}?([\d][\d.,]*)\s*kwh\b/i,
      /(?:totale\s+kwh|consumo\s+totale\s*\(?kwh\)?)\s*[:=\-]?\s*([\d][\d.,]*)\b/i,
      /([\d][\d.,]*)\s*kwh\b[^\n\r]{0,42}?(?:consumo\s+totale|totale\s+consum[oi])/i,
      /(?:consum[oi]\s+nel\s+periodo|energia\s+consumata\s+nel\s+periodo)\s*[:=\-]?\s*([\d][\d.,]*)\s*kwh\b/i,
      /([\d][\d.,]*)\s*kwh\b\s*(?:consum[oi]\s+nel\s+periodo|energia\s+consumata\s+nel\s+periodo)/i,
      /(?:consumo|energia)\s+del\s+periodo\s*[:=\-]?\s*([\d][\d.,]*)\s*kwh\b/i
    ];
    var numbers = [];
    for (var i = 0; i < rows.length; i++) {
      if (/\b(?:gas|smc|lettur[ae]|cumulat[oi]|potenza|produzione|immessa|immissione)\b/i.test(rows[i])) continue;
      var context = rows.slice(i, i + 3).join(' ');
      if (context.length > 250) context = context.slice(0, 250);
      if (/\b(?:gas|smc|lettur[ae]|cumulat[oi]|potenza|produzione|immessa|immissione)\b/i.test(context)) continue;
      for (var k = 0; k < expressions.length; k++) {
        var m = expressions[k].exec(context);
        if (m) {
          var v = numberFromItalian(m[1]);
          if (isFinite(v) && numbers.indexOf(v) < 0) numbers.push(v);
          break;
        }
      }
    }
    return numbers.length === 1 ? numbers[0] : null;
  }

  function visibleText() {
    return document.body ? String(document.body.innerText || '').slice(0, 80000) : '';
  }
  function status(message, success) {
    var node = document.getElementById('costoluce-status');
    if (node) {
      node.textContent = message;
      node.style.color = success ? '#047857' : '#475569';
    }
  }
  function submitTotal(n) {
    if (!isFinite(n) || n <= 0) return;
    var value = String(Math.round(n * 1000) / 1000);
    if (lastSent === value) return;
    lastSent = value;
    window.location.href = 'costoluce://captured/' + value;
  }

  function visibleControl(element) {
    if (!element || element.disabled) return false;
    if (element.closest && element.closest('#costoluce-panel')) return false;
    if (element.getAttribute && element.getAttribute('aria-hidden') === 'true') return false;
    if (element.getBoundingClientRect) {
      var rect = element.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return false;
    }
    return true;
  }
  function safeClick(control, action) {
    if (actionAttempts >= maxActions || !visibleControl(control)) return false;
    var now = Date.now();
    if (now - lastAction < 2200) return false;
    // Solo elementi della pagina corrente; mai seguire automaticamente URL esterni.
    var href = control.getAttribute && control.getAttribute('href');
    if (href && /^(https?:|\/\/)/i.test(href)) {
      try {
        var dest = new URL(href, window.location.href);
        if (dest.protocol !== 'https:' || !/^(www\.)?consumienergia\.it$/i.test(dest.hostname)) return false;
      } catch (ignored) { return false; }
    }
    // Evita click ripetuti nei redirect o nelle navigazioni che ricaricano la pagina.
    try {
      var key = 'cl-auto-' + action;
      var previous = Number(window.sessionStorage.getItem(key) || 0);
      if (now - previous < 15000) return false;
      window.sessionStorage.setItem(key, String(now));
    } catch (ignored) { }
    actionAttempts++;
    lastAction = now;
    control.click();
    return true;
  }
  function findControl(pattern) {
    if (!document.querySelectorAll) return null;
    var all = document.querySelectorAll('a,button,[role="button"],[role="link"],label,option');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (!visibleControl(el)) continue;
      var val = String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
      if (val.length > 90 || !pattern.test(val)) continue;
      if (/entra|spid|cie|autorizza|consenso|scarica|elimina|logout|esci/i.test(val)) continue;
      if (el.getAttribute && (el.getAttribute('aria-current') === 'page' || /\b(active|selected|current)\b/i.test(el.className || ''))) continue;
      return el;
    }
    return null;
  }
  function selectPeriod() {
    if (periodAttempted) return false;
    var pattern = selectedMonths === 1
      ? /^(?:ultimo mese|ultimi 30 giorni|1 mese|mensile)$/i
      : /^(?:ultimi due mesi|ultimi 2 mesi|2 mesi|bimestre|bimestrale)$/i;
    var control = findControl(pattern);
    if (!control) return false;
    periodAttempted = true;
    if (String(control.tagName).toUpperCase() === 'OPTION') {
      var select = control.parentElement;
      if (!select || !visibleControl(select)) return false;
      control.selected = true;
      if (typeof Event === 'function') select.dispatchEvent(new Event('change', {bubbles:true}));
      status('Periodo selezionato automaticamente, recupero il consumo…', false);
      return true;
    }
    if (safeClick(control, 'period-' + selectedMonths)) {
      status('Periodo selezionato automaticamente, recupero il consumo…', false);
      return true;
    }
    return false;
  }
  function navigateAfterLogin(body) {
    // Nessun click sul login o sui consensi SPID/CIE.
    if (/entra\s+con\s+(?:spid|cie)|accedi\s+con\s+(?:spid|cie)/i.test(body) &&
        !/\bconsum[oi]\s+nel\s+periodo\b|\bseleziona\s+fornitura\b/i.test(body)) {
      status('Effettua l’accesso SPID/CIE: il resto sarà automatico.', false);
      return;
    }
    if (selectPeriod()) return;
    var controls = [
      {pattern:/^i miei consumi$/i, action:'my-consumptions'},
      {pattern:/^(?:consumi elettrici|energia elettrica|fornitura elettrica|luce)$/i, action:'electricity'},
      {pattern:/^(?:visualizza consumi|dettaglio consumi|vai ai consumi)$/i, action:'details'}
    ];
    for (var i = 0; i < controls.length; i++) {
      var found = findControl(controls[i].pattern);
      if (found && safeClick(found, controls[i].action)) {
        status('Cerco automaticamente i consumi elettrici…', false);
        return;
      }
    }
    status('Accesso rilevato. Non posso identificare con sicurezza la sezione o il periodo: nessun dato è stato importato.', false);
  }
  function scan() {
    if(selectedMonths===3){status('Per il trimestre usa la connessione Enel o importa un file con il periodo esatto. Il recupero automatico dal Portale Consumi non verifica questo raggruppamento.',false);return;}
    var text = visibleText();
    // Non importare totali quando si è sulla pagina pubblica di autenticazione.
    if (/entra\s+con\s+(?:spid|cie)|accedi\s+con\s+(?:spid|cie)/i.test(text) &&
        !/\bconsum[oi]\s+nel\s+periodo\b|\bseleziona\s+fornitura\b/i.test(text)) {
      navigateAfterLogin(text);
      return;
    }
    // Se esiste una selezione esplicita del periodo richiesto, attivala prima del recupero.
    if (selectPeriod()) return;
    // Se ci sono più forniture elettriche visibili senza una selezione univoca, non sceglierne una a caso.
    var podMatches = text.match(/\bIT\d{3}E\d{8}\b/gi) || [];
    var distinctPods = podMatches.filter(function(x,i){return podMatches.indexOf(x)===i;});
    if (distinctPods.length > 1) {
      status('Sono presenti più forniture elettriche: serve scegliere una fornitura almeno una volta.', false);
      return;
    }
    var total = extractFromText(text);
    if (total !== null) {
      status('Trovati ' + total.toLocaleString('it-IT') + ' kWh. Importazione automatica…', true);
      submitTotal(total);
      return;
    }
    navigateAfterLogin(text);
  }
  function addPanel() {
    if (!document.body || document.getElementById('costoluce-panel')) return;
    var panel = document.createElement('div');
    panel.id = 'costoluce-panel';
    panel.style.cssText = 'position:fixed!important;z-index:2147483647!important;left:9px;right:9px;bottom:12px;background:#fff;color:#152844;border:1px solid #c7d5ff;border-radius:13px;padding:10px;box-shadow:0 5px 24px #0003;font:12px Arial,sans-serif;';
    panel.innerHTML = '<div style="display:flex;align-items:center;gap:6px;justify-content:space-between">' +
      '<strong>Costo Luce · Aggiornamento automatico</strong>' +
      '<button id="costoluce-home" type="button" style="border:0;border-radius:8px;background:#edf2ff;color:#2448bd;padding:9px">Esci</button>' +
      '</div><div id="costoluce-status" style="margin-top:7px;line-height:1.4">Cerco i consumi…</div>';
    document.body.appendChild(panel);
    document.getElementById('costoluce-home').addEventListener('click', function() {
      window.location.href='costoluce://home';
    });
  }
  function scheduleScan() {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = setTimeout(function() { addPanel(); scan(); }, 1600);
  }
  addPanel();
  var observer = new MutationObserver(function(changes) {
    for (var i = 0; i < changes.length; i++) {
      var target = changes[i].target;
      if (target && target.closest && target.closest('#costoluce-panel')) continue;
      scheduleScan();
      break;
    }
  });
  if (document.body) observer.observe(document.body, {childList:true,subtree:true,characterData:true});
  scheduleScan();
  window.__costoLuceExtractTotalForTests = extractFromText;
  window.__costoLuceNavigateForTests = navigateAfterLogin;
})();
