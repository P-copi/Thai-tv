from pathlib import Path

p = Path('src/main/AndroidManifest.xml')
s = p.read_text(encoding='utf-8')

# Keep phone manifest untouched; this script runs only in the TV Box workflow.
if 'android.software.leanback' not in s:
    s = s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">', '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.hardware.faketouch" android:required="false" />''', 1)

s = s.replace('android:screenOrientation="sensor"', 'android:screenOrientation="landscape"', 1)

old = '''<category android:name="android.intent.category.LAUNCHER" />'''
new = '''<category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />'''
if 'android.intent.category.LEANBACK_LAUNCHER' not in s:
    if old not in s:
        raise SystemExit('launcher category not found')
    s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('Applied Android TV manifest compatibility settings')
