package com.pcopi.thaitvnew;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ThaiTVApplication extends Application {
    private Activity currentActivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Activity, VolumeBrightnessOverlay> overlays = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Activity, MediaNavOverlay> mediaOverlays = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) { currentActivity = activity; installOverlay(activity); installMediaNav(activity); }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { VolumeBrightnessOverlay o=overlays.remove(a); if(o!=null)o.remove(); MediaNavOverlay m=mediaOverlays.remove(a); if(m!=null && m.getParent() instanceof ViewGroup)((ViewGroup)m.getParent()).removeView(m); if(currentActivity==a)currentActivity=null; }
        });
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration c) { if(currentActivity!=null) main.postDelayed(()->{installOverlay(currentActivity);installMediaNav(currentActivity);},250); }
            @Override public void onLowMemory() {}
        });
    }

    private void installOverlay(Activity activity) {
        boolean landscape=activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        VolumeBrightnessOverlay overlay=overlays.get(activity);
        if(!landscape){ if(overlay!=null)overlay.setOverlayEnabled(false); return; }
        if(overlay==null){
            overlay=new VolumeBrightnessOverlay(activity); overlays.put(activity,overlay);
            View content=activity.findViewById(android.R.id.content);
            if(content instanceof ViewGroup)((ViewGroup)content).addView(overlay,new ViewGroup.LayoutParams(-1,-1));
            else ((ViewGroup)activity.getWindow().getDecorView()).addView(overlay,new ViewGroup.LayoutParams(-1,-1));
        }
        overlay.setOverlayEnabled(true);overlay.bringToFront();
    }

    private void installMediaNav(Activity activity){
        boolean portrait=activity.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE;
        MediaNavOverlay nav=mediaOverlays.get(activity);
        if(nav==null){
            nav=new MediaNavOverlay(activity);mediaOverlays.put(activity,nav);
            View content=activity.findViewById(android.R.id.content);
            if(content instanceof ViewGroup)((ViewGroup)content).addView(nav,new ViewGroup.LayoutParams(-1,-1));
            else ((ViewGroup)activity.getWindow().getDecorView()).addView(nav,new ViewGroup.LayoutParams(-1,-1));
        }
        nav.setEnabledForOrientation(portrait);
        nav.bringToFront();
    }

    private static class VolumeBrightnessOverlay extends View {
        private final Activity activity;private final AudioManager audio;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();private final Handler handler=new Handler(Looper.getMainLooper());private float downX,downY,startLevel,shownLevel=-1f;private boolean changing,volumeMode;private long hideAt;private final Runnable hide=()->{if(System.currentTimeMillis()>=hideAt){shownLevel=-1f;invalidate();}};
        VolumeBrightnessOverlay(Activity a){super(a);activity=a;audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);setBackgroundColor(Color.TRANSPARENT);setClickable(true);setFocusable(false);}
        void setOverlayEnabled(boolean enabled){setVisibility(enabled?VISIBLE:GONE);if(!enabled){shownLevel=-1f;handler.removeCallbacks(hide);}}
        void remove(){handler.removeCallbacks(hide);if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(shownLevel<0f||!isShown())return;float w=getWidth(),h=getHeight(),cx=volumeMode?w-dp(70):dp(70),barH=Math.min(h*.46f,dp(300)),barW=dp(12),top=(h-barH)/2f,bottom=top+barH;paint.setColor(0xCC000000);rect.set(cx-dp(46),top-dp(64),cx+dp(46),bottom+dp(64));c.drawRoundRect(rect,dp(24),dp(24),paint);paint.setColor(0x55FFFFFF);rect.set(cx-barW/2f,top,cx+barW/2f,bottom);c.drawRoundRect(rect,barW/2f,barW/2f,paint);paint.setColor(Color.WHITE);float fill=bottom-barH*Math.max(0f,Math.min(1f,shownLevel));rect.set(cx-barW/2f,fill,cx+barW/2f,bottom);c.drawRoundRect(rect,barW/2f,barW/2f,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(18));c.drawText(volumeMode?"VOL":"BRI",cx,top-dp(18),paint);c.drawText(Math.round(shownLevel*100f)+"%",cx,bottom+dp(34),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){if(getVisibility()!=VISIBLE)return false;float edge=Math.min(dp(120),getWidth()*.28f);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();changing=false;if(downX<=edge)volumeMode=false;else if(downX>=getWidth()-edge)volumeMode=true;else return false;startLevel=volumeMode?getVolumeLevel():getBrightnessLevel();shownLevel=startLevel;invalidate();return true;case MotionEvent.ACTION_MOVE:float dy=downY-e.getY();if(!changing&&Math.abs(dy)>=dp(8))changing=true;if(changing){float travel=Math.max(dp(260),getHeight()*.42f),level=Math.max(0f,Math.min(1f,startLevel+dy/travel));if(volumeMode)setVolumeLevel(level);else setBrightnessLevel(level);shownLevel=level;hideAt=System.currentTimeMillis()+1000;handler.removeCallbacks(hide);handler.postDelayed(hide,1050);invalidate();}return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:hideAt=System.currentTimeMillis()+650;handler.removeCallbacks(hide);handler.postDelayed(hide,700);return true;default:return true;}}
        private float getVolumeLevel(){if(audio==null)return 0f;int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);return max>0?audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max:0f;}private void setVolumeLevel(float level){if(audio==null)return;int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(level*max),0);}private float getBrightnessLevel(){float b=activity.getWindow().getAttributes().screenBrightness;if(b>=0f)return b;try{return Math.max(0f,Math.min(1f,Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS)/255f));}catch(Exception e){return .5f;}}private void setBrightnessLevel(float level){Window w=activity.getWindow();WindowManager.LayoutParams lp=w.getAttributes();lp.screenBrightness=Math.max(.02f,Math.min(1f,level));w.setAttributes(lp);}private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    }
}
