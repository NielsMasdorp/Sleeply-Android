package com.nielsmasdorp.sleeply.interactor;

import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.nielsmasdorp.sleeply.R;
import com.nielsmasdorp.sleeply.model.Stream;
import com.nielsmasdorp.sleeply.service.StreamService;
import com.nielsmasdorp.sleeply.ui.stream.OnStreamServiceListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Niels Masdorp (NielsMasdorp)
 */
public class MainInteractorImpl implements MainInteractor {

    final static String TAG = MainInteractorImpl.class.getSimpleName();
    final static String LAST_STREAM_IDENTIFIER = "last_stream_identifier";

    Application application;
    StreamService streamService;
    Boolean bound = false;
    OnStreamServiceListener listener;
    List<Stream> streams;
    SharedPreferences preferences;
    Stream currentStream;

    public MainInteractorImpl(Application application, SharedPreferences preferences) {

        this.application = application;
        this.preferences = preferences;

        streams = new ArrayList<>();
        streams.add(new Stream(0, "https://api.soundcloud.com/tracks/110697958/stream", application.getString(R.string.rainy_stream_title), application.getString(R.string.rainy_stream_desc), R.drawable.rain_background));
        streams.add(new Stream(1, "https://api.soundcloud.com/tracks/13262271/stream", application.getString(R.string.ocean_stream_title), application.getString(R.string.ocean_stream_desc), R.drawable.ocean_background));
        streams.add(new Stream(2, "https://api.soundcloud.com/tracks/97924982/stream", application.getString(R.string.forest_stream_title), application.getString(R.string.forest_stream_desc), R.drawable.nature_background));
        streams.add(new Stream(3, "https://api.soundcloud.com/tracks/149844883/stream", application.getString(R.string.meditation_stream_title), application.getString(R.string.meditation_stream_desc), R.drawable.meditation_background));
    }

    @Override
    public void startService(OnStreamServiceListener listener) {
        this.listener = listener;

        Intent intent = new Intent(application, StreamService.class);
        if (!isServiceAlreadyRunning()) {
            Log.i(TAG, "onStart: service not running, starting service.");
            application.startService(intent);
        }
        Log.i(TAG, "onStart: binding to service.");
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        registerBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {

        Log.i(TAG, "onStart: registering broadcast receiver.");
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction(StreamService.STREAM_DONE_LOADING_INTENT);
        broadcastIntentFilter.addAction(StreamService.TIMER_DONE_INTENT);
        broadcastIntentFilter.addAction(StreamService.TIMER_UPDATE_INTENT);
        LocalBroadcastManager.getInstance(application).registerReceiver((broadcastReceiver), broadcastIntentFilter);
    }

    @Override
    public void unbindService() {

        if (bound) {
            application.unbindService(serviceConnection);
        }
        LocalBroadcastManager.getInstance(application).unregisterReceiver(broadcastReceiver);

        preferences.edit().putInt(LAST_STREAM_IDENTIFIER, currentStream.getId()).apply();
    }

    @Override
    public void playStream() {

        if (!streamService.isPlaying()) {
            streamService.playStream(currentStream);
            listener.setLoading();
        } else {
            streamService.stopStreaming();
            listener.streamStopped();
        }
    }

    @Override
    public void nextStream() {

        int currentStreamId = currentStream.getId();
        if (currentStreamId != (streams.size() - 1)) {
            currentStream = streams.get(currentStreamId + 1);
        } else {
            currentStream = streams.get(0);
        }

        if (streamService.isPlaying()) {
            streamService.stopStreaming();
            playStream();
        }

        listener.animateTo(currentStream);
    }

    @Override
    public void previousStream() {

        int currentStreamId = currentStream.getId();
        if (currentStreamId != 0) {
            currentStream = streams.get(currentStreamId - 1);
        } else {
            currentStream = streams.get(streams.size() - 1);
        }

        if (streamService.isPlaying()) {
            streamService.stopStreaming();
            playStream();
        }

        listener.animateTo(currentStream);
    }

    @Override
    public void setSleepTimer(int option) {

        if (streamService.isPlaying()) {
            streamService.setSleepTimer(calculateMs(option));
        } else {
            listener.error(application.getString(R.string.start_stream_error_toast));
        }
    }

    /**
     * See if the StreamService is already running in the background.
     *
     * @return boolean indicating if the service runs
     */
    private boolean isServiceAlreadyRunning() {
        ActivityManager manager = (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (StreamService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the intents the broadcast receiver receives
     *
     * @param intent
     */
    private void handleIntent(Intent intent) {

        if (intent.getAction().equals(StreamService.STREAM_DONE_LOADING_INTENT)) {
            boolean success = intent.getBooleanExtra(StreamService.STREAM_DONE_LOADING_SUCCESS, false);
            if (!success) {
                listener.streamStopped();
            } else {
                listener.streamPlaying();
            }
        } else if (intent.getAction().equals(StreamService.TIMER_DONE_INTENT)) {
            listener.streamStopped();
        } else if (intent.getAction().equals(StreamService.TIMER_UPDATE_INTENT)) {
            long timerValue = (long) intent.getIntExtra(StreamService.TIMER_UPDATE_VALUE, 0);
            listener.updateTimerValue(timerValue);
        }
    }

    private int calculateMs(int option) {
        switch (option) {
            case 0:
                return 0;
            case 1:
                return (int) TimeUnit.MINUTES.toMillis(15);
            case 2:
                return (int) TimeUnit.MINUTES.toMillis(20);
            case 3:
                return (int) TimeUnit.MINUTES.toMillis(30);
            case 4:
                return (int) TimeUnit.MINUTES.toMillis(40);
            case 5:
                return (int) TimeUnit.MINUTES.toMillis(50);
            case 6:
                return (int) TimeUnit.HOURS.toMillis(1);
            case 7:
                return (int) TimeUnit.HOURS.toMillis(2);
            case 8:
                return (int) TimeUnit.HOURS.toMillis(3);
            default:
                return 0;
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected: successfully bound to service.");
            StreamService.StreamBinder binder = (StreamService.StreamBinder) service;
            streamService = binder.getService();
            bound = true;
            currentStream = streamService.getPlayingStream();
            if (currentStream != null) {
                listener.restoreUI(currentStream, true);
            } else {
                int last = preferences.getInt(LAST_STREAM_IDENTIFIER, 0);
                currentStream = streams.get(last);
                listener.restoreUI(currentStream, false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "onServiceDisconnected: disconnected from service.");
            bound = false;
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            handleIntent(intent);
        }
    };
}
