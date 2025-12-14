package com.example.honeypot01.service;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.honeypot01.model.CallDetailsHolder;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallScreeningService extends CallScreeningService {
    private static final String TAG = "CallScreeningService";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        String phoneNumber = null;

        // Lấy số điện thoại
        if (callDetails.getHandle() != null) {
            phoneNumber = callDetails.getHandle().getSchemeSpecificPart();
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.w(TAG, "No phone number available");
            respondToCall(callDetails,
                    new CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .build()
            );
            return;
        }

        // Tạo CallDetailsHolder với thông tin chi tiết
        CallDetailsHolder holder = new CallDetailsHolder();
        holder.setPhoneNumber(phoneNumber);

        // Lấy Verification Status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int verificationStatus = callDetails.getCallerNumberVerificationStatus();
            switch (verificationStatus) {
                case Connection.VERIFICATION_STATUS_PASSED:
                    holder.setVerificationStatus("PASSED");
                    break;
                case Connection.VERIFICATION_STATUS_FAILED:
                    holder.setVerificationStatus("FAILED");
                    break;
                case Connection.VERIFICATION_STATUS_NOT_VERIFIED:
                default:
                    holder.setVerificationStatus("NOT_VERIFIED");
            }
        }

        // Lấy Call Type
        int callDirection = callDetails.getCallDirection();
        if (callDirection == Call.Details.DIRECTION_INCOMING) {
            holder.setCallType("CALL_TYPE_INCOMING");
        } else if (callDirection == Call.Details.DIRECTION_OUTGOING) {
            holder.setCallType("CALL_TYPE_OUTGOING");
        } else {
            holder.setCallType("CALL_TYPE_UNKNOWN");
        }

        // Lấy Caller Display Name
        if (callDetails.getCallerDisplayName() != null) {
            holder.setCallerDisplayName(callDetails.getCallerDisplayName());
        }

        Log.d(TAG, "📋 Call Details:");
        Log.d(TAG, "   Number: " + phoneNumber);
        Log.d(TAG, "   Verification: " + holder.getVerificationStatus());
        Log.d(TAG, "   Type: " + holder.getCallType());
        Log.d(TAG, "   Display Name: " + holder.getCallerDisplayName());

        // Truyền sang AutoReceiveSpamService
        AutoReceiveSpamService.setPendingCallDetails(holder);

        // Cho phép cuộc gọi đi qua (không chặn)
        respondToCall(callDetails,
                new CallResponse.Builder()
                        .setDisallowCall(false)
                        .setRejectCall(false)
                        .setSkipCallLog(false)
                        .setSkipNotification(false)
                        .build()
        );
    }
}