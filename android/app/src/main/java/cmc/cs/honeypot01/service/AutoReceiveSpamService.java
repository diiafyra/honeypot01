package cmc.cs.honeypot01.service;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;


import cmc.cs.honeypot01.model.CallDetailsHolder;
import cmc.cs.honeypot01.repository.CallDataManager;

import java.util.concurrent.Executor;

@SuppressLint("AccessibilityPolicy")
public class AutoReceiveSpamService extends Service {

    private static final String TAG = "AutoReceiveSpam";

    private Handler handler;
    private TelephonyManager telephonyManager;
    private TelecomManager telecomManager;
    private PhoneStateListener phoneStateListener;
    private CustomTelephonyCallback telephonyCallback;
    private CallDataManager callDataManager;

    // NEW: Handler for delayed answering logic
    private DelayedCallHandler delayedHandler;

    // Call details từ CallScreeningService
    private static CallDetailsHolder pendingCallDetails = null;

    // Call tracking
    private long callStartTime = 0;
    private boolean isCallActive = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // NEW: Static reference to service instance for receiver communication
    private static AutoReceiveSpamService instance = null; // NEW: Static instance for receiver to call methods

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // NEW: Set static instance
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        callDataManager = new CallDataManager(this);

        // NEW: Initialize delayed handler
        delayedHandler = new DelayedCallHandler(this);

        registerPhoneStateListener();
        startAsForeground();

        Log.d(TAG, "Service Created");
    }


    /**
     * Set call details từ CallScreeningService
     */
    public static void setPendingCallDetails(CallDetailsHolder callDetails) {
        pendingCallDetails = callDetails;
        Log.d(TAG, "Pending call: " + callDetails.getPhoneNumber());
    }

    /**
     * NEW: Get pending call details (for handler)
     */
    public CallDetailsHolder getPendingCallDetailsInstance() {
        return pendingCallDetails;
    }

    /**
     * NEW: Set pending call details (for handler)
     */
    public void setPendingCallDetailsInstance(CallDetailsHolder details) {
        pendingCallDetails = details;
    }

    /**
     * NEW: Get call data manager (for handler)
     */
    public CallDataManager getCallDataManager() {
        return callDataManager;
    }

    /**
     * NEW: Public method to answer call (for handler)
     */
    public void answerCallPublic() {
        Log.d(TAG, "answerCallPublic called");
        answerCall();
    }

    /**
     * NEW: Start call processing (for handler)
     */
    public void startCall(CallDataManager cdm) {
        if (!isCallActive && pendingCallDetails != null) {
            isCallActive = true;
            callStartTime = System.currentTimeMillis();
            Log.d(TAG, "CALL STARTED: " + pendingCallDetails.getPhoneNumber());
            cdm.onCallStarted(pendingCallDetails);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHONE STATE LISTENER
    // ═══════════════════════════════════════════════════════════════════

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
                // Android < 12: tạo CallDetailsHolder nếu chưa có
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

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class CustomTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleCallStateChange(state);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALL STATE HANDLING
    // ═══════════════════════════════════════════════════════════════════

    private void handleCallStateChange(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                handleRingingState();
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                handleOffhookState();
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                handleIdleState();
                break;
        }
    }

    /**
     * RINGING: Cuộc gọi đến → Tự động answer
     */
    private void handleRingingState() {
        // NEW: Delegate to handler for delayed logic
        delayedHandler.onRinging();
    }

    /**
     * OFFHOOK: Cuộc gọi đang active → Track call start
     */
    private void handleOffhookState() {
        // NEW: Delegate to handler
        delayedHandler.onOffhook(callDataManager);
    }

    /**
     * IDLE: Cuộc gọi kết thúc → Lưu log và transcribe
     */
    private void handleIdleState() {
        if (isCallActive && pendingCallDetails != null) {
            isCallActive = false;
            long callEndTime = System.currentTimeMillis();
            long duration = callEndTime - callStartTime;

            Log.d(TAG, "CALL ENDED: " + pendingCallDetails.getPhoneNumber() +
                    " (duration: " + (duration / 1000) + "s)");

            callDataManager.onCallEnded(
                    pendingCallDetails,
                    callStartTime,
                    duration
            );

            // Reset
            pendingCallDetails = null;
            callStartTime = 0;
            // NEW: Reset handler
            delayedHandler.onIdle();
        }
    }

    // NEW: Static method for receiver to notify number received
    public static void onNumberReceived(String number) {
        if (instance != null) {
            instance.onNumberReceivedInternal(number);
        }
    }

    // NEW: Instance method
    private void onNumberReceivedInternal(String number) {
        // NEW: Delegate to handler
        delayedHandler.onNumberReceived(number);
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTO ANSWER
    // ═══════════════════════════════════════════════════════════════════

    private void answerCall() {
        Log.d(TAG, "answerCall: Checking permissions");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission");
            return;
        }

        Log.d(TAG, "answerCall: TelecomManager is " + (telecomManager != null ? "not null" : "null"));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                Log.d(TAG, "answerCall: Calling acceptRingingCall");
                telecomManager.acceptRingingCall();
                Log.d(TAG, "Call answered via TelecomManager");
            } else {
                Log.e(TAG, "Cannot answer: API < 26 or TelecomManager null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to answer call", e);
        }
    }

    // NEW: Combined method to answer call and start processing with number
    private void answerAndStartCall(String phoneNumber) {
        // Answer the call
        answerCall();

        // Set up call details
        CallDetailsHolder holder = new CallDetailsHolder();
        holder.setPhoneNumber(phoneNumber);
        holder.setVerificationStatus("UNKNOWN");
        holder.setHandlePresentation("ALLOWED");
        holder.setCallerDisplayName(""); // Empty, as we don't have actual caller name
        pendingCallDetails = holder;

        // Start call processing
        isCallActive = true;
        callStartTime = System.currentTimeMillis();
        Log.d(TAG, "CALL STARTED: " + phoneNumber);
        callDataManager.onCallStarted(pendingCallDetails);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSIBILITY (BỎ TRỐNG)
    // ═══════════════════════════════════════════════════════════════════

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

        Log.d(TAG, "🛑 Service Destroyed");
    }
    private void startAsForeground() {
        String channelId = "honeypot_call_service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Call Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Honeypot đang hoạt động")
                    .setContentText("Đang theo dõi cuộc gọi đến")
                    .setSmallIcon(android.R.drawable.ic_menu_call)
                    .setOngoing(true)
                    .build();
        }

        startForeground(1, notification);
    }

}