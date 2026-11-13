Costo Luce 6.10.0 - Euro mensili ripartiti dalle fatture

- La vista € mensile nell'app e nel widget attribuisce ai mesi gli importi originali della fattura Enel di appartenenza. Nessun prezzo storico viene ricalcolato con le tariffe attuali.
- Quando il documento contiene il riepilogo esplicito «Quota fissa e quota potenza» e un'aliquota IVA univoca applicata, le quote fisse comprensive di IVA vengono distribuite tra i mesi; il resto del totale elettricità è ripartito in proporzione ai kWh mensili reali.
- Se il dettaglio fisso non viene riconosciuto, il totale elettricità viene ripartito proporzionalmente ai kWh. Gli importi sono etichettati «Ripartito», con spiegazione del metodo: attribuzioni mensili, non fatture mensili ufficiali.
- Arrotondamento ai centesimi con distribuzione deterministica dei resti: la somma dei mesi coincide esattamente con il totale elettricità della fattura. Canone TV e altre voci fuori elettricità esclusi.
- Consumi mensili assenti o incompleti impediscono l'intera ripartizione di quella fattura. Nessun consumo viene inventato. Con tutti i consumi misurati a zero è ammessa solo una fattura interamente fissa riconosciuta o a totale zero; altrimenti ripartizione non disponibile.
- I mesi del gruppo corrente sono tutti verde acqua e indicati come «Stima corrente», con asterisco sul mese parziale. Storico ripartito blu. Schema mensile/bimestrale/trimestrale preservato; la vista Bolletta mantiene gli importi emessi originali.
- Al prossimo Aggiorna le fatture già archiviate senza il nuovo dettaglio vengono rilette una volta da Enel e arricchite senza alterare numero, periodo e totali. Se la rilettura fallisce resta utilizzabile la ripartizione proporzionale quando i consumi ci sono. Nessun caricamento manuale.
- Widget: per ripartire una fattura usa l'archivio mensile completo, anche se mostra solo quattro barre. Credenziali e tariffe personalizzate preservate.

Verifiche: casi comuni condivisi tra Java e JS per bimestri, trimestri, anno nuovo, centesimi, misure decimali, zero, dati mancanti e parziali; totali emessi immutabili durante l'arricchimento, rilettura cache, estrazione PDF e colori effettivi del bitmap Android. Compilazione e smoke test RemoteViews. APK di test con firma compatibile.
