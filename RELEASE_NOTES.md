# Costo Luce 6.21.0 — Account e widget

- Impostazioni divisa in due voci: Configurazione account e fornitura; Impostazioni widget.
- Nuova schermata nativa navy per configurare l’account, con Gestore in una scheda dedicata e un elenco che contiene soltanto Enel.
- Credenziali e dati della fornitura in una scheda separata: email, password mascherata con Mostra/Nascondi, POD, numero utente e codice fiscale facoltativo.
- Scudo verde e indicazioni esplicite: password inviata soltanto ai server HTTPS Enel, nessuna credenziale a ChatGPT o GitHub, salvataggio cifrato facoltativo sul telefono per l’aggiornamento del widget.
- Le credenziali già salvate vengono ripristinate nativamente; aprire o annullare il modulo non le modifica. Password e account non transitano nella WebView.
- Impostazioni widget conserva le opzioni esistenti, inclusa la scelta costo corrente/proiezione nei widget compatti.
- Diagnostica, archivio e importazione manuale rimangono negli strumenti dell’account, accessibili dal modulo. Navigazione indietro e gestione tastiera dedicate.

Verifiche JavaScript di navigazione e persistenza, layout 320/390/600 px e test Android del modulo nativo, selettore Gestore, validazione, valori salvati, password e tastiera. Screenshot su emulatore pulito in GitHub Actions. Firma invariata: aggiornamento senza disinstallare.
