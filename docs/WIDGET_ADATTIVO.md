# Widget adattivo 6.14

Il launcher comunica dimensioni in dp, non celle. Il widget usa le dimensioni effettive e la scala caratteri, conservando lo stile navy/teal. Nessuno zoom.

- Altezza inferiore a 130 dp (adattata alla scala caratteri): valori kWh/€, affiancati se largo almeno 260 dp. Aggiorna compare solo se rimangono almeno 48 dp di bersaglio e valori leggibili.
- Misure intermedie: periodo, due valori, Aggiorna; data quando entra. Il riepilogo parte dall'alto.
- Da 130×330, 240×230 o 340×190 dp: riepilogo e storico su due pagine. L'altezza richiesta cresce con la scala caratteri. Sotto 240 dp di larghezza il grafico usa quattro barre orizzontali; altrimenti quattro colonne. Nei formati strettissimi rimangono dot e freccia avanti, evitando bersagli troppo piccoli.
- Da 360×420 dp: riepilogo e grafico insieme, senza pagine ridondanti. Le soglie di altezza crescono con la scala caratteri.
- Nei riepiloghi alti: dettaglio dei mesi reali del periodo (massimo tre). Nessun valore ricostruito.
- L'altezza del disegno delle barre è limitata rispetto alla larghezza: le colonne non vengono allungate quando cresce il contenitore.
- Aggiorna è disponibile anche sulla pagina grafico. Nei formati stretti un solo selettore ciclico passa fra Bolletta, kWh e € mensili. Nei formati larghi restano i tre pulsanti.
- Diagnostica ordinaria delle fatture esclusa dal piè di pagina; errori e aggiornamento restano disponibili anche nella descrizione accessibile di Aggiorna.

La pagina scelta viene conservata quando il widget passa a una misura più piccola o unificata. Toggle, importi fatturati, riparti, tariffe, credenziali e recupero Enel non cambiano. Nessuna gesture orizzontale simulata: RemoteViews usa frecce/dot; lo swipe della Home dipende dal launcher.

Verifica: JVM per soglie e limiti; strumentazione Android su emulatore pulito per RemoteViews, misure intermedie, scala 1/1,3, valori lunghi, bersagli da 48 dp, conservazione stato, budget bitmap e regressione altezza intrinseca. Screenshot nativi popolati delle cinque forme aggiuntive pubblicati con l'APK, marcati come dati di test e generati da preferenze isolate nel solo APK di strumentazione; non equivalgono a un collaudo su ogni launcher Samsung.
