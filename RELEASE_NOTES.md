Costo Luce 6.8.0 - Bolletta elettricità nel widget

- Nella pagina grafico del widget: tre pulsanti Bolletta / kWh / euro. Bolletta mostra gli ultimi quattro periodi mensili, bimestrali o trimestrali, secondo lo schema configurato in Tariffe.
- Il valore di ogni periodo somma i costi mensili, includendo tutte le voci fisse e variabili configurate, perdite, accisa mensile e IVA attive. Non è la sola componente energia. Il canone TV resta separato, non viene aggiunto automaticamente.
- Mesi futuri esclusi da consumi e quote fisse; periodi con misure mancanti indicati con un trattino, parziali con asterisco. Tutti gli importi restano stime con le tariffe salvate, non fatture ufficiali.
- Il widget avvisa quando le quote fisse di vendita non vengono riconosciute. Nell'app è possibile aggiungere le quote mancanti del riferimento E-Light direttamente dall'avviso nel grafico, senza duplicare quelle già riconosciute o sovrascrivere personalizzazioni.
- Nuova descrizione esplicita del totale elettricità nell'app e nelle etichette accessibili del widget. Valori di riferimento: le tariffe effettive del periodo devono essere configurate; nessuna ricostruzione automatica dei prezzi storici.
- Selezione del modo persistente e tramite broadcast, senza aprire l'app. Cambiare l'unità del riepilogo nell'app non azzera il modo Bolletta del widget.
- Quando serve, la selezione Bolletta richiede lo storico in background con credenziali già salvate; il refresh manuale del widget in questo modo consente di ritentare lo storico annuale. Nessuna credenziale nel widget.
- I formati 2x1 e intermedi restano riepiloghi compatti; i grafici e i tre selettori sono nei widget con due pagine. Nei formati compatti del grafico gli anni sono abbreviati se necessario.
- RemoteViews non offre uno swipe orizzontale affidabile: nel widget rimangono frecce/dot per le due pagine; lo scorrimento dello storico esteso è nell'app.

Verifiche: test del totale elettricità completo (127,56 euro con le tariffe e i consumi decimali del riferimento agosto-settembre), quote fisse con consumo misurato zero, trimestri con mesi futuri, dati mancanti e riconoscimento quote; test JS della scorciatoia senza doppioni; compilazione e test Android dei tre selettori, persistenza e dimensioni con testo ingrandito. APK di test.
