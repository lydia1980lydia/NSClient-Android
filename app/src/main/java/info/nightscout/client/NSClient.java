package info.nightscout.client;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.UtilityModels.XDripEmulator;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.squareup.otto.Bus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Date;

import info.nightscout.client.acks.NSAddAck;
import info.nightscout.client.acks.NSAuthAck;
import info.nightscout.client.acks.NSPingAck;
import info.nightscout.client.acks.NSUpdateAck;
import info.nightscout.client.broadcasts.BroadcastCals;
import info.nightscout.client.broadcasts.BroadcastDeviceStatus;
import info.nightscout.client.broadcasts.BroadcastMbgs;
import info.nightscout.client.broadcasts.BroadcastProfile;
import info.nightscout.client.broadcasts.BroadcastSgvs;
import info.nightscout.client.broadcasts.BroadcastStatus;
import info.nightscout.client.broadcasts.BroadcastTreatment;
import info.nightscout.client.data.DbRequest;
import info.nightscout.client.data.NSCal;
import info.nightscout.client.data.NSSgv;
import info.nightscout.client.data.NSStatus;
import info.nightscout.client.data.NSTreatment;
import info.nightscout.client.data.UploadQueue;
import info.nightscout.client.events.NSStatusEvent;
import info.nightscout.client.data.NSProfile;
import info.nightscout.client.tests.TestReceiveID;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class NSClient {
    private static Logger log = LoggerFactory.getLogger(NSClient.class);

    private static Integer dataCounter = 0;

    static public Handler handler;
    static private HandlerThread handlerThread;

    public static UploadQueue uploadQueue = new UploadQueue();

    private Bus mBus;
    public Socket mSocket;
    public boolean isConnected = false;
    public boolean forcerestart = false;
    private String connectionStatus = "Not connected";

    public static String nightscoutVersionName = "";
    public static Integer nightscoutVersionCode = 0;

    private boolean nsEnabled = false;
    private String nsURL = "";
    private String nsAPISecret = "";
    private String nsDevice = "";
    private Integer nsHours = 3;
    private boolean acquireWiFiLock = false;

    private final Integer timeToWaitForResponseInMs = 30000;
    private boolean uploading = false;
    public Date lastReception = new Date();

    private String nsAPIhashCode = "";

    WifiManager.WifiLock wifiLock = null;

    public NSClient(Bus bus) {
        MainApp.setNSClient(this);
        mBus = bus;

        dataCounter = 0;

        if (handler == null) {
            handlerThread = new HandlerThread(NSClient.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        readPreferences();

        if (acquireWiFiLock)
            keepWiFiOn(MainApp.instance().getApplicationContext(), true);

        if (nsAPISecret != "")
            nsAPIhashCode = Hashing.sha1().hashString(nsAPISecret, Charsets.UTF_8).toString();

        Intent i = new Intent(MainApp.instance().getApplicationContext(), PreferencesActivity.class);
        mBus.post(new NSStatusEvent(connectionStatus));
        if (!nsEnabled) {
            log.debug("NSCLIENT disabled");
            connectionStatus = "Disabled";
            mBus.post(new NSStatusEvent(connectionStatus));
            Toast.makeText(MainApp.instance().getApplicationContext(), "NS connection disabled", Toast.LENGTH_LONG).show();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainApp.instance().getApplicationContext().startActivity(i);
        } else if (nsURL != "") {
            try {
                connectionStatus = "Connecting ...";
                mBus.post(new NSStatusEvent(connectionStatus));
                IO.Options opt = new IO.Options();
                opt.forceNew = true;
                mSocket = IO.socket(nsURL, opt);
                log.debug("NSCLIENT connect");
                mSocket.connect();
                mSocket.on("dataUpdate", onDataUpdate);
                mSocket.on("ping", onPing);
                // resend auth on reconnect is needed on server restart
                //mSocket.on("reconnect", resendAuth);
                sendAuthMessage(new NSAuthAck());
                log.debug("NSCLIENT start");
            } catch (URISyntaxException e) {
                log.debug("NSCLIENT Wrong URL syntax");
                connectionStatus = "Wrong URL syntax";
                mBus.post(new NSStatusEvent(connectionStatus));
                Toast.makeText(MainApp.instance().getApplicationContext(), "Wrong URL syntax", Toast.LENGTH_LONG).show();
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                MainApp.instance().getApplicationContext().startActivity(i);
            }
        } else {
            log.debug("NSCLIENT No NS URL specified");
            connectionStatus = "Disabled";
            mBus.post(new NSStatusEvent(connectionStatus));
            Toast.makeText(MainApp.instance().getApplicationContext(), "No NS URL specified", Toast.LENGTH_LONG).show();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainApp.instance().getApplicationContext().startActivity(i);
        }
    }

    public void destroy() {
        MainApp.bus().unregister(this);
        if (mSocket != null) {
            log.debug("NSCLIENT destroy");
            mSocket.disconnect();
            mSocket = null;
            MainApp.setNSClient(null);
        }
        if (acquireWiFiLock)
            keepWiFiOn(MainApp.instance().getApplicationContext(), false);
    }

    public void sendAuthMessage(NSAuthAck ack) {
        JSONObject authMessage = new JSONObject();
        try {
            authMessage.put("client", "Android_" + nsDevice);
            authMessage.put("history", nsHours);
            authMessage.put("status", true); // receive status
            authMessage.put("pingme", true); // send mi pings to keep alive
            authMessage.put("secret", nsAPIhashCode);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        log.debug("NSCLIENT authorize " + mSocket.id());
        try {
            mSocket.emit("authorize", authMessage, ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized (ack) {
            try {
                ack.wait(timeToWaitForResponseInMs);
            } catch (InterruptedException e) {
                ack.interrupted = true;
            }
        }
        if (ack.interrupted) {
            log.debug("NSCLIENT Auth interrupted");
            isConnected = false;
            connectionStatus = "Auth interrupted";
            mBus.post(new NSStatusEvent(connectionStatus));
        } else if (ack.received) {
            log.debug("NSCLIENT Authenticated");
            connectionStatus = "Authenticated (";
            if (ack.read) connectionStatus += "R";
            if (ack.write) connectionStatus += "W";
            if (ack.write_treatment) connectionStatus += "T";
            connectionStatus += ')';
            isConnected = true;
            mBus.post(new NSStatusEvent(connectionStatus));
            if (!ack.write) {
                Toast.makeText(MainApp.instance().getApplicationContext(), "Write permission not granted", Toast.LENGTH_LONG).show();
            }
            if (!ack.write_treatment) {
                Toast.makeText(MainApp.instance().getApplicationContext(), "Write treatment permission not granted", Toast.LENGTH_LONG).show();
            }
            lastReception = new Date();
        } else {
            log.debug("NSCLIENT Auth timed out " + mSocket.id());
            isConnected = true;
            connectionStatus = "Auth timed out";
            mBus.post(new NSStatusEvent(connectionStatus));
            return;
        }
    }

    public void readPreferences() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        try {
            nsEnabled = SP.getBoolean("ns_enable", false);
            acquireWiFiLock = SP.getBoolean("ns_android_acquirewifilock", false);
            nsURL = SP.getString("ns_url", "");
            nsAPISecret = SP.getString("ns_api_secret", "");
            nsHours = Integer.parseInt(SP.getString("ns_api_hours", "3"));
            nsDevice = SP.getString("ns_api_device", "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Emitter.Listener onPing = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (Config.detailedLog) log.debug("NSCLIENT Ping received");
            // send data if there is something waiting
            UploadQueue.resend("Ping received");
        }
    };

    private Emitter.Listener onDataUpdate = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            lastReception = new Date();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "onDataUpdate");
                    wakeLock.acquire();
                    try {
                        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
                        boolean emulatexDrip = SP.getBoolean("ns_emulate_xdrip", false);
                        connectionStatus = "Data packet " + dataCounter++;
                        mBus.post(new NSStatusEvent(connectionStatus));

                        JSONObject data = (JSONObject) args[0];
                        NSCal actualCal = new NSCal();
                        boolean broadcastProfile = false;
                        try {
                            // delta means only increment/changes are comming
                            boolean isDelta = data.has("delta");
                            boolean isFull = !isDelta;
                            log.debug("NSCLIENT Data packet #" + dataCounter + (isDelta ? " delta" : " full"));

                            if (data.has("profiles")) {
                                JSONArray profiles = (JSONArray) data.getJSONArray("profiles");
                                if (profiles.length() > 0) {
                                    JSONObject profile = (JSONObject) profiles.get(profiles.length() - 1);
                                    String activeProfile = MainApp.getNsProfile() == null ? null : MainApp.getNsProfile().getActiveProfile();
                                    NSProfile nsProfile = new NSProfile(profile, activeProfile);
                                    MainApp.setNsProfile(nsProfile);
                                    broadcastProfile = true;
                                    log.debug("NSCLIENT profile received");
                                }
                            }

                            if (data.has("status")) {
                                JSONObject status = data.getJSONObject("status");
                                NSStatus nsStatus = new NSStatus(status);

                                if (!status.has("versionNum")) {
                                    if (status.getInt("versionNum") < 900) {
                                        Toast.makeText(MainApp.instance().getApplicationContext(), "Unsupported Nightscout version", Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    nightscoutVersionName = status.getString("version");
                                    nightscoutVersionCode = status.getInt("versionNum");
                                }

                                BroadcastStatus bs = new BroadcastStatus();
                                bs.handleNewStatus(nsStatus, MainApp.instance().getApplicationContext(), isDelta);

                                if (MainApp.getNsProfile() != null) {
                                    String oldActiveProfile = MainApp.getNsProfile().getActiveProfile();
                                    String receivedActiveProfile = nsStatus.getActiveProfile();
                                    MainApp.getNsProfile().setActiveProfile(receivedActiveProfile);
                                    if (receivedActiveProfile != null) {
                                        log.debug("NSCLIENT status activeProfile received: " + receivedActiveProfile);
                                    }
                                    // Change possible nulls to ""
                                    String oldP = oldActiveProfile == null ? "" : oldActiveProfile;
                                    String newP = receivedActiveProfile == null ? "" : receivedActiveProfile;
                                    if (!newP.equals(oldP)) {
                                        broadcastProfile = true;
                                    }
                                }
                    /*  Other received data to 2016/02/10
                        {
                          status: 'ok'
                          , name: env.name
                          , version: env.version
                          , versionNum: versionNum (for ver 1.2.3 contains 10203)
                          , serverTime: new Date().toISOString()
                          , apiEnabled: apiEnabled
                          , careportalEnabled: apiEnabled && env.settings.enable.indexOf('careportal') > -1
                          , boluscalcEnabled: apiEnabled && env.settings.enable.indexOf('boluscalc') > -1
                          , head: env.head
                          , settings: env.settings
                          , extendedSettings: ctx.plugins && ctx.plugins.extendedClientSettings ? ctx.plugins.extendedClientSettings(env.extendedSettings) : {}
                          , activeProfile ..... calculated from treatments or missing
                        }
                     */
                            } else if (!isDelta) {
                                Toast.makeText(MainApp.instance().getApplicationContext(), "Unsupported Nightscout version", Toast.LENGTH_LONG).show();
                            }

                            // If new profile received or change detected broadcast it
                            if (broadcastProfile && MainApp.getNsProfile() != null) {
                                BroadcastProfile bp = new BroadcastProfile();
                                bp.handleNewTreatment(MainApp.getNsProfile(), MainApp.instance().getApplicationContext(), isDelta);
                                log.debug("NSCLIENT broadcasting profile" + MainApp.getNsProfile().log());
                            }

                            if (data.has("treatments")) {
                                JSONArray treatments = (JSONArray) data.getJSONArray("treatments");
                                JSONArray removedTreatments = new JSONArray();
                                JSONArray updatedTreatments = new JSONArray();
                                JSONArray addedTreatments = new JSONArray();
                                BroadcastTreatment bt = new BroadcastTreatment();
                                if (treatments.length() > 0)
                                    log.debug("NSCLIENT received " + treatments.length() + " treatments");
                                for (Integer index = 0; index < treatments.length(); index++) {
                                    JSONObject jsonTreatment = treatments.getJSONObject(index);
                                    NSTreatment treatment = new NSTreatment(jsonTreatment);

                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonTreatment);
                                    if (treatment.getAction() == null) {
                                        // ********** TEST CODE ***********
                                        if (jsonTreatment.has("NSCLIENTTESTRECORD")) {
                                            MainApp.bus().post(new TestReceiveID(jsonTreatment.getString("_id")));
                                            log.debug("----- Broadcasting test _id -----");
                                            continue;
                                        }
                                        // ********* TEST CODE END ********
                                        if (!isCurrent(treatment)) continue;
                                        addedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("update")) {
                                        if (!isCurrent(treatment)) continue;
                                        updatedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("remove")) {
                                        removedTreatments.put(jsonTreatment);
                                    }
                                }
                                if (removedTreatments.length() > 0) {
                                    bt.handleRemovedTreatment(removedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (updatedTreatments.length() > 0) {
                                    bt.handleChangedTreatment(updatedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (addedTreatments.length() > 0) {
                                    bt.handleNewTreatment(addedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("devicestatus")) {
                                BroadcastDeviceStatus bds = new BroadcastDeviceStatus();
                                JSONArray devicestatuses = (JSONArray) data.getJSONArray("devicestatus");
                                if (devicestatuses.length() > 0) {
                                    log.debug("NSCLIENT received " + devicestatuses.length() + " devicestatuses");
                                    for (Integer index = 0; index < devicestatuses.length(); index++) {
                                        JSONObject jsonStatus = devicestatuses.getJSONObject(index);
                                        // remove from upload queue if Ack is failing
                                        UploadQueue.removeID(jsonStatus);
                                    }
                                    // send only last record
                                    bds.handleNewDeviceStatus(devicestatuses.getJSONObject(devicestatuses.length()-1), MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("mbgs")) {
                                BroadcastMbgs bmbg = new BroadcastMbgs();
                                JSONArray mbgs = (JSONArray) data.getJSONArray("mbgs");
                                if (mbgs.length() > 0)
                                    log.debug("NSCLIENT received " + mbgs.length() + " mbgs");
                                for (Integer index = 0; index < mbgs.length(); index++) {
                                    JSONObject jsonMbg = mbgs.getJSONObject(index);
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonMbg);
                                }
                                bmbg.handleNewMbg(mbgs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("cals")) {
                                BroadcastCals bc = new BroadcastCals();
                                JSONArray cals = (JSONArray) data.getJSONArray("cals");
                                if (cals.length() > 0)
                                    log.debug("NSCLIENT received " + cals.length() + " cals");
                                // Retreive actual calibration
                                for (Integer index = 0; index < cals.length(); index++) {
                                    if (index == 0) {
                                        actualCal.set(cals.optJSONObject(index));
                                    }
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(cals.optJSONObject(index));
                                }
                                bc.handleNewCal(cals, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("sgvs")) {
                                BroadcastSgvs bs = new BroadcastSgvs();
                                String units = MainApp.getNsProfile() != null ? MainApp.getNsProfile().getUnits() : "mg/dl";
                                XDripEmulator emulator = new XDripEmulator();
                                JSONArray sgvs = (JSONArray) data.getJSONArray("sgvs");
                                if (sgvs.length() > 0)
                                    log.debug("NSCLIENT received " + sgvs.length() + " sgvs");
                                for (Integer index = 0; index < sgvs.length(); index++) {
                                    JSONObject jsonSgv = sgvs.getJSONObject(index);
                                    // log.debug("NSCLIENT svg " + sgvs.getJSONObject(index).toString());
                                    NSSgv sgv = new NSSgv(jsonSgv);
                                    // Handle new sgv here
                                    if (emulatexDrip) {
                                        BgReading bgReading = new BgReading(sgv, actualCal, units);
                                        emulator.handleNewBgReading(bgReading, isFull && index == 0, MainApp.instance().getApplicationContext());
                                    }
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonSgv);
                                }
                                bs.handleNewSgv(sgvs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        //log.debug("NSCLIENT onDataUpdate end");
                    } finally {
                        wakeLock.release();
                    }
                }

            });
        }
    };

    public void dbUpdate(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBUPDATE Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", dbr.data);
            mSocket.emit("dbUpdate", message, ack);
            synchronized (ack) {
                try {
                    ack.wait(timeToWaitForResponseInMs);
                } catch (InterruptedException e) {
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbUpdate(DbRequest dbr) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBUPDATE Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", dbr.data);
            mSocket.emit("dbUpdate", message);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbUpdateUnset(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBUPUNSET Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", dbr.data);
            mSocket.emit("dbUpdateUnset", message, ack);
            synchronized (ack) {
                try {
                    ack.wait(timeToWaitForResponseInMs);
                } catch (InterruptedException e) {
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbUpdateUnset(DbRequest dbr) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBUPUNSET Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", dbr.data);
            mSocket.emit("dbUpdateUnset", message);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbRemove(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBREMOVE Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            mSocket.emit("dbRemove", message, ack);
            synchronized (ack) {
                try {
                    ack.wait(timeToWaitForResponseInMs);
                } catch (InterruptedException e) {
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbRemove(DbRequest dbr) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBREMOVE Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            mSocket.emit("dbRemove", message);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbAdd(DbRequest dbr, NSAddAck ack) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBADD Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("data", dbr.data);
            mSocket.emit("dbAdd", message, ack);
            synchronized (ack) {
                try {
                    ack.wait(timeToWaitForResponseInMs);
                } catch (InterruptedException e) {
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }

    public void dbAdd(DbRequest dbr) {
        try {
            if (!isConnected) return;
            if (uploading) {
                log.debug("DBADD Busy, adding to queue");
                return;
            }
            uploading = true;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("data", dbr.data);
            mSocket.emit("dbAdd", message);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        uploading = false;
    }


    public void doPing() {
        if (!isConnected) return;
        log.debug("NSCLIENT Sending Ping");
        uploading = true;
        JSONObject message = new JSONObject();
        try {
            message.put("mills", new Date().getTime());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        NSPingAck ack = new NSPingAck();
        mSocket.emit("nsping", message, ack);
        synchronized (ack) {
            try {
                ack.wait(timeToWaitForResponseInMs);
            } catch (InterruptedException e) {
            }
        }
        if (ack.received) {
            String connectionStatus = "NSCLIENT Pong received";
            if (ack.auth_received) {
                connectionStatus += ": ";
                if (ack.read) connectionStatus += "R";
                if (ack.write) connectionStatus += "W";
                if (ack.write_treatment) connectionStatus += "T";
            }
            if (!ack.read) sendAuthMessage(new NSAuthAck());
            log.debug(connectionStatus);
        } else {
            log.debug("NSCLIENT Ping lost");
        }
        uploading = false;
    }

    private boolean isCurrent(NSTreatment treatment) {
        long now = (new Date()).getTime();
        long minPast = now - nsHours * 60l * 60 * 1000;
        if (treatment.getMills() == null) {
            log.debug("treatment.getMills() == null " + treatment.getData().toString());
            return false;
        }
        if (treatment.getMills() > minPast) return true;
        return false;
    }

    public void keepWiFiOn(Context context, boolean on) {
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "NSClient");
                wifiLock.setReferenceCounted(true);
            }
        }
        if (wifiLock != null) { // May be null if wm is null
            if (on) {
                wifiLock.acquire();
                log.debug("Acquired WiFi lock");
            } else if (wifiLock.isHeld()) {
                wifiLock.release();
                log.debug("Released WiFi lock");
            }
        }
    }

}
