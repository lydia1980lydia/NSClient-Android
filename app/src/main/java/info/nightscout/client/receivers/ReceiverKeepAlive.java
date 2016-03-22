package info.nightscout.client.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;
import info.nightscout.client.events.RestartEvent;
import info.nightscout.client.services.ServiceNS;

public class ReceiverKeepAlive extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(ReceiverKeepAlive.class);

    @Override
	public void onReceive(Context context, Intent intent) {

        //Intent intent = new Intent(context, ServiceNS.class);
        //context.startService(intent);
        startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));
        //log.debug("KEEPALIVE started ServiceNS " + intent);
        if (Config.detailedLog) log.debug("KEEPALIVE");
        if (MainApp.getNSClient() != null && MainApp.getNSClient().forcerestart) {
            MainApp.bus().post(new RestartEvent());
            return;
        }
    }

}
