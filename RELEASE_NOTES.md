Costo Luce 6.12.1 - Correzione proiezione bimestrale

- Risolto il blocco «Un mese concluso contiene giornate incomplete» quando il mese ha un totale mensile valido e tutte le giornate presenti, ma una giornata non è idonea alla media settimanale oraria.
- I mesi conclusi conservano il totale reale Enel senza proiezione né integrazioni inventate. Rimangono i controlli sulla copertura mensile e sulla corrispondenza delle somme giornaliere.
- La media per i giorni restanti richiede sempre almeno 5 giornate complete nella settimana di riferimento. Non cambiano il metodo di previsione e il calcolo di fissi, variabili, accisa e IVA; canone TV separato.
- Messaggio coerente quando una proiezione non è disponibile. Test aggiunto sul caso ottobre–novembre con una giornata storica esclusa dalla media.
- Aggiornamento installabile sulla 6.12.0 con la stessa firma, conservando credenziali, tariffe, periodo e dati salvati.

Verifiche: suite JavaScript, test JVM e test Android su emulatore, inclusi il bimestre con giornata storica parziale e la soglia di 4/5/7 giorni recenti.
