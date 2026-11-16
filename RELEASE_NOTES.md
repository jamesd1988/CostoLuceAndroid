# Costo Luce 6.19.0 — Consumi e proiezione

- Barra Dati Enel in cima a Consumi, con gli stessi stati, orario e blocco dei doppi tocchi della Home.
- Rimossi da Consumi l’inserimento manuale iniziale e il lungo riquadro di collegamento Enel. Diagnostica nelle Impostazioni, insieme agli strumenti manuali in una sezione richiudibile.
- Interruttore Proiezione nel dettaglio dei consumi, sincronizzato con la Home: consumi e importo previsti, elenco completo delle componenti fisse e variabili, perdite, accisa e IVA configurate. Canone TV separato.
- Accisa calcolata mese per mese sui consumi previsti; totale coincidente con la proiezione Home, senza modificare i consumi rilevati o quelli del widget.
- Se mancano sette giorni completi, motivo esplicito e nessun importo o dettaglio inventato.
- Corretto il titolo del totale: totale elettricità, comprensivo di tutte le voci configurate.

Verifiche JavaScript mensili/bimestrali/trimestrali, parità con la Home, conservazione dei dati e layout 320/390/600 px. Test Android e screenshot su emulatore pulito in GitHub Actions. Firma invariata: aggiornamento senza disinstallare.
