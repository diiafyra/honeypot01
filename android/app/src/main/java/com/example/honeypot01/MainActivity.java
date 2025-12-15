package com.example.honeypot01;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.embedding.android.FlutterActivity;

public class MainActivity extends FlutterActivity {

    private static final String TAG = "MainActivity";

    // Request codes
    private static final int REQUEST_DANGEROUS_PERMISSIONS = 100;
    private static final int REQUEST_CALL_SCREENING_ROLE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra và xin permissions
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        Log.d(TAG, "=== CHECKING ALL PERMISSIONS ===");
        logPermissionStatus();

        requestDangerousPermissions();
    }

    private void logPermissionStatus() {
        Log.d(TAG, (hasPermission(Manifest.permission.READ_PHONE_STATE) ? "✅" : "❌") + " READ_PHONE_STATE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ? "✅" : "❌") + " READ_MEDIA_AUDIO");
        } else {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ? "✅" : "❌") + " READ_EXTERNAL_STORAGE");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS) ? "✅" : "❌") + " ANSWER_PHONE_CALLS");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, (isCallScreeningApp() ? "✅" : "❌") + " CALL_SCREENING_ROLE");
        }

        Log.d(TAG, (isAccessibilityServiceEnabled() ? "✅" : "❌") + " ACCESSIBILITY_SERVICE");
        Log.d(TAG, "================================");
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean isCallScreeningApp() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/.service.AutoReceiveSpamService";
        try {
            int accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                return settingValue != null && settingValue.toLowerCase().contains(service.toLowerCase());
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Accessibility setting not found", e);
        }
        return false;
    }

    /** ==================== REQUEST PERMISSIONS ==================== */

    /**
     * Xin tất cả dangerous permissions 1 lần duy nhất
     */
    private void requestDangerousPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        // Lọc những permission chưa có
        permissions = filterUngrantedPermissions(permissions);

        if (permissions.length > 0) {
            Log.d(TAG, "📱 Requesting dangerous permissions...");
            ActivityCompat.requestPermissions(this, permissions, REQUEST_DANGEROUS_PERMISSIONS);
        } else {
            Log.d(TAG, "✅ All dangerous permissions already granted");
            requestCallScreeningRole();
        }
    }

    private String[] filterUngrantedPermissions(String[] permissions) {
        return java.util.Arrays.stream(permissions)
                .filter(p -> !hasPermission(p))
                .toArray(String[]::new);
    }

    /**
     * Xin Call Screening Role (special/system-level)
     */
    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!isCallScreeningApp()) {
                Log.d(TAG, "🔍 Requesting Call Screening Role...");
                RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                    startActivityForResult(intent, REQUEST_CALL_SCREENING_ROLE);
                } else {
                    Log.e(TAG, "❌ RoleManager not available");
                    openAccessibilitySettings();
                }
            } else {
                Log.d(TAG, "✅ Call Screening Role already granted");
                openAccessibilitySettings();
            }
        } else {
            openAccessibilitySettings();
        }
    }

    private void openAccessibilitySettings() {
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "♿ Opening Accessibility Settings...");
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "Please enable 'Auto Receive Spam Service'", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Cannot open Accessibility Settings", e);
            }
        } else {
            Log.d(TAG, "✅ Accessibility Service already enabled");
            Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show();
        }
    }

    /** ==================== CALLBACKS ==================== */

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_DANGEROUS_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "✅ All dangerous permissions granted");
                Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "❌ Some dangerous permissions denied");
                Toast.makeText(this, "❌ Some permissions denied - app may not work properly", Toast.LENGTH_LONG).show();
            }

            // Dù granted hay không, tiếp tục flow special permissions
            requestCallScreeningRole();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CALL_SCREENING_ROLE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isCallScreeningApp()) {
                Log.d(TAG, "✅ Call Screening Role GRANTED");
                Toast.makeText(this, "✅ Call Screening Role granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "❌ Call Screening Role DENIED");
                Toast.makeText(this, "❌ Call Screening Role denied", Toast.LENGTH_LONG).show();
            }

            // Mở Accessibility Settings cuối cùng
            openAccessibilitySettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        logPermissionStatus();
    }
}
