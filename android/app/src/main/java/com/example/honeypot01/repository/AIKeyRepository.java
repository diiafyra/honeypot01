package cmc.cs.honeypot01.repository;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AIKeyRepository {

    private static final String TAG = "AIKeyRepository";

    private static final List<String> keyList =
            Collections.synchronizedList(new ArrayList<>());

    private static int currentIndex = 0;
    private static boolean loaded = false;

    /**
     * Blocking load – dùng khi app start hoặc trước khi gọi Gemini
     */
    public static synchronized void loadKeysBlocking() {
        if (loaded) return;

        try {
            QuerySnapshot snapshot = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("ai_keys")
                            .get(),
                    10,
                    TimeUnit.SECONDS
            );

            keyList.clear();

            for (var doc : snapshot.getDocuments()) {
                String apiKey = doc.getId(); // 🔥 API KEY = document ID
                if (apiKey != null && !apiKey.isBlank()) {
                    keyList.add(apiKey);
                }
            }

            currentIndex = 0;
            loaded = true;

            Log.d(TAG, "Loaded " + keyList.size() + " AI keys");

        } catch (Exception e) {
            Log.e(TAG, "Failed to load AI keys", e);
        }
    }

    /**
     * Lấy key hiện tại
     */
    public static synchronized String getCurrentKey() {
        if (!loaded || keyList.isEmpty()) return null;
        return keyList.get(currentIndex);
    }


    /**
     * Chuyển sang key tiếp theo
     */
    public static synchronized boolean moveToNextKey() {
        if (currentIndex + 1 < keyList.size()) {
            currentIndex++;
            Log.w(TAG, "Switching to next API key (index=" + currentIndex + ")");
            return true;
        }
        return false;
    }

    /**
     * Reset lại key đầu (ví dụ sau 1 khoảng thời gian)
     */
    public static synchronized void reset() {
        currentIndex = 0;
    }

    /**
     * Dùng khi muốn reload key từ Firestore
     */
    public static synchronized void forceReload() {
        loaded = false;
        loadKeysBlocking();
    }
}
