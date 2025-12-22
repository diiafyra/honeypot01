package com.example.honeypot01.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.honeypot01.ai.GeminiClassifier;
import com.example.honeypot01.helper.FileHelper;
import com.example.honeypot01.model.*;
import com.example.honeypot01.stt.AssemblyAI;
import com.example.honeypot01.stt.SherpaOnnxStt;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CallDataManager {

    private final SpamNumbersRepository spamRepo;
    private final ToolCallLogsRepository logRepo;
    private final SharedPreferences prefs;
    private final Context context;
    private static final String TAG = "CallDataManager";
    private static final String KEY_POT_NUMBER = "pot_number";
    public CallDataManager(Context context) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        this.spamRepo = new SpamNumbersRepository(db);
        this.logRepo = new ToolCallLogsRepository(db);
        this.context = context;

        this.prefs = context.getApplicationContext()
                .getSharedPreferences("HoneypotPrefs", Context.MODE_PRIVATE);
    }

    public void onCallStarted(CallDetailsHolder call) {
        spamRepo.upsert(call);
    }

    public void onCallEnded(CallDetailsHolder call,
                            long startTime,
                            long duration) {

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                File audio = FileHelper.getLatestMp3();
                Log.d(TAG, "[STEP 1] Starting STT transcription...");
                long sttStart = System.currentTimeMillis();

                String transcript = null;
                try {
                    // TIMEOUT WRAPPER: 60 giây tối đa cho STT
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(() ->
                            SherpaOnnxStt.transcribe(context, audio)
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

                ToolCallLog log = new ToolCallLog(
                        prefs.getString(KEY_POT_NUMBER, "unknown"),
                        call.getPhoneNumber(),
                        startTime,
                        (int) (duration / 1000),
                        transcript,
                        audio != null ? audio.getAbsolutePath() : null
                );

                logRepo.save(log);

                String history = logRepo.getConcatTranscript(call.getPhoneNumber());
                Log.d(TAG, "History: " + history);

                String label = GeminiClassifier.classifyConversation(history);

                spamRepo.updateLabel(call.getPhoneNumber(), label);

                Log.d(TAG, "Label = " + label);

            } catch (Exception e) {
                Log.e(TAG, "Process failed", e);
            }
        }).start();
    }
}