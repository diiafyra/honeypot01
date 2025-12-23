package cmc.cs.honeypot01.service;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import cmc.cs.honeypot01.model.CallDetailsHolder;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallScreeningService extends CallScreeningService {
    private static final String TAG = "CallScreeningService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔥 CALL SCREENING SERVICE CREATED!");
        Log.d(TAG, "Service is ready to screen calls");
    }

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.d(TAG, "🚨 CALL SCREENING SERVICE TRIGGERED!");
        Log.d(TAG, "Call details received: " + callDetails.toString());

        // Respond immediately to avoid timeout on Samsung devices
        CallResponse response = buildAllowResponse();
        respondToCall(callDetails, response);
        Log.d(TAG, "⚡ IMMEDIATE RESPONSE SENT");

        // Process call details in background thread
        new Thread(() -> processCallDetails(callDetails)).start();
    }

    private void processCallDetails(Call.Details callDetails) {
        try {
            String phoneNumber = null;

            if (callDetails.getHandle() != null) {
                phoneNumber = callDetails.getHandle().getSchemeSpecificPart();
                Log.d(TAG, "Handle scheme: " + callDetails.getHandle().getScheme());
                Log.d(TAG, "Phone number extracted: " + phoneNumber);
            } else {
                Log.w(TAG, "Call handle is null!");
                return;
            }

            if (phoneNumber == null || phoneNumber.isEmpty()) {
                Log.w(TAG, "No phone number available from CallScreeningService");
                return;
            }

            CallDetailsHolder holder = new CallDetailsHolder();
            holder.setPhoneNumber(phoneNumber);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int verificationStatus = callDetails.getCallerNumberVerificationStatus();
            switch (verificationStatus) {
                case Connection.VERIFICATION_STATUS_PASSED:
                    holder.setVerificationStatus("PASSED");
                    break;
                case Connection.VERIFICATION_STATUS_FAILED:
                    holder.setVerificationStatus("FAILED");
                    Log.w(TAG, "SPAM - Verification Failed: " + phoneNumber);
                    break;
                case Connection.VERIFICATION_STATUS_NOT_VERIFIED:
                default:
                    holder.setVerificationStatus("NOT_VERIFIED");
            }
        } else {
            holder.setVerificationStatus("NOT_AVAILABLE");
        }

        int presentation = callDetails.getHandlePresentation();
        String presentationStr;
        switch (presentation) {
            case 1: // PRESENTATION_ALLOWED
                presentationStr = "ALLOWED";
                break;
            case 2: // PRESENTATION_RESTRICTED
                presentationStr = "RESTRICTED"; // Số ẩn - Nghi spam cao!
                Log.w(TAG, "RESTRICTED NUMBER (Hidden) - High spam risk");
                break;
            case 3: // PRESENTATION_UNKNOWN
                presentationStr = "UNKNOWN";
                Log.w(TAG, "UNKNOWN PRESENTATION - Medium spam risk");
                break;
            case 4: // PRESENTATION_PAYPHONE
                presentationStr = "PAYPHONE";
                break;
            default:
                presentationStr = "UNDEFINED";
        }
        holder.setHandlePresentation(presentationStr);

        String callerDisplayName = callDetails.getCallerDisplayName();
        if (callerDisplayName != null && !callerDisplayName.isEmpty()) {
            holder.setCallerDisplayName(callerDisplayName);

            // Check spam keywords từ carrier
            String lowerName = callerDisplayName.toLowerCase();
            if (lowerName.contains("spam") ||
                    lowerName.contains("scam") ||
                    lowerName.contains("fraud") ||
                    lowerName.contains("telemarketer") ||
                    lowerName.contains("robocall")) {
                Log.w(TAG, "SPAM - Carrier marked as: " + callerDisplayName);
            }
        } else {
            holder.setCallerDisplayName("UNKNOWN");
        }

            // Log tổng hợp
            Log.d(TAG, "Call Details Summary:");
            Log.d(TAG, "   1. Number: " + phoneNumber);
            Log.d(TAG, "   2. Verification: " + holder.getVerificationStatus());
            Log.d(TAG, "   3. Presentation: " + holder.getHandlePresentation());
            Log.d(TAG, "   4. Display Name: " + holder.getCallerDisplayName());

            // Set pending call details
            AutoReceiveSpamService.setPendingCallDetails(holder);
            Log.d(TAG, "✅ CallScreeningService set pending call details for: " + phoneNumber);

        } catch (Exception e) {
            Log.e(TAG, "Error processing call details", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 CallScreeningService destroyed");
    }

    private CallResponse buildAllowResponse() {
        return new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
    }
}