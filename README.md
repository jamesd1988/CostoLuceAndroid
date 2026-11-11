# Costo Luce Cloud — Android (versione 4.1 auto-sync test)

**Obiettivo:** sostituire le precedenti APK prodotte con DEX artigianale con una build Android ufficiale prodotta da Gradle su GitHub Actions.

## Funzioni

- Calcolo delle **sole quote variabili** in funzione dei kWh inseriti; componenti modificabili, IVA e stima accisa.
- **Aggiorna consumi**: tenta di navigare automaticamente dopo il login nella sezione consumi elettrici, selezionare il periodo disponibile e trovare il totale in kWh. Funziona solo per etichette riconosciute: nessun accesso automatico a SPID/CIE e nessuna garanzia sulla pagina autenticata. L'utente effettua personalmente l'accesso SPID/CIE. Acquisizione **sperimentale**, non validata con una sessione reale: alcuni provider possono rifiutare l'accesso da WebView.
- **Apri in Chrome** come alternativa per accedere al portale. L'app **non può leggere** il contenuto della sessione esterna di Chrome.
- Importazione di file `.csv` / `.txt` dal telefono come ripiego (solo se necessario), oltre all'inserimento manuale dei kWh.
- Nessuna richiesta di password SPID/CIE da parte dell'app, nessun salvataggio di token/cookie da parte del codice applicativo. Lo script di lettura viene iniettato esclusivamente sul dominio ufficiale `consumienergia.it`.

## Compilazione nel cloud

1. Crea un repository privato su GitHub (ad esempio `CostoLuce-Android`).
2. Carica il contenuto di questa cartella nella radice del repository, mantenendo `.github/workflows/android.yml`.
3. In GitHub vai in **Actions** → **Android APK + test di avvio** → **Run workflow** (o esegui un push sul ramo `main`).
4. Il job `build` usa **JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, API 35**, compila con `:app:assembleDebug` ed espone l'APK negli Artifacts del workflow.
5. Il job `smoke-test` installa l'APK nell'emulatore Android, apre MainActivity e verifica che il processo resti in esecuzione. In caso di errore conserva il logcat negli Artifacts.
6. Scarica l'artifact `CostoLuce-GalaxyS25-debug` da GitHub Actions, estrai `app-debug.apk`, installalo sul Galaxy S25.

**Nota:** la compilazione e la prova su emulatore non sono state eseguite in questo ambiente. Non considerare l'APK funzionante prima che il workflow sia verde e che il Galaxy S25 lo apra correttamente.

## Requisiti e note

- Non c'è Gradle Wrapper in questa versione: il workflow installa **Gradle 8.9** tramite `gradle/actions/setup-gradle@v4`. Non sono inclusi artefatti generati con strumenti personalizzati.
- Package dell'app: `com.costoluce.cloud`. È distinto dalle build precedenti ed evita conflitti di firma in fase di installazione.
- L'APK di **debug** è firmato automaticamente dal runner GitHub e destinato alle prove, non alla distribuzione commerciale. Per aggiornamenti incrementali futuri servirà una chiave di firma persistente, conservata come GitHub Actions Secret.
- I consumi del Portale Consumi non sono necessariamente disponibili in tempo reale.
- Le tariffe predefinite corrispondono alla stima ricavata dalla precedente fattura giugno-luglio 2026: non sono automaticamente aggiornate.
- L'esenzione accisa è solo una stima, basata sull'ipotesi di consumo distribuito uniformemente nel periodo. L'utente deve verificare i requisiti della propria utenza.

## Test JavaScript locali (se disponibile Node.js)

```bash
node tests/test_calculations.js
node tests/test_import.js
node tests/test_capture.js
```

## Automazione 4.1

Il flusso mira a: Aggiorna consumi → SPID/CIE manuale → navigazione alla pagina consumi elettrici (se riconoscibile) → selezione di 1/2 mesi (se l’opzione esiste) → lettura totale. Nessun clic sui consensi/credenziali; non si utilizza scraping di sessioni Chrome esterne. Se ci sono più POD o periodo non identificabile, la sincronizzazione si ferma senza inventare dati. **La pagina privata reale non è stata verificata; il funzionamento end-to-end non è garantito.**
