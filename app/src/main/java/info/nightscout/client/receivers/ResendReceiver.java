package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import info.nightscout.client.data.UploadQueue;
import info.nightscout.client.services.ServiceNS;

public class ResendReceiver extends WakefulBroadcastReceiver {
    public ResendReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));

        UploadQueue.resend("Intent received");
        completeWakefulIntent(intent);
    }
}
