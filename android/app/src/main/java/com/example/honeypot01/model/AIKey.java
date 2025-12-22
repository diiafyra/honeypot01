package com.example.honeypot01.model;

import com.google.firebase.firestore.PropertyName;

public class AIKey {
    @PropertyName("api_key")
    String apiKey;
    @PropertyName("description")
    String description;
    public AIKey() {}
    public AIKey(String apiKey, String description) {
        this.apiKey = apiKey;
        this.description = description;
    }
    @PropertyName("api_key")
    public String getApiKey() {
        return apiKey;
    }
    @PropertyName("description")
    public String getDescription() {
        return description;
    }

}
