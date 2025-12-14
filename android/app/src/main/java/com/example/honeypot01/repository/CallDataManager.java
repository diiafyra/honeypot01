package com.example.honeypot01.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.honeypot01.model.CallDetailsHolder;
import com.example.honeypot01.model.SpamNumber;
import com.example.honeypot01.model.ToolCallLog;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class CallDataManager {

    private static final String TAG = "CallDataManager";
    private static final String PREFS_NAME = "HoneypotPrefs";
    private static final String KEY_POT_NUMBER = "pot_number";

    private final FirebaseFirestore db;
    private final SharedPreferences prefs;

    public CallDataManager(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ================= CALL START =================
    public void handleCallStarted(CallDetailsHolder callDetails, long startTime) {
        String spamNumber = callDetails.getPhoneNumber();
        Log.d(TAG, "📞 Call Started from: " + spamNumber);

        upsertSpamNumber(callDetails);
    }

    // ================= CALL END =================
    public void handleCallEnded(CallDetailsHolder callDetails,
                                long startTime,
                                long endTime,
                                long duration) {

        Log.d(TAG, "❌ Call Ended - duration: " + duration / 1000 + "s");
        saveToolCallLog(callDetails, startTime, duration);
    }

    // ================= SPAM NUMBER =================
    private void upsertSpamNumber(CallDetailsHolder callDetails) {
        String spamNumber = callDetails.getPhoneNumber();
        DocumentReference ref =
                db.collection("spam_numbers").document(spamNumber);

        // 1️⃣ Data chỉ dùng nếu document CHƯA tồn tại
        SpamNumber initData = new SpamNumber(
                spamNumber,
                callDetails.getVerificationStatus(),
                callDetails.getCallType(),
                callDetails.getCallerDisplayName()
        );

        Log.d(TAG, "🔄 Upserting SpamNumber: " + initData);

        // Tạo doc nếu chưa có
        ref.set(initData, SetOptions.merge());

        // 2️⃣ Update cho cả 2 trường hợp
        Map<String, Object> updates = new HashMap<>();
        long now = System.currentTimeMillis();

        updates.put("call_count", FieldValue.increment(1));
        updates.put("last_seen", now);

        ref.update(updates)
                .addOnSuccessListener(v ->
                        Log.d(TAG, "✅ SpamNumber updated: " + spamNumber))
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ SpamNumber update failed", e));
    }

    // ================= TOOL CALL LOG =================
    private void saveToolCallLog(CallDetailsHolder callDetails,
                                 long startTime,
                                 long duration) {

        String potNumber = prefs.getString(KEY_POT_NUMBER, "unknown");
        String spamNumber = callDetails.getPhoneNumber();
        long now = System.currentTimeMillis();

        ToolCallLog log = new ToolCallLog(
                potNumber,
                spamNumber,
                now,
                (int) (duration / 1000)
        );

        db.collection("tool_call_logs")
                .add(log)
                .addOnSuccessListener(docRef ->
                        Log.d(TAG, "✅ Call log saved: " + spamNumber))
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ Failed to save call log", e));
    }
}
