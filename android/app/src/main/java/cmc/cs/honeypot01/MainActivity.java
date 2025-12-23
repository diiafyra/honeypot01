package cmc.cs.honeypot01;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import cmc.cs.honeypot01.service.AutoReceiveSpamService;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {

    private static final String TAG = "MainActivity";
    private static final String CHANNEL = "cmc.cs.honeypot01/permissions";

    // Request codes
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_READ_PHONE_STATE = 101;
    private static final int REQUEST_ANSWER_PHONE_CALLS = 102;
    private static final int REQUEST_READ_CALL_LOG = 103;
    private static final int REQUEST_STORAGE_ACCESS = 104;
    private static final int REQUEST_CALL_SCREENING = 101;
    private static final int REQUEST_ALL_FILES_ACCESS = 102;

    private Handler handler;
    private MethodChannel.Result pendingResult;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        case "hasCallScreeningRole":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                result.success(hasCallScreeningRole());
                            } else {
                                result.success(true); // Not needed for older versions
                            }
                            break;
                        case "requestCallScreeningRole":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                pendingResult = result;
                                requestCallScreeningRole();
                            } else {
                                result.success(true);
                            }
                            break;
                        case "hasAllFilesAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                result.success(hasAllFilesAccess());
                            } else {
                                result.success(true); // Not needed for older versions
                            }
                            break;
                        case "requestAllFilesAccess":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                requestAllFilesAccess();
                                result.success(null);
                            } else {
                                result.success(true);
                            }
                            break;
                        case "hasReadPhoneState":
                            result.success(hasPermission(Manifest.permission.READ_PHONE_STATE));
                            break;
                        case "requestReadPhoneState":
                            requestPermission(Manifest.permission.READ_PHONE_STATE, REQUEST_READ_PHONE_STATE);
                            result.success(null);
                            break;
                        case "hasAnswerPhoneCalls":
                            result.success(hasPermission(Manifest.permission.ANSWER_PHONE_CALLS));
                            break;
                        case "requestAnswerPhoneCalls":
                            requestPermission(Manifest.permission.ANSWER_PHONE_CALLS, REQUEST_ANSWER_PHONE_CALLS);
                            result.success(null);
                            break;
                        case "hasReadCallLog":
                            result.success(hasPermission(Manifest.permission.READ_CALL_LOG));
                            break;
                        case "requestReadCallLog":
                            requestPermission(Manifest.permission.READ_CALL_LOG, REQUEST_READ_CALL_LOG);
                            result.success(null);
                            break;
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
                        case "forceCheckCallScreening":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                boolean hasRole = hasCallScreeningRole();
                                Log.d(TAG, "🔍 Force check Call Screening role: " + hasRole);
                                result.success(hasRole);
                            } else {
                                result.success(true);
                            }
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
        checkPermissions();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHECK PERMISSIONS
    // ═══════════════════════════════════════════════════════════════════

    private void checkPermissions() {
        logAllPermissions();

        if (hasDangerousPermissions() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || hasAllFilesAccess()) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasCallScreeningRole())) {
            startService();
        } else {
            Log.d(TAG, "Permissions not granted, waiting for user to grant via UI");
        }
    }

    private void logAllPermissions() {
        Log.d(TAG, "=== PERMISSION STATUS ===");
        Log.d(TAG, (hasPermission(Manifest.permission.READ_PHONE_STATE) ? "✓ " : "✗ ") + "READ_PHONE_STATE");
        Log.d(TAG, (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS) ? "✓ " : "✗ ") + "ANSWER_PHONE_CALLS");
        Log.d(TAG, (hasPermission(Manifest.permission.READ_CALL_LOG) ? "✓ " : "✗ ") + "READ_CALL_LOG");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ? "✓ " : "✗ ") + "READ_MEDIA_AUDIO");
        } else {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ? "✓ " : "✗ ") + "READ_EXTERNAL_STORAGE");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, (hasAllFilesAccess() ? "✓ " : "✗ ") + "ALL_FILES_ACCESS");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, (hasCallScreeningRole() ? "✓ " : "✗ ") + "CALL_SCREENING_ROLE");
        }

        Log.d(TAG, "========================");
    }

    // ═══════════════════════════════════════════════════════════════════
    // DANGEROUS PERMISSIONS (Phone)
    // ═══════════════════════════════════════════════════════════════════

    private boolean hasDangerousPermissions() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return false;
        if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return false;
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void requestDangerousPermissions() {
        Log.d(TAG, "📱 Requesting phone permissions...");

        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
    }

    private void requestPermission(String permission, int requestCode) {
        Log.d(TAG, "📱 Requesting permission: " + permission);
        ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
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
            Log.e(TAG, "Cannot open All Files Access settings", e);
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, REQUEST_ALL_FILES_ACCESS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALL SCREENING ROLE (Android 10+)
    // ═══════════════════════════════════════════════════════════════════

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean hasCallScreeningRole() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void requestCallScreeningRole() {
        Log.d(TAG, "🔍 Requesting Call Screening Role...");

        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (roleManager != null) {
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQUEST_CALL_SCREENING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // START SERVICE
    // ═══════════════════════════════════════════════════════════════════

    private void startService() {
        Log.d(TAG, "✅ ALL PERMISSIONS OK - Starting service...");

        Intent intent = new Intent(this, AutoReceiveSpamService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, (granted ? "✓ " : "✗ ") + permissions[i]);
                if (!granted) allGranted = false;
            }

            if (allGranted) {
                Log.d(TAG, "✅ All phone permissions granted");
                checkPermissions();
            } else {
                Log.e(TAG, "❌ Some permissions denied");
                Toast.makeText(this, "App needs all permissions to work", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_READ_PHONE_STATE ||
                   requestCode == REQUEST_ANSWER_PHONE_CALLS ||
                   requestCode == REQUEST_READ_CALL_LOG ||
                   requestCode == REQUEST_STORAGE_ACCESS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, (granted ? "✓ " : "✗ ") + "Permission granted for request code: " + requestCode);
            // Check if all permissions are now granted
            handler.postDelayed(() -> checkPermissions(), 1000); // Delay to allow UI to update
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ALL_FILES_ACCESS) {
            handler.postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) {
                    Log.d(TAG, "✅ All Files Access granted");
                } else {
                    Log.w(TAG, "⚠️ All Files Access not granted");
                }
                checkPermissions();
            }, 500);

        } else if (requestCode == REQUEST_CALL_SCREENING) {
            handler.postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    boolean granted = hasCallScreeningRole();
                    if (granted) {
                        Log.d(TAG, "✅ Call Screening Role granted");
                    } else {
                        Log.e(TAG, "❌ Call Screening Role denied");
                    }
                    if (pendingResult != null) {
                        pendingResult.success(granted);
                        pendingResult = null;
                    }
                }
                checkPermissions();
            }, 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(this::logAllPermissions, 500);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}