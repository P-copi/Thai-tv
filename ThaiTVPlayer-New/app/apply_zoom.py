from pathlib import Path

p = Path('src/main/java/com/pcopi/thaitvnew/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = 'public class MainActivity extends AppCompatActivity {'
new = '''public class MainActivity extends AppCompatActivity {\n    // Pinch-to-zoom for the video area: 100% to 400%.\n    private static class ZoomPlayerView extends PlayerView {\n        private final android.view.ScaleGestureDetector zoomDetector;\n        private float zoomScale = 1.0f;\n        private boolean zooming = false;\n        ZoomPlayerView(android.content.Context c) {\n            super(c);\n            setPivotX(0.5f); setPivotY(0.5f);\n            zoomDetector = new android.view.ScaleGestureDetector(c, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {\n                @Override public boolean onScaleBegin(android.view.ScaleGestureDetector d) { zooming = true; return true; }\n                @Override public boolean onScale(android.view.ScaleGestureDetector d) {\n                    zoomScale *= d.getScaleFactor();\n                    zoomScale = Math.max(1.0f, Math.min(4.0f, zoomScale));\n                    setScaleX(zoomScale); setScaleY(zoomScale);\n                    return true;\n                }\n                @Override public void onScaleEnd(android.view.ScaleGestureDetector d) { zooming = false; }\n            });\n        }\n        @Override public boolean onTouchEvent(android.view.MotionEvent e) {\n            zoomDetector.onTouchEvent(e);\n            if (zooming || e.getPointerCount() > 1) return true;\n            return super.onTouchEvent(e);\n        }\n    }'''
if old not in s:
    raise SystemExit('MainActivity class declaration not found')
s = s.replace(old, new, 1)
old2 = 'playerView=new PlayerView(this);'
new2 = 'playerView=new ZoomPlayerView(this);'
if old2 not in s:
    raise SystemExit('PlayerView construction not found')
s = s.replace(old2, new2, 1)
p.write_text(s, encoding='utf-8')
print('Applied pinch-to-zoom video area, max 400%')
