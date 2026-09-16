from pathlib import Path

main = Path('src/main/java/com/pcopi/thaitvnew/MainActivity.java')
s = main.read_text(encoding='utf-8')

# TV boxes are normally landscape. The phone code intentionally hides the
# channel list/controls in landscape, which made the TV build look like a
# completely black screen before a channel was selected. Keep the full
# channel UI visible on TV and avoid phone-only fullscreen behavior.
old = '''private void applyOrientation(int o){
        boolean landscape=o==Configuration.ORIENTATION_LANDSCAPE;
        if(normalPanel!=null)normalPanel.setVisibility(View.VISIBLE);
        if(toolbar!=null)toolbar.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(controls!=null)controls.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(status!=null)status.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(recycler!=null)recycler.setVisibility(landscape?View.GONE:View.VISIBLE);'''
new = '''private void applyOrientation(int o){
        boolean landscape=o==Configuration.ORIENTATION_LANDSCAPE;
        if(BuildConfig.TV_BOX_BUILD){
            // TV is a large-screen, D-pad driven UI. Keep the channel list,
            // playlist controls and status visible instead of using the phone
            // fullscreen player layout.
            if(normalPanel!=null)normalPanel.setVisibility(View.VISIBLE);
            if(toolbar!=null)toolbar.setVisibility(View.VISIBLE);
            if(controls!=null)controls.setVisibility(View.VISIBLE);
            if(status!=null)status.setVisibility(View.VISIBLE);
            if(recycler!=null)recycler.setVisibility(View.VISIBLE);
            if(drawer!=null)drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            if(playerFrame!=null){
                playerFrame.setBackgroundColor(Color.BLACK);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(300));
                playerFrame.setLayoutParams(lp);
            }
            if(closePlayer!=null)closePlayer.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            return;
        }
        if(normalPanel!=null)normalPanel.setVisibility(View.VISIBLE);
        if(toolbar!=null)toolbar.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(controls!=null)controls.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(status!=null)status.setVisibility(landscape?View.GONE:View.VISIBLE);
        if(recycler!=null)recycler.setVisibility(landscape?View.GONE:View.VISIBLE);'''
if old not in s:
    raise SystemExit('applyOrientation anchor not found')
s = s.replace(old, new, 1)

# Keep D-pad keys working with Media3 PlayerView on TV.
anchor = '    @Override protected void onCreate(Bundle b){super.onCreate(b);'
if anchor not in s:
    raise SystemExit('onCreate anchor not found')
insert = '''    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        if(BuildConfig.TV_BOX_BUILD && playerView!=null && playerView.dispatchKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }

'''
s = s.replace(anchor, insert + anchor, 1)

# Make the PlayerView the initial focus target for remote-control navigation.
old2 = 'player=new ExoPlayer.Builder(this).setRenderersFactory(renderers).setMediaSourceFactory(mf).build();playerView.setPlayer(player);'
new2 = 'player=new ExoPlayer.Builder(this).setRenderersFactory(renderers).setMediaSourceFactory(mf).build();playerView.setPlayer(player);if(BuildConfig.TV_BOX_BUILD){playerView.setFocusable(true);playerView.setFocusableInTouchMode(true);playerView.requestFocus();}'
if old2 not in s:
    raise SystemExit('player setup anchor not found')
s = s.replace(old2, new2, 1)
main.write_text(s, encoding='utf-8')

# TV boxes should not receive the phone-only gesture overlay. Also disable
# the custom media-navigation overlay on TV because the normal TV UI already
# exposes the channel/playlist controls and the overlay is touch-oriented.
app = Path('src/main/java/com/pcopi/thaitvnew/ThaiTVApplication.java')
s = app.read_text(encoding='utf-8')
old = 'private void installOverlay(Activity a) {\n        boolean land ='
new = '''private void installOverlay(Activity a) {
        if (BuildConfig.TV_BOX_BUILD) return;
        boolean land ='''
if old not in s:
    raise SystemExit('installOverlay anchor not found')
s = s.replace(old, new, 1)
oldm = 'private void installMediaNav(Activity a) {\n        boolean portrait ='
newm = '''private void installMediaNav(Activity a) {
        if (BuildConfig.TV_BOX_BUILD) return;
        boolean portrait ='''
if oldm not in s:
    raise SystemExit('installMediaNav anchor not found')
s = s.replace(oldm, newm, 1)
app.write_text(s, encoding='utf-8')

print('Applied TV Box landscape UI, D-pad focus, and overlay isolation fix')
