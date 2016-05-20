package info.nightscout.client.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import info.nightscout.client.Config;
import info.nightscout.client.MainApp;
import info.nightscout.client.NSClient;
import info.nightscout.client.acks.NSAddAck;
import info.nightscout.client.acks.NSUpdateAck;
import info.nightscout.client.broadcasts.BroadcastQueueStatus;
import info.nightscout.client.broadcasts.BroadcastStatus;
import info.nightscout.client.events.RestartEvent;

/**
 * Created by mike on 21.02.2016.
 */
public class UploadQueue {
    private static Logger log = LoggerFactory.getLogger(UploadQueue.class);

    public static HashMap<String, DbRequest> queue = null;

    public UploadQueue() {
        if (queue == null) queue = new HashMap<String, DbRequest>();
    }

    public static String status() {
        return "QUEUE: " + queue.size();
    }

    public static void add(final DbRequest dbr) {
        final NSClient nsClient = MainApp.getNSClient();
        if (nsClient == null) return;
        nsClient.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE adding: " + dbr.data.toString());
                queue.put(dbr.hash(), dbr);
            }
        });
    }

    public static void put(final String hash, final DbRequest dbr) {
        final NSClient nsClient = MainApp.getNSClient();
        if (nsClient == null) return;
        nsClient.handler.post(new Runnable() {
            @Override
            public void run() {
                queue.put(hash, dbr);
                BroadcastQueueStatus bs = new BroadcastQueueStatus();
                bs.handleNewStatus(queue.size(), MainApp.instance().getApplicationContext());
            }
        });
    }

    public static void reset() {
        final NSClient nsClient = MainApp.getNSClient();
        if (nsClient == null) return;
        nsClient.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE Reset");
                queue.clear();
                log.debug(status());
            }
        });
    }

    public static void removeID(final JSONObject record) {
        final NSClient nsClient = MainApp.getNSClient();
        if (nsClient == null) return;
        nsClient.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long id = -1l;
                    if (record.has("NSCLIENT_ID")) {
                        id = record.getLong("NSCLIENT_ID");
                    } else {
                        return;
                    }
                    Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();
                    while (iter.hasNext()) {
                        DbRequest dbr = iter.next().getValue();
                        JSONObject data = dbr.data;
                        long nsclientId = -1;
                        if (data.has("NSCLIENT_ID")) {
                            nsclientId = data.getLong("NSCLIENT_ID");
                            if (nsclientId == id) {
                                log.debug("Removing item from UploadQueue");
                                iter.remove();
                                log.debug(UploadQueue.status());
                                return;
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void resend(final String reason) {
        if (queue.size() == 0)
            return;

        final NSClient nsClient = MainApp.getNSClient();
        if (nsClient == null) return;
        if (!nsClient.isConnected) return;

        log.debug("QUEUE Resend started: " + reason);

        nsClient.handler.post(new Runnable() {
            @Override
            public void run() {
                Logger log = LoggerFactory.getLogger(UploadQueue.class);
                Iterator<Map.Entry<String, DbRequest>> iter = queue.entrySet().iterator();

                if (nsClient.mSocket == null || !nsClient.mSocket.connected()) return;

                while (iter.hasNext()) {
                    DbRequest dbr = iter.next().getValue();
                    if (dbr.action.equals("dbAdd")) {
                        NSAddAck addAck = new NSAddAck();
                        nsClient.dbAdd(dbr, addAck);
                        if (addAck._id == null) {
                            log.debug("QUEUE No response on dbAdd");
                            log.debug(UploadQueue.status());
                            return;
                        }
                        if (Config.detailedLog) log.debug("QUEUE dbAdd processed: " + dbr.data.toString());
                        else log.debug("QUEUE dbAdd processed");
                        iter.remove();
                        log.debug(UploadQueue.status());
                    } else if (dbr.action.equals("dbRemove")) {
                        NSUpdateAck removeAck = new NSUpdateAck();
                        nsClient.dbRemove(dbr, removeAck);
                        if (!removeAck.result) {
                            log.debug("QUEUE No response on dbRemove");
                            log.debug(UploadQueue.status());
                            return;
                        }
                        log.debug("QUEUE dbRemove processed: " + dbr._id);
                        iter.remove();
                        log.debug(UploadQueue.status());
                    } else if (dbr.action.equals("dbUpdate")) {
                        NSUpdateAck updateAck = new NSUpdateAck();
                        nsClient.dbUpdate(dbr, updateAck);
                        if (!updateAck.result) {
                            log.debug("QUEUE No response on dbUpdate");
                            log.debug(UploadQueue.status());
                            return;
                        }
                        log.debug("QUEUE dbUpdate processed: " + dbr._id);
                        iter.remove();
                        log.debug(UploadQueue.status());
                    } else if (dbr.action.equals("dbUpdateUnset")) {
                        NSUpdateAck updateUnsetAck = new NSUpdateAck();
                        nsClient.dbUpdateUnset(dbr, updateUnsetAck);
                        if (!updateUnsetAck.result) {
                            log.debug("QUEUE No response on dbUpdateUnset");
                            log.debug(UploadQueue.status());
                            return;
                        }
                        log.debug("QUEUE dbUpdateUnset processed: " + dbr._id);
                        iter.remove();
                        log.debug(UploadQueue.status());
                    }
                }
                log.debug("QUEUE Resend ended: " + reason);
            }
        });
    }

}
