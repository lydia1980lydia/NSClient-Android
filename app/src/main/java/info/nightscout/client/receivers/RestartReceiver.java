package info.nightscout.client.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import info.nightscout.client.MainApp;
import info.nightscout.client.events.EventRestart;
import info.nightscout.client.services.ServiceNS;

public class RestartReceiver extends WakefulBroadcastReceiver {
    public RestartReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));

        MainApp.bus().post(new EventRestart());
        completeWakefulIntent(intent);
    }
}
