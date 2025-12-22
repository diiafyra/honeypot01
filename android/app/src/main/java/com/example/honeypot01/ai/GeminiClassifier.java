package com.example.honeypot01.ai;

import android.util.Log;

import com.example.honeypot01.repository.LabelRepository;
import com.example.honeypot01.repository.ToolCallLogsRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClassifier {

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final String TAG = "GeminiClassifier";

    private static final MediaType JSON =
            MediaType.parse("application/json");

    public static String classifyConversation(String conversation) {

        try {
            // 1. Load labels
            List<String> labels = LabelRepository.getAllLabelsBlocking();
            labels.add("unknown");
            Log.d(TAG, labels.toString());

            // 2. Prompt
            String prompt = buildPrompt(conversation, labels);

            // 3. Body (viết tay cho an toàn Android)
            String body =
                    "{ \"contents\": [ { \"parts\": [ { \"text\": "
                            + gson.toJson(prompt)
                            + " } ] } ] }";

            Request request = new Request.Builder()
                    .url(GeminiConfig.ENDPOINT)
                    .addHeader("x-goog-api-key", GeminiConfig.API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();

            // 4. Call Gemini
            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    Log.e(TAG, response.body().string());
                    return "unknown";
                }

                String json = response.body().string();

                JsonObject root = gson.fromJson(json, JsonObject.class);
                String result = root
                        .getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString()
                        .trim();

                // 5. Validate label
                if (!labels.contains(result)) {
                    return "unknown";
                }

                return result;
            }

        } catch (Exception e) {
            Log.e(TAG, "Gemini failed", e);
            return "unknown";
        }
    }

    private static String buildPrompt(String conversation, List<String> labels) {

        StringBuilder sb = new StringBuilder();

        sb.append("Bạn là một hệ thống phân loại.\n\n");
        sb.append("Nhiệm vụ của bạn là phân loại số điện thoại spam dưới đây");
        sb.append("vào MỘT trong các nhãn sau:\n");
        sb.append(String.join(", ", labels)).append("\n\n");

        sb.append("Quy tắc:\n");
        sb.append("- CHỈ trả về đúng TÊN NHÃN\n");
        sb.append("- KHÔNG giải thích\n");
        sb.append("- KHÔNG thêm ký tự thừa\n");
        sb.append("- Nếu không chắc chắn, trả về \"unknown\"\n\n");

        sb.append("Nội dung các cuộc gọi gần đây: \n");
        sb.append("\"").append(conversation).append("\"");

        return sb.toString();
    }
}
