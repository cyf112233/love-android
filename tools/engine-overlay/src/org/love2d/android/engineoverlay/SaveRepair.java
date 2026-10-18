package org.love2d.android.engineoverlay;

import android.content.Context;

import java.io.File;

/** Renames zero-byte JSON files in the engine pack's save directory. */
final class SaveRepair {
    private SaveRepair() {
    }

    static void repair(Context context) {
        File saveDir = new File(context.getExternalFilesDir(null), "save");
        repairDirectory(saveDir);
    }

    private static int repairDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) return 0;
        File[] children = directory.listFiles();
        if (children == null) return 0;
        int repaired = 0;
        for (File file : children) {
            if (file.isDirectory()) {
                repaired += repairDirectory(file);
                continue;
            }
            if (file.length() != 0L) continue;
            if (!file.getName().toLowerCase().endsWith(".json")) continue;
            File backup = new File(file.getParentFile(),
                file.getName() + ".empty-" + System.currentTimeMillis() + ".bak");
            if (file.renameTo(backup)) repaired++;
        }
        return repaired;
    }
}
