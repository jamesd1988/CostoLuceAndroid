# Costo Luce 5.2.0 — consumi per mese (Enel beta)

App Android per stimare i soli costi variabili dai kWh; Android Gradle Plugin e GitHub Actions.

## Nuova connessione diretta

- Pulsante **Aggiorna da Enel**: quando l’account è stato configurato, interroga i servizi Enel senza scaricare manualmente file o accedere al Portale Consumi ARERA.
- Configurazione iniziale direttamente sul dispositivo: email, password Enel Energia, POD e **numero utente Enel**. Non inviare tali credenziali in chat né inserirle nel repository.
- Credenziali **salvate solo su consenso esplicito**, cifrate con Android Keystore AES-GCM, mai nel sorgente, nei log, negli URL o nei server del progetto.
- Importa i dati degli **ultimi uno o due mesi solari conclusi**, non il mese corrente, e somma la serie `hourlyConsumption` restituendo solo il totale kWh. Qualsiasi errore o dati ambigui interrompono l’importazione.
- Tenta il login SAML e la lettura dell'endpoint Enel descritto dal progetto open-source **EnelGrid** (MIT): https://github.com/sathia-musso/enelgrid. L'integrazione è indipendente, non ufficiale, non affiliata a Enel Energia. Il codice è originale e segue lo schema pubblicamente descritto dall'autore.
- L’API e l’autenticazione sono **non documentate pubblicamente da Enel** e potrebbero cambiare, presentare CAPTCHA/MFA o richiedere ulteriore consenso. Non aggiriamo tali controlli e **non abbiamo verificato il collegamento con un account reale**.
- Nessun dato di energia o credenziale viene trasmesso a GitHub, ChatGPT o altri server; contatti HTTPS limitati a `www.enel.it` e `accounts.enel.com` durante il recupero.

## Test e compilazione

Vedi `.github/workflows/android.yml`. Il workflow compila un APK di debug e verifica l'avvio su emulatore, ma **non prova il login reale**, che richiede account e dati di fornitura personali.

Nota: la firma dell'APK di debug può cambiare a ogni run. Per sostituire una precedente build potrebbe essere necessario disinstallarla (perdendo i dati locali). Per release definitive servirà una firma persistente.

## Diagnosi 5.1

- Stato visibile per le quattro fasi di collegamento SAML e recupero kWh.
- Tempo massimo complessivo del client di circa 90 secondi e indicazione visibile dopo 100 secondi.
- Nessuna credenziale o contenuto delle risposte compare nella diagnostica o nei log CI.
- Collegamento reale non verificato: NON inserire credenziali in GitHub Actions o in chat.

- Pulsante **Diagnostica login Enel (senza password)**: controlla la sola pagina pubblica e la presenza del parametro SAML atteso, senza usare o trasmettere credenziali.

## Nomi e distribuzione v5.1.1

- Nome dell’app installata: **Costo Luce 5.1.1**.
- Versione Android: **5.1.1-enel-diagnostica**, codice versione **9**.
- APK prodotto: **CostoLuce_v5.1.1_EnelDiagnostica_S25.apk**.
- Distribuzione diretta: GitHub Release `v5.1.1` (prerelease; installazione ancora sperimentale). Non richiede la Libreria ChatGPT.

## Dettaglio mensile v5.2.0

- Selezionando **2 mesi** vengono mostrati i nomi degli ultimi due **mesi solari conclusi** (per esempio settembre e ottobre 2026 quando siamo a novembre).
- Dopo una sincronizzazione Enel riuscita, i dati `hourlyConsumption` sono aggregati **separatamente per mese**. Il riepilogo mostra nome/anno, kWh del mese, giorni con misure, totale del periodo e avvisi di incompletezza.
- Un mese senza misure è indicato **Non disponibile**, mai come 0 kWh. Anche il totale è etichettato **disponibile** se alcuni periodi sono incompleti.
- Non è possibile garantire tutte le misure orarie solo con i giorni: la schermata lo dichiara.
- Il calcolo in euro usa il totale dei kWh disponibili; per stime definitive servono misure complete.
- Corrige bug preesistente del tasto 1 mese / 2 mesi quando il vecchio collegamento ARERA non è presente.
- Test Java JUnit su raggruppamento mensile con date 2026 e cambio anno, e test JS di rendering del riepilogo.
- Versione applicazione **5.2.0-dettaglio-mensile**, `versionCode 10`, nome installato **Costo Luce 5.2.0**. APK `CostoLuce_v5.2.0_DettaglioMensile_S25.apk`.
- Rimane una **beta**: autenticazione e lettura dall'endpoint Enel non sono verificate in una sessione reale.
