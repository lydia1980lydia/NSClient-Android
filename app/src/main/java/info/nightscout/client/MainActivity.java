package info.nightscout.client;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.UtilityModels.XDripEmulator;
import com.squareup.otto.Subscribe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import info.nightscout.client.broadcasts.Intents;
import info.nightscout.client.data.UploadQueue;
import info.nightscout.client.events.EventRestart;
import info.nightscout.client.services.ServiceNS;
import info.nightscout.client.tests.TestReceiveID;
import info.nightscout.client.utils.DateUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(MainActivity.class);
    static Handler handler;
    static private HandlerThread handlerThread;
    private TextView mTextView;
    static private ScrollView scrollview;
    private Switch switchAutoscroll;
    TextViewLogger newAppender;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            setTitle("NS Client v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        try {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.mipmap.ic_launcher);
        } catch (NullPointerException e) {
            // no action
        }

        if(handler==null) {
            handlerThread = new HandlerThread(MainActivity.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        scrollview = ((ScrollView) findViewById(R.id.scrollView));
        switchAutoscroll = ((Switch) findViewById(R.id.nsAutoScroll));
        mTextView = (TextView) findViewById(R.id.log);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.getLoggerList();
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        newAppender = new TextViewLogger(mTextView, new Handler(Looper.getMainLooper()));
        newAppender.setContext(lc);
        newAppender.start();
        logger.addAppender(newAppender);

        logger.setLevel(Level.DEBUG);
        logger.setAdditive(true);

        handler.post(new Runnable() {
            @Override
            public void run() {
                startService(new Intent(getApplicationContext(), ServiceNS.class));
                registerBus();
                setupAlarmManager();
                log.debug("ALARMMAN setup");
                onStatusEvent(new EventRestart());
            }
        });

        boolean autoscrollEnabled = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("nsAutoScroll", true);
        switchAutoscroll.setChecked(autoscrollEnabled);
        switchAutoscroll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("nsAutoScroll", isChecked);
                editor.commit();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_preferences) {
            log.debug("Opening preferences activity");
            Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_clearlog) {
            newAppender.clearLog();
            log.debug("Log cleared");
            return true;
        }

        if (id == R.id.action_restartservices) {
            MainApp.bus().post(new EventRestart());
            return true;
        }

        if (id == R.id.action_showqueue) {
            log.debug(UploadQueue.status());
            return true;
        }

        if (id == R.id.action_deliverqueue) {
            UploadQueue.resend("Menu");
            return true;
        }

        if (id == R.id.action_clearqueue) {
            UploadQueue.reset();
            return true;
        }

        if (id == R.id.action_ressenddanaapp) {
            XDripEmulator xe = new XDripEmulator();
            xe.sendToBroadcastReceiverToDanaAps(getApplicationContext());
            return true;
        }

        if (id == R.id.action_runtest) {
            createTestBroadast();
            return true;
        }
/*
        if (id == R.id.action_readstatus) {
            log.debug("Reading status");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    DanaConnection dc = MainApp.getDanaConnection();
                    dc.connectIfNotConnected("connect req from UI");
                    dc.pingStatus();
                }
            });
            return true;
        }

        if (id == R.id.action_exit) {
            log.debug("Exiting");

            //MainApp.closeDbHelper(); !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            MainApp.getNSClient().destroy();
            MainApp.setNSClient(null);
            finish();
            System.runFinalization();
            System.exit(0);
            return true;
        }
*/
        return super.onOptionsItemSelected(item);
    }

    public class TextViewLogger extends AppenderBase<ILoggingEvent> {
        private final Handler handler;
        private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        private TextView mTextView;

        private List<String> listLog = new ArrayList<String>();
        private int MAX_ERROR_LINES = 400;

        private void addToLog(String str) {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            try { MAX_ERROR_LINES = SP.getInt("ns_log_size", 400); } catch(Exception e) {}
            if (str.length() > 0) {
                listLog.add( str) ;
            }
            // remove the first line if log is too large
            if (listLog.size() >= MAX_ERROR_LINES) {
                listLog.remove(0);
            }
        }

        private void updateLog() {
            String log = "";
            for (String str : listLog) {
                log += str + "\n";
            }
            setToGui(mTextView, log);
        }

        private void setToGui(final TextView textView, final String log) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(log);
                    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
                    if (SP.getBoolean("nsAutoScroll", true)) {
                        scrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                }
            });
        }

        public TextViewLogger(TextView mTextView, Handler handler) {
            this.mTextView = mTextView;
            this.handler = handler;
        }

        public void clearLog() {
            listLog = new ArrayList<String>();
            updateLog();
        }

        @Override
        protected void append(final ILoggingEvent eventObject) {
            if (mTextView != null) {
                addToLog(timeFormat.format(new Date()) + " " + eventObject.getMessage());
                updateLog();
            }
        }
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventRestart e) {
        final SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        final String web = SP.getString("ns_url", "");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView viewWeb = ((TextView) findViewById(R.id.nsWeb));
                viewWeb.setText(Html.fromHtml("<a href=" + web + ">" + web + "</a>"));
                viewWeb.setMovementMethod(LinkMovementMethod.getInstance());
            }});
    }

    private boolean haveNetworkConnection() {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

    private void chancelAlarmManager() {
        AlarmManager am = ( AlarmManager ) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent( "info.nightscout.client.ReceiverKeepAlive.action.PING"  );
        PendingIntent pi = PendingIntent.getBroadcast( this, 0, intent, 0 );

        am.cancel(pi);
    }

    private void setupAlarmManager() {
        AlarmManager am = ( AlarmManager ) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent( "info.nightscout.client.ReceiverKeepAlive.action.PING"  );
        PendingIntent pi = PendingIntent.getBroadcast( this, 0, intent, 0 );

        long interval = 1*60_000L;
        long triggerTime = SystemClock.elapsedRealtime() + interval;

        try {
            pi.send();
        } catch (PendingIntent.CanceledException e) {
        }

        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime(), interval, pi);

        List<ResolveInfo> queryBroadcastReceivers = getPackageManager().queryBroadcastReceivers(intent, 0);
        log.debug("ALARMMAN: receivers " + queryBroadcastReceivers.size());

    }
    /*
     *
     *   ***** TEST CODE ******
     *
     */

    private void createTestBroadast() {
        Thread testAddTreatment =  new Thread(){
            @Override
            public void run() {
                log.debug("----- TEST ACTION -----");
                // Create new record with 1U of insulin and 24g of carbs
                //
                // there is a little hack because I don't receive treatments broadcasts here
                // I have to receive _id of created record via MainApp().bus()
                // So onUpdate looks for "NSCLIENTTESTRECORD" key
                try {
                    Context context = MainApp.instance().getApplicationContext();
                    JSONObject data = new JSONObject();
                    data.put("eventType", "Meal Bolus");
                    data.put("insulin", 1);
                    data.put("carbs", 24);
                    data.put("created_at", DateUtil.toISOString(new Date()));
                    data.put("NSCLIENTTESTRECORD", "NSCLIENTTESTRECORD");
                    Bundle bundle = new Bundle();
                    bundle.putString("action", "dbAdd");
                    bundle.putString("collection", "treatments"); // "treatments" || "entries" || "devicestatus" || "profile" || "food"
                    bundle.putString("data", data.toString());
                    Intent intent = new Intent(Intents.ACTION_DATABASE);
                    intent.putExtras(bundle);
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    context.sendBroadcast(intent);
                    List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                    if (q.size() < 1) {
                        log.error("TEST DBADD No receivers");
                    } else log.debug("TEST DBADD dbAdd " + q.size() + " receivers");
                } catch (JSONException e) {
                }
            }
        };
        testAddTreatment.start();
    }

    @Subscribe
    public void onStatusEvent(final TestReceiveID t) {
        Thread cont = new Thread() {
            @Override
            public void run() {
                String _id = t._id;
                Object o = new Object();
                // Now change insulin to 2U
                try {
                    Context context = MainApp.instance().getApplicationContext();
                    JSONObject data = new JSONObject();
                    data.put("insulin", 2);
                    Bundle bundle = new Bundle();
                    bundle.putString("action", "dbUpdate");
                    bundle.putString("collection", "treatments"); // "treatments" || "entries" || "devicestatus" || "profile" || "food"
                    bundle.putString("data", data.toString());
                    bundle.putString("_id", _id);
                    Intent intent = new Intent(Intents.ACTION_DATABASE);
                    intent.putExtras(bundle);
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    context.sendBroadcast(intent);
                    List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                    if (q.size() < 1) {
                        log.error("TEST DBUPD No receivers");
                    } else log.debug("TEST DBUPD dbUpdate " + q.size() + " receivers");
                } catch (JSONException e) {
                }

                synchronized (o) {
                    try {
                        o.wait(10000);
                    } catch (InterruptedException e) {
                    }
                }

                // Now remove carbs from record
                try {
                    Context context = MainApp.instance().getApplicationContext();
                    JSONObject data = new JSONObject();
                    data.put("carbs", 1);
                    Bundle bundle = new Bundle();
                    bundle.putString("action", "dbUpdateUnset");
                    bundle.putString("collection", "treatments"); // "treatments" || "entries" || "devicestatus" || "profile" || "food"
                    bundle.putString("data", data.toString());
                    bundle.putString("_id", _id);
                    Intent intent = new Intent(Intents.ACTION_DATABASE);
                    intent.putExtras(bundle);
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    context.sendBroadcast(intent);
                    List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                    if (q.size() < 1) {
                        log.error("TEST DBUPDUN No receivers");
                    } else log.debug("TEST DBUPDUN dbUpdateUnset " + q.size() + " receivers");
                } catch (JSONException e) {
                }

                synchronized (o) {
                    try {
                        o.wait(10000);
                    } catch (InterruptedException e) {
                    }
                }

                // And finaly remove the record
                Context context = MainApp.instance().getApplicationContext();
                Bundle bundle = new Bundle();
                bundle.putString("action", "dbRemove");
                bundle.putString("collection", "treatments"); // "treatments" || "entries" || "devicestatus" || "profile" || "food"
                bundle.putString("_id", _id);
                Intent intent = new Intent(Intents.ACTION_DATABASE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
                List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                if (q.size() < 1){
                    log.error("TEST DBREMOVE No receivers");
                } else log.debug("TEST DBREMOVE dbRemove " + q.size() + " receivers");
            }
        };
        cont.start();
    }

    /*
     *
     *   ***** TEST CODE END******
     *
     */


}
