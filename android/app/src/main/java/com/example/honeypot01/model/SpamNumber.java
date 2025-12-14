package com.example.honeypot01.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

public class SpamNumber {

    // ===== Runtime only (Document ID) =====
    @Exclude
    private String phoneNumber;

    // ===== AI / Label =====
    @PropertyName("label")
    private String label;

    @PropertyName("confidence")
    private String confidence;

    // ===== Stats =====
    @PropertyName("call_count")
    private int callCount;

    // ===== Time (epoch millis) =====
    @PropertyName("create_date")
    private Long createDate;

    @PropertyName("last_seen")
    private Long lastSeen;

    @PropertyName("last_update")
    private Long lastUpdate;

    // ===== Source =====
    @PropertyName("source")
    private int source;

    // ===== Call details =====
    @PropertyName("verification_status")
    private String verificationStatus;

    @PropertyName("call_type")
    private String callType;

    @PropertyName("caller_display_name")
    private String callerDisplayName;

    // ===== Required =====
    public SpamNumber() {}

    public SpamNumber(String phoneNumber,
                      String verificationStatus,
                      String callType,
                      String callerDisplayName) {

        long now = System.currentTimeMillis();

        this.phoneNumber = phoneNumber;

        this.label = "unknown";
        this.confidence = "0";

        this.callCount = 1;

        this.createDate = now;
        this.lastSeen = now;
        this.lastUpdate = now;

        this.source = 1;

        this.verificationStatus = verificationStatus;
        this.callType = callType;
        this.callerDisplayName = callerDisplayName;
    }

    // ===== Getters (BẮT BUỘC) =====
    @PropertyName("label")
    public String getLabel() { return label; }

    @PropertyName("confidence")
    public String getConfidence() { return confidence; }

    @PropertyName("call_count")
    public int getCallCount() { return callCount; }

    @PropertyName("create_date")
    public Long getCreateDate() { return createDate; }

    @PropertyName("last_seen")
    public Long getLastSeen() { return lastSeen; }

    @PropertyName("last_update")
    public Long getLastUpdate() { return lastUpdate; }

    @PropertyName("source")
    public int getSource() { return source; }

    @PropertyName("verification_status")
    public String getVerificationStatus() { return verificationStatus; }

    @PropertyName("call_type")
    public String getCallType() { return callType; }

    @PropertyName("caller_display_name")
    public String getCallerDisplayName() { return callerDisplayName; }

    @NonNull
    @Override
    public String toString() {
        return "SpamNumber{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", callCount=" + callCount +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
