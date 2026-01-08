package cmc.cs.honeypot01.model;

public class CallDetailsHolder {
    private String phoneNumber;
    private String verificationStatus;
    private String handlePresentation;
    private String callerDisplayName;
    private String simSlotInfo;
    private int subscriptionId;

    public CallDetailsHolder() {
        this.verificationStatus = "NOT_VERIFIED";
        this.handlePresentation = "UNKNOWN";
        this.callerDisplayName = "";
        this.simSlotInfo = "UNKNOWN";
        this.subscriptionId = -1;
    }

    public CallDetailsHolder(String phoneNumber, String verificationStatus,
                             String handlePresentation, String callerDisplayName) {
        this.phoneNumber = phoneNumber;
        this.verificationStatus = verificationStatus;
        this.handlePresentation = handlePresentation;
        this.callerDisplayName = callerDisplayName;
        this.simSlotInfo = "UNKNOWN";
        this.subscriptionId = -1;
    }

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getHandlePresentation() { return handlePresentation; }
    public void setHandlePresentation(String handlePresentation) {
        this.handlePresentation = handlePresentation;
    }

    public String getCallerDisplayName() { return callerDisplayName; }
    public void setCallerDisplayName(String callerDisplayName) {
        this.callerDisplayName = callerDisplayName;
    }

    // ← GETTERS/SETTERS MỚI
    public String getSimSlotInfo() { return simSlotInfo; }
    public void setSimSlotInfo(String simSlotInfo) { this.simSlotInfo = simSlotInfo; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }
}