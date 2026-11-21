[21/11/2026 05:14 م] °•♡ رَفِيـقُ دَربِـي ♡•°: package com.example.adhanhelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private TextView status;
    private EditText city, country, method;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("adhan", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(100, 40, 100, 40);
        sv.addView(l);

        status = new TextView(this);
        status.setTextSize(22);
        l.addView(status);

        l.addView(label("المدينة (بالانجليزي، مثال: Amman)"));
        city = field(sp.getString("city", "Amman"), false);
        l.addView(city);
        l.addView(label("الدولة (مثال: Jordan)"));
        country = field(sp.getString("country", "Jordan"), false);
        l.addView(country);
        l.addView(label("رقم طريقة الحساب (23 = وزارة الأوقاف الأردنية، 4 = أم القرى، 5 = مصر، 3 = رابطة العالم الإسلامي)"));
        method = field(sp.getString("method", "23"), true);
        l.addView(method);

        l.addView(btn("حفظ وتحديث المواقيت", v -> {
            sp.edit().putString("city", city.getText().toString().trim())
                    .putString("country", country.getText().toString().trim())
                    .putString("method", method.getText().toString().trim()).apply();
            AdhanService.start(this, AdhanService.ACTION_SCHEDULE, null, null);
            Toast.makeText(this, "تم الحفظ، جاري جلب المواقيت", Toast.LENGTH_LONG).show();
        }));
        l.addView(btn("اختيار ملف الأذان", v -> pick("adhan_path")));
        l.addView(btn("اختيار ملف أذان الفجر (اختياري)", v -> pick("fajr_path")));
        l.addView(btn("تجربة الأذان الآن", v ->
                AdhanService.start(this, AdhanService.ACTION_PLAY, "Test", "التجربة")));

        setContentView(sv);
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        AdhanService.start(this, AdhanService.ACTION_SCHEDULE, null, null);
    }

    @Override protected void onResume() {
        super.onResume();
        status.setText("المدينة: " + sp.getString("city", "") + "\n"
                + "ملف الأذان: " + name(sp.getString("adhan_path", "")) + "\n"
                + "ملف الفجر: " + name(sp.getString("fajr_path", "")) + "\n"
                + sp.getString("next_text", "") + "\n");
    }

    private String name(String path) {
        return path.isEmpty() ? "(لم يتم الاختيار)" : new File(path).getName();
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, 24, 0, 4);
        return t;
    }

    private EditText field(String v, boolean number) {
        EditText e = new EditText(this);
        e.setText(v);
        e.setSingleLine(true);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private Button btn(String t, android.view.View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(t);
        b.setOnClickListener(c);
        return b;
    }
[21/11/2026 05:14 م] °•♡ رَفِيـقُ دَربِـي ♡•°: // ---------- اختيار ملف صوت من الذاكرة / USB ----------
    private void pick(String prefKey) {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            Toast.makeText(this, "اسمح بالوصول للملفات ثم اضغط الزر مرة ثانية", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "جاري البحث عن ملفات الصوت...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<File> found = new ArrayList<>();
            scan(Environment.getExternalStorageDirectory(), 0, found);
            File[] vols = new File("/storage").listFiles();
            if (vols != null) for (File v : vols) {
                String n = v.getName();
                if (!n.equals("self") && !n.equals("emulated")) scan(v, 0, found);
            }
            runOnUiThread(() -> showList(prefKey, found));
        }).start();
    }

    private void scan(File d, int depth, List<File> out) {
        if (depth > 4 || out.size() > 300) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (!n.startsWith(".") && !n.equals("Android")) scan(f, depth + 1, out);
            } else {
                String x = n.toLowerCase(Locale.ROOT);
                if (x.endsWith(".mp3")  x.endsWith(".wav")  x.endsWith(".ogg")
                         x.endsWith(".m4a")  x.endsWith(".aac")) out.add(f);
            }
        }
    }

    private void showList(String prefKey, List<File> files) {
        if (files.isEmpty()) {
            Toast.makeText(this, "ما لقيت ملفات صوت. حط الملف بمجلد Download أو على USB", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[files.size()];
        for (int i = 0; i < items.length; i++)
            items[i] = files.get(i).getName() + "   (" + files.get(i).getParentFile().getName() + ")";
        new AlertDialog.Builder(this).setTitle("اختر الملف")
                .setItems(items, (d, which) -> {
                    sp.edit().putString(prefKey, files.get(which).getAbsolutePath()).apply();
                    onResume();
                }).show();
    }
}
