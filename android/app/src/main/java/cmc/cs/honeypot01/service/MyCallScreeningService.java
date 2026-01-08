package cmc.cs.honeypot01.service;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.List;

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

        // Respond immediately to avoid timeout
        CallResponse response = buildAllowResponse();
        respondToCall(callDetails, response);
        Log.d(TAG, "⚡ IMMEDIATE RESPONSE SENT");

        // Process call details in background thread
        new Thread(() -> processCallDetails(callDetails)).start();
    }

    private void processCallDetails(Call.Details callDetails) {
        try {
            // ═══════════════════════════════════════════════════════════
            // BƯỚC 1: LẤY SỐ ĐIỆN THOẠI
            // ═══════════════════════════════════════════════════════════
            String phoneNumber = null;

            if (callDetails.getHandle() != null) {
                phoneNumber = callDetails.getHandle().getSchemeSpecificPart();
                Log.d(TAG, "📞 Phone number extracted: " + phoneNumber);
            } else {
                Log.w(TAG, "❌ Call handle is null!");
                return;
            }

            if (phoneNumber == null || phoneNumber.isEmpty()) {
                Log.w(TAG, "❌ No phone number available");
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 2: LẤY THÔNG TIN SIM
            // ═══════════════════════════════════════════════════════════
            PhoneAccountHandle accountHandle = callDetails.getAccountHandle();
            int subscriptionId = -1;
            String simSlotInfo = "UNKNOWN";

            if (accountHandle != null) {
                Log.d(TAG, "✓ AccountHandle found: " + accountHandle.toString());

                // Cách 1: Lấy từ ID của accountHandle
                String accountId = accountHandle.getId();
                Log.d(TAG, "Account ID: " + accountId);

                // Thử parse subscription ID từ ID
                try {
                    if (accountId != null) {
                        // Nhiều ROM lưu subId trong ID dạng "subId1", "subId2", hoặc "1", "2"
                        if (accountId.startsWith("subId")) {
                            subscriptionId = Integer.parseInt(accountId.replace("subId", ""));
                            Log.d(TAG, "✓ Parsed subId from 'subId' prefix: " + subscriptionId);
                        } else if (accountId.matches("\\d+")) {
                            subscriptionId = Integer.parseInt(accountId);
                            Log.d(TAG, "✓ Parsed subId from numeric ID: " + subscriptionId);
                        } else {
                            Log.d(TAG, "Account ID format: " + accountId + " (not numeric)");
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse subId from accountId: " + e.getMessage());
                }

                // Cách 2: Lấy từ TelecomManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    TelecomManager telecomManager = getSystemService(TelecomManager.class);
                    if (telecomManager != null) {
                        PhoneAccount phoneAccount = telecomManager.getPhoneAccount(accountHandle);
                        if (phoneAccount != null) {
                            Log.d(TAG, "✓ PhoneAccount found");

                            // Lấy label (SIM 1, SIM 2, tên nhà mạng, etc.)
                            CharSequence label = phoneAccount.getLabel();
                            if (label != null && !label.toString().isEmpty()) {
                                simSlotInfo = label.toString();
                                Log.d(TAG, "✓ PhoneAccount label: " + simSlotInfo);
                            } else {
                                Log.w(TAG, "PhoneAccount label is NULL or empty");
                            }

                            // Lấy subscription ID từ extras (Android 7+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Bundle extras = phoneAccount.getExtras();
                                if (extras != null && !extras.isEmpty()) {
                                    Log.d(TAG, "PhoneAccount extras keys: " + extras.keySet().toString());

                                    // Thử key chính thức
                                    if (extras.containsKey("android.telecom.extra.SUBSCRIPTION_ID")) {
                                        int subId = extras.getInt("android.telecom.extra.SUBSCRIPTION_ID", -1);
                                        if (subId != -1) {
                                            subscriptionId = subId;
                                            Log.d(TAG, "✓ SubID from official key: " + subscriptionId);
                                        }
                                    }

                                    // Thử key thay thế
                                    if (subscriptionId == -1 && extras.containsKey("subscription_id")) {
                                        int subId = extras.getInt("subscription_id", -1);
                                        if (subId != -1) {
                                            subscriptionId = subId;
                                            Log.d(TAG, "✓ SubID from alternate key: " + subscriptionId);
                                        }
                                    }

                                    // Debug: In tất cả extras
                                    for (String key : extras.keySet()) {
                                        Object value = extras.get(key);
                                        Log.d(TAG, "  Extra: " + key + " = " + value);
                                    }
                                } else {
                                    Log.w(TAG, "PhoneAccount extras is NULL or empty");
                                }
                            }
                        } else {
                            Log.w(TAG, "❌ PhoneAccount is NULL");
                        }
                    } else {
                        Log.w(TAG, "❌ TelecomManager is NULL");
                    }
                }

                // Cách 3: Fallback - Dùng accountId làm identifier
                if (simSlotInfo.equals("UNKNOWN") && accountId != null) {
                    simSlotInfo = "ACCOUNT_" + accountId;
                    Log.d(TAG, "⚠️ Using fallback SIM identifier: " + simSlotInfo);
                }

                // Cách 4: Nếu có subId, tạo tên SIM
                if (subscriptionId != -1 && simSlotInfo.equals("UNKNOWN")) {
                    simSlotInfo = "SIM_SLOT_" + subscriptionId;
                    Log.d(TAG, "⚠️ Generated SIM name from subId: " + simSlotInfo);
                }

            } else {
                Log.w(TAG, "❌ AccountHandle is NULL - cannot determine SIM");
                SubscriptionManager sm = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
                Log.d(TAG, "Active subscriptions count: " + (subs != null ? subs.size() : 0));
                for (SubscriptionInfo info : subs) {
                    Log.d(TAG, "SIM slot " + info.getSimSlotIndex() + " - subId " + info.getSubscriptionId());
                }

            }

            Log.d(TAG, "📱 Final SIM Info - Slot: " + simSlotInfo + ", SubID: " + subscriptionId);

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 3: TẠO CALL DETAILS HOLDER
            // ═══════════════════════════════════════════════════════════
            CallDetailsHolder holder = new CallDetailsHolder();
            holder.setPhoneNumber(phoneNumber);
            holder.setSimSlotInfo(simSlotInfo);
            holder.setSubscriptionId(subscriptionId);

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 4: LẤY VERIFICATION STATUS
            // ═══════════════════════════════════════════════════════════
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int verificationStatus = callDetails.getCallerNumberVerificationStatus();
                switch (verificationStatus) {
                    case Connection.VERIFICATION_STATUS_PASSED:
                        holder.setVerificationStatus("PASSED");
                        Log.d(TAG, "✓ Verification: PASSED");
                        break;
                    case Connection.VERIFICATION_STATUS_FAILED:
                        holder.setVerificationStatus("FAILED");
                        Log.w(TAG, "⚠️ SPAM - Verification FAILED: " + phoneNumber);
                        break;
                    case Connection.VERIFICATION_STATUS_NOT_VERIFIED:
                    default:
                        holder.setVerificationStatus("NOT_VERIFIED");
                        Log.d(TAG, "Verification: NOT_VERIFIED");
                }
            } else {
                holder.setVerificationStatus("NOT_AVAILABLE");
                Log.d(TAG, "Verification: NOT_AVAILABLE (API < 30)");
            }

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 5: LẤY HANDLE PRESENTATION
            // ═══════════════════════════════════════════════════════════
            int presentation = callDetails.getHandlePresentation();
            String presentationStr;
            switch (presentation) {
                case 1: // PRESENTATION_ALLOWED
                    presentationStr = "ALLOWED";
                    break;
                case 2: // PRESENTATION_RESTRICTED
                    presentationStr = "RESTRICTED";
                    Log.w(TAG, "⚠️ RESTRICTED NUMBER - High spam risk");
                    break;
                case 3: // PRESENTATION_UNKNOWN
                    presentationStr = "UNKNOWN";
                    Log.w(TAG, "⚠️ UNKNOWN PRESENTATION - Medium spam risk");
                    break;
                case 4: // PRESENTATION_PAYPHONE
                    presentationStr = "PAYPHONE";
                    break;
                default:
                    presentationStr = "UNDEFINED";
            }
            holder.setHandlePresentation(presentationStr);
            Log.d(TAG, "Presentation: " + presentationStr);

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 6: LẤY CALLER DISPLAY NAME
            // ═══════════════════════════════════════════════════════════
            String callerDisplayName = callDetails.getCallerDisplayName();
            if (callerDisplayName != null && !callerDisplayName.isEmpty()) {
                holder.setCallerDisplayName(callerDisplayName);
                Log.d(TAG, "Display Name: " + callerDisplayName);

                // Check spam keywords từ carrier
                String lowerName = callerDisplayName.toLowerCase();
                if (lowerName.contains("spam") ||
                        lowerName.contains("scam") ||
                        lowerName.contains("fraud") ||
                        lowerName.contains("telemarketer") ||
                        lowerName.contains("robocall")) {
                    Log.w(TAG, "⚠️ SPAM - Carrier marked as: " + callerDisplayName);
                }
            } else {
                holder.setCallerDisplayName("UNKNOWN");
                Log.d(TAG, "Display Name: UNKNOWN");
            }

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 7: LOG SUMMARY
            // ═══════════════════════════════════════════════════════════
            Log.d(TAG, "");
            Log.d(TAG, "╔═══════════════════════════════════════════════════");
            Log.d(TAG, "║ CALL DETAILS SUMMARY");
            Log.d(TAG, "╠═══════════════════════════════════════════════════");
            Log.d(TAG, "║ Number:       " + phoneNumber);
            Log.d(TAG, "║ SIM Slot:     " + simSlotInfo);
            Log.d(TAG, "║ SubID:        " + subscriptionId);
            Log.d(TAG, "║ Verification: " + holder.getVerificationStatus());
            Log.d(TAG, "║ Presentation: " + holder.getHandlePresentation());
            Log.d(TAG, "║ Display Name: " + holder.getCallerDisplayName());
            Log.d(TAG, "╚═══════════════════════════════════════════════════");
            Log.d(TAG, "");

            // ═══════════════════════════════════════════════════════════
            // BƯỚC 8: GỬI BROADCAST
            // ═══════════════════════════════════════════════════════════
            sendCallDetailsBroadcast(holder);
            Log.d(TAG, "✅ Broadcast sent successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing call details", e);
            e.printStackTrace();
        }
    }
    private void sendCallDetailsBroadcast(CallDetailsHolder holder) {
        Intent intent = new Intent("cmc.cs.honeypot01.CALL_DETAILS");
        intent.putExtra("phone_number", holder.getPhoneNumber());
        intent.putExtra("verification_status", holder.getVerificationStatus());
        intent.putExtra("handle_presentation", holder.getHandlePresentation());
        intent.putExtra("caller_display_name", holder.getCallerDisplayName());
        intent.putExtra("sim_slot_info", holder.getSimSlotInfo());
        intent.putExtra("subscription_id", holder.getSubscriptionId());

        sendBroadcast(intent);
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