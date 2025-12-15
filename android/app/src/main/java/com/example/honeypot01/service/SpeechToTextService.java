package com.example.honeypot01.service;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.example.honeypot01.receiver.SttResultReceiver;
import com.example.honeypot01.stt.VoskModelProvider;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SpeechToTextService extends IntentService {
    private static final String TAG = "SpeechToTextService";

    public static final String EXTRA_WAV_PATH = "wav_path";      // local path (wav/mp3/m4a/...)
    public static final String EXTRA_AUDIO_URI = "audio_uri";    // content:// Uri (MediaStore)
    public static final String EXTRA_REQUEST_ID = "request_id";

    public static final String ACTION_STT_RESULT = "com.example.honeypot01.STT_RESULT";
    public static final String EXTRA_TRANSCRIPT = "transcript";
    public static final String EXTRA_ERROR = "error";

    private static final float DEFAULT_SAMPLE_RATE_HZ = 16000.0f;

    public SpeechToTextService() {
        super("SpeechToTextService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        String audioUri = intent.getStringExtra(EXTRA_AUDIO_URI);
        String audioPath = intent.getStringExtra(EXTRA_WAV_PATH);
        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null) requestId = String.valueOf(System.currentTimeMillis());

        Uri inputUri = null;
        if (audioUri != null && !audioUri.trim().isEmpty()) {
            try {
                inputUri = Uri.parse(audioUri);
            } catch (Exception e) {
                broadcastError(requestId, "Invalid EXTRA_AUDIO_URI: " + audioUri);
                return;
            }
        }

        File audioFile = null;
        if (inputUri == null) {
            if (audioPath == null || audioPath.isEmpty()) {
                broadcastError(requestId, "Missing EXTRA_WAV_PATH or EXTRA_AUDIO_URI");
                return;
            }

            audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                broadcastError(requestId, "Audio file not found: " + audioPath);
                return;
            }
        }

        Model model = null;
        try {
            File modelDir = VoskModelProvider.ensureModelOnDisk(this);
            model = new Model(modelDir.getAbsolutePath());

            String transcript;
            if (inputUri != null) {
                transcript = transcribeWithVosk(model, inputUri);
            } else {
                transcript = transcribeWithVosk(model, audioFile);
            }
            broadcastSuccess(requestId, transcript);

        } catch (Throwable t) {
            Log.e(TAG, "STT failed", t);
            broadcastError(requestId, t.getMessage() != null ? t.getMessage() : t.toString());
        } finally {
            try {
                if (model != null) model.close();
            } catch (Exception ignored) {}
        }
    }

    private String transcribeWithVosk(Model model, File audioFile) throws Exception {
        File pcm16k = null;
        try {
            pcm16k = convertToPcm16kMono(audioFile);
            return transcribePcmFile(model, pcm16k);
        } finally {
            if (pcm16k != null) {
                //noinspection ResultOfMethodCallIgnored
                pcm16k.delete();
            }
        }
    }

    private String transcribeWithVosk(Model model, Uri audioUri) throws Exception {
        // FFmpegKit cần đường dẫn file => copy content:// về cache trước.
        File cached = null;
        try {
            cached = copyUriToCache(audioUri);
            return transcribeWithVosk(model, cached);
        } finally {
            if (cached != null) {
                //noinspection ResultOfMethodCallIgnored
                cached.delete();
            }
        }
    }

    private File copyUriToCache(Uri uri) throws Exception {
        File outFile = new File(getCacheDir(), "stt_in_" + System.currentTimeMillis() + ".bin");

        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalStateException("openInputStream returned null for uri=" + uri);

            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        }

        return outFile;
    }

    private File convertToPcm16kMono(File input) {
        File outPcm = new File(getCacheDir(), "stt_16k_" + System.currentTimeMillis() + ".pcm");
        String cmd = "-y -i " + ffQuote(input.getAbsolutePath())
                + " -vn -ac 1 -ar 16000 -f s16le " + ffQuote(outPcm.getAbsolutePath());

        var session = FFmpegKit.execute(cmd);
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            String logs = null;
            try {
                logs = session.getAllLogsAsString();
            } catch (Exception ignored) {}
            throw new IllegalStateException("FFmpegKit convert failed rc=" + session.getReturnCode()
                    + (logs != null ? ("\n" + logs) : ""));
        }

        return outPcm;
    }

    private static String ffQuote(String path) {
        return "\"" + path.replace("\"", "\\\"") + "\"";
    }

    private String transcribePcmFile(Model model, File pcmFile) throws Exception {
        try (Recognizer recognizer = new Recognizer(model, DEFAULT_SAMPLE_RATE_HZ);
             InputStream in = new FileInputStream(pcmFile)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                recognizer.acceptWaveForm(buffer, n);
            }
            return new JSONObject(recognizer.getFinalResult()).optString("text", "").trim();
        }
    }



    private void broadcastSuccess(String requestId, String transcript) {
        Intent result = new Intent(this, SttResultReceiver.class); // context là Service hoặc Activity
        result.setAction(ACTION_STT_RESULT);
        result.putExtra(EXTRA_REQUEST_ID, requestId);
        result.putExtra(EXTRA_TRANSCRIPT, transcript);
        this.sendBroadcast(result);
    }

    private void broadcastError(String requestId, String error) {
        Intent result = new Intent(this, SttResultReceiver.class);
        result.setAction(ACTION_STT_RESULT);
        result.putExtra(EXTRA_REQUEST_ID, requestId);
        result.putExtra(EXTRA_ERROR, error);
        this.sendBroadcast(result);
    }
}