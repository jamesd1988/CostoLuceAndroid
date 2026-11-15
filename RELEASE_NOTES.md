# Costo Luce 6.17.0 — Grafici essenziali e proiezione con sette giorni

- Rimossi dalla Home i pulsanti Precedenti/Recenti, il range e le spiegazioni sotto i grafici, il doppio importo di fine periodo e i dettagli/somme delle fatture.
- L’archivio e i dati delle fatture rimangono accessibili in Altro → Account Enel → Archivio e dettagli delle fatture. Lo storico continua a scorrere orizzontalmente.
- Bolletta, proiezione disattivata: “Stima bolletta al” con l’ultima data di consumo rilevato del periodo, distinta dalla data di sincronizzazione.
- Bolletta, proiezione attivata: “Proiezione fattura completa basata sugli ultimi 7 giorni rilevati”. Se la settimana è incompleta: “Proiezione non disponibile: aspettare 7 giorni rilevati”; nessuna barra di proiezione.
- Il requisito diventa di sette giorni completi della settimana di riferimento, anche nella Home e nei widget. Nessun importo inventato o sostituito con la stima attuale.
- Mantenuti aggiornamento Enel, tariffe, credenziali, fatture emesse e i tre valori fissi del riepilogo Home.

Verifica dei calcoli JavaScript/Java con 22 casi di confronto, soglia sei/sette giorni, assenza di proiezione, testi e layout Android. Aggiornamento con la stessa firma, senza disinstallare.
