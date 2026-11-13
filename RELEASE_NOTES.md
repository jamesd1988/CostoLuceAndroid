Costo Luce 6.7.0 - Storico annuale e grafico scorrevole

- La vista Bolletta richiede 12 mesi conclusi più il mese corrente. L'inizio si estende, quando necessario, per includere il primo bimestre/trimestre completo. Non viene garantita la disponibilità di dati che Enel non restituisce.
- Il periodo corrente viene recuperato prima dello storico. Le richieste storiche sono suddivise in blocchi fino a tre mesi; in caso di rifiuto o mese senza misure viene tentato il singolo mese. Tutto resta nel limite complessivo di 90 secondi.
- Apertura di Bolletta: controllo automatico al massimo ogni 24 ore per schema di raggruppamento, solo con credenziali già salvate e senza aprire finestre di login. Aggiorna nell'app forza un nuovo tentativo. I normali aggiornamenti widget leggono il periodo corrente e richiedono l'anno solo quando necessario.
- Conservazione delle letture reali già salvate, fino a 24 mesi. I mesi non disponibili restano mancanti; le misure a zero sono distinte dall'assenza di dati.
- Grafico scorrevole orizzontalmente nell'app per Bolletta, kWh ed euro. Si apre sui periodi recenti; scorrimento con il dito o pulsanti Precedenti/Recenti. La posizione viene conservata durante i ricalcoli, asse verticale fisso e scala comune allo storico.
- Somma delle bollette arrotondata sugli importi visualizzati: 99,40 + 111,43 produce 210,83. Nessuna somma costruita con importi nascosti o dati mancanti. Asse e griglia riallineati alla base delle barre.
- Avviso di stima potenzialmente incompleta se le quote fisse di vendita non vengono riconosciute, con accesso a Tariffe. Non sono inseriti automaticamente costi contrattuali nei profili personalizzati.
- Gli importi storici restano stime con le tariffe attualmente salvate; non ricostruiscono i prezzi storici e non includono canone TV o altre voci non configurate.
- Credenziali, prezzi e raggruppamenti salvati conservati; il widget mantiene il grafico mensile senza introdurre swipe RemoteViews.

Verifiche: calendario annuale e archivio su JVM, test JS su richieste automatiche, dati mancanti, avviso quote e regressione dei centesimi; controlli browser di scroll/posizione/overflow; compilazione e test Android dell'interfaccia e del widget. APK di test. Il recupero annuale su account Enel personale richiede la prova sul dispositivo con l'account dell'utente.
