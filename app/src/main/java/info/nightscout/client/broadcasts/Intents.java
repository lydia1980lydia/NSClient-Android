package info.nightscout.client.broadcasts;

/**
 * Created by mike on 20.02.2016.
 */
public interface Intents {
    // send
    String ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT";
    String ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT";
    String ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT";
    String ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE";
    String ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV";
    String ACTION_NEW_STATUS = "info.nightscout.client.NEW_STATUS";
    String ACTION_NEW_DEVICESTATUS = "info.nightscout.client.NEW_DEVICESTATUS";
    String ACTION_NEW_MBG = "info.nightscout.client.NEW_MBG";
    String ACTION_NEW_CAL = "info.nightscout.client.NEW_CAL";
    String ACTION_QUEUE_STATUS = "info.nightscout.client.QUEUE_STATUS";


    // Listen on
    String ACTION_DATABASE = "info.nightscout.client.DBACCESS";
    String ACTION_RESEND = "info.nightscout.client.RESEND";
    String ACTION_RESTART = "info.nightscout.client.RESTART";
}
