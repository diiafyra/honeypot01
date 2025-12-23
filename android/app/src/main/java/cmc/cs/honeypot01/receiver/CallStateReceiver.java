package cmc.cs.honeypot01.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import cmc.cs.honeypot01.model.CallDetailsHolder;
import cmc.cs.honeypot01.service.AutoReceiveSpamService;

public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "📡 Broadcast received: " + action);

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            // Outgoing call
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Log.d(TAG, "📞 Outgoing call to: " + phoneNumber);
            return;
        }

        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            Log.d(TAG, "📞 Phone state changed: " + state + ", number: " + phoneNumber);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) && phoneNumber != null && !phoneNumber.isEmpty()) {
                Log.d(TAG, "🔔 RINGING - Phone number from broadcast: " + phoneNumber);

                // Create call details holder as backup
                CallDetailsHolder holder = new CallDetailsHolder();
                holder.setPhoneNumber(phoneNumber);
                holder.setVerificationStatus("UNKNOWN");
                holder.setHandlePresentation("ALLOWED");
                holder.setCallerDisplayName("");

                // Set as pending call details
                AutoReceiveSpamService.setPendingCallDetails(holder);
                // NEW: Notify service that number is received
                AutoReceiveSpamService.onNumberReceived(phoneNumber);
                Log.d(TAG, "✅ Backup call details set from broadcast receiver: " + phoneNumber);
            }
        }
    }
}