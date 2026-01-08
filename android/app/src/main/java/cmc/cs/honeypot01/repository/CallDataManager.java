package cmc.cs.honeypot01.repository;

import android.content.Context;
import android.util.Log;

import cmc.cs.honeypot01.ai.GeminiClassifier;
import cmc.cs.honeypot01.helper.FileHelper;
import cmc.cs.honeypot01.model.*;
import cmc.cs.honeypot01.stt.SherpaOnnxStt;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CallDataManager - Quản lý dữ liệu cuộc gọi
 *
 * SIMPLIFIED: Không cần ReceiverInfoExtractor nữa
 * Tất cả thông tin đến từ InCallService
 */
public class CallDataManager {

    private final SpamNumbersRepository spamRepo;
    private final ToolCallLogsRepository logRepo;
    private final Context context;
    private static final String TAG = "CallDataManager";

    public CallDataManager(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        this.spamRepo = new SpamNumbersRepository(db);
        this.logRepo = new ToolCallLogsRepository(db);
        this.context = context;
    }

    /**
     * Được gọi khi cuộc gọi bắt đầu (ACTIVE state)
     */
    public void onCallStarted(CallDetailsHolder call) {
        Log.d(TAG, "✅ Call started: " + call.getPhoneNumber());
        Log.d(TAG, "📱 SIM: " + call.getSimSlotInfo() + " (SubID: " + call.getSubscriptionId() + ")");

        // Update spam numbers database
        spamRepo.upsert(call);
    }

    /**
     * Được gọi khi cuộc gọi kết thúc
     */
    public void onCallEnded(CallDetailsHolder call, long startTime, long duration) {
        Log.d(TAG, "📵 Call ended: " + call.getPhoneNumber());
        Log.d(TAG, "⏱️ Duration: " + (duration / 1000) + "s");

        // Process trong background thread
        new Thread(() -> processCallRecording(call, startTime, duration)).start();
    }

    /**
     * Xử lý recording: STT → Lưu log → Classify
     */
    private void processCallRecording(CallDetailsHolder call, long startTime, long duration) {
        try {
            // Delay để file được ghi xong
            Thread.sleep(5000);

            // 1. Tìm file audio mới nhất
            File audio = FileHelper.getLatestMp3();
            if (audio == null) {
                Log.w(TAG, "⚠️ No audio file found");
            } else {
                Log.d(TAG, "🎵 Audio file: " + audio.getName());
            }

            // 2. Thực hiện STT
            String transcript = performSTT(audio);

            // 3. Tạo ToolCallLog với SIM info
            ToolCallLog log = new ToolCallLog(
                    call.getSimSlotInfo(),      // Receiver number/SIM identifier
                    call.getSubscriptionId(),   // Subscription ID
                    call.getPhoneNumber(),      // Spam number
                    startTime,                  // Call time
                    (int) (duration / 1000),    // Duration in seconds
                    transcript,                 // Transcript text
                    audio != null ? audio.getAbsolutePath() : null  // File path
            );

            // 4. Lưu vào Firestore
            logRepo.save(log);
            Log.d(TAG, "✅ Call log saved to Firestore");

            // 5. Classification (nếu có transcript)
            if (transcript != null && !transcript.isEmpty()) {
                classifyAndUpdateLabel(call.getPhoneNumber());
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing call recording", e);
        }
    }

    /**
     * Thực hiện STT với timeout
     */
    private String performSTT(File audio) {
        if (audio == null) {
            return null;
        }

        String transcript = null;
        long sttStart = System.currentTimeMillis();

        Log.d(TAG, "🎙️ Starting STT transcription...");

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(() ->
                    SherpaOnnxStt.transcribe(context, audio)
            );

            try {
                transcript = future.get(60, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - sttStart;

                Log.d(TAG, "✅ STT completed in " + duration + "ms");
                Log.d(TAG, "📝 Transcript length: " +
                        (transcript != null ? transcript.length() + " chars" : "NULL"));

            } catch (TimeoutException te) {
                Log.e(TAG, "❌ STT TIMEOUT after 60s!");
                future.cancel(true);
                transcript = "Error: STT timeout (60s)";
            } finally {
                executor.shutdownNow();
            }

        } catch (Throwable t) {
            Log.e(TAG, "❌ STT CRASHED!", t);
            transcript = "Error: STT crashed";
        }

        return transcript;
    }

    /**
     * Classify conversation và update label
     */
    private void classifyAndUpdateLabel(String spamNumber) {
        try {
            Log.d(TAG, "🤖 Starting classification...");

            // Lấy tất cả transcript của spam number này
            String history = logRepo.getConcatTranscript(spamNumber);

            if (history == null || history.trim().isEmpty()) {
                Log.w(TAG, "⚠️ No transcript history for classification");
                return;
            }

            Log.d(TAG, "📚 History length: " + history.length() + " chars");

            // Gọi Gemini classifier
            String label = GeminiClassifier.classifyConversation(history);

            if (label != null && !label.isEmpty()) {
                // Update label trong database
                spamRepo.updateLabel(spamNumber, label);
                Log.d(TAG, "✅ Label updated: " + label);
            } else {
                Log.w(TAG, "⚠️ Classification returned empty label");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Classification failed", e);
        }
    }
}