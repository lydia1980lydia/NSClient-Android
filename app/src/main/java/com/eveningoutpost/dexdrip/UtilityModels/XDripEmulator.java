package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.BgReading;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;

/**
 * Created by stephenblack on 11/7/14.
 * Adapted by mike
 */
public class XDripEmulator {
    private static Logger log = LoggerFactory.getLogger(XDripEmulator.class);
    private static List<BgReading> latest6bgReadings = new ArrayList<BgReading>();
    static DecimalFormat formatNumber1place = new DecimalFormat("0.0");

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static Long preparedTimestamp = 0l;
    private static ScheduledFuture<?> outgoingIntent = null;

    public void addBgReading(BgReading bgReading) {
        bgReading.date = (new Date(bgReading.timestamp)).toString();

        // Skip if already exists
        for (int c = 0; c < latest6bgReadings.size(); c++) {
            if (latest6bgReadings.get(c).timestamp == bgReading.timestamp) {
                log.debug("Not adding duplicate reading");
                return;
            }
        }

        latest6bgReadings.add(bgReading);

        // sort
        class BgReadingsComparator implements Comparator<BgReading> {
            @Override
            public int compare(BgReading a, BgReading b) {
                return a.timestamp > b.timestamp ? 1 : (a.timestamp < b.timestamp ? -1 : 0);
            }
        }
        Collections.sort(latest6bgReadings, new BgReadingsComparator());

        Long msec6min = 6l * 60 * 1000;
        Long msec5min = 5l * 60 * 1000;
        Long msec1min = 1l * 60 * 1000;

        // go through from newest
        BgReading newest = latest6bgReadings.get(latest6bgReadings.size() - 1);
        // now process 5 older
        for (int c = 1; c < 6; c++) {
            int processingIndex = latest6bgReadings.size() - 1 - c;
            if (processingIndex < 0) {
                // not enough intems in array
                processingIndex++;
                latest6bgReadings.add(0, latest6bgReadings.get(processingIndex));
            }
            BgReading processing = latest6bgReadings.get(processingIndex);
            long offsetToNewest = newest.timestamp - processing.timestamp;

            if (offsetToNewest > (c * msec5min) + msec1min) {
                // how many are missing
                int missing = (int)((offsetToNewest - (c * msec5min) + msec1min) / msec5min);
                while (missing > 0) {
                    latest6bgReadings.add(processingIndex, processing);
                    missing--;
                    c++;
                }
            }
        }

        // cut off to 6 records
        while (latest6bgReadings.size() > 7) latest6bgReadings.remove(0);

        //log
        if (Config.detailedLog) {
            for (int c = 0; c < latest6bgReadings.size(); c++) {
                log.debug("DANABG " + c + " " + (new Date(latest6bgReadings.get(c).timestamp)));
            }

        }
    }

    public void handleNewBgReading(BgReading bgReading, boolean isFull, Context context) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "sendQueue");
        wakeLock.acquire();
        try {
            // reset array if data are comming from new connection
            if (isFull) latest6bgReadings = new ArrayList<BgReading>();

            Intent updateIntent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA);
            context.sendBroadcast(updateIntent);

            Bundle bundle = new Bundle();
            bundle.putDouble(Intents.EXTRA_BG_ESTIMATE, bgReading.value);
            bundle.putDouble(Intents.EXTRA_BG_SLOPE, bgReading.slope);
            bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, "9");
            bundle.putInt(Intents.EXTRA_SENSOR_BATTERY, bgReading.battery_level);
            bundle.putLong(Intents.EXTRA_TIMESTAMP, bgReading.timestamp);

            bundle.putDouble(Intents.EXTRA_RAW, bgReading.raw);
            Intent intent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent, Intents.RECEIVER_PERMISSION);
            List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

            log.debug("XDRIPEMU BG " + bgReading.valInUnit() + " (" + new SimpleDateFormat("H:mm").format(new Date(bgReading.timestamp)) + ") " + x.size() + " receivers");

            // add new reading CHANGE: now picked up as broadcast
            // addBgReading(bgReading);
            // if (sendToDanaApp) sendToBroadcastReceiverToDanaApp(context);


        } finally {
            wakeLock.release();
        }
    }

    public static void sendToBroadcastReceiverToDanaApp(Context context) {


        Intent intent = new Intent("danaR.action.BG_DATA");

        int sizeRecords = latest6bgReadings.size();
        double deltaAvg30min = 0d;
        double deltaAvg15min = 0d;
        double avg30min = 0d;
        double avg15min = 0d;

        boolean notGood = false;

        if (sizeRecords > 6) {
            BgReading timeMatchedRecordCurrent = latest6bgReadings.get(sizeRecords - 1);
            long now = (new Date()).getTime();
            long msec7min = 7l * 60 * 1000;
            if (now - timeMatchedRecordCurrent.timestamp >msec7min) {
                log.debug("Data too old to send to DanaAps");
                return;
            }
            for (int i = sizeRecords - 6; i < sizeRecords; i++) {
                short glucoseValueBeeingProcessed = (short) latest6bgReadings.get(i).value;
                //log.debug("DANAAPP" + i + ": " + formatNumber1place.format(glucoseValueBeeingProcessed / 18d));
                if (glucoseValueBeeingProcessed < 40) {
                    notGood = true;
                    log.debug("DANAAPP data not good " + latest6bgReadings.get(i).timestamp);
                }
                deltaAvg30min += glucoseValueBeeingProcessed - latest6bgReadings.get(i - 1).value;
                avg30min += glucoseValueBeeingProcessed;
                if (i >= sizeRecords - 3) {
                    avg15min += glucoseValueBeeingProcessed;
                    deltaAvg15min += glucoseValueBeeingProcessed - latest6bgReadings.get(i - 1).value;
                }
            }
            deltaAvg30min /= 6d;
            deltaAvg15min /= 3d;
            avg30min /= 6d;
            avg15min /= 3d;

            if (notGood) return;

            Bundle bundle = new Bundle();
            bundle.putLong("time", timeMatchedRecordCurrent.timestamp);
            bundle.putInt("value", (int) timeMatchedRecordCurrent.value);
            bundle.putInt("delta", (int) (timeMatchedRecordCurrent.value - latest6bgReadings.get(sizeRecords - 2).value));
            bundle.putDouble("deltaAvg30min", deltaAvg30min);
            bundle.putDouble("deltaAvg15min", deltaAvg15min);
            bundle.putDouble("avg30min", avg30min);
            bundle.putDouble("avg15min", avg15min);

            intent.putExtras(bundle);

            // Postpone sending because on restart of client multiple BGs are comming and we need to send only last one
            class RunnableWithParam implements Runnable {
                Intent intent;
                Context context;
                RunnableWithParam(Intent intent, Context context) {
                    this.context = context;
                    this.intent = intent;
                }
                public void run(){
                    List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                    log.debug("DANAAPP  " + x.size() + " receivers");

                    context.sendBroadcast(intent);
                    preparedTimestamp = 0l;
                };
            }

            // prepare task for execution in 5 sec
            // cancel waiting task to prevent sending multiple statuses
            if (preparedTimestamp != 0l)
                if (timeMatchedRecordCurrent.timestamp > preparedTimestamp) {
                    outgoingIntent.cancel(false);
                    preparedTimestamp = 0l;
                }
            if (preparedTimestamp == 0l) {
                Runnable task = new RunnableWithParam(intent, context);
                preparedTimestamp = timeMatchedRecordCurrent.timestamp;
                outgoingIntent = worker.schedule(task, 5, TimeUnit.SECONDS);
            }
        }
    }
}
