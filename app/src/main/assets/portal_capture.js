(function () {
  'use strict';
  // Eseguito SOLO su https://www.consumienergia.it o https://consumienergia.it.
  // Non viene mai eseguito sui provider SPID/CIE e non accede a cookie/credenziali.
  if (window.__costoLuceCaptureInstalled) return;
  window.__costoLuceCaptureInstalled = true;
  var lastSent = '';
  var scanTimer = 0;
  function numberFromItalian(raw) {
    var s = String(raw || '').replace(/[\s\u00a0\u202f]/g, '');
    if (!/^\d[\d.,]*$/.test(s)) return NaN;
    var c = s.lastIndexOf(','), d = s.lastIndexOf('.');
    if (c >= 0 && d >= 0) s = c > d ? s.replace(/\./g, '').replace(',', '.') : s.replace(/,/g, '');
    else if (c >= 0) s = s.replace(',', '.');
    else if (/^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, '');
    var n = Number(s);
    return isFinite(n) && n > 0 && n < 1000000 ? n : NaN;
  }
  function extractFromText(input) {
    var body = String(input || '').replace(/\u00a0/g, ' ');
    if (!/\bconsum[oi]\b|energia elettrica|preliev/i.test(body)) return null;
    var rows = body.split(/[\n\r]+/).map(function(x){return x.replace(/\s+/g,' ').trim();}).filter(Boolean);
    var expressions = [
      /(?:consum[oi](?:\s+di\s+energia)?|energia(?:\s+elettrica)?|preliev[oi])\s+(?:totale|complessiv[oa])[^\n\r]{0,48}?([\d][\d.,]*)\s*kwh\b/i,
      /(?:totale|complessiv[oa])\s+(?:dei\s+)?(?:consum[oi]|energia(?:\s+prelevata)?|preliev[oi])[^\n\r]{0,48}?([\d][\d.,]*)\s*kwh\b/i,
      /(?:totale\s+kwh|consumo\s+totale\s*\(?kwh\)?)\s*[:=\-]?\s*([\d][\d.,]*)\b/i,
      /([\d][\d.,]*)\s*kwh\b[^\n\r]{0,42}?(?:consumo\s+totale|totale\s+consum[oi])/i
    ];
    var numbers = [];
    for (var i = 0; i < rows.length; i++) {
      if (/\b(?:gas|smc|lettur[ae]|cumulat[oi]|potenza|produzione|immessa|immissione)\b/i.test(rows[i])) continue;
      var context = rows.slice(i, i + 3).join(' ');
      if (context.length > 250) context = context.slice(0, 250);
      for (var k = 0; k < expressions.length; k++) {
        var m = expressions[k].exec(context);
        if (m) {
          var v = numberFromItalian(m[1]);
          if (isFinite(v) && numbers.indexOf(v) < 0) numbers.push(v);
          break;
        }
      }
    }
    // Per sicurezza non importare automaticamente pagine con totali ambigui.
    return numbers.length === 1 ? numbers[0] : null;
  }
  function visibleText() {
    // innerText considera soltanto contenuti visualizzati; limite di lunghezza per evitare carichi eccessivi.
    return document.body ? String(document.body.innerText || '').slice(0, 80000) : '';
  }
  function status(t, ok) {
    var el = document.getElementById('costoluce-status');
    if (el) { el.textContent = t; el.style.color = ok ? '#047857' : '#475569'; }
  }
  function submitTotal(n) {
    if (!isFinite(n) || n <= 0) return;
    var s = String(Math.round(n * 1000) / 1000);
    if (lastSent === s) return;
    lastSent = s;
    // Intercettato dall'app SOLO quando la WebView si trova sul dominio istituzionale.
    window.location.href = 'costoluce://captured/' + s;
  }
  function scan(auto) {
    var text = visibleText();
    if (!/\bconsum[oi]\b/i.test(text) || !/\bkwh\b/i.test(text)) {
      status('Apri la sezione dei consumi elettrici e scegli il periodo.', false);
      return;
    }
    var total = extractFromText(text);
    if (total !== null) {
      status('Totale univoco trovato: ' + total.toLocaleString('it-IT') + ' kWh', true);
      if (auto) submitTotal(total);
    } else {
      status('Non trovo un totale univoco. Seleziona il riepilogo dei consumi.', false);
    }
  }
  function addPanel() {
    if (!document.body || document.getElementById('costoluce-panel')) return;
    var panel = document.createElement('div');
    panel.id = 'costoluce-panel';
    panel.style.cssText = 'position:fixed!important;z-index:2147483647!important;left:9px;right:9px;bottom:12px;background:#fff;color:#152844;border:1px solid #c7d5ff;border-radius:13px;padding:10px;box-shadow:0 5px 24px #0003;font:12px Arial,sans-serif;';
    panel.innerHTML = '<div style="display:flex;align-items:center;gap:6px;justify-content:space-between;flex-wrap:wrap">' +
      '<strong>Costo Luce · Sincronizzazione</strong>' +
      '<button id="costoluce-get" type="button" style="border:0;border-radius:8px;background:#365cda;color:white;padding:9px;font-weight:700">Acquisisci kWh</button>' +
      '<button id="costoluce-home" type="button" style="border:0;border-radius:8px;background:#edf2ff;color:#2448bd;padding:9px">Torna all’app</button>' +
      '</div><div id="costoluce-status" style="margin-top:7px;line-height:1.3"></div>';
    document.body.appendChild(panel);
    document.getElementById('costoluce-get').addEventListener('click', function () { scan(true); });
    document.getElementById('costoluce-home').addEventListener('click', function () { window.location.href = 'costoluce://home'; });
  }
  function scheduleScan() {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = setTimeout(function () { addPanel(); scan(true); }, 1250);
  }
  addPanel();
  scan(false);
  // SPA: il Portale può caricare i dati senza cambiare pagina.
  var observer = new MutationObserver(function (changes) {
    for (var i = 0; i < changes.length; i++) {
      var target = changes[i].target;
      if (target && target.closest && target.closest('#costoluce-panel')) continue;
      scheduleScan();
      break;
    }
  });
  if (document.body) observer.observe(document.body, {childList:true, subtree:true, characterData:true});
  scheduleScan();
  window.__costoLuceExtractTotalForTests = extractFromText;
})();
