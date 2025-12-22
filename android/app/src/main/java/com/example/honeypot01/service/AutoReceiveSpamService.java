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

import cmc.cs.honeypot01.R;
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

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        callDataManager = new CallDataManager(this);

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
        Log.d(TAG, "RINGING - Auto-answering...");
        answerCall();
    }

    /**
     * OFFHOOK: Cuộc gọi đang active → Track call start
     */
    private void handleOffhookState() {
        if (!isCallActive) {
            if (pendingCallDetails == null) {
                Log.w(TAG, "Pending call details missing (CallScreeningService didn't trigger?). Creating placeholder.");
                pendingCallDetails = new CallDetailsHolder();
                pendingCallDetails.setPhoneNumber("UNKNOWN_" + System.currentTimeMillis());
            }

            isCallActive = true;
            callStartTime = System.currentTimeMillis();

            Log.d(TAG, "CALL STARTED: " + pendingCallDetails.getPhoneNumber());
            callDataManager.onCallStarted(pendingCallDetails);
        }
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
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTO ANSWER
    // ═══════════════════════════════════════════════════════════════════

    private void answerCall() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                telecomManager.acceptRingingCall();
                Log.d(TAG, "Call answered via TelecomManager");
            } else {
                Log.e(TAG, "Cannot answer: API < 26 or TelecomManager null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to answer call", e);
        }
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
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .build();
        }

        startForeground(1, notification);
    }

}