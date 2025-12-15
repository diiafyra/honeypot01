package com.example.honeypot01.stt;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.example.honeypot01.service.SpeechToTextService;

import java.io.File;

public final class SttAfterCallLinker {
    private static final String TAG = "SttAfterCallLinker";

    // Theo yêu cầu: Internal Storage > Recordings > Call
    // Android path thường là: /storage/emulated/0/Recordings/Call
//    private static final String RECORDINGS_CALL_RELATIVE_DIR = "Recordings/Call";
    private static final String RECORDINGS_CALL_RELATIVE_DIR = "Internal storage/Recordings";

    // Chỉ chọn file có lastModified gần thời điểm call kết thúc để giảm rủi ro chọn nhầm.
    // (Tuỳ thực tế thiết bị/ứng dụng ghi âm, có thể cần tăng giảm window.)
    private static final long DEFAULT_PICK_WINDOW_MS = 10L * 60L * 1000L; // 10 phút

    private SttAfterCallLinker() {}

    public static void maybeStart(Context appContext, String requestId, long callEndTimeMs) {
        if (appContext == null) return;
        if (requestId == null || requestId.trim().isEmpty()) {
            Log.w(TAG, "Missing requestId, skip STT");
            return;
        }

        // 1) Ưu tiên tìm bằng MediaStore (hợp với Scoped Storage Android 10+)
        // Nếu không được, fallback sang đọc trực tiếp folder (thiết bị cũ / quyền hạn chế).
        String audioUri = resolveRecordingUriViaMediaStore(appContext, callEndTimeMs, DEFAULT_PICK_WINDOW_MS);
        String audioPath = null;

        if (audioUri == null || audioUri.trim().isEmpty()) {
            audioPath = resolveRecordingPath(callEndTimeMs, DEFAULT_PICK_WINDOW_MS);
        }

        if ((audioUri == null || audioUri.trim().isEmpty()) && (audioPath == null || audioPath.trim().isEmpty())) {
            Log.d(TAG, "No recording found, skip STT. requestId=" + requestId);
            return;
        }

        // 2) Start IntentService để chạy STT (Vosk) và broadcast kết quả
        try {
            Intent sttIntent = new Intent(appContext, SpeechToTextService.class);

            if (audioUri != null && !audioUri.trim().isEmpty()) {
                sttIntent.putExtra(SpeechToTextService.EXTRA_AUDIO_URI, audioUri);
            } else {
                // Chỉ check file nếu dùng path trực tiếp
                File f = new File(audioPath);
                if (!f.exists() || !f.isFile()) {
                    Log.w(TAG, "Recording path not accessible: " + audioPath);
                    return;
                }
                sttIntent.putExtra(SpeechToTextService.EXTRA_WAV_PATH, audioPath);
            }
            sttIntent.putExtra(SpeechToTextService.EXTRA_REQUEST_ID, requestId);

            // NOTE: Android 8+ có giới hạn startService khi app ở background.
            // Ở mức tối thiểu, ta thử startService; nếu bị chặn thì log lỗi để không crash app.
            appContext.startService(sttIntent);
            Log.d(TAG, "STT started. requestId=" + requestId + " audioUri=" + audioUri + " audioPath=" + audioPath);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start SpeechToTextService", t);
        }
    }

    private static String resolveRecordingUriViaMediaStore(Context context, long callEndTimeMs, long windowMs) {
        try {
            // RELATIVE_PATH chỉ có từ Android 10 (API 29)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;

            long from = callEndTimeMs - Math.max(0L, windowMs);
            long to = callEndTimeMs + Math.max(0L, windowMs);

            // MediaStore.DATE_MODIFIED là "seconds since epoch"
            long fromSec = from / 1000L;
            long toSec = to / 1000L;

            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.RELATIVE_PATH
            };

            // RELATIVE_PATH thường có trailing '/'
            String likePath = "%" + RECORDINGS_CALL_RELATIVE_DIR + "/%";

            String selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? AND " +
                    MediaStore.Audio.Media.DATE_MODIFIED + " >= ? AND " +
                    MediaStore.Audio.Media.DATE_MODIFIED + " <= ?";

            String[] selectionArgs = {
                    likePath,
                    String.valueOf(fromSec),
                    String.valueOf(toSec)
            };

            String sortOrder = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";

            try (Cursor cursor = context.getContentResolver().query(collection, projection, selection, selectionArgs, sortOrder)) {
                if (cursor == null) return null;
                int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                if (!cursor.moveToFirst()) return null;

                long id = cursor.getLong(idCol);
                Uri uri = ContentUris.withAppendedId(collection, id);
                return uri.toString();
            }
        } catch (Throwable t) {
            Log.w(TAG, "MediaStore resolve failed", t);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static String resolveRecordingPath(long callEndTimeMs, long windowMs) {
        try {
            File base = Environment.getExternalStorageDirectory();
            Log.d(TAG, "External storage directory: " + base.getAbsolutePath());
            File dir = new File(base, RECORDINGS_CALL_RELATIVE_DIR);
            Log.d(TAG, "Looking for recordings in: " + dir.getAbsolutePath());
            if (!dir.isDirectory()) {
                Log.d(TAG, "Recordings folder not found: " + dir.getAbsolutePath());
                return null;
            }

            File[] files = dir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".m4a") || n.endsWith(".mp3") || n.endsWith(".aac") || n.endsWith(".wav")
                        || n.endsWith(".3gp") || n.endsWith(".mp4");
            });
            if (files == null || files.length == 0) return null;

            long from = callEndTimeMs - Math.max(0L, windowMs);
            long to = callEndTimeMs + Math.max(0L, windowMs);

            File best = null;
            long bestTs = Long.MIN_VALUE;

            // Ưu tiên file nằm trong [from..to] và có lastModified mới nhất.
            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                long ts = f.lastModified();
                if (ts < from || ts > to) continue;
                if (ts > bestTs) {
                    bestTs = ts;
                    best = f;
                }
            }

            if (best != null) return best.getAbsolutePath();

            // Nếu không có file trong window, fallback: chọn file mới nhất trong folder.
            File newest = null;
            long newestTs = Long.MIN_VALUE;
            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                long ts = f.lastModified();
                if (ts > newestTs) {
                    newestTs = ts;
                    newest = f;
                }
            }

            return newest != null ? newest.getAbsolutePath() : null;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to resolve recording path", t);
            return null;
        }
    }
}
