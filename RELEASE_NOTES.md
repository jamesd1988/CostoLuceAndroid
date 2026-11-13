Costo Luce 6.9.0 - Storico fatturato e sola stima corrente

- In Bolletta, i periodi chiusi non vengono più ricalcolati dai consumi. Si mostrano soltanto importi letti da fatture Enel importate, oppure «Fattura assente». Solo il mese/bimestre/trimestre corrente viene stimato con le tariffe configurate.
- Importazione locale di una fattura Enel PDF per volta tramite selettore documenti Android. Anteprima di periodo, totale elettricità e importi fuori elettricità (canone TV/altre voci); i dati sono salvati solo dopo il tasto Importa. Il PDF non viene conservato né inviato a servizi esterni.
- Lettura rigorosa di POD, numero fattura, periodo e Totale Bolletta distinto dal Totale da pagare. Documenti non riconosciuti, scansioni senza testo, totali ambigui, note di credito o sovrapposizioni richiedono verifica; nessuna stima sostitutiva.
- Archivio privato distinto per fornitura; se Enel è collegato, rifiuta PDF di un POD diverso. Importare la stessa fattura non crea duplicati. Le fatture emesse restano indipendenti dalle modifiche alle tariffe e dalla periodicità corrente.
- App e widget distinguono importi fatturati, stima corrente e dati assenti; il dettaglio conserva fuori dal totale elettricità canone TV e altre voci del documento.
- IMPORTANTE: il download automatico delle fatture da Enel NON è implementato/confermato. Il connettore esistente recupera consumi; l'archivio fatture richiede una sessione autenticata e verifica delle chiamate reali. In questa versione i PDF vanno scaricati da Enel e importati. L'interfaccia esplicita questa limitazione; il tasto Aggiorna non simula il recupero fatture.
- Credenziali, tariffe personalizzate, schema di fatturazione e accisa mensile preservati. Supporto Android 7+ mantenuto tramite desugaring delle date.

Verifiche: prezzi fatturati immutabili al cambio tariffa, consumo storico senza fattura mai convertito in euro, periodicità e confini anno, parsing separato elettricità/totale, ambiguità e più POD rifiutati; test PDFBox in memoria nell'emulatore, test JVM/JS e compilazione Android. Nessun documento o dato personale incluso nell'APK o nelle fixture pubbliche.
