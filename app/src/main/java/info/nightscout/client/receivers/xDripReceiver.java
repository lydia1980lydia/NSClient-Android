package info.nightscout.client.receivers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.UtilityModels.Intents;
import com.eveningoutpost.dexdrip.UtilityModels.XDripEmulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;
import info.nightscout.client.services.ServiceNS;

public class xDripReceiver extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(xDripReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        boolean sendToDanaApp = SP.getBoolean("ns_sendtodanaapp", false);

        startWakefulService(context, new Intent(context, ServiceNS.class)
                .setAction(intent.getAction())
                .putExtras(intent));

        if (Config.detailedLog) log.debug("xDripReceiver.onReceive");
        Bundle bundle = intent.getExtras();
        if (bundle != null) {

            if (sendToDanaApp) {
                BgReading bgReading = new BgReading();

                bgReading.value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE);
                bgReading.slope = bundle.getDouble(Intents.EXTRA_BG_SLOPE);
                bgReading.battery_level = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY);
                bgReading.timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP);
                bgReading.raw = bundle.getDouble(Intents.EXTRA_RAW);

                XDripEmulator emulator = new XDripEmulator();

                log.debug("XDRIPREC BG " + bgReading.valInUnit() + " (" + new SimpleDateFormat("H:mm").format(new Date(bgReading.timestamp)) + ")");

                emulator.addBgReading(bgReading);
                emulator.sendToBroadcastReceiverToDanaAps(context);
            }
        }
        completeWakefulIntent(intent);
    }
}
