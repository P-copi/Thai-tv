from pathlib import Path

main = Path('src/main/java/com/pcopi/thaitvnew/MainActivity.java')
s = main.read_text(encoding='utf-8')
old = 'playerView.setUseController(true);'
new = '''playerView.setUseController(true);
        if (BuildConfig.TV_BOX_BUILD) {
            // Legacy Android TV boxes can show a black SurfaceView.
            // TextureView is generally more compatible with older GPU/compositor implementations.
            playerView.setSurfaceType(PlayerView.SURFACE_TYPE_TEXTURE_VIEW);
        }'''
if old not in s:
    raise SystemExit('PlayerView controller line not found')
s = s.replace(old, new, 1)
main.write_text(s, encoding='utf-8')

app = Path('src/main/java/com/pcopi/thaitvnew/ThaiTVApplication.java')
s = app.read_text(encoding='utf-8')
old = 'private void installOverlay(Activity a) {\n        boolean land ='
new = '''private void installOverlay(Activity a) {
        // Volume/brightness gesture overlay is intended for touch phones.
        // Skip it on legacy Android TV boxes so it cannot interfere with the TV compositor/input layer.
        if (BuildConfig.TV_BOX_BUILD) return;
        boolean land ='''
if old not in s:
    raise SystemExit('installOverlay anchor not found')
s = s.replace(old, new, 1)
app.write_text(s, encoding='utf-8')

print('Applied legacy Android TV rendering/input compatibility fix')
