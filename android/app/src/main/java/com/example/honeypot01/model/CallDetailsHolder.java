package com.example.honeypot01.model;

public class CallDetailsHolder {
    private String phoneNumber;
    private String verificationStatus;
    private String callType;
    private String callerDisplayName;
    public CallDetailsHolder() {
        this.verificationStatus = "NOT_VERIFIED";
        this.callType = "UNKNOWN";
        this.callerDisplayName = "";
    }

    public CallDetailsHolder(String phoneNumber, String verificationStatus,
                             String callType, String callerDisplayName) {
        this.phoneNumber = phoneNumber;
        this.verificationStatus = verificationStatus;
        this.callType = callType;
        this.callerDisplayName = callerDisplayName;
    }

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public String getCallerDisplayName() { return callerDisplayName; }
    public void setCallerDisplayName(String callerDisplayName) {
        this.callerDisplayName = callerDisplayName;
    }

}