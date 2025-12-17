package com.example.honeypot01.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.honeypot01.ai.GeminiClassifier;
import com.example.honeypot01.helper.FileHelper;
import com.example.honeypot01.model.*;
import com.example.honeypot01.stt.AssemblyAI;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;

public class CallDataManager {

    private final SpamNumbersRepository spamRepo;
    private final ToolCallLogsRepository logRepo;
    private final SharedPreferences prefs;
    private static final String TAG = "CallDataManager";
    private static final String KEY_POT_NUMBER = "pot_number";
    public CallDataManager(Context context) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        this.spamRepo = new SpamNumbersRepository(db);
        this.logRepo = new ToolCallLogsRepository(db);

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
                String transcript = audio != null
                        ? AssemblyAI.transcribe(audio)
                        : null;


                ToolCallLog log = new ToolCallLog(
                        prefs.getString(KEY_POT_NUMBER, "unknown"),
                        call.getPhoneNumber(),
                        startTime,
                        (int) (duration / 1000),
                        transcript
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
