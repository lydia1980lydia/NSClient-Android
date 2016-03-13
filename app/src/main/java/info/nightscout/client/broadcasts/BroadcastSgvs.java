package info.nightscout.client.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PowerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.client.data.NSProfile;
import info.nightscout.client.data.NSSgv;

/**
 * Created by mike on 22.02.2016.
 */
public class BroadcastSgvs {
    private static Logger log = LoggerFactory.getLogger(BroadcastSgvs.class);

    public void handleNewSgv(NSSgv sgv, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("sgv", sgv.getData().toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_SGV);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("SGV " + x.size() + " receivers");
    }

}
