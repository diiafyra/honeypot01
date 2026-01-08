package cmc.cs.honeypot01.model;

import com.google.firebase.firestore.PropertyName;

public class ToolCallLog {

    @PropertyName("pot_number")
    private String potNumber;  // DEPRECATED: giữ để tương thích cũ

    @PropertyName("receiver_number")
    private String receiverNumber;  // Số điện thoại nhận cuộc gọi (nếu có)

    @PropertyName("subscription_id")
    private Integer subscriptionId;  // ID subscription/SIM slot

    @PropertyName("spam_number")
    private String spamNumber;

    @PropertyName("call_time")
    private Long callTime;

    @PropertyName("duration")
    private int duration;

    @PropertyName("transcript")
    private String transcript;

    @PropertyName("file_path")
    private String filePath;

    public ToolCallLog() {}

    // Constructor cũ (deprecated)
    @Deprecated
    public ToolCallLog(String potNumber,
                       String spamNumber,
                       Long callTime,
                       int duration,
                       String transcript,
                       String filePath) {
        this.potNumber = potNumber;
        this.receiverNumber = potNumber;  // Fallback
        this.spamNumber = spamNumber;
        this.callTime = callTime;
        this.duration = duration;
        this.transcript = transcript;
        this.filePath = filePath;
    }

    // Constructor mới
    public ToolCallLog(String receiverNumber,
                       Integer subscriptionId,
                       String spamNumber,
                       Long callTime,
                       int duration,
                       String transcript,
                       String filePath) {
        this.receiverNumber = receiverNumber;
        this.subscriptionId = subscriptionId;
        this.potNumber = receiverNumber != null ? receiverNumber :
                (subscriptionId != null ? "SIM_" + subscriptionId : "UNKNOWN");
        this.spamNumber = spamNumber;
        this.callTime = callTime;
        this.duration = duration;
        this.transcript = transcript;
        this.filePath = filePath;
    }

    // Getters
    @PropertyName("pot_number")
    public String getPotNumber() { return potNumber; }

    @PropertyName("receiver_number")
    public String getReceiverNumber() { return receiverNumber; }

    @PropertyName("subscription_id")
    public Integer getSubscriptionId() { return subscriptionId; }

    @PropertyName("spam_number")
    public String getSpamNumber() { return spamNumber; }

    @PropertyName("call_time")
    public Long getCallTime() { return callTime; }

    @PropertyName("duration")
    public int getDuration() { return duration; }

    @PropertyName("transcript")
    public String getTranscript() { return transcript; }

    @PropertyName("file_path")
    public String getFilePath() { return filePath; }

    // Setters
    public void setReceiverNumber(String receiverNumber) {
        this.receiverNumber = receiverNumber;
    }

    public void setSubscriptionId(Integer subscriptionId) {
        this.subscriptionId = subscriptionId;
    }
}