package cmc.cs.honeypot01.stt;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import okhttp3.*;

public class AssemblyAI {

    private static final String TAG = "AssemblyAI";
    private static final String API_KEY = "410b990126534c488c263ac1ca9ae4b2";
    private static final String BASE_URL = "https://api.assemblyai.com/v2";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * Transcribe audio file to text
     */
    public static String transcribe(File audioFile) {
        try {
            // Upload file
            String audioUrl = uploadFile(audioFile);

            // Create transcript
            JSONObject request = new JSONObject();
            request.put("audio_url", audioUrl);
            request.put("language_code", "vi");

            JSONObject response = postJson(BASE_URL + "/transcript", request);
            String transcriptId = response.getString("id");
            String result = pollResult(transcriptId);
            Log.d(TAG, result);

            // Poll for result
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Transcription failed", e);
            return null;
        }
    }

    private static String uploadFile(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }

        RequestBody body = RequestBody.create(data, MediaType.parse("application/octet-stream"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/upload")
                .addHeader("authorization", API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new JSONObject(response.body().string()).getString("upload_url");
        }
    }

    private static JSONObject postJson(String url, JSONObject json) throws Exception {
        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("authorization", API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return new JSONObject(response.body().string());
        }
    }

    private static String pollResult(String transcriptId) throws Exception {
        String url = BASE_URL + "/transcript/" + transcriptId;

        while (true) {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("authorization", API_KEY)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JSONObject json = new JSONObject(response.body().string());
                String status = json.getString("status");

                if ("completed".equals(status)) {
                    return json.getString("text");
                } else if ("error".equals(status)) {
                    return null;
                }
            }

            Thread.sleep(3000);
        }
    }
}