package cmc.cs.honeypot01;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * DialerActivity - Xử lý tất cả dial/call intents
 * Samsung yêu cầu phải có activity riêng để handle dial actions
 */
public class DialerActivity extends Activity {

    private static final String TAG = "DialerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        Log.d(TAG, "DialerActivity launched");
        Log.d(TAG, "Action: " + action);
        Log.d(TAG, "Data: " + data);

        // Chỉ redirect về MainActivity, không xử lý cuộc gọi
        // InCallService sẽ tự động handle incoming calls
        Intent mainIntent = new Intent(this, MainActivity.class);
        if (data != null) {
            mainIntent.setData(data);
        }
        mainIntent.setAction(action);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(mainIntent);
        finish();
    }
}