package cmc.cs.honeypot01;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

/**
 * MainActivity - Simplified for InCallService
 *
 * XÓA BỎ:
 * - ANSWER_PHONE_CALLS permission (không cần, dùng call.answer())
 * - READ_PHONE_NUMBERS permission (không cần)
 * - READ_SMS permission (không cần)
 * - CALL_SCREENING role (không dùng CallScreeningService nữa)
 * - Start service logic (không có service nào cần start)
 */
public class MainActivity extends FlutterActivity {

    private static final String TAG = "MainActivity";
    private static final String CHANNEL = "cmc.cs.honeypot01/permissions";

    // Request codes - CHỈ GIỮ NHỮNG GÌ CẦN
    private static final int REQUEST_READ_PHONE_STATE = 101;
    private static final int REQUEST_READ_CALL_LOG = 103;
    private static final int REQUEST_STORAGE_ACCESS = 104;
    private static final int REQUEST_ALL_FILES_ACCESS = 106;
    private static final int REQUEST_DEFAULT_DIALER = 109;

    private Handler handler;
    private MethodChannel.Result pendingResult;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        // ═══════════════════════════════════════════════
                        // DEFAULT DIALER
                        // ═══════════════════════════════════════════════
                        case "isDefaultDialer":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                result.success(isDefaultDialer());
                            } else {
                                result.success(false);
                            }
                            break;

                        case "requestDefaultDialer":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                pendingResult = result;
                                requestDefaultDialer();
                            } else {
                                result.success(false);
                            }
                            break;

                        // ═══════════════════════════════════════════════
                        // CORE PERMISSIONS
                        // ═══════════════════════════════════════════════
                        case "hasReadPhoneState":
                            result.success(hasPermission(Manifest.permission.READ_PHONE_STATE));
                            break;

                        case "requestReadPhoneState":
                            requestPermission(Manifest.permission.READ_PHONE_STATE, REQUEST_READ_PHONE_STATE);
                            result.success(null);
                            break;

                        case "hasReadCallLog":
                            result.success(hasPermission(Manifest.permission.READ_CALL_LOG));
                            break;

                        case "requestReadCallLog":
                            requestPermission(Manifest.permission.READ_CALL_LOG, REQUEST_READ_CALL_LOG);
                            result.success(null);
                            break;

                        // ═══════════════════════════════════════════════
                        // STORAGE PERMISSIONS
                        // ═══════════════════════════════════════════════
                        case "hasStorageAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                result.success(hasPermission(Manifest.permission.READ_MEDIA_AUDIO));
                            } else {
                                result.success(hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
                            }
                            break;

                        case "requestStorageAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermission(Manifest.permission.READ_MEDIA_AUDIO, REQUEST_STORAGE_ACCESS);
                            } else {
                                requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_STORAGE_ACCESS);
                            }
                            result.success(null);
                            break;

                        case "hasAllFilesAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                result.success(hasAllFilesAccess());
                            } else {
                                result.success(true);
                            }
                            break;

                        case "requestAllFilesAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                requestAllFilesAccess();
                            }
                            result.success(null);
                            break;

                        default:
                            result.notImplemented();
                            break;
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();

        Log.d(TAG, "=== APP STARTED ===");
        logStatus();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFAULT DIALER
    // ═══════════════════════════════════════════════════════════════════

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isDefaultDialer() {
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            String defaultDialer = telecomManager.getDefaultDialerPackage();
            return getPackageName().equals(defaultDialer);
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestDefaultDialer() {
        Log.d(TAG, "📞 Requesting Default Dialer...");

        if (isDefaultDialer()) {
            Log.d(TAG, "✅ Already default dialer");
            if (pendingResult != null) {
                pendingResult.success(true);
                pendingResult = null;
            }
            return;
        }

        Toast.makeText(this,
                "Please select this app as default phone app",
                Toast.LENGTH_LONG).show();

        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
        startActivityForResult(intent, REQUEST_DEFAULT_DIALER);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ═══════════════════════════════════════════════════════════════════

    private void requestPermission(String permission, int requestCode) {
        Log.d(TAG, "📱 Requesting: " + permission);
        ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALL FILES ACCESS (Android 11+)
    // ═══════════════════════════════════════════════════════════════════

    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean hasAllFilesAccess() {
        return Environment.isExternalStorageManager();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestAllFilesAccess() {
        Log.d(TAG, "📁 Requesting All Files Access...");

        Toast.makeText(this,
                "Grant 'All files access' to read call recordings",
                Toast.LENGTH_LONG).show();

        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_ALL_FILES_ACCESS);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open settings", e);
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, REQUEST_ALL_FILES_ACCESS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

        String permName = permissions.length > 0 ? permissions[0] : "unknown";
        Log.d(TAG, (granted ? "✅ " : "❌ ") + permName);

        handler.postDelayed(this::logStatus, 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DEFAULT_DIALER) {
            handler.postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean isDefault = isDefaultDialer();

                    if (isDefault) {
                        Log.d(TAG, "✅ Default Dialer granted");
                        Toast.makeText(this,
                                "✅ Set as default phone app",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "❌ Default Dialer denied");
                        Toast.makeText(this,
                                "❌ Not set as default. SIM detection may not work.",
                                Toast.LENGTH_LONG).show();
                    }

                    if (pendingResult != null) {
                        pendingResult.success(isDefault);
                        pendingResult = null;
                    }
                }

                logStatus();
            }, 500);
        }
        else if (requestCode == REQUEST_ALL_FILES_ACCESS) {
            handler.postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    boolean granted = hasAllFilesAccess();
                    Log.d(TAG, (granted ? "✅" : "❌") + " All Files Access");
                }
                logStatus();
            }, 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(this::logStatus, 500);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS LOGGING
    // ═══════════════════════════════════════════════════════════════════

    private void logStatus() {
        Log.d(TAG, "");
        Log.d(TAG, "╔════════════════════════════════════════");
        Log.d(TAG, "║ PERMISSION STATUS");
        Log.d(TAG, "╠════════════════════════════════════════");

        // Default Dialer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "║ " + (isDefaultDialer() ? "✅" : "❌") + " Default Dialer");
        }

        // Core permissions
        Log.d(TAG, "║ " + (hasPermission(Manifest.permission.READ_PHONE_STATE) ? "✅" : "❌")
                + " READ_PHONE_STATE");
        Log.d(TAG, "║ " + (hasPermission(Manifest.permission.READ_CALL_LOG) ? "✅" : "❌")
                + " READ_CALL_LOG");

        // Storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "║ " + (hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ? "✅" : "❌")
                    + " READ_MEDIA_AUDIO");
        } else {
            Log.d(TAG, "║ " + (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ? "✅" : "❌")
                    + " READ_EXTERNAL_STORAGE");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "║ " + (hasAllFilesAccess() ? "✅" : "❌") + " ALL_FILES_ACCESS");
        }

        Log.d(TAG, "╚════════════════════════════════════════");
        Log.d(TAG, "");
    }
}