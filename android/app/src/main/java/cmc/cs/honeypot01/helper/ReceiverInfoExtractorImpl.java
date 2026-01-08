package cmc.cs.honeypot01.helper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.List;

/**
 * Implementation để lấy thông tin receiver
 * Mục tiêu: Chỉ cần xác định SIM 1 hay SIM 2, không cần số điện thoại chính xác
 */
class ReceiverInfoExtractorImpl implements ReceiverInfoExtractor {

    private static final String TAG = "ReceiverInfoExtractor";
    private final Context context;
    private final TelephonyManager telephonyManager;
    private final SubscriptionManager subscriptionManager;

    ReceiverInfoExtractorImpl(Context context) {
        this.context = context.getApplicationContext();
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            this.subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        } else {
            this.subscriptionManager = null;
        }
    }

    @Override
    public String getReceiverNumber() {
        Log.d(TAG, "=== Getting receiver identifier ===");

        // 1. Thử lấy số thật từ SharedPreferences (nếu user đã nhập)
        String saved = context.getSharedPreferences("HoneypotPrefs", Context.MODE_PRIVATE)
                .getString("pot_number", null);
        if (saved != null && !saved.isEmpty()) {
            Log.d(TAG, "✓ Using saved number");
            return saved;
        }

        // 2. Thử lấy số thật từ TelephonyManager
        String realNumber = tryGetRealNumber();
        if (realNumber != null && !realNumber.isEmpty()) {
            Log.d(TAG, "✓ Got real number from telephony");
            return realNumber;
        }

        // 3. FALLBACK: Trả về identifier dựa trên SIM slot
        String simIdentifier = getSimIdentifier();
        if (simIdentifier != null) {
            Log.d(TAG, "✓ Using SIM identifier: " + simIdentifier);
            return simIdentifier;
        }

        Log.w(TAG, "✗ Could not determine receiver (using default)");
        return "SIM_DEFAULT"; // Fallback cuối cùng
    }

    @Override
    public Integer getSubscriptionId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {

                    int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
                    if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        Log.d(TAG, "✓ Got subscription ID: " + defaultSubId);
                        return defaultSubId;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get subscription ID", e);
            }
        }

        Log.w(TAG, "Using default subscription ID (0)");
        return 0; // Trả về 0 thay vì null để tránh crash
    }

    /**
     * Thử lấy số điện thoại thật
     */
    private String tryGetRealNumber() {
        // Kiểm tra quyền
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        // Android 8+ cần thêm READ_PHONE_NUMBERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS)
                    != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
        }

        try {
            // Thử getLine1Number
            String number = telephonyManager.getLine1Number();
            if (number != null && !number.isEmpty() && !number.equals("Unknown")) {
                return number;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get line1 number", e);
        }

        // Thử từ SubscriptionManager
        return getNumberFromSubscription();
    }

    /**
     * Lấy identifier dựa trên SIM slot (SIM_1, SIM_2, etc.)
     */
    private String getSimIdentifier() {
        Log.d(TAG, "Attempting to get SIM identifier...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_PHONE_STATE permission not granted");
                return null;
            }

            try {
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptions == null) {
                    Log.w(TAG, "subscriptions list is NULL");
                    return null;
                }

                if (subscriptions.isEmpty()) {
                    Log.w(TAG, "subscriptions list is EMPTY");
                    return null;
                }

                Log.d(TAG, "Found " + subscriptions.size() + " active SIM(s)");

                int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
                Log.d(TAG, "Default voice subscription ID: " + defaultSubId);

                // Duyệt qua tất cả SIM để tìm default
                for (int i = 0; i < subscriptions.size(); i++) {
                    SubscriptionInfo info = subscriptions.get(i);
                    int subId = info.getSubscriptionId();
                    int slotIndex = info.getSimSlotIndex();
                    String carrierName = info.getCarrierName() != null
                            ? info.getCarrierName().toString()
                            : "Unknown";

                    Log.d(TAG, "SIM " + i + ": SubID=" + subId +
                            ", Slot=" + slotIndex +
                            ", Carrier=" + carrierName);

                    if (subId == defaultSubId) {
                        String identifier = "SIM_" + (slotIndex + 1) + "_" + carrierName;
                        Log.d(TAG, "✓ Using default SIM: " + identifier);
                        return identifier;
                    }
                }

                // Fallback: lấy SIM đầu tiên
                SubscriptionInfo firstSim = subscriptions.get(0);
                int slotIndex = firstSim.getSimSlotIndex();
                String carrierName = firstSim.getCarrierName() != null
                        ? firstSim.getCarrierName().toString()
                        : "Unknown";

                String identifier = "SIM_" + (slotIndex + 1) + "_" + carrierName;
                Log.d(TAG, "✓ Using first SIM (fallback): " + identifier);
                return identifier;

            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException getting SIM identifier", e);
            } catch (Exception e) {
                Log.e(TAG, "Error getting SIM identifier", e);
            }
        } else {
            Log.w(TAG, "SubscriptionManager not available (API < 22 or NULL)");
        }

        return null;
    }

    /**
     * Lấy số từ active subscriptions (thử lần cuối)
     */
    private String getNumberFromSubscription() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                return null;
            }

            try {
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();

                    for (SubscriptionInfo info : subscriptions) {
                        if (info.getSubscriptionId() == defaultSubId) {
                            String number = info.getNumber();
                            if (number != null && !number.isEmpty()) {
                                return number;
                            }
                        }
                    }

                    // Fallback: lấy số từ SIM đầu tiên
                    String number = subscriptions.get(0).getNumber();
                    if (number != null && !number.isEmpty()) {
                        return number;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting number from subscription", e);
            }
        }

        return null;
    }
}