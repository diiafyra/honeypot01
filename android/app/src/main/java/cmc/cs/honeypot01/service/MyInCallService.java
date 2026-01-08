package cmc.cs.honeypot01.service;

import android.content.Context;
import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import cmc.cs.honeypot01.model.CallDetailsHolder;
import cmc.cs.honeypot01.repository.CallDataManager;

/**
 * InCallService - Service duy nhất xử lý cuộc gọi
 *
 * MỤC ĐÍCH:
 * - Nhận tất cả cuộc gọi đến
 * - Trích xuất thông tin SIM từ PhoneAccountHandle
 * - Tự động trả lời
 * - Gửi thông tin đến CallDataManager
 *
 * ƯU ĐIỂM:
 * - Đơn giản, không cần broadcast
 * - PhoneAccountHandle LUÔN có giá trị (khi là default dialer)
 * - Không cần TelephonyManager/PhoneStateListener
 * - Không cần CallScreeningService
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class MyInCallService extends InCallService {

    private static final String TAG = "MyInCallService";
    private CallDataManager callDataManager;

    // Track call state
    private CallDetailsHolder activeCallDetails = null;
    private long callStartTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        callDataManager = new CallDataManager(this);
        Log.d(TAG, "🔥 InCallService CREATED");
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.d(TAG, "📞 ═══════════ NEW CALL ADDED ═══════════");

        try {
            Call.Details details = call.getDetails();

            // Extract call info
            CallDetailsHolder callDetails = extractCallDetails(details);

            if (callDetails != null) {
                activeCallDetails = callDetails;

                // Log extracted info
                logCallInfo(callDetails);

                // Register callback để track state changes
                registerCallCallback(call);

                // Auto answer nếu đang ringing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (details.getState() == Call.STATE_RINGING) {
                        Log.d(TAG, "⏰ Call is RINGING - Auto answering...");
                        call.answer(0); // 0 = audio only
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing call", e);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d(TAG, "📵 ═══════════ CALL REMOVED ═══════════");

        // Calculate duration and save
        if (activeCallDetails != null && callStartTime > 0) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - callStartTime;

            Log.d(TAG, "📊 Call duration: " + (duration / 1000) + "s");

            // Send to CallDataManager
            callDataManager.onCallEnded(activeCallDetails, callStartTime, duration);
        }

        // Reset state
        activeCallDetails = null;
        callStartTime = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXTRACT CALL DETAILS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Trích xuất đầy đủ thông tin từ Call.Details
     */
    private CallDetailsHolder extractCallDetails(Call.Details details) {
        CallDetailsHolder holder = new CallDetailsHolder();

        // 1. Phone number
        String phoneNumber = null;
        if (details.getHandle() != null) {
            phoneNumber = details.getHandle().getSchemeSpecificPart();
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.w(TAG, "❌ No phone number available");
            return null;
        }

        holder.setPhoneNumber(phoneNumber);
        Log.d(TAG, "📞 Phone: " + phoneNumber);

        // 2. SIM info từ PhoneAccountHandle
        extractSimInfo(details.getAccountHandle(), holder);

        // 3. Caller display name
        String displayName = details.getCallerDisplayName();
        holder.setCallerDisplayName(
                (displayName != null && !displayName.isEmpty()) ? displayName : "UNKNOWN"
        );

        // 4. Presentation
        int presentation = details.getHandlePresentation();
        holder.setHandlePresentation(getPresentationString(presentation));

        // 5. Verification (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int verification = details.getCallerNumberVerificationStatus();
            holder.setVerificationStatus(getVerificationString(verification));
        }

        return holder;
    }

    /**
     * Trích xuất thông tin SIM từ PhoneAccountHandle
     * ĐÂY LÀ PHẦN QUAN TRỌNG NHẤT - GIẢI QUYẾT VẤN ĐỀ DUAL-SIM
     */
    private void extractSimInfo(PhoneAccountHandle accountHandle, CallDetailsHolder holder) {
        if (accountHandle == null) {
            Log.w(TAG, "⚠️ PhoneAccountHandle is NULL");
            holder.setSimSlotInfo("UNKNOWN");
            holder.setSubscriptionId(-1);
            return;
        }

        Log.d(TAG, "✅ PhoneAccountHandle available!");

        String accountId = accountHandle.getId();
        Log.d(TAG, "📱 Account ID: " + accountId);

        // Parse subscription ID từ account ID
        int subscriptionId = parseSubscriptionId(accountId);
        holder.setSubscriptionId(subscriptionId);

        // Lấy SIM info chi tiết
        String simInfo = getSimInfoFromSubscriptionId(subscriptionId);

        // Fallback: Dùng PhoneAccount label
        if (simInfo == null || simInfo.equals("UNKNOWN")) {
            simInfo = getSimInfoFromPhoneAccount(accountHandle);
        }

        holder.setSimSlotInfo(simInfo);

        Log.d(TAG, "📱 Final SIM info: " + simInfo + " (SubID: " + subscriptionId + ")");
    }

    /**
     * Parse subscription ID từ account ID string
     */
    private int parseSubscriptionId(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return -1;
        }

        try {
            // Format: "subId1", "subId2"
            if (accountId.startsWith("subId")) {
                return Integer.parseInt(accountId.replace("subId", ""));
            }

            // Format: "1", "2"
            if (accountId.matches("\\d+")) {
                return Integer.parseInt(accountId);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Cannot parse subId from: " + accountId);
        }

        return -1;
    }

    /**
     * Lấy thông tin SIM từ subscription ID
     */
    private String getSimInfoFromSubscriptionId(int subscriptionId) {
        if (subscriptionId == -1) {
            return "UNKNOWN";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subManager = (SubscriptionManager)
                        getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                if (subManager != null) {
                    SubscriptionInfo info = subManager.getActiveSubscriptionInfo(subscriptionId);
                    if (info != null) {
                        int slotIndex = info.getSimSlotIndex();
                        CharSequence carrierName = info.getCarrierName();

                        String carrier = (carrierName != null && carrierName.length() > 0)
                                ? carrierName.toString()
                                : "Unknown";

                        return "SIM_" + (slotIndex + 1) + "_" + carrier;
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException getting SIM info", e);
            } catch (Exception e) {
                Log.e(TAG, "Error getting SIM info", e);
            }
        }

        return "SIM_SUB_" + subscriptionId; // Fallback
    }

    /**
     * Lấy SIM info từ PhoneAccount label
     */
    private String getSimInfoFromPhoneAccount(PhoneAccountHandle accountHandle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                TelecomManager telecomManager = getSystemService(TelecomManager.class);
                if (telecomManager != null) {
                    android.telecom.PhoneAccount phoneAccount =
                            telecomManager.getPhoneAccount(accountHandle);

                    if (phoneAccount != null) {
                        CharSequence label = phoneAccount.getLabel();
                        if (label != null && label.length() > 0) {
                            return label.toString();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting PhoneAccount", e);
            }
        }

        return "UNKNOWN";
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALL STATE TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Đăng ký callback để track state changes
     */
    private void registerCallCallback(Call call) {
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                handleStateChange(call, state);
            }
        });
    }

    /**
     * Xử lý state changes
     */
    private void handleStateChange(Call call, int state) {
        String stateName = getStateName(state);
        Log.d(TAG, "📊 Call state changed: " + stateName);

        switch (state) {
            case Call.STATE_ACTIVE:
                if (callStartTime == 0) {
                    callStartTime = System.currentTimeMillis();
                    Log.d(TAG, "✅ Call ACTIVE - Recording start time");

                    // Notify CallDataManager
                    if (activeCallDetails != null) {
                        callDataManager.onCallStarted(activeCallDetails);
                    }
                }
                break;

            case Call.STATE_DISCONNECTED:
                Log.d(TAG, "❌ Call DISCONNECTED");
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private String getStateName(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NEW";
            case Call.STATE_RINGING: return "RINGING";
            case Call.STATE_DIALING: return "DIALING";
            case Call.STATE_ACTIVE: return "ACTIVE";
            case Call.STATE_HOLDING: return "HOLDING";
            case Call.STATE_DISCONNECTED: return "DISCONNECTED";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    private String getPresentationString(int presentation) {
        switch (presentation) {
            case 1: return "ALLOWED";
            case 2: return "RESTRICTED";
            case 3: return "UNKNOWN";
            case 4: return "PAYPHONE";
            default: return "UNDEFINED";
        }
    }

    private String getVerificationString(int verification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            switch (verification) {
                case android.telecom.Connection.VERIFICATION_STATUS_PASSED:
                    return "PASSED";
                case android.telecom.Connection.VERIFICATION_STATUS_FAILED:
                    return "FAILED";
                case android.telecom.Connection.VERIFICATION_STATUS_NOT_VERIFIED:
                default:
                    return "NOT_VERIFIED";
            }
        }
        return "NOT_AVAILABLE";
    }

    /**
     * Log call info for debugging
     */
    private void logCallInfo(CallDetailsHolder details) {
        Log.d(TAG, "");
        Log.d(TAG, "╔═══════════════════════════════════════════");
        Log.d(TAG, "║ CALL INFORMATION");
        Log.d(TAG, "╠═══════════════════════════════════════════");
        Log.d(TAG, "║ Phone:        " + details.getPhoneNumber());
        Log.d(TAG, "║ SIM:          " + details.getSimSlotInfo());
        Log.d(TAG, "║ SubID:        " + details.getSubscriptionId());
        Log.d(TAG, "║ Display Name: " + details.getCallerDisplayName());
        Log.d(TAG, "║ Presentation: " + details.getHandlePresentation());
        Log.d(TAG, "║ Verification: " + details.getVerificationStatus());
        Log.d(TAG, "╚═══════════════════════════════════════════");
        Log.d(TAG, "");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 InCallService DESTROYED");
    }
}