package com.example.honeypot01.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.honeypot01.model.*;
import com.example.honeypot01.stt.SherpaOnnxStt;
import com.google.firebase.firestore.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class CallDataManager {

    private static final String TAG = "CallDataManager";
    private static final String PREFS_NAME = "HoneypotPrefs";
    private static final String KEY_POT_NUMBER = "pot_number";

    private static final String AUDIO_DIR =
            "/storage/emulated/0/Recordings";

    private final Context appContext;
    private final FirebaseFirestore db;
    private final SharedPreferences prefs;

    public CallDataManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ================= CALL START =================
    public void handleCallStarted(CallDetailsHolder callDetails, long startTime) {
        Log.d(TAG, "Call Started: " + callDetails.getPhoneNumber());
        upsertSpamNumber(callDetails);
    }

    // ================= CALL END =================
    public void handleCallEnded(CallDetailsHolder callDetails,
                                long startTime,
                                long duration) {

        Log.d(TAG, "Call Ended - duration: " + duration / 1000 + "s");

        new Thread(() -> {
            try {
                // Đợi MIUI ghi xong file
                Thread.sleep(5000);

                File audioFile = getLatestAudioFile();

                if (audioFile == null) {
                    Log.w(TAG, "No audio file found");
                    saveToolCallLog(callDetails, startTime, duration, null);
                    return;
                }

                Log.d(TAG, "Using audio file: "
                        + audioFile.getName()
                        + " | size=" + audioFile.length()
                        + " | lastModified=" + audioFile.lastModified());

                Log.d(TAG, "[STEP 1] Starting STT transcription...");
                long sttStart = System.currentTimeMillis();
                
                String transcript = null;
                try {
                    // TIMEOUT WRAPPER: 60 giây tối đa cho STT
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    final File finalAudioFile = audioFile;
                    Future<String> future = executor.submit(() -> 
                        SherpaOnnxStt.transcribe(appContext, finalAudioFile)
                    );
                    
                    try {
                        transcript = future.get(60, TimeUnit.SECONDS);
                        Log.d(TAG, "[STEP 2] STT completed in " + (System.currentTimeMillis() - sttStart) + "ms");
                        Log.d(TAG, "[STEP 2] Result: " + (transcript == null ? "NULL" : transcript.length() + " chars"));
                    } catch (TimeoutException te) {
                        Log.e(TAG, "[STEP 2] STT TIMEOUT after 60s! Cancelling...");
                        future.cancel(true);
                        transcript = "Error: STT timeout (60s)";
                    } finally {
                        executor.shutdownNow();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "[STEP 2] STT CRASHED!", t);
                }

                Log.d(TAG, "[STEP 3] Saving to Firestore...");
                saveToolCallLog(callDetails, startTime, duration, transcript);

            } catch (Throwable t) {
                Log.e(TAG, "[FATAL] Error processing call audio", t);
                try {
                    saveToolCallLog(callDetails, startTime, duration, null);
                } catch (Throwable t2) {
                    Log.e(TAG, "[FATAL] Failed to save error log", t2);
                }
            }
            Log.d(TAG, "[END] Background thread finished");
        }).start();
    }

    // ================= SPAM NUMBER =================
    private void upsertSpamNumber(CallDetailsHolder callDetails) {
        String number = callDetails.getPhoneNumber();
        DocumentReference ref = db.collection("spam_numbers").document(number);

        SpamNumber initData = new SpamNumber(
                number,
                callDetails.getVerificationStatus(),
                callDetails.getHandlePresentation(),
                callDetails.getCallerDisplayName()
        );

        ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                ref.set(initData, SetOptions.merge());
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("call_count", FieldValue.increment(1));
            updates.put("last_seen", System.currentTimeMillis());
            ref.update(updates);
        });
    }

    // ================= TOOL CALL LOG =================
    private void saveToolCallLog(CallDetailsHolder callDetails,
                                 long startTime,
                                 long duration,
                                 String transcript) {

        String potNumber = prefs.getString(KEY_POT_NUMBER, "unknown");

        ToolCallLog log = new ToolCallLog(
                potNumber,
                callDetails.getPhoneNumber(),
                startTime,
                (int) (duration / 1000),
                transcript
        );

        db.collection("tool_call_logs").add(log)
                .addOnSuccessListener(doc ->
                        Log.d(TAG, "Call log saved: " + doc.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to save call log", e));
    }

    private File getLatestAudioFile() {
        File dir = new File(AUDIO_DIR);

        if (!dir.exists() || !dir.isDirectory()) {
            Log.e(TAG, "Audio directory not found: " + AUDIO_DIR);
            return null;
        }

        File[] all = dir.listFiles();
        Log.d(TAG, "Scanning audio directory: " + dir.getAbsolutePath() + " (files=" + (all == null ? 0 : all.length) + ")");

        File[] files = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".mp3")
                    || n.endsWith(".m4a")
                    || n.endsWith(".aac")
                    || n.endsWith(".wav")
                    || n.endsWith(".3gp")
                    || n.endsWith(".mp4");
        });

        if (files == null || files.length == 0) {
            Log.w(TAG, "No audio files found in " + AUDIO_DIR + " (supported: mp3/m4a/aac/wav/3gp/mp4)");
            return null;
        }

        // 🔥 SORT THE RIGHT WAY
        Arrays.sort(files, (f1, f2) ->
                Long.compare(f2.lastModified(), f1.lastModified())
        );

        return files[0];
    }
}
