# Costo Luce 6.15.0 — Riepilogo Home fisso

- Rimossa dalla Home la barra Visualizza € / kWh.
- Periodo di fatturazione con tre valori sempre nello stesso ordine: Consumo kWh, costo stimato sui consumi disponibili, proiezione della bolletta a fine periodo.
- Il riepilogo conserva ordine, importi e posizione passando fra gli istogrammi Bolletta, kWh e € mensili.
- La proiezione del riepilogo resta visibile anche quando l’interruttore del grafico è disattivato. Con dati insufficienti viene mostrato — con il motivo; resta il requisito di cinque giorni completi recenti.
- Nessuna modifica all’algoritmo, alle tariffe, alle fatture storiche, al canone TV separato o alle credenziali. Conservati i widget adattivi e il grafico 2×2.

Test: calcoli e proiezione, indipendenza dai modi del grafico, assenza di dati e stabilità del layout su viewport 320/390/600 px; test Android e screenshot nativi. Stessa firma delle versioni precedenti: aggiornamento senza disinstallare.
