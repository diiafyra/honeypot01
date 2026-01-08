package cmc.cs.honeypot01.service;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import cmc.cs.honeypot01.model.CallDetailsHolder;
import cmc.cs.honeypot01.repository.CallDataManager;
import cmc.cs.honeypot01.helper.ReceiverInfoExtractor;

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
    private ReceiverInfoExtractor receiverInfoExtractor;

    // Call state
    private CallDetailsHolder pendingCallDetails = null;
    private String pendingNumberFromBroadcast = null;
    private boolean isRinging = false;
    private boolean isCallActive = false;
    private long callStartTime = 0;

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
        receiverInfoExtractor = ReceiverInfoExtractor.create(this);

        registerPhoneStateListener();
        registerNumberReceiver();
        startAsForeground();

        Log.d(TAG, "Service Created");
        logReceiverInfo();
    }

    /**
     * Log thông tin receiver khi service khởi động
     */
    private void logReceiverInfo() {
        String receiverNumber = receiverInfoExtractor.getReceiverNumber();
        Integer subscriptionId = receiverInfoExtractor.getSubscriptionId();

        Log.d(TAG, "=== RECEIVER INFO ===");
        Log.d(TAG, "Receiver Number: " + (receiverNumber != null ? receiverNumber : "N/A"));
        Log.d(TAG, "Subscription ID: " + (subscriptionId != null ? subscriptionId : "N/A"));
        Log.d(TAG, "====================");
    }

    // ═══════════════════════════════════════════════════════════════════
    // BROADCAST RECEIVERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Receiver cho số điện thoại từ nguồn khác (fallback)
     */
    private BroadcastReceiver numberReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String number = intent.getStringExtra("phone_number");
            if (number != null) {
                onNumberReceived(number);
            }
        }
    };

    /**
     * Receiver cho call details từ CallScreeningService (primary)
     */
    private BroadcastReceiver callDetailsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String phoneNumber = intent.getStringExtra("phone_number");
            String verificationStatus = intent.getStringExtra("verification_status");
            String handlePresentation = intent.getStringExtra("handle_presentation");
            String callerDisplayName = intent.getStringExtra("caller_display_name");
            String simSlotInfo = intent.getStringExtra("sim_slot_info");
            int subscriptionId = intent.getIntExtra("subscription_id", -1);

            if (phoneNumber != null) {
                onCallDetailsReceived(phoneNumber, verificationStatus,
                        handlePresentation, callerDisplayName,
                        simSlotInfo, subscriptionId);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerNumberReceiver() {
        IntentFilter numberFilter = new IntentFilter("cmc.cs.honeypot01.PHONE_NUMBER");
        IntentFilter detailsFilter = new IntentFilter("cmc.cs.honeypot01.CALL_DETAILS");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(numberReceiver, numberFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(callDetailsReceiver, detailsFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(numberReceiver, numberFilter);
            registerReceiver(callDetailsReceiver, detailsFilter);
        }

        Log.d(TAG, "Receivers registered");
    }

    private void onNumberReceived(String number) {
        Log.d(TAG, "Number received from broadcast: " + number);
        pendingNumberFromBroadcast = number;

        // Create CallDetailsHolder if not exists
        if (pendingCallDetails == null) {
            pendingCallDetails = new CallDetailsHolder();
            pendingCallDetails.setPhoneNumber(number);
            pendingCallDetails.setVerificationStatus("UNKNOWN");
            pendingCallDetails.setHandlePresentation("ALLOWED");
            pendingCallDetails.setCallerDisplayName("");
        }

        // Answer if already ringing
        if (isRinging) {
            answerCall();
        }
    }

    /**
     * Nhận call details đầy đủ từ CallScreeningService
     */
    private void onCallDetailsReceived(String phoneNumber, String verificationStatus,
                                       String handlePresentation, String callerDisplayName,
                                       String simSlotInfo, int subscriptionId) {
        Log.d(TAG, "Call details received from CallScreeningService");
        Log.d(TAG, "Number: " + phoneNumber);
        Log.d(TAG, "📱 SIM: " + simSlotInfo + " (SubID: " + subscriptionId + ")");

        pendingCallDetails = new CallDetailsHolder();
        pendingCallDetails.setPhoneNumber(phoneNumber);
        pendingCallDetails.setVerificationStatus(verificationStatus != null ? verificationStatus : "UNKNOWN");
        pendingCallDetails.setHandlePresentation(handlePresentation != null ? handlePresentation : "ALLOWED");
        pendingCallDetails.setCallerDisplayName(callerDisplayName != null ? callerDisplayName : "");
        pendingCallDetails.setSimSlotInfo(simSlotInfo != null ? simSlotInfo : "UNKNOWN");
        pendingCallDetails.setSubscriptionId(subscriptionId);

        // Answer if already ringing
        if (isRinging) {
            answerCall();
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
                // Android < 12: create CallDetailsHolder if needed
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
    private class CustomTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
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

    private void handleRingingState() {
        isRinging = true;
        Log.d(TAG, "RINGING - Checking for number...");

        // LẤY SIM INFO KHI ĐANG RINGING
        String simSlotInfo = "UNKNOWN";
        int subscriptionId = -1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                // Lấy subscription ID của cuộc gọi đang ringing
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {

                    // Cách 1: Từ TelephonyManager (Android 12+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+ có API mới
                        subscriptionId = telephonyManager.getSubscriptionId();
                        Log.d(TAG, "📱 SubID from TelephonyManager (API 31+): " + subscriptionId);
                    }

                    // Cách 2: Từ SubscriptionManager
                    if (subscriptionId == -1) {
                        SubscriptionManager subManager = (SubscriptionManager)
                                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                        if (subManager != null) {
                            subscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId();
                            Log.d(TAG, "📱 SubID from SubscriptionManager: " + subscriptionId);
                        }
                    }

                    // Lấy thông tin SIM từ subscription ID
                    if (subscriptionId != -1) {
                        SubscriptionManager subManager = (SubscriptionManager)
                                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                        if (subManager != null) {
                            SubscriptionInfo info = subManager.getActiveSubscriptionInfo(subscriptionId);
                            if (info != null) {
                                int slotIndex = info.getSimSlotIndex();
                                String carrierName = info.getCarrierName() != null
                                        ? info.getCarrierName().toString()
                                        : "Unknown";
                                simSlotInfo = "SIM_" + (slotIndex + 1) + "_" + carrierName;
                                Log.d(TAG, "📱 SIM Info: " + simSlotInfo);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting SIM info", e);
            }
        }

        // Priority: CallScreeningService > Broadcast > Wait
        String number = null;
        if (pendingCallDetails != null && pendingCallDetails.getPhoneNumber() != null) {
            number = pendingCallDetails.getPhoneNumber();
            Log.d(TAG, "Using CallScreeningService number: " + number);

            // CẬP NHẬT SIM INFO VÀO pendingCallDetails
            if (!simSlotInfo.equals("UNKNOWN")) {
                pendingCallDetails.setSimSlotInfo(simSlotInfo);
                pendingCallDetails.setSubscriptionId(subscriptionId);
                Log.d(TAG, "✓ Updated SIM info in CallDetails");
            }

        } else if (pendingNumberFromBroadcast != null) {
            number = pendingNumberFromBroadcast;
            Log.d(TAG, "Using broadcast number: " + number);

            // Ensure CallDetailsHolder exists
            if (pendingCallDetails == null) {
                pendingCallDetails = new CallDetailsHolder();
                pendingCallDetails.setPhoneNumber(number);
                pendingCallDetails.setVerificationStatus("UNKNOWN");
                pendingCallDetails.setHandlePresentation("ALLOWED");
                pendingCallDetails.setCallerDisplayName("");
            }

            // CẬP NHẬT SIM INFO
            pendingCallDetails.setSimSlotInfo(simSlotInfo);
            pendingCallDetails.setSubscriptionId(subscriptionId);
        }

        if (number != null) {
            Log.d(TAG, "📱 Final call info - Number: " + number +
                    ", SIM: " + simSlotInfo + ", SubID: " + subscriptionId);
            answerCall();
        } else {
            Log.w(TAG, "Number not ready, waiting...");
        }
    }
    private void handleOffhookState() {
        if (!isCallActive && pendingCallDetails != null) {
            isCallActive = true;
            callStartTime = System.currentTimeMillis();
            Log.d(TAG, "CALL STARTED: " + pendingCallDetails.getPhoneNumber());
            callDataManager.onCallStarted(pendingCallDetails);
        }
    }

    private void handleIdleState() {
        if (isCallActive && pendingCallDetails != null) {
            isCallActive = false;
            long callEndTime = System.currentTimeMillis();
            long duration = callEndTime - callStartTime;

            Log.d(TAG, "CALL ENDED: " + pendingCallDetails.getPhoneNumber() +
                    " (duration: " + (duration / 1000) + "s)");

            callDataManager.onCallEnded(pendingCallDetails, callStartTime, duration);
        }

        // Reset all state
        pendingCallDetails = null;
        pendingNumberFromBroadcast = null;
        isRinging = false;
        callStartTime = 0;
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
                Log.d(TAG, "Call answered");
            } else {
                Log.e(TAG, "Cannot answer: API < 26 or TelecomManager null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to answer call", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback);
        } else if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        unregisterReceiver(numberReceiver);
        unregisterReceiver(callDetailsReceiver);

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "Service Destroyed");
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