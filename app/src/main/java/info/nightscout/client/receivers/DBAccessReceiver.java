package info.nightscout.client.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.client.MainApp;
import info.nightscout.client.NSClient;
import info.nightscout.client.acks.NSAddAck;
import info.nightscout.client.acks.NSUpdateAck;
import info.nightscout.client.data.DbRequest;
import info.nightscout.client.data.UploadQueue;

public class DBAccessReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(DBAccessReceiver.class);


    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "sendQueue");
        wakeLock.acquire();
        try {
            Bundle bundles = intent.getExtras();
            if (bundles == null) return;
            if (!bundles.containsKey("action")) return;

            NSClient nsClient = MainApp.getNSClient();
            if (nsClient == null) return;

            String collection = null;
            String _id = null;
            JSONObject data = null;
            String action = bundles.getString("action");
            try { collection = bundles.getString("collection"); } catch (Exception e) {}
            try { _id = bundles.getString("_id"); } catch (Exception e) {}
            try { data = new JSONObject(bundles.getString("data")); } catch (Exception e) {}

            if (data == null) {
                log.debug("DBACCESS no data inside record");
                return;
            }

            // mark by id
            try {
                data.put("NSCLIENT_ID", (new Date()).getTime());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (action.equals("dbAdd")) {
                if (!isAllowedCollection(collection)) {
                    log.debug("DBACCESS wrong collection specified");
                    return;
                }
                DbRequest dbar = new DbRequest("dbAdd", collection, data);
                NSAddAck addAck = new NSAddAck();
                nsClient.dbAdd(dbar, addAck);
                if (addAck._id == null) {
                    log.debug("DBACCESS No response on dbAdd");
                    UploadQueue.put(dbar.hash(), dbar);
                    log.debug(UploadQueue.status());
                    return;
                }
                log.debug("DBACCESS dbAdd processed: " + data.toString());
            }

            if (action.equals("dbRemove")) {
                if (!isAllowedCollection(collection)) {
                    log.debug("DBACCESS wrong collection specified");
                    return;
                }
                DbRequest dbrr = new DbRequest("dbRemove", collection, _id);
                NSUpdateAck updateAck = new NSUpdateAck();
                nsClient.dbRemove(dbrr, updateAck);
                if (!updateAck.result) {
                    log.debug("DBACCESS No response on dbRemove");
                    UploadQueue.put(dbrr.hash(), dbrr);
                    log.debug(UploadQueue.status());
                    return;
                }
                log.debug("DBACCESS dbRemove processed: " + _id);
            }

            if (action.equals("dbUpdate")) {
                if (!isAllowedCollection(collection)) {
                    log.debug("DBACCESS wrong collection specified");
                    return;
                }
                DbRequest dbur = new DbRequest("dbUpdate", collection, _id, data);
                NSUpdateAck updateAck = new NSUpdateAck();
                nsClient.dbUpdate(dbur, updateAck);
                if (!updateAck.result) {
                    log.debug("DBACCESS No response on dbUpdate");
                    UploadQueue.put(dbur.hash(), dbur);
                    log.debug(UploadQueue.status());
                    return;
                }
                log.debug("DBACCESS dbUpdate processed: " + _id + " " + data.toString());
            }

            if (action.equals("dbUpdateUnset")) {
                if (!isAllowedCollection(collection)) {
                    log.debug("DBACCESS wrong collection specified");
                    return;
                }
                DbRequest dbur = new DbRequest("dbUpdateUnset", collection, _id, data);
                NSUpdateAck updateAck = new NSUpdateAck();
                nsClient.dbUpdateUnset(dbur, updateAck);
                if (!updateAck.result) {
                    log.debug("DBACCESS No response on dbUpdateUnset");
                    UploadQueue.put(_id, dbur);
                    log.debug(UploadQueue.status());
                    return;
                }
                log.debug("DBACCESS dbUpdateUnset processed: " + _id + " " + data.toString());
            }
        } finally {
            wakeLock.release();
        }

    }

    private boolean isAllowedCollection(String collection) {
        // "treatments" || "entries" || "devicestatus" || "profile" || "food"
        if (collection.equals("treatments")) return true;
        if (collection.equals("entries")) return true;
        if (collection.equals("devicestatus")) return true;
        if (collection.equals("profile")) return true;
        if (collection.equals("food")) return true;
        return false;
    }
}
