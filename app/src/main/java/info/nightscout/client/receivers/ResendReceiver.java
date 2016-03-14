package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import info.nightscout.client.data.UploadQueue;

public class ResendReceiver extends BroadcastReceiver {
    public ResendReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        UploadQueue.resend("Intent received");
    }
}
