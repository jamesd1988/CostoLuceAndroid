Costo Luce 6.3.0 — Widget adattivo

- Ridimensionamento in entrambe le direzioni: minimo richiesto 280 × 260 dp; dimensione iniziale 340 × 300 dp. La griglia del launcher determina le caselle effettivamente occupate.
- Formato compatto: periodo, costo/consumo, aggiornamento e navigazione; grafico con quattro barre, valori, mesi e mese corrente parziale.
- Formato medio: didascalie, scala essenziale e data di aggiornamento. Formato grande: scala completa, anni, stato dei mesi e informazioni aggiuntive.
- Il grafico viene disegnato per le dimensioni disponibili: niente immagine panoramica rimpicciolita in un widget stretto. Testi del grafico da 11–12 sp e pulsanti principali da 48 × 48 dp.
- Dimensioni esatte dal launcher su Android 12 e successivi; disposizione per entrambi gli orientamenti sulle versioni precedenti. I formati stretti/alti e larghi/bassi mantengono il livello di dettaglio compatto.
- Restano disponibili frecce/dot, selezione persistente € / kWh e aggiornamento in background. Lo swipe orizzontale rimane gestito dal launcher, senza gesture simulate.
- Dati mensili Enel reali, mesi mancanti distinti dagli zeri misurati, periodo corrente parziale. Credenziali, tariffe personalizzate, accisa e ciclo di fatturazione preservati.

Verifiche: test JavaScript e JVM, compilazione e prove delle vere RemoteViews nelle tre dimensioni, in formati stretti/alti e larghi/bassi, e con testo ingrandito. Le immagini CI mostrano lo stato senza consumi disponibili, senza introdurre dati dimostrativi nell’app. APK di test.
