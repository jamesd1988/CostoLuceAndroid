# Costo Luce 6.14.1 — Grafico costi anche nel widget 2 × 2

- Nei formati piccoli, da 120 × 150 dp con caratteri standard, è disponibile anche il grafico a quattro barre.
- Il selettore compatto passa fra riepilogo, Bolletta, € mensili e kWh. Aggiorna rimane accanto e lavora in background.
- Importi preceduti da ≈ arrotondati solo nell’etichetta del grafico per motivi di spazio; valori esatti conservati nella descrizione accessibile e nell’app. Periodo corrente parziale marcato con *.
- Nei formati più grandi rimane il layout adattivo della 6.14.0. La pagina e le tariffe sono conservate.
- Dati Enel e fatture originali, costi fissi e variabili, imposte e credenziali non cambiano. Nessun dato inventato nell’app.

Verifica con test JVM, JavaScript e RemoteViews Android, inclusi caratteri ingranditi e passaggi fra dimensioni. Anteprime popolate marcate come dati di test e confinate nell’APK di strumentazione. Celle effettive dipendenti dal launcher; installabile sopra la versione precedente con la stessa firma.
