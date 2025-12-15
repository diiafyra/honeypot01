package com.example.honeypot01.stt;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class VoskModelProvider {
    private static final String MODEL_ASSET_DIR = "vosk-model-small-vn-0.4";
    private static final String MODEL_LOCAL_DIR = "vosk-model-small-vn-0.4";

    private VoskModelProvider() {}

    public static File ensureModelOnDisk(Context context) throws Exception {
        File dstDir = new File(context.getFilesDir(), MODEL_LOCAL_DIR);

        // "Already extracted" check
        if (new File(dstDir, "conf").isDirectory() && new File(dstDir, "graph").isDirectory()) {
            return dstDir;
        }

        if (dstDir.exists()) deleteRecursive(dstDir);
        if (!dstDir.mkdirs()) throw new IllegalStateException("Failed to create model dir: " + dstDir.getAbsolutePath());

        copyAssetFolder(context.getAssets(), MODEL_ASSET_DIR, dstDir);
        return dstDir;
    }

    private static void copyAssetFolder(AssetManager assetManager, String assetPath, File dstDir) throws Exception {
        String[] children = assetManager.list(assetPath);
        if (children == null) return;

        if (children.length == 0) {
            copyAssetFile(assetManager, assetPath, dstDir);
            return;
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childDst = new File(dstDir, child);

            String[] grandChildren = assetManager.list(childAssetPath);
            if (grandChildren != null && grandChildren.length > 0) {
                if (!childDst.exists() && !childDst.mkdirs()) {
                    throw new IllegalStateException("Failed to mkdir: " + childDst.getAbsolutePath());
                }
                copyAssetFolder(assetManager, childAssetPath, childDst);
            } else {
                copyAssetFile(assetManager, childAssetPath, childDst);
            }
        }
    }

    private static void copyAssetFile(AssetManager assetManager, String assetFilePath, File dstFile) throws Exception {
        File parent = dstFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to mkdir: " + parent.getAbsolutePath());
        }

        try (InputStream in = assetManager.open(assetFilePath);
             FileOutputStream out = new FileOutputStream(dstFile)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
