package info.nightscout.client.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.v7.app.NotificationCompat;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.client.MainApp;
import info.nightscout.client.NSClient;
import info.nightscout.client.events.EventAppExit;
import info.nightscout.client.events.EventRestart;

public class ServiceNS extends Service {
    private static Logger log = LoggerFactory.getLogger(ServiceNS.class);

    private static Handler mHandler;
    private static HandlerThread mHandlerThread;

    private Notification mNotification;

    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationCompatBuilder;
    private NSClient mNSClient;
    static boolean restartingService = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //log.info("SERVICENS onStartCommand");

        if(mHandlerThread==null) {
            enableForeground();
            log.debug("SERVICENS Creating handler thread");
            this.mHandlerThread = new HandlerThread(ServiceNS.class.getSimpleName()+"Handler");
            mHandlerThread.start();

            this.mHandler = new Handler(mHandlerThread.getLooper());

            mNSClient = MainApp.getNSClient();

            registerBus();
            if(mNSClient==null) {
                log.debug("SERVICENS Creating new NS client");
                mNSClient = new NSClient(MainApp.bus());
                MainApp.setNSClient(mNSClient);
            }
        }

        //log.info("SERVICENS onStartCommand end");
        return START_STICKY;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void enableForeground() {
        mNotificationCompatBuilder = new NotificationCompat.Builder(getApplicationContext());
        mNotificationCompatBuilder.setContentTitle("Nightscout client")
//                .setSmallIcon(R.drawable.ic_stat_name)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setLocalOnly(true);

        mNotification = mNotificationCompatBuilder.build();

        nortifManagerNotify();

        startForeground(129, mNotification);
    }

    private void nortifManagerNotify() {
        mNotificationManager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(129, mNotification);
    }

    @Subscribe
    public void onStopEvent(EventAppExit event) {
        log.debug("EventAppExit received");
        if (mNSClient != null) {
            mNSClient.destroy();
        }

        stopForeground(true);
        stopSelf();
        log.debug("onStopEvent finished");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //log.debug("SERVICENS onCreate");
        mHandler = new Handler();
    }

    @Override
    public void onDestroy() {
        MainApp.setNSClient(null);
        super.onDestroy();
        log.debug("SERVICENS onDestroy");
        mNSClient.destroy();
    }

    @Subscribe
    public synchronized void onStatusEvent(final EventRestart e) {
        if (restartingService) {
            log.debug("Restarting of WS Client already in progress");
            return;
        }
        restartingService = true;
        Thread restart = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                log.debug("----- Restarting WS Client");
                MainApp.setNSClient(null);
                mNSClient.destroy();
                Object o = new Object();
                synchronized (o) {
                    try {
                        o.wait(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                log.debug("SERVICENS Creating new WS client");
                mNSClient = new NSClient(MainApp.bus());
                MainApp.setNSClient(mNSClient);
                synchronized (o) {
                    try {
                        o.wait(10000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                restartingService = false;
                log.debug("Restarting of WS Client already finished");
            }
        };
        restart.start();
    }


}
