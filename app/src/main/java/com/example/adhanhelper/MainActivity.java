package com.example.adhanhelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AdhanActivity extends Activity {
    private BroadcastReceiver receiver;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#0B3D2E"));

        String name = getIntent().getStringExtra("name");
        TextView t = new TextView(this);
        t.setText("حان وقت أذان " + name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(56);
        t.setGravity(Gravity.CENTER);
        l.addView(t);

        TextView s = new TextView(this);
        s.setText("الله أكبر الله أكبر");
        s.setTextColor(Color.parseColor("#D4AF37"));
        s.setTextSize(32);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, 30, 0, 60);
        l.addView(s);

        Button stop = new Button(this);
        stop.setText("إيقاف الأذان");
        stop.setOnClickListener(v -> {
            AdhanService.start(this, AdhanService.ACTION_STOP, null, null);
            finish();
        });
        l.addView(stop);
        setContentView(l);
        stop.requestFocus();

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { finish(); }
        };
        registerReceiver(receiver, new IntentFilter(AdhanService.ACTION_DONE));
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
