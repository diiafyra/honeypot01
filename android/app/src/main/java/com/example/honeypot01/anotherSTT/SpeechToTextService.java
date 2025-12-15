package com.example.honeypot01.anotherSTT;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.honeypot01.anotherSTT.receiver.SttResultReceiver;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SpeechToTextService extends IntentService {
    private static final String TAG = "SpeechToTextService";

    public static final String EXTRA_WAV_PATH = "wav_path";      // local path (wav/mp3/m4a/...)
    public static final String EXTRA_AUDIO_URI = "audio_uri";    // content:// Uri (MediaStore)
    public static final String EXTRA_LANGUAGE = "language";      // optional (unused for now)
    public static final String EXTRA_REQUEST_ID = "request_id";

    public static final String ACTION_STT_RESULT = "com.example.honeypot01.STT_RESULT";
    public static final String EXTRA_TRANSCRIPT = "transcript";
    public static final String EXTRA_ERROR = "error";

    // VN model in assets:
    private static final String MODEL_ASSET_DIR = "vosk-model-small-vn-0.4";
    // Copied to internal storage:
    private static final String MODEL_LOCAL_DIR = "vosk-model-small-vn-0.4";

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
            File modelDir = ensureModelOnDisk();
            model = new Model(modelDir.getAbsolutePath());

            String transcript;
            if (inputUri != null) {
                transcript = transcribeWithVosk(model, inputUri);
            } else {
                transcript = transcribeWithVosk(model, audioFile);
            }
            broadcastSuccess(requestId, transcript);

        } catch (Exception e) {
            Log.e(TAG, "STT failed", e);
            broadcastError(requestId, e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            try {
                if (model != null) model.close();
            } catch (Exception ignored) {}
        }
    }

    private String transcribeWithVosk(Model model, File audioFile) throws Exception {
        // 1) Try WAV fast-path (PCM16 mono)
        try {
            WavInfo wavInfo = tryParseWav(audioFile);
            if (wavInfo != null) {
                return transcribePcmStream(model, audioFile, wavInfo.sampleRateHz, wavInfo.dataOffset, wavInfo.dataSize);
            }
        } catch (Exception wavUnsupported) {
            // If it's a WAV but not PCM16 mono, fall back to decoder path.
            Log.w(TAG, "WAV unsupported, falling back to MediaCodec: " + wavUnsupported.getMessage());
        }

        // 2) Decode m4a/mp3/aac/… to PCM via MediaCodec and feed Vosk
        return transcribeViaMediaCodec(model, audioFile);
    }

    private String transcribeWithVosk(Model model, Uri audioUri) throws Exception {
        // Với content Uri, ưu tiên decode qua MediaCodec để tránh phụ thuộc đường dẫn file.
        return transcribeViaMediaCodec(model, audioUri);
    }

    private String transcribePcmStream(Model model, File audioFile, float sampleRate, long dataOffset, long dataSize) throws Exception {
        Recognizer recognizer = null;
        try {
            recognizer = new Recognizer(model, sampleRate);

            try (InputStream raw = new BufferedInputStream(new FileInputStream(audioFile))) {
                skipFully(raw, dataOffset);

                byte[] buffer = new byte[4096];
                long remaining = dataSize;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int n = raw.read(buffer, 0, toRead);
                    if (n < 0) break;
                    recognizer.acceptWaveForm(buffer, n);
                    remaining -= n;
                }
            }

            String finalJson = recognizer.getFinalResult();
            return new JSONObject(finalJson).optString("text", "").trim();
        } finally {
            try {
                if (recognizer != null) recognizer.close();
            } catch (Exception ignored) {}
        }
    }

    private String transcribeViaMediaCodec(Model model, File audioFile) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(audioFile.getAbsolutePath());

        int audioTrackIndex = -1;
        MediaFormat trackFormat = null;
        String mime = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String m = format.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                audioTrackIndex = i;
                trackFormat = format;
                mime = m;
                break;
            }
        }

        if (audioTrackIndex < 0 || trackFormat == null || mime == null) {
            extractor.release();
            throw new IllegalArgumentException("No audio track found / unsupported audio container");
        }

        extractor.selectTrack(audioTrackIndex);

        int sampleRate = trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : (int) DEFAULT_SAMPLE_RATE_HZ;

        int channelCount = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 1;

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(trackFormat, null, null, 0);
        codec.start();

        Recognizer recognizer = null;
        try {
            recognizer = new Recognizer(model, (float) sampleRate);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT; // default

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        if (inputBuffer == null) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = codec.getOutputFormat();

                    // Some devices expose PCM encoding here (API 24+ key).
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }

                    // Channel count / sample rate can also appear here; we log but keep recognizer sample rate as created.
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        int outSr = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        if (outSr != sampleRate) Log.w(TAG, "Decoder output sampleRate changed " + sampleRate + " -> " + outSr);
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        int outCh = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (outCh != channelCount) Log.w(TAG, "Decoder output channels changed " + channelCount + " -> " + outCh);
                    }

                } else if (outIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);

                        byte[] pcmChunk = new byte[info.size];
                        outputBuffer.get(pcmChunk);

                        byte[] mono16 = convertToMono16(pcmChunk, channelCount, pcmEncoding);
                        if (mono16.length > 0) {
                            recognizer.acceptWaveForm(mono16, mono16.length);
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            String finalJson = recognizer.getFinalResult();
            return new JSONObject(finalJson).optString("text", "").trim();

        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {}
            try {
                codec.stop();
            } catch (Exception ignored) {}
            try {
                codec.release();
            } catch (Exception ignored) {}
            try {
                if (recognizer != null) recognizer.close();
            } catch (Exception ignored) {}
        }
    }

    private String transcribeViaMediaCodec(Model model, Uri audioUri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, audioUri, null);

        int audioTrackIndex = -1;
        MediaFormat trackFormat = null;
        String mime = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String m = format.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                audioTrackIndex = i;
                trackFormat = format;
                mime = m;
                break;
            }
        }

        if (audioTrackIndex < 0 || trackFormat == null || mime == null) {
            extractor.release();
            throw new IllegalArgumentException("No audio track found / unsupported audio container");
        }

        extractor.selectTrack(audioTrackIndex);

        int sampleRate = trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : (int) DEFAULT_SAMPLE_RATE_HZ;

        int channelCount = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 1;

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(trackFormat, null, null, 0);
        codec.start();

        Recognizer recognizer = null;
        try {
            recognizer = new Recognizer(model, (float) sampleRate);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT; // default

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        if (inputBuffer == null) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = codec.getOutputFormat();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }

                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        int outSr = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        if (outSr != sampleRate) Log.w(TAG, "Decoder output sampleRate changed " + sampleRate + " -> " + outSr);
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        int outCh = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (outCh != channelCount) Log.w(TAG, "Decoder output channels changed " + channelCount + " -> " + outCh);
                    }

                } else if (outIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);

                        byte[] pcmChunk = new byte[info.size];
                        outputBuffer.get(pcmChunk);

                        byte[] mono16 = convertToMono16(pcmChunk, channelCount, pcmEncoding);
                        if (mono16.length > 0) {
                            recognizer.acceptWaveForm(mono16, mono16.length);
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            String finalJson = recognizer.getFinalResult();
            return new JSONObject(finalJson).optString("text", "").trim();

        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {}
            try {
                codec.stop();
            } catch (Exception ignored) {}
            try {
                codec.release();
            } catch (Exception ignored) {}
            try {
                if (recognizer != null) recognizer.close();
            } catch (Exception ignored) {}
        }
    }

    // Converts decoded PCM to mono 16-bit little-endian.
    // Supports PCM_16BIT and PCM_FLOAT (if provided). Others will error.
    private static byte[] convertToMono16(byte[] pcm, int channelCount, int pcmEncoding) {
        if (channelCount <= 1 && pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            return pcm;
        }

        if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            // Interleaved little-endian 16-bit samples.
            if (channelCount <= 0) channelCount = 1;
            int frameSizeBytes = 2 * channelCount;
            int frames = pcm.length / frameSizeBytes;
            byte[] out = new byte[frames * 2];

            int outIdx = 0;
            for (int f = 0; f < frames; f++) {
                int sum = 0;
                int base = f * frameSizeBytes;
                for (int ch = 0; ch < channelCount; ch++) {
                    int i = base + ch * 2;
                    int lo = pcm[i] & 0xFF;
                    int hi = pcm[i + 1]; // signed
                    short s = (short) ((hi << 8) | lo);
                    sum += s;
                }
                short mono = (short) (sum / channelCount);
                out[outIdx++] = (byte) (mono & 0xFF);
                out[outIdx++] = (byte) ((mono >> 8) & 0xFF);
            }
            return out;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            // Interleaved little-endian float samples [-1..1].
            if (channelCount <= 0) channelCount = 1;
            ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            int floatCount = pcm.length / 4;
            int frames = floatCount / channelCount;

            byte[] out = new byte[frames * 2];
            int outIdx = 0;

            for (int f = 0; f < frames; f++) {
                float sum = 0f;
                for (int ch = 0; ch < channelCount; ch++) {
                    sum += bb.getFloat();
                }
                float monoF = sum / channelCount;
                monoF = Math.max(-1f, Math.min(1f, monoF));
                short mono = (short) (monoF * 32767f);
                out[outIdx++] = (byte) (mono & 0xFF);
                out[outIdx++] = (byte) ((mono >> 8) & 0xFF);
            }
            return out;
        }

        throw new IllegalArgumentException("Unsupported decoder PCM encoding: " + pcmEncoding);
    }

    private File ensureModelOnDisk() throws Exception {
        File dstDir = new File(getFilesDir(), MODEL_LOCAL_DIR);

        // "Already extracted" check
        File confDir = new File(dstDir, "conf");
        File graphDir = new File(dstDir, "graph");
        if (confDir.isDirectory() && graphDir.isDirectory()) {
            return dstDir;
        }

        // Fresh extract
        if (dstDir.exists()) deleteRecursive(dstDir);
        if (!dstDir.mkdirs()) throw new IllegalStateException("Failed to create model dir: " + dstDir.getAbsolutePath());

        copyAssetFolder(getAssets(), MODEL_ASSET_DIR, dstDir);
        return dstDir;
    }

    private static void copyAssetFolder(AssetManager assetManager, String assetPath, File dstDir) throws Exception {
        String[] children = assetManager.list(assetPath);
        if (children == null) return;

        if (children.length == 0) {
            copyAssetFile(assetManager, assetPath, dstDir);
            return;
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childDst = new File(dstDir, child);

            String[] grandChildren = assetManager.list(childAssetPath);
            if (grandChildren != null && grandChildren.length > 0) {
                if (!childDst.exists() && !childDst.mkdirs()) {
                    throw new IllegalStateException("Failed to mkdir: " + childDst.getAbsolutePath());
                }
                copyAssetFolder(assetManager, childAssetPath, childDst);
            } else {
                copyAssetFile(assetManager, childAssetPath, childDst);
            }
        }
    }

    private static void copyAssetFile(AssetManager assetManager, String assetFilePath, File dstFile) throws Exception {
        File parent = dstFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to mkdir: " + parent.getAbsolutePath());
        }

        try (InputStream in = assetManager.open(assetFilePath);
             FileOutputStream out = new FileOutputStream(dstFile)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void skipFully(InputStream is, long bytes) throws Exception {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) {
                if (is.read() < 0) break;
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static final class WavInfo {
        final float sampleRateHz;
        final long dataOffset;
        final long dataSize;

        WavInfo(float sampleRateHz, long dataOffset, long dataSize) {
            this.sampleRateHz = sampleRateHz;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
        }
    }

    // Supports PCM WAV (format=1), mono, 16-bit. Returns null if not a WAV.
    private static WavInfo tryParseWav(File file) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 44) return null;

            String riff = readAscii(raf, 4);
            /* int riffSize = */ readLe32(raf);
            String wave = readAscii(raf, 4);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) return null;

            Integer sampleRate = null;
            Integer channels = null;
            Integer bitsPerSample = null;
            Integer audioFormat = null;

            long dataOffset = -1;
            long dataSize = -1;

            while (raf.getFilePointer() + 8 <= raf.length()) {
                String chunkId = readAscii(raf, 4);
                int chunkSize = readLe32(raf);

                long chunkDataStart = raf.getFilePointer();

                if ("fmt ".equals(chunkId)) {
                    audioFormat = readLe16(raf);
                    channels = readLe16(raf);
                    sampleRate = readLe32(raf);
                    /* int byteRate = */ readLe32(raf);
                    /* int blockAlign = */ readLe16(raf);
                    bitsPerSample = readLe16(raf);

                    long remaining = chunkSize - 16L;
                    if (remaining > 0) raf.seek(raf.getFilePointer() + remaining);

                } else if ("data".equals(chunkId)) {
                    dataOffset = raf.getFilePointer();
                    dataSize = chunkSize;
                    break;
                } else {
                    raf.seek(chunkDataStart + chunkSize);
                }

                if ((chunkSize & 1) == 1 && raf.getFilePointer() < raf.length()) {
                    raf.seek(raf.getFilePointer() + 1);
                }
            }

            if (sampleRate == null || channels == null || bitsPerSample == null || audioFormat == null) return null;
            if (dataOffset < 0 || dataSize < 0) return null;

            if (audioFormat != 1) throw new IllegalArgumentException("Unsupported WAV format (need PCM=1), got: " + audioFormat);
            if (channels != 1) throw new IllegalArgumentException("Unsupported WAV channels (need mono=1), got: " + channels);
            if (bitsPerSample != 16) throw new IllegalArgumentException("Unsupported WAV bit depth (need 16), got: " + bitsPerSample);

            return new WavInfo(sampleRate.floatValue(), dataOffset, dataSize);
        }
    }

    private static String readAscii(RandomAccessFile raf, int len) throws Exception {
        byte[] b = new byte[len];
        raf.readFully(b);
        return new String(b, "US-ASCII");
    }

    private static int readLe16(RandomAccessFile raf) throws Exception {
        int lo = raf.readUnsignedByte();
        int hi = raf.readUnsignedByte();
        return (hi << 8) | lo;
    }

    private static int readLe32(RandomAccessFile raf) throws Exception {
        int b0 = raf.readUnsignedByte();
        int b1 = raf.readUnsignedByte();
        int b2 = raf.readUnsignedByte();
        int b3 = raf.readUnsignedByte();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    private void broadcastSuccess(String requestId, String transcript) {
        Intent result = new Intent(this, SttResultReceiver.class); // context là Service hoặc Activity
        result.putExtra(EXTRA_REQUEST_ID, requestId);
        result.putExtra(EXTRA_TRANSCRIPT, transcript);
        this.sendBroadcast(result);


    }

    private void broadcastError(String requestId, String error) {
        Intent result = new Intent(this, SttResultReceiver.class);
        result.putExtra(EXTRA_REQUEST_ID, requestId);
        result.putExtra(EXTRA_ERROR, error);
        this.sendBroadcast(result);

    }
}