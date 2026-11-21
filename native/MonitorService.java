package com.smaam.app;
import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Watches the foreground app once a second and covers it with a lock screen when it is over its limit. */
public class MonitorService extends Service {
  static final Map<String, Long> LIVE = new ConcurrentHashMap<>();
  private final Handler h = new Handler(Looper.getMainLooper());
  private final Map<String, Long> open = new HashMap<>(), done = new HashMap<>();
  private String fg; private long last, mute; private int day = -1; private View overlay;

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public int onStartCommand(Intent i, int f, int id) {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("smaam", "SMAAM", NotificationManager.IMPORTANCE_LOW));
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "smaam") : new Notification.Builder(this);
    Notification n = b.setContentTitle("SMAAM is guarding your screen time").setSmallIcon(android.R.drawable.ic_lock_idle_lock).build();
    if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(1, n);
    h.removeCallbacksAndMessages(null); h.post(tick);
    return START_STICKY;
  }

  private final Runnable tick = new Runnable() { public void run() { try { poll(); } catch (Exception e) {} h.postDelayed(this, 1000); } };

  private void poll() throws Exception {
    Calendar c = Calendar.getInstance(); int d = c.get(Calendar.DAY_OF_YEAR);
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
    long now = System.currentTimeMillis();
    if (d != day) { day = d; done.clear(); open.clear(); last = c.getTimeInMillis(); fg = null; }
    UsageStatsManager um = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
    UsageEvents ev = um.queryEvents(last, now); UsageEvents.Event e = new UsageEvents.Event();
    while (ev.hasNextEvent()) {
      ev.getNextEvent(e); String p = e.getPackageName();
      if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) { if (!open.containsKey(p)) open.put(p, e.getTimeStamp()); fg = p; }
      else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
        Long s = open.remove(p);
        if (s != null) { Long x = done.get(p); done.put(p, (x == null ? 0 : x) + Math.max(0, e.getTimeStamp() - s)); }
        if (p.equals(fg)) fg = null;
      }
    }
    last = now;
    Map<String, Long> live = new HashMap<>(done);
    for (Map.Entry<String, Long> s : open.entrySet()) { Long x = live.get(s.getKey()); live.put(s.getKey(), (x == null ? 0 : x) + now - s.getValue()); }
    LIVE.clear(); LIVE.putAll(live);

    JSONObject cfg = new JSONObject(getSharedPreferences("smaam", 0).getString("rules", "{}"));
    JSONArray rs = cfg.optJSONArray("rules"); long total = 0; String hit = null;
    if (rs != null) for (int i = 0; i < rs.length(); i++) {
      JSONObject r = rs.getJSONObject(i); String p = r.getString("pkg");
      Long x = live.get(p); long used = x == null ? 0 : x; total += used;
      if (p.equals(fg) && (r.optBoolean("locked") || used >= r.getLong("limit") * 1000)) hit = r.optString("name");
    }
    long budget = cfg.optLong("budget") * 1000;
    boolean all = budget > 0 && total >= budget;
    if (fg != null && !allowed(fg) && (all || hit != null)) {
      if (now > mute) show(all ? "Time\u2019s up" : "Limit reached", all ? "Your daily screen time is used up. Everything is locked until tomorrow." : hit + " is locked for today.");
    } else if (overlay != null && (fg == null || allowed(fg))) hide();
  }

  /** Apps that are never blocked, so the phone stays usable. */
  private boolean allowed(String p) {
    if (p.equals(getPackageName()) || p.startsWith("com.android.systemui") || p.equals("android") || p.contains("permissioncontroller") || p.equals("com.android.settings")) return true;
    ResolveInfo ri = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
    if (ri != null && p.equals(ri.activityInfo.packageName)) return true;
    try { if (p.equals(((TelecomManager) getSystemService(TELECOM_SERVICE)).getDefaultDialerPackage())) return true; } catch (Exception x) {}
    return false;
  }

  private void show(String t, String m) {
    if (overlay != null || !Settings.canDrawOverlays(this)) return;
    LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setGravity(Gravity.CENTER); v.setPadding(60, 60, 60, 60); v.setBackgroundColor(0xFFE8DFCC);
    TextView a = new TextView(this); a.setText(t.toUpperCase()); a.setTextSize(34); a.setTypeface(Typeface.SERIF, Typeface.BOLD); a.setTextColor(0xFFB3261E); a.setRotation(-8); a.setGravity(Gravity.CENTER); a.setPadding(0, 0, 0, 70);
    TextView b = new TextView(this); b.setText(m); b.setTextSize(20); b.setTextColor(0xFF23201A); b.setGravity(Gravity.CENTER); b.setPadding(0, 0, 0, 70);
    Button go = new Button(this); go.setText("Go home");
    go.setOnClickListener(x -> { mute = System.currentTimeMillis() + 2500; startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); hide(); });
    v.addView(a); v.addView(b); v.addView(go);
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
      Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
    ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp); overlay = v;
  }

  private void hide() {
    if (overlay == null) return;
    try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay); } catch (Exception e) {}
    overlay = null;
  }

  @Override public void onDestroy() { h.removeCallbacksAndMessages(null); hide(); super.onDestroy(); }
}
