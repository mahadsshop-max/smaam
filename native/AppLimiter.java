package com.smaam.app;
import android.app.AppOpsManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Map;

@CapacitorPlugin(name = "AppLimiter")
public class AppLimiter extends Plugin {
  private void go(Intent i) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(i); }

  @PluginMethod public void status(PluginCall c) {
    Context x = getContext();
    AppOpsManager a = (AppOpsManager) x.getSystemService(Context.APP_OPS_SERVICE);
    int m = a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), x.getPackageName());
    JSObject o = new JSObject(); o.put("usage", m == AppOpsManager.MODE_ALLOWED); o.put("overlay", Settings.canDrawOverlays(x)); c.resolve(o);
  }
  @PluginMethod public void openUsage(PluginCall c) { go(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); c.resolve(); }
  @PluginMethod public void openOverlay(PluginCall c) {
    go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()))); c.resolve();
  }
  @PluginMethod public void apps(PluginCall c) {
    PackageManager pm = getContext().getPackageManager(); JSArray arr = new JSArray();
    Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
      String p = r.activityInfo.packageName; if (p.equals(getContext().getPackageName())) continue;
      JSObject o = new JSObject(); o.put("pkg", p); o.put("name", r.loadLabel(pm).toString()); arr.put(o);
    }
    JSObject res = new JSObject(); res.put("apps", arr); c.resolve(res);
  }
  @PluginMethod public void rules(PluginCall c) {
    Context x = getContext();
    x.getSharedPreferences("smaam", 0).edit().putString("rules", c.getString("json", "{}")).apply();
    Intent s = new Intent(x, MonitorService.class);
    if (Build.VERSION.SDK_INT >= 26) x.startForegroundService(s); else x.startService(s);
    c.resolve();
  }
  @PluginMethod public void usage(PluginCall c) {
    JSObject u = new JSObject();
    for (Map.Entry<String, Long> e : MonitorService.LIVE.entrySet()) u.put(e.getKey(), e.getValue());
    JSObject res = new JSObject(); res.put("usage", u); c.resolve(res);
  }
}
