from pathlib import Path

p = Path('src/main/java/com/pcopi/thaitvnew/MediaNavOverlay.java')
s = p.read_text(encoding='utf-8')
old = 'addView(bar, new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM));'
new = '''FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        // Keep the bottom media navigation above Samsung/Android system navigation area.
        // S22 devices can otherwise place the bar behind the gesture/navigation buttons.
        barLp.bottomMargin = dp(40);
        addView(bar, barLp);'''
if old not in s:
    raise SystemExit('Bottom navigation layout line not found')
s = s.replace(old, new, 1)
old2 = 'pp.setMargins(0, dp(8), 0, dp(64));'
new2 = 'pp.setMargins(0, dp(8), 0, dp(104));'
if old2 not in s:
    raise SystemExit('Page bottom margin line not found')
s = s.replace(old2, new2, 1)
p.write_text(s, encoding='utf-8')
print('Raised bottom media navigation above Samsung/Android system navigation area')
