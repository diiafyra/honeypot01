package com.example.honeypot01.repository;

import android.util.Log;

import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

import com.example.honeypot01.model.ToolCallLog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

public class ToolCallLogsRepository {

    private static final String TAG = "ToolCallLogsRepo";
    private final FirebaseFirestore db;

    public ToolCallLogsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public void save(ToolCallLog log) {
        db.collection("tool_call_logs")
                .add(log)
                .addOnSuccessListener(doc ->
                        Log.d(TAG, "Saved log: " + doc.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Save failed", e));
    }

    /**
     * Lấy toàn bộ transcript của 1 số điện thoại và concat lại
     */
    public String getConcatTranscript(String spamNumber)
            throws Exception {

        StringBuilder sb = new StringBuilder();

        var task = db.collection("tool_call_logs")
                .whereEqualTo("spam_number", spamNumber)
                .orderBy("call_time", Query.Direction.DESCENDING)
                .limit(20)
                .get();

        QuerySnapshot snapshot = com.google.android.gms.tasks.Tasks.await(task);

        int index = 1;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String transcript = doc.getString("transcript");

            if (transcript != null && !transcript.isEmpty()) {

                // 🔍 Log từng transcript
                Log.d(TAG,
                        "[" + index + "] " + transcript);

                sb.append(transcript).append("\n");
                index++;
            }
        }

        return sb.toString().trim();
    }


}

