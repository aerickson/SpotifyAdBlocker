package com.cameron.spotifyadblocker;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Cameron on 6/7/2016.
 */
// Note: Rename this class during debugging (Refactor->Rename for ease). Android caching may prevent the service from binding on a previously-tested device.
// rename to CustomNotificationListener before committing for real
public class CustomNotificationListener extends NotificationListenerService {
    private boolean muted;
    private int originalVolume;
    private int zeroVolume;
    private static Timer timer;
    private static boolean running;
    private HashSet<String> blocklist;

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    public int onStartCommand(Intent intent, int flags, int startID) {
        timer = new Timer();
        running = true;
        blocklist = new HashSet<String>();
        muted = false;
        originalVolume = 0;
        zeroVolume = 0;

        // Load blocklist
        Resources resources = getResources();
        InputStream inputStream = resources.openRawResource(R.raw.blocklist);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                blocklist.add(line);
            }
            SharedPreferences preferences = getSharedPreferences(getString(R.string.saved_filters), MODE_PRIVATE);
            blocklist.addAll((Collection<? extends String>) preferences.getAll().values());
        } catch (IOException e) {
            e.printStackTrace();
        }
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                StatusBarNotification[] notifications = getActiveNotifications();
                Notification notification = new Notification();
                boolean foundNotification = false;
                if (notifications != null) {
//                    Log.d("scheduleAtFixedRate", "Checking notifications.");

                    // Find which notification is Spotify
                    for (int i = 0; i < notifications.length; ++i) {
                        String name = notifications[i].getPackageName();
                        if (name.contains("spotify")) {
//                            Log.d("scheduleAtFixedRate", name);
                            notification = notifications[i].getNotification();
                            foundNotification = true;
                            break;
                        }
                    }
                    // Check if it is an ad
                    if (foundNotification) {
                        Bundle extras = notification.extras;
                        String title = extras.getCharSequence(Notification.EXTRA_TITLE, "").toString();
                        String text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString();
                        Log.d("scheduleAtFixedRate", text);
                        Log.d("scheduleAtFixedRate", title);
//                            boolean isAdPlaying = blocklist.contains(title);
//                        boolean isAdPlaying = !title.contains("-");
                        boolean isAdPlaying = text.isEmpty();
                        String s = isAdPlaying? "Ad playing" : "Ad not playing";
                        Log.d("scheduleAtFixedRate", s);
                        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (isAdPlaying && !muted) {
                            Log.i("scheduleAtFixedRate", "Ad detected, setting mute state.");
                            Log.i("scheduleAtFixedRate", ("Track info: '" + text + "' '" + title + "'"));
                            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, zeroVolume, AudioManager.FLAG_SHOW_UI);
                            muted = true;
                        }
                        else if (!isAdPlaying && muted) {
                            Log.i("scheduleAtFixedRate", "Ad over, restoring volume.");
                            Log.i("scheduleAtFixedRate", ("Track info: '" + text + "' '" + title + "'"));
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, AudioManager.FLAG_SHOW_UI);
                            muted = false;
                        }
                    }
                }
            }
        }, 10, 250);
        return START_NOT_STICKY;
    }

    public static void killService() {
        if(timer!=null) {
            timer.cancel();
        }
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onDestroy() {
        Log.d("onDestroy", "Destroying Service");
        try {
            killService();
            Log.d("onDestroy", "Timer canceled.");
        } catch (NullPointerException ex) {
            Log.w("onDestroy", "NullPointer encountered while cancelling timer.");
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) { }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) { }
}
