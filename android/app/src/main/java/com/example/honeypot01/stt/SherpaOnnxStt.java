package com.example.honeypot01.stt;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;

public class SherpaOnnxStt {
    private static final String TAG = "SherpaOnnxStt";
    private static OfflineRecognizer recognizer;
    private static final Object LOCK = new Object();

    // === PATHS FOR INT8 MODEL ===
    private static final String MODEL_DIR = "sherpa-model-vi";
    private static final String TOKENS = "tokens.txt";
    private static final String ENCODER = "encoder-epoch-12-avg-8.int8.onnx";
    private static final String DECODER = "decoder-epoch-12-avg-8.onnx";
    private static final String JOINER = "joiner-epoch-12-avg-8.int8.onnx";

    public static String transcribe(Context context, File audioFile) {
        Log.d(TAG, "[T1] transcribe() called for: " + audioFile.getName());
        
        synchronized (LOCK) {
            if (recognizer == null) {
                Log.d(TAG, "[T2] Recognizer is null, initializing...");
                initRecognizer(context);
            }
        }

        if (recognizer == null) {
            Log.e(TAG, "[T3] FAILED: Recognizer still null after init!");
            return "Error: Engine not ready";
        }
        Log.d(TAG, "[T3] Recognizer ready");

        try {
            Log.d(TAG, "[T4] Decoding audio...");
            long decodeStart = System.currentTimeMillis();
            float[] samples = AudioUtils.decodeAndResample(audioFile);
            Log.d(TAG, "[T4] Decode took " + (System.currentTimeMillis() - decodeStart) + "ms");
            
            if (samples == null) {
                Log.e(TAG, "[T5] FAILED: samples is NULL");
                return "Error: Decode failed (null)";
            }
            if (samples.length == 0) {
                Log.e(TAG, "[T5] FAILED: samples is EMPTY");
                return "Error: Decode failed (empty)";
            }
            Log.d(TAG, "[T5] Got " + samples.length + " samples (" + (samples.length / 16000.0) + " seconds)");

            Log.d(TAG, "[T6] Creating stream...");
            OfflineStream stream = recognizer.createStream();
            
            Log.d(TAG, "[T7] Accepting waveform...");
            stream.acceptWaveform(samples, 16000);
            
            Log.d(TAG, "[T8] Starting decode (THIS MAY HANG)...");
            long inferStart = System.currentTimeMillis();
            recognizer.decode(stream);
            Log.d(TAG, "[T8] Decode completed in " + (System.currentTimeMillis() - inferStart) + "ms");
            
            Log.d(TAG, "[T9] Getting result...");
            String result = recognizer.getResult(stream).getText();
            Log.d(TAG, "[T9] Result text: '" + result + "'");
            
            Log.d(TAG, "[T10] Releasing stream...");
            stream.release();
            
            Log.d(TAG, "[T11] SUCCESS!");
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "[CRASH] Transcribe failed", t);
            return "Error: " + t.getMessage();
        }
    }

    private static void initRecognizer(Context context) {
        Log.d(TAG, "[INIT-1] Starting initRecognizer...");
        try {
            Log.d(TAG, "[INIT-2] Copying model files...");
            long copyStart = System.currentTimeMillis();
            String path = copyModelFiles(context);
            Log.d(TAG, "[INIT-2] Copy took " + (System.currentTimeMillis() - copyStart) + "ms");
            
            if (path == null) {
                Log.e(TAG, "[INIT-3] FAILED: Model path is null!");
                return;
            }
            Log.d(TAG, "[INIT-3] Model path: " + path);

            // Verify files exist
            File tokensFile = new File(path, TOKENS);
            File encoderFile = new File(path, ENCODER);
            File decoderFile = new File(path, DECODER);
            File joinerFile = new File(path, JOINER);
            
            Log.d(TAG, "[INIT-4] File check:");
            Log.d(TAG, "  tokens: " + tokensFile.exists() + " (" + tokensFile.length() + " bytes)");
            Log.d(TAG, "  encoder: " + encoderFile.exists() + " (" + encoderFile.length() + " bytes)");
            Log.d(TAG, "  decoder: " + decoderFile.exists() + " (" + decoderFile.length() + " bytes)");
            Log.d(TAG, "  joiner: " + joinerFile.exists() + " (" + joinerFile.length() + " bytes)");

            Log.d(TAG, "[INIT-5] Creating config...");
            OfflineRecognizerConfig config = new OfflineRecognizerConfig();
            OfflineModelConfig modelConfig = config.getModelConfig();

            modelConfig.setTokens(path + "/" + TOKENS);
            
            OfflineTransducerModelConfig transducer = modelConfig.getTransducer();
            transducer.setEncoder(path + "/" + ENCODER);
            transducer.setDecoder(path + "/" + DECODER);
            transducer.setJoiner(path + "/" + JOINER);

            modelConfig.setModelType("transducer");
            modelConfig.setNumThreads(1);
            config.setDecodingMethod("greedy_search");
            
            Log.d(TAG, "[INIT-6] Creating OfflineRecognizer (THIS MAY TAKE TIME)...");
            long initStart = System.currentTimeMillis();
            
            recognizer = new OfflineRecognizer(null, config);
            
            Log.d(TAG, "[INIT-6] Recognizer created in " + (System.currentTimeMillis() - initStart) + "ms");
            Log.d(TAG, "[INIT-7] Sherpa Int8 Initialized! ✅");

        } catch (Throwable t) {
            Log.e(TAG, "[INIT-CRASH] Init failed", t);
            recognizer = null;
        }
    }

    private static String copyModelFiles(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "sherpa_int8_v2");
        if (!dir.exists()) dir.mkdirs();

        String[] files = {TOKENS, ENCODER, DECODER, JOINER};
        AssetManager assets = context.getAssets();

        for (String f : files) {
            File dest = new File(dir, f);
            if (dest.exists()) dest.delete(); 
            
            try (InputStream is = assets.open(MODEL_DIR + "/" + f);
                 FileOutputStream os = new FileOutputStream(dest)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            }
        }
        return dir.getAbsolutePath();
    }
}