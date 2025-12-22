package cmc.cs.honeypot01.stt;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioUtils {
    private static final String TAG = "AudioUtils";
    private static final int TARGET_SAMPLE_RATE = 16000;

    public static float[] decodeAndResample(File inputFile) {
    Log.d(TAG, "🎧 Bắt đầu Decode file: " + inputFile.getName()); // <--- THÊM
    try {
        float[] result = decodeWithMediaCodec(inputFile);
        Log.d(TAG, "✅ Decode thành công. Số mẫu (samples): " + result.length); // <--- THÊM
        return result;
    } catch (Exception e) {
        Log.e(TAG, "❌ Lỗi MediaCodec, thử fallback...", e); // <--- THÊM
        return readRawPcm(inputFile);
    }
}

    private static float[] decodeWithMediaCodec(File inputFile) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(inputFile.getAbsolutePath());

        int trackIndex = -1;
        String mime = null;
        MediaFormat format = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                trackIndex = i;
                break;
            }
        }

        if (trackIndex == -1) {
            extractor.release();
            throw new IOException("No audio track found in " + inputFile);
        }

        extractor.selectTrack(trackIndex);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        int inputSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        
        Log.d(TAG, "Decoding " + mime + ", " + inputSampleRate + "Hz, " + channelCount + " channels");

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        
        // Timeout for dequeueing buffers (microseconds)
        final long kTimeOutUs = 5000;

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(kTimeOutUs);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }

            int outputBufferId = codec.dequeueOutputBuffer(bufferInfo, kTimeOutUs);
            if (outputBufferId >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                
                if (bufferInfo.size > 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                    byte[] chunk = new byte[bufferInfo.size];
                    outputBuffer.get(chunk);
                    outputBuffer.clear();
                    outputStream.write(chunk);
                }
                
                codec.releaseOutputBuffer(outputBufferId, false);
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = codec.getOutputFormat();
                inputSampleRate = newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : inputSampleRate;
                channelCount = newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channelCount;
                Log.d(TAG, "Output format changed: " + inputSampleRate + "Hz, " + channelCount + " channels");
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        byte[] rawBytes = outputStream.toByteArray();
        // Convert 16-bit PCM bytes to float array
        float[] samples = bytesToFloats(rawBytes);
        
        // Mix down to mono if needed
        if (channelCount > 1) {
            samples = mixToMono(samples, channelCount);
        }
        
        // Resample if needed
        if (inputSampleRate != TARGET_SAMPLE_RATE) {
            samples = resample(samples, inputSampleRate, TARGET_SAMPLE_RATE);
        }
        
        return samples;
    }

    private static float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 2];
        for (int i = 0; i < floats.length; i++) {
            int lsb = bytes[2 * i] & 0xFF;
            int msb = bytes[2 * i + 1]; // Sign extended
            short val = (short) ((msb << 8) | lsb);
            floats[i] = val / 32768.0f;
        }
        return floats;
    }

    private static float[] mixToMono(float[] input, int channels) {
        float[] mono = new float[input.length / channels];
        for (int i = 0; i < mono.length; i++) {
            float sum = 0;
            for (int j = 0; j < channels; j++) {
                sum += input[i * channels + j];
            }
            mono[i] = sum / channels;
        }
        return mono;
    }

    private static float[] resample(float[] input, int inputRate, int targetRate) {
        if (inputRate == targetRate) return input;
        
        long outputLength = (long) input.length * targetRate / inputRate;
        float[] output = new float[(int) outputLength];
        
        for (int i = 0; i < output.length; i++) {
            float inputIndex = (float) i * inputRate / targetRate;
            int index1 = (int) inputIndex;
            int index2 = index1 + 1;
            float frac = inputIndex - index1;
            
            float val1 = (index1 < input.length) ? input[index1] : 0;
            float val2 = (index2 < input.length) ? input[index2] : 0;
            
            output[i] = val1 * (1 - frac) + val2 * frac;
        }
        
        Log.d(TAG, "Resampled from " + inputRate + " to " + targetRate + " (" + input.length + " -> " + output.length + " samples)");
        return output;
    }

    private static float[] readRawPcm(File pcmFile) {
        List<Float> floatList = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(pcmFile)))) {
            while (dis.available() > 0) {
                // Read 16-bit short, convert to float (-1.0 to 1.0)
                short sample = Short.reverseBytes(dis.readShort()); // FFmpeg writes Little Endian, Java reads Big Endian
                floatList.add(sample / 32768f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PCM file", e);
            return new float[0];
        }

        // Convert List to primitive array
        float[] result = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            result[i] = floatList.get(i);
        }
        return result;
    }
}