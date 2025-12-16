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

    // Model paths in assets
    private static final String MODEL_DIR = "sherpa-model-vi";
    private static final String TOKENS = "tokens.txt";
    private static final String ENCODER = "encoder-epoch-12-avg-8.onnx";
    private static final String DECODER = "decoder-epoch-12-avg-8.onnx";
    private static final String JOINER = "joiner-epoch-12-avg-8.onnx";
    private static final String BPE_MODEL = "bpe.model";

    /**
     * Transcribe an audio file using Sherpa-ONNX Vietnamese model
     */
    public static String transcribe(Context context, File audioFile) {
        synchronized (LOCK) {
            if (recognizer == null) {
                initRecognizer(context);
            }
        }

        if (recognizer == null) {
            Log.e(TAG, "Failed to initialize recognizer");
            return "";
        }

        try {
            // For production use, you would decode audio file and process samples
            // For now, returning placeholder
            // Decode audio file to float samples
            float[] samples = com.example.honeypot01.stt.AudioUtils.decodeAndResample(audioFile);
            if (samples == null || samples.length == 0) {
                Log.e(TAG, "Failed to decode audio file or no samples found");
                return "";
            }

            Log.i(TAG, "Processing " + samples.length + " audio samples");

            // Create OfflineStream and decode
            OfflineStream stream = recognizer.createStream();
            stream.acceptWaveform(samples, 16000); // 16kHz sample rate

            // Decode the stream
            recognizer.decode(stream);

            // Get recognition result
            String result = "";
            try {
                 OfflineRecognizerResult resultObj = recognizer.getResult(stream);
                 result = resultObj.getText();
            } catch (Exception e) {
                Log.e(TAG, "Error getting result", e);
            }

            Log.i(TAG, "Transcription result: " + result);

            // Clean up
            stream.release();

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Transcription error", e);
            return "";
        }
    }

    /**
     * Initialize the Sherpa-ONNX recognizer with Vietnamese model
     */
    private static void initRecognizer(Context context) {
        try {
            String modelPath = copyModelFiles(context);
            if (modelPath == null) {
                Log.e(TAG, "Failed to copy model files");
                return;
            }

            // Build recognizer config
            OfflineRecognizerConfig config = buildConfig(modelPath);
            // Pass null as AssetManager because we are loading models from the filesystem (SD card/app files)
            recognizer = new OfflineRecognizer(null, config);
            
            Log.i(TAG, "Sherpa-ONNX Vietnamese recognizer initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize recognizer", e);
            recognizer = null;
        }
    }

    /**
     * Build the recognizer configuration for Vietnamese Conformer model
     */
    private static OfflineRecognizerConfig buildConfig(String modelPath) {
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        
        // Configure the model
        OfflineModelConfig modelConfig = config.getModelConfig();
        
        // Use setTokens for tokens
        modelConfig.setTokens(modelPath + "/" + TOKENS);
        
        // For Transducer models (Conformer/Zipformer), we need to set encoder/decoder/joiner
        // The API might use setTransducer() taking a config object, or individual setters
        // Based on errors, setEncoder/setDecoder/setJoiner are missing on OfflineModelConfig
        // They might be on a sub-object or named differently (e.g. setEncEncoder)
        
        // Let's try to use the Transducer config object if available
        OfflineTransducerModelConfig transducerConfig = modelConfig.getTransducer();
        transducerConfig.setEncoder(modelPath + "/" + ENCODER);
        transducerConfig.setDecoder(modelPath + "/" + DECODER);
        transducerConfig.setJoiner(modelPath + "/" + JOINER);
        
        // Set model type
        modelConfig.setModelType("conformer");
        
        // Configure decoding parameters
        config.setDecodingMethod("greedy_search");
        config.setMaxActivePaths(4);

        // Configure feature extraction - FeatureConfig might be named differently or accessed differently
        // The error said: symbol: class OfflineFeatureConfig
        // This means OfflineFeatureConfig class is not imported or doesn't exist.
        // It's likely FeatureConfig in this version.
        FeatureConfig featConfig = config.getFeatConfig();
        featConfig.setSampleRate(16000);
        featConfig.setFeatureDim(80);
        
        return config;
    }

    /**
     * Copy model files from assets to app's files directory
     */
    private static String copyModelFiles(Context context) throws IOException {
        File modelDir = new File(context.getFilesDir(), "sherpa-models");
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        String[] requiredFiles = {TOKENS, ENCODER, DECODER, JOINER, BPE_MODEL};
        AssetManager assetManager = context.getAssets();

        for (String fileName : requiredFiles) {
            File targetFile = new File(modelDir, fileName);
            
            // Skip if already exists
            if (targetFile.exists()) {
                Log.d(TAG, "File already exists: " + fileName);
                continue;
            }

            try (InputStream inputStream = assetManager.open(MODEL_DIR + "/" + fileName);
                 FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                Log.d(TAG, "Copied model file: " + fileName);
            }
        }

        return modelDir.getAbsolutePath();
    }

    /**
     * Clean up resources
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (recognizer != null) {
                recognizer.release();
                recognizer = null;
                Log.d(TAG, "Recognizer released");
            }
        }
    }
}