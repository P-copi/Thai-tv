package com.pcopi.passwordvault;

import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Adds a timestamp to backup filenames without changing the existing backup/restore flow. */
public class MainActivityV4 extends MainActivityV3 {
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        Intent i = withTimestampedBackupName(intent);
        super.startActivityForResult(i, requestCode);
    }

    private Intent withTimestampedBackupName(Intent source) {
        Intent copy = new Intent(source);
        String action = copy.getAction();
        String name = "MyPasswordVault_Backup_" +
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".mpv";

        if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            copy.putExtra(Intent.EXTRA_TITLE, name);
        } else if (Intent.ACTION_CHOOSER.equals(action)) {
            Intent target = copy.getParcelableExtra(Intent.EXTRA_INTENT);
            if (target != null && Intent.ACTION_CREATE_DOCUMENT.equals(target.getAction())) {
                target = new Intent(target);
                target.putExtra(Intent.EXTRA_TITLE, name);
                copy.putExtra(Intent.EXTRA_INTENT, target);
            }
        }
        return copy;
    }
}
