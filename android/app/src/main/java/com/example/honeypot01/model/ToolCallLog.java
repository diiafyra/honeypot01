package com.example.honeypot01.model;

import com.google.firebase.firestore.PropertyName;

public class ToolCallLog {

    @PropertyName("pot_number")
    private String potNumber;

    @PropertyName("spam_number")
    private String spamNumber;

    @PropertyName("call_time")
    private Long callTime;

    @PropertyName("duration")
    private int duration;
    @PropertyName("transcript")
    private String  transcript;

    public ToolCallLog() {}

    public ToolCallLog(String potNumber,
                       String spamNumber,
                       Long callTime,
                       int duration,
                       String transcript) {
        this.potNumber = potNumber;
        this.spamNumber = spamNumber;
        this.callTime = callTime;
        this.duration = duration;
        this.transcript = transcript;
    }

    @PropertyName("pot_number")
    public String getPotNumber() { return potNumber; }

    @PropertyName("spam_number")
    public String getSpamNumber() { return spamNumber; }

    @PropertyName("call_time")
    public Long getCallTime() { return callTime; }

    @PropertyName("duration")
    public int getDuration() { return duration; }

    @PropertyName("transcript")
    public String getTranscript() { return transcript; }
}
