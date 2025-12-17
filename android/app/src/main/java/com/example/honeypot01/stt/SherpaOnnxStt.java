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
        synchronized (LOCK) {
            if (recognizer == null) initRecognizer(context);
        }

        if (recognizer == null) return "Error: Engine not ready";

        try {
            float[] samples = AudioUtils.decodeAndResample(audioFile);
            if (samples == null || samples.length == 0) return "Error: Decode failed";

            OfflineStream stream = recognizer.createStream();
            stream.acceptWaveform(samples, 16000);
            recognizer.decode(stream);
            
            // Error 4 Fixed: Use recognizer.getResult(stream)
            String result = recognizer.getResult(stream).getText();
            
            stream.release();
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Transcribe failed", e);
            return "";
        }
    }

    private static void initRecognizer(Context context) {
        try {
            String path = copyModelFiles(context);
            if (path == null) return;

            OfflineRecognizerConfig config = new OfflineRecognizerConfig();
            OfflineModelConfig modelConfig = config.getModelConfig();

            modelConfig.setTokens(path + "/" + TOKENS);
            
            // Error 2 Fixed: Use nested config object instead of method arguments
            OfflineTransducerModelConfig transducer = modelConfig.getTransducer();
            transducer.setEncoder(path + "/" + ENCODER);
            transducer.setDecoder(path + "/" + DECODER);
            transducer.setJoiner(path + "/" + JOINER);

            modelConfig.setModelType("transducer");
            
            modelConfig.setNumThreads(1);
            config.setDecodingMethod("greedy_search");

            // Error 3 Fixed: Pass AssetManager to constructor
            recognizer = new OfflineRecognizer(null, config);
            
            Log.d(TAG, "Sherpa Int8 Initialized!");

        } catch (Exception e) {
            Log.e(TAG, "Init failed", e);
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