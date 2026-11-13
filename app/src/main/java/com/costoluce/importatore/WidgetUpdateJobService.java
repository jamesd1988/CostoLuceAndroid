package com.costoluce.importatore;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a user-triggered widget refresh off the UI thread. No periodic polling.
 * Never emits credentials, POD or authenticated URLs to logs or widget text.
 */
public final class WidgetUpdateJobService extends JobService {
    private Thread worker;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @Override public boolean onStartJob(JobParameters params) {
        stopped.set(false);
        worker = new Thread(() -> {
            try {
                EnelConnector.Credentials credentials = new EnelCredentialStore(this).read();
                if (credentials == null)
                    throw new IllegalStateException("Collega Enel nell'app e abilita il salvataggio cifrato delle credenziali.");
                CostoLuceWidgetProvider.WidgetConfiguration config =
                        CostoLuceWidgetProvider.readConfiguration(this);
                if (config == null)
                    throw new IllegalStateException("Apri Costo Luce e premi Aggiorna widget Home per configurare le tariffe.");
                CostoLuceWidgetProvider.setRefreshStatus(this, "Enel: collegamento in corso...", true);
                boolean annual=CostoLuceWidgetProvider.annualHistoryDue(this,config.months,config.anchor)
                    ||(params.getJobId()==CostoLuceWidgetProvider.REFRESH_JOB_ID&&"bill".equals(CostoLuceWidgetProvider.chartMode(this)));
                if(annual)CostoLuceWidgetProvider.markAnnualHistoryAttempt(this,config.months,config.anchor);
                EnelConnector.Result result = EnelConnector.fetch(credentials, config.months, config.anchor, annual, phase -> {
                    if (stopped.get()) return;
                    String label;
                    if (phase.startsWith("Fase 1/4")) label = "Enel 1/4 · apertura login";
                    else if (phase.startsWith("Fase 2/4")) label = "Enel 2/4 · verifica accesso";
                    else if (phase.startsWith("Fase 3/4")) label = "Enel 3/4 · sessione";
                    else if (phase.startsWith("Fase 4/4")) label = "Enel 4/4 · lettura kWh";
                    else label = "Enel · controllo dei dati";
                    CostoLuceWidgetProvider.setRefreshStatus(this, label, true);
                });
                // History includes measured zeros even when billing has no positive total.
                CostoLuceWidgetProvider.saveHistory(this,result);
                if (result.kwh <= 0)
                    throw new IllegalStateException("Non sono disponibili kWh positivi nel periodo scelto.");
                if (!stopped.get() && !Thread.currentThread().isInterrupted()) {
                    if (!CostoLuceWidgetProvider.saveResultFromEnel(this, result, config.months))
                        throw new IllegalStateException("Dati mensili Enel non validi.");
                }
            } catch (Exception error) {
                if (!stopped.get()) {
                    // Never reveal remote messages, credentials or authenticated URLs.
                    final String status;
                    if (error instanceof java.io.IOException)
                        status = "Enel non raggiungibile o login non valido. Dati invariati.";
                    else if (error instanceof IllegalStateException)
                        status = "Configurazione o dati Enel incompleti. Dati invariati.";
                    else
                        status = "Risposta Enel non utilizzabile. Dati invariati.";
                    CostoLuceWidgetProvider.setRefreshStatus(this, status, false);
                }
            } finally {
                if (!stopped.get()) jobFinished(params, false);
            }
        }, "costoluce-widget-enel-sync");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped.set(true);
        if (worker != null) worker.interrupt();
        CostoLuceWidgetProvider.setRefreshStatus(this, "Aggiornamento interrotto. Riprova.", false);
        return false;
    }
}
