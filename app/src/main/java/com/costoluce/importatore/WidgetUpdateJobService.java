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
                CostoLuceWidgetProvider.setRefreshStatus(this, "Connessione Enel avviata...", true);
                EnelConnector.Result result = EnelConnector.fetch(credentials, config.months, phase -> {
                    if (!stopped.get()) CostoLuceWidgetProvider.setRefreshStatus(this, phase, true);
                });
                if (result.kwh <= 0)
                    throw new IllegalStateException("Non sono disponibili kWh positivi nel periodo scelto.");
                if (!stopped.get() && !Thread.currentThread().isInterrupted()) {
                    if (!CostoLuceWidgetProvider.saveResultFromEnel(this, result, config.months))
                        throw new IllegalStateException("Dati mensili Enel non validi.");
                }
            } catch (Exception ignored) {
                if (!stopped.get()) {
                    // Don't display exception messages or secrets from untrusted remote services.
                    CostoLuceWidgetProvider.setRefreshStatus(this, "Errore: dati Enel non disponibili. Apri l'app.", false);
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
