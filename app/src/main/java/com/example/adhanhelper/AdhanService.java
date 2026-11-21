package com.example.adhanhelper;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AdhanService extends Service {
    static final String ACTION_SCHEDULE = "com.example.adhanhelper.SCHEDULE";
    static final String ACTION_PLAY = "com.example.adhanhelper.PLAY";
    static final String ACTION_STOP = "com.example.adhanhelper.STOP";
    static final String ACTION_DONE = "com.example.adhanhelper.DONE";
    static final String CH = "adhan";
    static final String[] KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] NAMES = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};

    private MediaPlayer player;

    static void start(Context c, String action, String key, String name) {
        Intent i = new Intent(c, AdhanService.class).setAction(action);
        if (key != null) i.putExtra("key", key).putExtra("name", name);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Adhan", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private SharedPreferences sp() { return getSharedPreferences("adhan", MODE_PRIVATE); }

    private Notification notif(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        return b.setContentTitle("مواقيت الأذان").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).build();
    }

    private void updateNotif(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notif(text));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notif(sp().getString("next_text", "الخدمة تعمل")));
        String a = intent == null ? ACTION_SCHEDULE : intent.getAction();
        if (a == null) a = ACTION_SCHEDULE;
        if (ACTION_PLAY.equals(a)) {
            play(intent.getStringExtra("key"), intent.getStringExtra("name"));
        } else if (ACTION_STOP.equals(a)) {
            stopPlayer();
        } else {
            new Thread(this::schedule).start();
        }
        return START_STICKY;
    }

    // ---------- الصوت ----------
    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void stopPlayer() {
        releasePlayer();
        sendBroadcast(new Intent(ACTION_DONE).setPackage(getPackageName()));
        updateNotif(sp().getString("next_text", "الخدمة تعمل"));
    }

    private void play(String key, String name) {
        releasePlayer();
        SharedPreferences p = sp();
        String path = p.getString("adhan_path", "");
        String fajr = p.getString("fajr_path", "");
        if ("Fajr".equals(key) && !fajr.isEmpty()) path = fajr;
updateNotif("حان وقت أذان " + name);
        try {
            Intent a = new Intent(this, AdhanActivity.class).putExtra("name", name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(a);
        } catch (Exception ignored) {}

        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (!path.isEmpty() && new File(path).exists()) {
                player.setDataSource(path);
            } else {
                player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            }
            player.setOnCompletionListener(m -> stopPlayer());
            player.setOnErrorListener((m, w, e) -> { stopPlayer(); return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopPlayer();
        }
    }

    // ---------- المواقيت والمنبّهات ----------
    private JSONObject fetch(SharedPreferences p) throws Exception {
        String u = "https://api.aladhan.com/v1/timingsByCity?city="
                + URLEncoder.encode(p.getString("city", "Amman"), "UTF-8")
                + "&country=" + URLEncoder.encode(p.getString("country", "Jordan"), "UTF-8")
                + "&method=" + URLEncoder.encode(p.getString("method", "23"), "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return new JSONObject(sb.toString()).getJSONObject("data").getJSONObject("timings");
    }

    private PendingIntent pi(int code, Intent i) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(this, code, i, fl)
                : PendingIntent.getService(this, code, i, fl);
    }

    private void setAlarm(AlarmManager am, long t, PendingIntent p) {
        if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
        else am.setExact(AlarmManager.RTC_WAKEUP, t, p);
    }

    private void schedule() {
        SharedPreferences p = sp();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONObject t = null;
        try {
            t = fetch(p);
            p.edit().putString("cache", t.toString()).putString("cache_date", today).apply();
        } catch (Exception e) {
            if (today.equals(p.getString("cache_date", ""))) {
                try { t = new JSONObject(p.getString("cache", "")); } catch (Exception ignored) {}
            }
        }

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Calendar now = Calendar.getInstance();

        // تحديث تلقائي كل يوم الساعة 00:10
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 10);
        tomorrow.set(Calendar.SECOND, 0);
        setAlarm(am, tomorrow.getTimeInMillis(),
                pi(10, new Intent(this, AdhanService.class).setAction(ACTION_SCHEDULE)));

        if (t == null) {
            setAlarm(am, now.getTimeInMillis() + 15 * 60 * 1000L,
                    pi(11, new Intent(this, AdhanService.class).setAction(ACTION_SCHEDULE)));
            String msg = "تعذر جلب المواقيت - سيعاد المحاولة";
            p.edit().putString("next_text", msg).apply();
            updateNotif(msg);
            return;
        }
String nextText = "لا مزيد من الأذان اليوم";
        long nextTime = Long.MAX_VALUE;
        for (int i = 0; i < KEYS.length; i++) {
            String s = t.optString(KEYS[i]);
            if (s.length() < 5) continue;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(s.substring(0, 2)));
            c.set(Calendar.MINUTE, Integer.parseInt(s.substring(3, 5)));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.after(now)) {
                Intent in = new Intent(this, AdhanService.class).setAction(ACTION_PLAY)
                        .putExtra("key", KEYS[i]).putExtra("name", NAMES[i]);
                setAlarm(am, c.getTimeInMillis(), pi(i, in));
                if (c.getTimeInMillis() < nextTime) {
                    nextTime = c.getTimeInMillis();
                    nextText = "الأذان القادم: " + NAMES[i] + " " + s.substring(0, 5);
                }
            }
        }
        p.edit().putString("next_text", nextText).apply();
        updateNotif(nextText);
    }

    @Override public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
