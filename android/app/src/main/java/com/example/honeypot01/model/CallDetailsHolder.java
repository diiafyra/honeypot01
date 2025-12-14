package com.example.honeypot01.model;

public class CallDetailsHolder {
    private String phoneNumber;
    private String verificationStatus;
    private String handlePresentation;
    private String callerDisplayName;
    public CallDetailsHolder() {
        this.verificationStatus = "NOT_VERIFIED";
        this.handlePresentation = "UNKNOWN"; // ← Đổi tên
        this.callerDisplayName = "";
    }

    public CallDetailsHolder(String phoneNumber, String verificationStatus, String handlePresentation, String callerDisplayName) {
        this.phoneNumber = phoneNumber;
        this.verificationStatus = verificationStatus;
        this.handlePresentation = handlePresentation; // ← Đổi tên
        this.callerDisplayName = callerDisplayName;
    }

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getHandlePresentation() { return handlePresentation; } // ← Đổi tên
    public void setHandlePresentation(String handlePresentation) { // ← Đổi tên
        this.handlePresentation = handlePresentation;
    }

    public String getCallerDisplayName() { return callerDisplayName; }
    public void setCallerDisplayName(String callerDisplayName) {
        this.callerDisplayName = callerDisplayName;
    }
}