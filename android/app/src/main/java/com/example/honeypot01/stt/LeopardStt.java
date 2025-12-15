package com.example.honeypot01.stt;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.example.honeypot01.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ai.picovoice.leopard.Leopard;
import ai.picovoice.leopard.LeopardException;
import ai.picovoice.leopard.LeopardTranscript;

public final class LeopardStt {

    private static final String TAG = "LeopardStt";

    private static final Object LOCK = new Object();
    private static Leopard leopard;
    private static String cachedModelPath;

    private static final long MIN_MODEL_BYTES = 1_000_000L;

    private LeopardStt() {
    }

    public static String transcribe(Context appContext, File audioFile) throws LeopardException, IOException {
        if (appContext == null) {
            throw new IllegalArgumentException("appContext is null");
        }
        if (audioFile == null) {
            throw new IllegalArgumentException("audioFile is null");
        }
        if (!audioFile.exists()) {
            throw new IllegalArgumentException("Audio file not found: " + audioFile.getAbsolutePath());
        }

        Leopard engine = getOrCreate(appContext.getApplicationContext());

        LeopardTranscript result = engine.processFile(audioFile.getAbsolutePath());
        try {
            return result.getTranscriptString();
        } catch (Throwable t) {
            // Fallback in case the Android binding exposes a different accessor.
            Log.w(TAG, "LeopardTranscript.getTranscriptString() not available, falling back to toString()", t);
            return String.valueOf(result);
        }
    }

    private static Leopard getOrCreate(Context appContext) throws LeopardException, IOException {
        synchronized (LOCK) {
            if (leopard != null) {
                return leopard;
            }

            if (BuildConfig.PICOVOICE_ACCESS_KEY == null || BuildConfig.PICOVOICE_ACCESS_KEY.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Missing Picovoice AccessKey. Set picovoice.accessKey in android/local.properties");
            }

            String modelPath = ensureModelFileOnDisk(appContext);

            leopard = new Leopard.Builder()
                    .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                    .setModelPath(modelPath)
                    .build(appContext);

            return leopard;
        }
    }

    private static String ensureModelFileOnDisk(Context appContext) throws IOException {
        if (cachedModelPath != null) {
            return cachedModelPath;
        }

        String assetName = BuildConfig.LEOPARD_MODEL_ASSET;
        if (assetName == null || assetName.trim().isEmpty()) {
            assetName = "leopard_params.pv";
        }

        File outFile = new File(appContext.getFilesDir(), assetName);
        if (!outFile.exists() || outFile.length() < MIN_MODEL_BYTES) {
            copyAssetToFile(appContext.getAssets(), assetName, outFile);
        }

        long bytes = outFile.length();
        Log.i(TAG, "Leopard model file: " + outFile.getAbsolutePath() + " (bytes=" + bytes + ")");
        if (bytes < MIN_MODEL_BYTES) {
            throw new IllegalStateException(
                    "Invalid Leopard model file (" + bytes + " bytes). " +
                            "This usually means you copied the wrong .pv file or an incomplete download. " +
                            "Replace the asset with the official Leopard model for Android (e.g., lib/common/leopard_params.pv from Picovoice Leopard) " +
                            "or a Console-downloaded Leopard model, then retry.");
        }

        cachedModelPath = outFile.getAbsolutePath();
        return cachedModelPath;
    }

    private static void copyAssetToFile(AssetManager assetManager, String assetName, File outFile) throws IOException {
        try (InputStream inputStream = assetManager.open(assetName);
             FileOutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (leopard != null) {
                try {
                    leopard.delete();
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to delete Leopard engine", t);
                }
                leopard = null;
            }
        }
    }
}
