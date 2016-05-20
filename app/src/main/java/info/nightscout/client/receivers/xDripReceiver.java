package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.UtilityModels.Intents;
import com.eveningoutpost.dexdrip.UtilityModels.XDripEmulator;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;
import info.nightscout.client.data.NSSgv;

public class xDripReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(xDripReceiver.class);
    public xDripReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        boolean sendToDanaApp = SP.getBoolean("ns_sendtodanaapp", false);

        if (Config.detailedLog) log.debug("xDripReceiver.onReceive");
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

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
            emulator.sendToBroadcastReceiverToDanaApp(context);
        }
    }
}
