Costo Luce 6.4.1 — Aggiornamento nel widget compatto

- Pulsante ↻ sulla destra anche nel formato compatto a una riga, quando può convivere con kWh e costo leggibili. Funziona anche nella riga larga.
- Area del pulsante 48 × 48 dp; aggiornamento in background tramite broadcast e JobScheduler, senza apertura dell’app. Durante il recupero compare «…».
- Rimossa l’azione del contenitore quando il pulsante è visibile, anche riutilizzando una vista appena ridimensionata, per non intercettarne il tocco.
- Nei limiti estremi da 96 × 48 dp rimangono solo i due valori. Il pulsante viene mostrato da 120 × 56 dp se le cifre entrano mantenendo una dimensione leggibile; se non entrano, basta allargare il widget.
- Misure iniziali 2 × 1, dati Enel reali, credenziali e tariffe rimangono quelli già salvati.

Test JavaScript, JVM e Android 15, compresi formato 150 × 80 dp, testo al 130%, valori lunghi, stato di aggiornamento e riutilizzo della vista dopo ridimensionamento. Le immagini CI mostrano dati mancanti senza introdurre consumi dimostrativi. APK di test.
