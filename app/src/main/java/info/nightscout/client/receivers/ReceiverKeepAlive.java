package info.nightscout.client.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;
import info.nightscout.client.NSClient;
import info.nightscout.client.events.EventRestart;
import info.nightscout.client.services.ServiceNS;

public class ReceiverKeepAlive extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(ReceiverKeepAlive.class);

    @Override
	public void onReceive(Context context, Intent intent) {

         startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));
        //log.debug("KEEPALIVE started ServiceNS " + intent);
        if (Config.detailedLog) log.debug("KEEPALIVE");
        NSClient nsClient = MainApp.getNSClient();
        if (nsClient != null) {
            long tenMinutesInMs = 10 * 60 * 1000l;
            if (Config.detailedLog)
                log.debug("KEEPALIVE last reception " + nsClient.lastReception.toString());
            if (new Date().getTime() > nsClient.lastReception.getTime() + tenMinutesInMs) {
                log.debug("KEEPALIVE no reception for 10 min");
                nsClient.doPing();
            }

            if (nsClient.forcerestart) {
                MainApp.bus().post(new EventRestart());
            }
        }
        completeWakefulIntent(intent);
    }

}
