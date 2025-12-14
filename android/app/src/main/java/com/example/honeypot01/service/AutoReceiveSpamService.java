package com.example.honeypot01.service;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.example.honeypot01.model.CallDetailsHolder;
import com.example.honeypot01.repository.CallDataManager;

import java.util.List;
import java.util.concurrent.Executor;

@SuppressLint("AccessibilityPolicy")
public class AutoReceiveSpamService extends AccessibilityService {

    private static final String TAG = "AutoReceiveSpam";

    private Handler handler;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private CustomTelephonyCallback telephonyCallback;
    private CallDataManager callDataManager;

    // Call details sẽ được set từ CallScreeningService
    private static CallDetailsHolder pendingCallDetails = null;

    private long callStartTime = 0;
    private boolean isCallActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        callDataManager = new CallDataManager(this);

        registerPhoneStateListener();

        Log.d(TAG, "Service Created");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service Connected - Auto-answer ACTIVE");
    }

    /**
     * Method public để CallScreeningService có thể set call details
     */
    public static void setPendingCallDetails(CallDetailsHolder callDetails) {
        pendingCallDetails = callDetails;
        Log.d(TAG, "Pending call details set: " + callDetails.getPhoneNumber() +
                " | Type: " + callDetails.getHandlePresentation()+
                " | Verification: " + callDetails.getVerificationStatus());
    }


    private void registerPhoneStateListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing READ_PHONE_STATE permission");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallback();
        } else {
            registerPhoneStateListenerLegacy();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void registerTelephonyCallback() {
        telephonyCallback = new CustomTelephonyCallback();
        Executor executor = command -> handler.post(command);
        telephonyManager.registerTelephonyCallback(executor, telephonyCallback);
        Log.d(TAG, "TelephonyCallback registered (API 31+)");
    }

    @SuppressLint("MissingPermission")
    private void registerPhoneStateListenerLegacy() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                // Android < 12: phoneNumber có sẵn, tạo CallDetailsHolder
                if (phoneNumber != null && !phoneNumber.isEmpty() && pendingCallDetails == null) {
                    pendingCallDetails = new CallDetailsHolder();
                    pendingCallDetails.setPhoneNumber(phoneNumber);
                }
                handleCallStateChange(state);
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        Log.d(TAG, "PhoneStateListener registered (Legacy)");
    }

    private void handleCallStateChange(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (!isCallActive && pendingCallDetails != null) {
                    isCallActive = true;
                    callStartTime = System.currentTimeMillis();
                    Log.d(TAG, "✅ CALL STARTED - Number: " +
                            pendingCallDetails.getPhoneNumber());

                    callDataManager.handleCallStarted(pendingCallDetails, callStartTime);
                }
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                if (isCallActive && pendingCallDetails != null) {
                    isCallActive = false;
                    long callEndTime = System.currentTimeMillis();
                    long duration = callEndTime - callStartTime;

                    Log.d(TAG, "❌ CALL ENDED - Duration: " + (duration / 1000) + "s");

                    callDataManager.handleCallEnded(
                            pendingCallDetails,
                            callStartTime,
                            callEndTime,
                            duration
                    );

                    pendingCallDetails = null;
                    callStartTime = 0;
                }
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class CustomTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleCallStateChange(state);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String eventString = event.toString().toLowerCase();

        if (eventString.contains("incoming call") ||
                eventString.contains("answer") ||
                eventString.contains("decline")) {
            Log.d(TAG, "INCOMING CALL DETECTED via Accessibility!");
            handleIncomingCall();
        }
    }

    private void handleIncomingCall() {
        Log.d(TAG, "Xử lý cuộc gọi đến...");
        answerCall();
    }

    private void answerCall() {
        boolean success = answerWithTelecomManager();

        if (success) {
            Log.d(TAG, "Call answered successfully!");
        } else {
            Log.e(TAG, "Failed to answer call");
        }
    }

    private boolean answerWithTelecomManager() {
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission");
                    return false;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tm.acceptRingingCall();
                }
                Log.d(TAG, "Answered via TelecomManager");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "TelecomManager failed: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service Interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback);
        } else if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "Service Destroyed");
    }
}