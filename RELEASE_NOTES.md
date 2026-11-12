Costo Luce 6.2.0 — Aggiunta, eliminazione e ripristino delle voci

- Pulsante Aggiungi voce in Tariffe: nome, importo e selezione Variabile (€/kWh) o Fisso (€/mese), nello stesso pannello di modifica già approvato.
- Elimina voce nel pannello di modifica, con conferma e Annulla eliminazione. Anche le voci predefinite sono eliminabili, eccetto Energia €/kWh.
- Le nuove voci e le eliminazioni sono persistenti e aggiornano riepilogo, dettaglio, grafico in euro e widget. È possibile aggiungere fino a 30 voci personalizzate.
- Ripristino delle voci e dei prezzi originali con conferma; l’ultima eliminazione può essere annullata anche dopo aver riaperto l’app.
- Modifica del nome delle voci, con nome Energia €/kWh protetto.
- Quote variabili in €/kWh o fisse in €/mese. Cambiando tipo è necessario inserire il nuovo importo. Nessuna conversione automatica.
- IVA e perdite mantengono il calcolo percentuale; l’accisa variabile mantiene la logica mensile esistente.
- Quote fisse incluse nel totale e nel grafico mensile in euro di app e widget; i mesi futuri del bimestre non maturano quote fisse. Nel calcolo manuale si usano i mesi selezionati.
- Tutte le voci e gli importi esistenti restano predefiniti all’installazione: nessuna quota dimostrativa da 8 € viene aggiunta. Migrazione delle tariffe salvate, senza interventi sugli archivi delle credenziali.

Test JavaScript e JVM su aggiunta, eliminazione, ripristino e calcoli; compilazione e prova su emulatore Android 15 del pannello Tariffe e delle due pagine widget, con verifica del trasferimento delle nuove voci e delle eliminazioni e confronto dei totali app/widget. Le anteprime del pannello di modifica sono prove CI con un importo dimostrativo, senza dati di consumo inventati. Login Enel da verificare sul dispositivo. APK di test.
