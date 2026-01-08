package cmc.cs.honeypot01.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * BroadcastReceiver để bắt incoming call từ system
 * Dùng làm fallback nếu CallScreeningService không hoạt động
 */
public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "📡 Broadcast received: " + action);

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            // Outgoing call - ignore
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Log.d(TAG, "📞 Outgoing call to: " + phoneNumber);
            return;
        }

        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            Log.d(TAG, "📞 Phone state: " + state + ", number: " + phoneNumber);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) &&
                    phoneNumber != null && !phoneNumber.isEmpty()) {

                Log.d(TAG, "🔔 RINGING - Sending number via broadcast: " + phoneNumber);

                // Gửi call details qua broadcast (như CallScreeningService)
                sendCallDetailsBroadcast(context, phoneNumber);

                Log.d(TAG, "✅ Broadcast sent from CallStateReceiver: " + phoneNumber);
            }
        }
    }

    /**
     * Gửi call details qua broadcast để AutoReceiveSpamService nhận
     * Dùng cùng format với CallScreeningService
     */
    private void sendCallDetailsBroadcast(Context context, String phoneNumber) {
        Intent broadcastIntent = new Intent("cmc.cs.honeypot01.CALL_DETAILS");
        broadcastIntent.putExtra("phone_number", phoneNumber);
        broadcastIntent.putExtra("verification_status", "UNKNOWN");
        broadcastIntent.putExtra("handle_presentation", "ALLOWED");
        broadcastIntent.putExtra("caller_display_name", "");

        context.sendBroadcast(broadcastIntent);
        Log.d(TAG, "Call details broadcast sent");
    }
}