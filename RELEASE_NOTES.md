# Costo Luce 6.16.0 — Costo corrente o proiezione nel widget compatto

- In Altro → Widget Home puoi scegliere Costo corrente oppure Proiezione bolletta. La preferenza viene salvata e vale per i widget che mostrano soltanto kWh e costo.
- I kWh rimangono sempre quelli misurati. La proiezione in € è preceduta da ≈; i due testi mantengono la stessa dimensione.
- Il widget calcola la proiezione in background dai giorni reali salvati, anche con l’app chiusa: media degli ultimi 7 giorni, con almeno 5 giorni completi, stessi costi fissi/variabili, accisa mensile e IVA della Home. Canone TV separato.
- Con letture mancanti, ferme o incoerenti viene mostrato — €, senza sostituire di nascosto la proiezione con il costo attuale. Aggiorna recupera i dati da Enel.
- I widget più grandi e i tre valori fissi della Home non cambiano. Preservati tariffe, credenziali e fatture storiche.

Verifica: 20 casi di confronto fra algoritmo nativo e quello reale della Home, test JVM/JavaScript, preferenza persistente e test Android con anteprime marcate come dati di test. Stessa firma: installazione sopra la versione precedente senza disinstallare.
