# Costo Luce 5.6.0 — aggiornamento widget senza aprire l'app (Enel beta)

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

## Widget Android v5.3.0

- Un widget Home Android (dimensioni di base circa 3 × 2 celle) mostra **ultimo costo variabile stimato**, **kWh**, **periodo**, **origine** e **data di salvataggio**.
- Dal calcolatore usa **Aggiorna widget Home**, dopo aver realmente inserito o importato kWh. Il valore iniziale dimostrativo non viene inviato al widget.
- I dati sono memorizzati solo localmente in SharedPreferences; non vengono salvate password, POD, cookie o dati personali sul widget.
- Il widget mostra sempre l'ultimo riepilogo **esplicitamente salvato**, non effettua interrogazioni automatiche o periodiche a Enel. Toccandolo si apre Costo Luce.
- Per aggiungerlo su Samsung: pressione prolungata schermata Home → Widget → Costo Luce 5.3.0.
- Versione Android `5.3.0-home-widget`, versionCode 11, file APK `CostoLuce_v5.3.0_WidgetHome_S25.apk`.
- Attenzione: prezzo stimato basato sulle tariffe attualmente configurate nell'app, non fattura definitiva.

## Widget aggiornabile v5.4.0

- Nuovo pulsante **↻** direttamente sul widget Android. L'interazione pianifica un `JobScheduler` Android con rete richiesta, quindi può eseguirsi senza aprire l'app (a seconda delle restrizioni di Android sulla batteria).
- Il widget usa le credenziali Enel solo quando l'utente le ha già salvate cifrate con AndroidKeyStore nelle impostazioni dell'app. **Non** le memorizza nel widget e non le invia a terzi.
- Recupera gli ultimi 1 o 2 mesi solari chiusi (secondo l'ultimo periodo configurato), ricalcola **kWh + costo stimato** usando i dieci corrispettivi e il criterio accisa salvati al momento di **Aggiorna widget Home**.
- Mostra stato richiesta, errore o dati parziali e mantiene il precedente riepilogo in caso di mancata connessione; non spaccia mai un vecchio valore per aggiornato.
- I valori aggiornati sul widget possono essere ripristinati nell'app quando viene riaperta. Anche un aggiornamento Enel dall'app aggiorna il widget se lo stesso era configurato.
- Per consumi manuali/CSV senza login Enel salvato, il pulsante informa che non può acquisire automaticamente nuovi kWh. Non tenta di inventarli.
- **Versione 5.4.0, versionCode 12**, APK `CostoLuce_v5.4.0_WidgetRefresh_S25.apk`.
- Non è un servizio real-time, non è una sincronizzazione periodica; la possibilità effettiva di login Enel rimane **beta non verificata** su account reali.

## Build semplificata 5.5.0

- Workflow attivo: `.github/workflows/apk-release.yml`. Un solo job: test JS + JUnit + compilazione Android + verifica manifest + pubblicazione diretta su GitHub Releases.
- L'APK viene pubblicato **subito dopo la compilazione**, senza dipendere dal test su emulatore né dal caricamento degli artifact Actions, entrambi problematici nei vecchi run.
- Il precedente `android.yml` è eseguibile solo manualmente per diagnostica Android. Il nuovo workflow si avvia con modifiche su `main` o manualmente.
- I runner GitHub sono comunque necessari: se Actions resta in stato `queued` senza job, rifare il workflow non risolve automaticamente un problema di infrastruttura o permessi.

## Widget in background v5.6.0

- Il tasto **↻** in alto a destra e la riga inferiore del widget inviano un `PendingIntent.getBroadcast` a `CostoLuceWidgetProvider`, **non avviano un'Activity**. Le altre parti continuano ad aprire la schermata principale, quando desiderato.
- Il ricevitore cambia subito il testo di stato, poi pianifica un JobScheduler che recupera i consumi Enel in background. Il job usa esclusivamente credenziali già salvate con consenso tramite Android Keystore. A esito positivo aggiorna contemporaneamente kWh, periodo, stima dei costi e timestamp del widget con le tariffe configurate.
- Se la rete, il login non ufficiale Enel o i dati non sono disponibili, **non** cambia il costo e il consumo precedentemente acquisiti e segnala l'errore in chiaro senza informazioni sensibili.
- Non aggiorna in automatico tariffe ARERA/GME né può aggirare CAPTCHA o SPID. Senza credenziali Enel salvate e autenticazione funzionante il tasto non può reperire nuovi kWh.
- Test del collegamento nel file `tests/test_widget_wiring.js`; il workflow release controlla che l'Activity precedente non sia più registrata nel Manifest.
- Versione Android **5.6.0**, `versionCode 14`, file `CostoLuce_v5.6.0_WidgetBackgroundRefresh_S25.apk`. In caso di app firmata diversamente, una nuova installazione può richiedere rimozione del vecchio pacchetto e comportare perdita dei dati locali.

## Prima configurazione widget v5.7.0

- Si possono salvare i **dieci corrispettivi, periodo 1/2 mesi e opzione residente** anche senza consumi reali, evitando lo stallo iniziale del widget vuoto.
- Il pulsante **Salva configurazione widget** invia `costoluce://widget-config` solo quando i kWh provengono ancora dall'esempio iniziale; il codice nativo valida e salva le tariffe ma non inserisce mai kWh dimostrativi.
- Se esistono consumi reali manuali/importati/Enel, rimane attivo `widget-save`, con snapshot validato.
- Il widget mostra distintamente "Configurato · premi ↻ per i dati Enel" prima della prima acquisizione.
- La sincronizzazione dal widget continua a richiedere credenziali Enel **salvate volontariamente e cifrate** e un accesso effettivamente funzionante. Il login Enel sperimentale non è ancora stato verificato con un account reale.
- Versione **5.7.0**, codice versione 15, APK `CostoLuce_v5.7.0_WidgetSetupAndRefresh_S25.apk`.

## Widget automatico e nuova grafica v5.8.0

- **Rimosso il passaggio manuale «Salva configurazione widget».** La pagina calcolatore trasferisce automaticamente al widget le impostazioni al primo avvio, alla modifica delle tariffe, del periodo, dell'opzione residente, e quando i kWh reali vengono inseriti o acquisiti da file/portale/Enel. Invio tramite schema `costoluce://widget-config` o `costoluce://widget-save`, intercettato esclusivamente dalla WebView locale dell'app, con debounce di 450 ms; nessuna credenziale passa negli URL.
- I **479 kWh dimostrativi** iniziali non vengono mai pubblicati: prima di acquisire consumi reali si inviano solo tariffe e periodo.
- Memorizzazione separata tra `configured_months` (futura acquisizione) e `months` (periodo dell'ultimo consumo): i vecchi dati non vengono erroneamente attribuiti al nuovo periodo, neppure durante la riapertura dell'app.
- Quando cambiano solo i prezzi, il costo è ricalcolato ma **la data dell'ultimo consumo non viene aggiornata artificialmente**. Le misure mensili Enel restano preservate finché il dato è lo stesso.
- Widget riprogettato: gradiente blu profondo, tipografia gerarchica, costo in evidenza, pill verde-acqua dei kWh, header dedicato, data e periodo, footer di stato separato e aggiornamento ↻ da 46 dp.
- Il pulsante ↻ continua a funzionare in background tramite `PendingIntent.getBroadcast` + `JobScheduler`, senza aprire il calcolatore. Necessario account Enel configurato con credenziali salvate volontariamente e cifrate sul telefono; il connettore non ufficiale resta sperimentale.
- Versione **5.8.0**, `versionCode 16`; APK: `CostoLuce_v5.8.0_AutoSyncWidget_S25.apk`.

## Periodo di fatturazione in corso (v5.9.0)

- Allineamento al ciclo Enel di due mesi **con inizio nei mesi pari**: giugno-luglio, agosto-settembre, ottobre-novembre, dicembre-gennaio, febbraio-marzo, aprile-maggio (coerente con l'ultima fattura giugno-luglio). In novembre il periodo selezionato è **1 ottobre – oggi**, e il widget lo presenta come **ottobre + novembre in corso**.
- La modalità «Mese corrente» richiede solo i consumi dal giorno 1 del mese alla data odierna.
- I giorni successivi a oggi non sono richiesti, proiettati né sommati; la lettura Enel può arrivare in ritardo. Il mese corrente è sempre indicato come **in corso** e il costo come **provvisorio**.
- Se il bimestre appena iniziato include un mese futuro (es. dicembre-gennaio, a dicembre), il secondo mese è segnalato **«Non iniziato»**, con 0 consumi *non interpretati come una vera lettura*.
- Il dato annuale non viene alterato: se lo snapshot precedente appartiene a settembre–ottobre, l'app lo riconosce come ciclo passato e non lo associa a ottobre–novembre. Il vecchio valore può restare visualizzato sul widget con la sua data finché non viene effettuata una lettura nuova.
- Per utente residente l'accisa si calcola sui kWh effettivi **separatamente per ciascun mese** quando Enel fornisce il dettaglio; per consumi manuali senza suddivisione si mantiene una stima su ripartizione uniforme. Il prezzo delle altre componenti resta quello salvato nella calcolatrice, non viene aggiornato dai servizi tariffari.
- Codice versione 17, versione **5.9.0**, APK `CostoLuce_v5.9.0_BimestreCorrente_S25.apk`.


## Costo Luce 6.0

Interfaccia navy/teal e widget RemoteViews a due pagine: riepilogo del periodo e grafico degli ultimi quattro mesi incluso il corrente. Frecce e dot cambiano pagina tramite broadcast senza aprire l’app. Lo swipe orizzontale non viene simulato: Android lo riserva normalmente alla navigazione del launcher. Il toggle € / kWh è persistente e condiviso con il widget; i costi sono stime variabili alle tariffe impostate, escluse quote fisse.

Il connettore richiede dal primo giorno di tre mesi fa a oggi (fuso Europe/Rome), raggruppa solo le misure orarie effettivamente restituite e separa lo storico dal riepilogo del bimestre. Se il range esteso fallisce, tenta il range del periodo di fatturazione nella stessa sessione. Nessun valore storico è ricavato dividendo il bimestre: i mesi senza misure sono mancanti, gli zeri con misure sono veri zeri, il corrente è sempre parziale. Al cambio mese vengono riallineate le quattro colonne senza attribuire letture precedenti al nuovo mese. Prezzi storici: non sono fatture Enel, ma stime con le tariffe attualmente impostate e accisa calcolata per ciascun mese.

Il refresh manuale usa JobScheduler in background; ogni circa sei ore un job con vincolo di rete aggiorna i widget presenti. Tempi soggetti a risparmio energetico e policy Android. Il job periodico viene eliminato rimuovendo l’ultimo widget. Credenziali cifrate, alias AndroidKeyStore e applicationId rimangono invariati; nessuna credenziale o POD è incluso nel widget o nello storico.

Verifica: suite JavaScript, test JVM su accisa e periodi, nuovi test storico su mesi mancanti/zero reale/cambio anno/bimestre, build Android e smoke test su emulatore API 35 con applicazione delle vere RemoteViews di entrambe le pagine. Gli screenshot CI mostrano lo stato senza dati reali, senza introdurre consumi dimostrativi nell’app. Login Enel e copertura storica dipendono dall’interfaccia non pubblica e devono essere verificati con l’account sul dispositivo. Grafica allineata al mockup fornito: navy/blu elettrico, accenti teal, scheda del periodo, costo e consumo affiancati, grafico con scala e anni, pulsanti circolari e navigazione app realmente funzionante.

### Aggiornamento senza perdita di credenziali

Il workflow precedente generava un certificato debug su un runner effimero: la chiave privata 5.9 non è nel repository. Un APK può conservare i dati installati solo se Android accetta la firma come aggiornamento. Il nuovo workflow può riusare la precedente debug.keystore tramite il secret ANDROID_DEBUG_KEYSTORE_BASE64 (password/alias standard Android debug); in assenza conserva la nuova firma di test nella cache per le build successive. La cache non sostituisce un backup durevole di firma. La compatibilità con l’APK 5.x già installato non è garantita. Non disinstallare la versione precedente per superare un errore di firma: si perderebbero credenziali e dati locali. Serve la chiave privata originale per un aggiornamento compatibile.


## Costo Luce 6.1 — Tariffe personalizzabili

Le voci predefinite e i prezzi restano quelli esistenti. Ogni nome è modificabile, eccetto Energia €/kWh. Le voci monetarie possono essere variabili (€/kWh) o fisse (€/mese); cambiando tipo il campo importo viene svuotato e richiede un valore esplicito. IVA e perdite rimangono percentuali. Rinominarle non cambia la loro base di calcolo. L’accisa variabile conserva l’imponibile mensile e l’agevolazione già applicati.

I filtri Tutte / Variabili / Fisse agiscono sulla lista delle voci. Il pannello Modifica voce consente nome, tipo, importo, Annulla e Salva modifiche. Le personalizzazioni sono persistenti e vengono trasferite al widget, che usa lo stesso modello di calcolo. La migrazione mantiene i prezzi numerici salvati in costoluce_rates_v1 e aggiunge soltanto metadati opzionali _meta; credenziali e alias AndroidKeyStore restano invariati. I totali includono le quote fisse configurate e l’IVA; le quote mensili si contano nei mesi iniziati del periodo Enel, oppure nei mesi selezionati in modalità manuale. Lo storico in kWh usa sempre soltanto misure Enel reali.

## Costo Luce 6.2 — Gestione delle voci

In Tariffe, Aggiungi voce apre lo stesso pannello per nome, tipo variabile/fisso e importo. Si possono creare fino a 30 voci personalizzate. Elimina voce richiede conferma ed esclude la voce da tutti i calcoli, compresi quelli del widget; Energia €/kWh resta protetta. Le voci originali eliminate conservano internamente il valore e i metadati per consentire il ripristino. Annulla eliminazione recupera l'ultima voce rimossa, anche dopo aver riaperto l'app. Il ripristino generale richiede conferma, ripristina le dieci voci originali e rimuove le aggiunte.

La migrazione conserva le tariffe precedenti: i metadati opzionali `_meta.enabled` registrano le voci predefinite escluse e `_custom` contiene soltanto le nuove voci. Credenziali, prezzi esistenti, ciclo di fatturazione e formule di accisa non vengono modificati. App e widget includono le nuove quote e omettono le voci eliminate; IVA e perdite mantengono la loro base percentuale finché presenti.


## Costo Luce 6.3 — Widget adattivo

Dimensione minima richiesta 280 × 260 dp, iniziale 340 × 300 dp. Android 12+ riceve una mappa delle dimensioni esatte fornite dal launcher; sulle versioni precedenti si usano i limiti di ritratto e paesaggio. Il livello di dettaglio dipende da entrambe le dimensioni: compatto sotto 320 dp di larghezza o 290 dp di altezza; medio da 320 × 290 dp; dettagliato da 380 × 340 dp. Le anteprime di riferimento sono 280 × 260, 340 × 300 e 400 × 350 dp. I launcher arrotondano questi limiti alla propria griglia.

Il riepilogo usa tutta la larghezza. Il grafico viene disegnato nelle dimensioni disponibili, con testo da 12 sp e annotazioni da 11 sp, invece di ridurre un'immagine fissa. Compatto: quattro barre, valori, mesi e nota sul corrente parziale; medio: scala essenziale e aggiornamento; grande: scala completa, anni e stato dei mesi. I valori troppo lunghi vengono abbreviati soltanto graficamente, conservando quelli completi nella descrizione accessibile. Il budget complessivo delle immagini tiene conto del numero di varianti e delle dimensioni dello schermo.

I pulsanti principali hanno aree di 48 × 48 dp. Frecce/dot e toggle continuano a funzionare tramite broadcast; nessuno swipe simulato. Le prove Android verificano contenuti entro i bordi e dimensioni dei pulsanti alle tre taglie, nei formati 280 × 500 e 500 × 260 dp e a caratteri ingranditi. Nessuna modifica a credenziali, consumi reali, prezzi o calcolo delle imposte.
