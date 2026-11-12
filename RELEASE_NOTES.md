Costo Luce 6.3.1 — Correzione ridimensionamento verticale

- Rimosso il minimo implicito di 400 × 320 dp introdotto dalla decorazione vettoriale dello sfondo. Le onde mantengono lo stesso disegno e si adattano al contenitore, senza imporre una misura minima al widget.
- L’evento di ridimensionamento usa direttamente le nuove dimensioni fornite dal launcher.
- Rimangono i tre livelli di dettaglio e il minimo richiesto di 280 × 260 dp, soggetto alla griglia della Home.
- Nuova prova di regressione Android: confronto con lo sfondo della 6.3.0, misura dell’altezza richiesta senza forzarla e verifica in AppWidgetHostView dopo riduzioni successive da 500 a 260 dp, anche con misura AT_MOST.
- Credenziali, tariffe, consumi Enel reali, accisa, navigazione e refresh in background restano invariati.

Test JavaScript, JVM, compilazione e controlli Android 15 prima della pubblicazione. Le immagini CI mostrano dati mancanti e non inseriscono consumi dimostrativi. Il comportamento sulla Home Samsung segnalata va verificato con questo aggiornamento sul telefono: la prova su emulatore non sostituisce quel riscontro. APK di test.
