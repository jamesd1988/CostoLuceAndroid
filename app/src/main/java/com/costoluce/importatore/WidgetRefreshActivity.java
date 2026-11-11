package com.costoluce.importatore;

import android.app.Activity;
import android.os.Bundle;

/**
 * A widget PendingIntent launched by a real user interaction.
 * Schedules a network refresh and exits without opening the main app.
 */
public final class WidgetRefreshActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CostoLuceWidgetProvider.requestDataRefresh(this);
        } finally {
            finish();
        }
    }
}
