package com.example.honeypot01.anotherSTT.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.honeypot01.anotherSTT.SpeechToTextService;
import com.google.firebase.firestore.FirebaseFirestore;

public class SttResultReceiver extends BroadcastReceiver {
    private static final String TAG = "SttResultReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String requestId = intent.getStringExtra(SpeechToTextService.EXTRA_REQUEST_ID);
        String transcript = intent.getStringExtra(SpeechToTextService.EXTRA_TRANSCRIPT);
        String error = intent.getStringExtra(SpeechToTextService.EXTRA_ERROR);

        if (requestId == null || requestId.isEmpty()) {
            Log.e(TAG, "Missing request_id in STT result");
            return;
        }
        if (error != null && !error.isEmpty()) {
            Log.e(TAG, "STT error for " + requestId + ": " + error);
            return;
        }

        if (transcript == null) transcript = "";
        Log.d(TAG, "Received STT result for " + requestId + ": " + transcript);

        FirebaseFirestore.getInstance()
                .collection("tool_call_logs")
                .document(requestId)
                .update("transcript", transcript)
                .addOnSuccessListener(v -> Log.d(TAG, "Updated transcript for " + requestId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update transcript for " + requestId, e));
    }
}