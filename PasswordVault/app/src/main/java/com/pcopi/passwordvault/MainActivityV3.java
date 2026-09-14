package com.pcopi.passwordvault;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/** Adds the optional save-and-lock action without changing the existing V2 save flow. */
public class MainActivityV3 extends MainActivityV2 {
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    void edit(VaultStore.Entry old) {
        super.edit(old);
        addSaveAndLockButton();
    }

    private void addSaveAndLockButton() {
        Button save = findSaveButton(root);
        if (save == null) return;
        ViewGroup parent = (ViewGroup) save.getParent();
        if (parent == null) return;

        Button saveLock = bt("บันทึกและล็อกแอป");
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(-1, d(54));
        if (save.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(
                    (android.widget.LinearLayout.LayoutParams) save.getLayoutParams());
            p.width = -1;
            p.height = d(54);
            p.setMargins(0, d(10), 0, 0);
            lp = p;
        }
        int index = parent.indexOfChild(save);
        parent.addView(saveLock, index + 1, lp);
        saveLock.setOnClickListener(v -> {
            ViewGroup oldRoot = root;
            save.performClick();
            // The existing Save action calls home() only after a successful save.
            // If validation/storage failed, remain on the edit screen.
            if (root != oldRoot) {
                master = "";
                lock();
            }
        });
    }

    private Button findSaveButton(View v) {
        if (v instanceof Button) {
            CharSequence t = ((Button) v).getText();
            if (t != null && t.toString().trim().equals("💾  บันทึก")) return (Button) v;
            if (t != null && t.toString().trim().equals("บันทึก")) return (Button) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button b = findSaveButton(g.getChildAt(i));
                if (b != null) return b;
            }
        }
        return null;
    }
}
