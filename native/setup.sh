set -e
# copies the native Android code into the generated project and registers it in the manifest
cp native/*.java android/app/src/main/java/com/smaam/app/
RES=android/app/src/main/res
cp -r native/res/. $RES/
for f in $(find $RES -name splash.png); do case "$f" in *land*) cp native/splash-land.png "$f";; *) cp native/splash-port.png "$f";; esac; done
python3 - <<'PY'
p = "android/app/src/main/AndroidManifest.xml"
s = open(p).read()
head = """<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"/>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent></queries>
    <application"""
svc = """<service android:name=".MonitorService" android:exported="false" android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="screen time limiter"/>
        </service>
    </application>"""
s = s.replace("<application", head, 1).replace("</application>", svc, 1)
open(p, "w").write(s)
PY
