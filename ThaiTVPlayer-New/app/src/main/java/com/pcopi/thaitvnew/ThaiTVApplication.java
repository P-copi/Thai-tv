package com.pcopi.thaitvnew;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ThaiTVApplication extends Application {
    private Activity currentActivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<PlayerView, Boolean> installed = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                installVolumeGesture(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (currentActivity == a) currentActivity = null; }
        });
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration newConfig) {
                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && currentActivity != null) {
                    main.postDelayed(() -> installVolumeGesture(currentActivity), 300);
                }
            }
            @Override public void onLowMemory() {}
        });
    }

    private void installVolumeGesture(Activity activity) {
        if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) return;
        PlayerView playerView = findPlayerView(activity.getWindow().getDecorView());
        if (playerView == null || installed.containsKey(playerView)) return;
        installed.put(playerView, Boolean.TRUE);

        playerView.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            float lastY;
            boolean changingVolume;
            int accumulatedSteps;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getY();
                        lastY = downY;
                        changingVolume = false;
                        accumulatedSteps = 0;
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        float totalDy = downY - event.getY();
                        if (!changingVolume && Math.abs(totalDy) >= dp(activity, 18)) {
                            changingVolume = true;
                        }
                        if (changingVolume) {
                            float delta = lastY - event.getY();
                            lastY = event.getY();
                            accumulatedSteps += Math.round(delta / dp(activity, 45));
                            if (accumulatedSteps != 0) {
                                int direction = accumulatedSteps > 0 ? 1 : -1;
                                accumulatedSteps -= direction;
                                AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
                                if (am != null) {
                                    am.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        direction > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                                        AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND
                                    );
                                }
                            }
                            return true;
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return changingVolume;

                    default:
                        return false;
                }
            }
        });
    }

    private float dp(Activity activity, float value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }

    private PlayerView findPlayerView(View view) {
        if (view instanceof PlayerView) return (PlayerView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                PlayerView found = findPlayerView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
