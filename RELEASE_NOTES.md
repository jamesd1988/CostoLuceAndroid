# Costo Luce 6.24.0 — Android 16 / API 36

- compileSdk e targetSdk aggiornati a 36; compatibilità minima invariata ad Android 7 (API 24).
- Android Gradle Plugin 8.10.1, Gradle 8.11.1 e Build Tools 36.0.0.
- Gestione Indietro migrata a OnBackInvokedCallback su Android 13 e successivi, mantenendo il percorso legacy sui sistemi precedenti. Le schede interne e il dettaglio giornaliero si chiudono prima di lasciare l’app.
- Prove sul target effettivo 36: Android 16 telefono e tablet, sistemi precedenti, tastiera, orientamenti, caratteri grandi, widget e azione Indietro del sistema.
- Resta la gestione esplicita di barre di sistema e tastiera senza opt-out edge-to-edge. Aggiornamenti widget tramite JobScheduler, con fallback quando il lavoro accelerato viene rifiutato.

Credenziali, tariffe, fatture e preferenze salvate preservate. Stessa firma APK: aggiornamento senza disinstallare.
