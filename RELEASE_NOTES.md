Costo Luce 6.8.1 - Completa tutte le quote fisse della bolletta

- Corretto il completamento: aggiunge ora anche rete quota fissa, quota potenza e qualità, oltre a commercializzazione e DISPbt. Prima il pulsante della versione 6.8.0 completava solo le due quote di vendita.
- Un unico pulsante nell’app, sopra il grafico e in Tariffe, inserisce solo le cinque quote di riferimento mancanti. Non sostituisce importi già presenti, riconosce gli ID anche rinominati e nomi equivalenti; blocca doppioni, conflitti fisso/variabile e inserimenti parziali.
- Valori proposti dalla fattura E-Light agosto-settembre 2026, residente 3 kW: rete 1,92 + 5,88 + 0,0497 €/mese, commercializzazione 12 €/mese, DISPbt 0,092250 €/mese. Per utenze/potenze diverse usare il profilo ARERA e i prezzi del proprio contratto. Nessuna importazione automatica di prezzi storici su altri periodi.
- Nell’aggiornamento occorre premere «Aggiungi tutte le quote fisse mancanti» per completare le tariffe esistenti: nessuna modifica silenziosa delle personalizzazioni o delle voci eliminate. I nuovi costi sono salvati e sincronizzati con il widget.
- L’avviso di stima incompleta è ora sopra il grafico; anche il widget rileva l’assenza delle tre quote rete. Nel dettaglio espandibile di ogni bolletta: costi variabili + fissi, con IVA configurata inclusa.
- Periodicità e calcolo mensile dell’accisa invariati. Canone TV escluso. Le stime usano le tariffe salvate, non una ricostruzione delle fatture storiche.

Verifiche: regressione sulla configurazione solo variabile, tutte e cinque le quote e persistenza, importi personalizzati, duplicati, riconoscimento quote rete, totale agosto-settembre 127,56 euro con consumi e prezzi del periodo; test JS, unitari JVM, compilazione APK e smoke test Android/RemoteViews. APK di test con firma compatibile.
