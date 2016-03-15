package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.client.MainApp;
import info.nightscout.client.events.RestartEvent;
import info.nightscout.client.services.ServiceNS;

import static android.support.v4.content.WakefulBroadcastReceiver.startWakefulService;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(NetworkChangeReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Wakeup service
        startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));

        Bundle bundles = intent.getExtras();
        if (bundles == null)
            return;

        if (bundles.containsKey("bssid")) {
            String val;
            val = intent.getStringExtra("bssid");
            if (val != null) log.debug("NETCHANGE:   bssid: " + val);
        }

        if (bundles.containsKey("networkInfo")) {
            NetworkInfo info;
            info = (NetworkInfo) intent.getParcelableExtra("networkInfo");
            if (info != null) {
                //log.debug("NETCHANGE:   networkInfo: " + info);
                if (info.isConnected()) {
                    log.debug("NETCHANGE:   connected " + info.getTypeName());
                    //MainApp.bus().post(new RestartEvent());
                } else {
                    log.debug("NETCHANGE:   disconnected " + info.getTypeName());
                }
            }
        }

        if (bundles.containsKey("newRssi")) {
            int val;
            val = intent.getIntExtra("newRssi", -1);
            log.debug("NETCHANGE:   newRssi: " + val);
        }

        if (bundles.containsKey("newState")) {
            SupplicantState state;
            state = (SupplicantState) intent.getParcelableExtra("newState");
            if (state != null) log.debug("NETCHANGE:   newState: " + state);
        }

        if (bundles.containsKey("previous_wifi_state")) {
            int wifi_state;
            wifi_state = intent.getIntExtra("previous_wifi_state", -1);
            if (wifi_state != -1) log.debug("NETCHANGE:   previous_wifi_state: " + wifi_state);
        }

        if (bundles.containsKey("connected")) {
            boolean connected;
            connected = intent.getBooleanExtra("connected", false);
            log.debug("NETCHANGE:   connected: " + connected);
        }

        if (bundles.containsKey("supplicantError")) {
            int error;
            error = intent.getIntExtra("supplicantError", -1);
            if (error != -1) log.debug("NETCHANGE:   supplicantError: " + error);
        }

        if (bundles.containsKey("wifiInfo")) {
            WifiInfo info;
            info = (WifiInfo) intent.getParcelableExtra("wifiInfo");
            if (info != null) log.debug("NETCHANGE:   wifiInfo: " + info);
        }

        if (bundles.containsKey("wifi_state")) {
            int wifi_state;
            wifi_state = intent.getIntExtra("wifi_state", -1);
            if (wifi_state != -1) log.debug("NETCHANGE:   wifi_state: " + wifi_state);
        }

    }
}