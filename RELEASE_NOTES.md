Costo Luce 6.5.0 - Importazione tariffe ARERA

- In Tariffe: controllo del file ufficiale ARERA, selezione mese, residente/non residente e potenza impegnata, riepilogo e importazione esplicita.
- Importa rete, perequazione/qualità, ASOS e ARIM; converte le quote annuali in mensili e moltiplica le quote potenza per i kW indicati. Le quote fisse mancanti vengono aggiunte senza duplicare precedenti importazioni.
- Voci sempre modificabili con la matita e nomi personalizzati conservati. Prima di importare, verificare eventuali quote rete aggiunte con altri nomi per evitare duplicazioni.
- Controllo automatico del catalogo all'apertura, al massimo ogni 24 ore; tasto manuale per controllare subito. I controlli non sovrascrivono tariffe salvate. Nessun nuovo processo periodico in background.
- Ultimo controllo riuscito, mese/profilo/potenza importati e segnalazione delle successive modifiche manuali. Errori di rete o formato conservano i dati precedenti.
- Esclusi energia, commercializzazione, dispacciamento, sbilanciamento, perdite e imposte. Il dispacciamento E-Light richiede verifica contrattuale.
- Le tariffe del mese scelto diventano quelle correnti per tutti i calcoli, incluso il grafico storico: non è ancora una ricostruzione con tariffe diverse per ogni mese. Quote fisse con il conteggio mensile già presente.
- Credenziali salvate, consumi Enel, periodo bimestrale e calcolo accisa conservati. Il profilo residente ARERA è distinto dall'agevolazione accisa in Periodo.

Verifiche: parser con dati ufficiali agosto/settembre 2026, confronto rete/oneri con importi fatturati, rifiuto di intestazioni/unità alterate e XML non consentito; test importazione, potenza, profili, modifiche manuali e mesi mancanti, oltre ai controlli Android esistenti. APK di test.
