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
    private static final int REQUEST_PHONE_PERMISSIONS = 100;
    private static final int REQUEST_ANSWER_CALLS = 101;
    private static final int REQUEST_AUDIO_PERMISSIONS = 103;
    private static final int REQUEST_CALL_SCREENING_ROLE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tự động kiểm tra và xin tất cả permissions
        checkAndRequestAllPermissions();
    }

    /**
     * Kiểm tra và tự động xin tất cả permissions cần thiết
     */
    private void checkAndRequestAllPermissions() {
        Log.d(TAG, "=== CHECKING ALL PERMISSIONS ===");

        // Kiểm tra và log trạng thái hiện tại
        logPermissionStatus();

        // Bắt đầu flow xin permissions
        requestPhonePermissions();
    }

    /**
     * Log trạng thái tất cả permissions
     */
    private void logPermissionStatus() {
        boolean hasReadPhone = hasPermission(Manifest.permission.READ_PHONE_STATE);
        Log.d(TAG, (hasReadPhone ? "✅" : "❌") + " READ_PHONE_STATE");

        // Quyền đọc audio để truy cập thư mục Recordings/Call (phục vụ STT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasReadAudio = hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
            Log.d(TAG, (hasReadAudio ? "✅" : "❌") + " READ_MEDIA_AUDIO");
        } else {
            boolean hasReadStorage = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            Log.d(TAG, (hasReadStorage ? "✅" : "❌") + " READ_EXTERNAL_STORAGE");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean hasAnswerCalls = hasPermission(Manifest.permission.ANSWER_PHONE_CALLS);
            Log.d(TAG, (hasAnswerCalls ? "✅" : "❌") + " ANSWER_PHONE_CALLS");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isCallScreening = isCallScreeningApp();
            Log.d(TAG, (isCallScreening ? "✅" : "❌") + " CALL_SCREENING_ROLE");
        }

        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled();
        Log.d(TAG, (isAccessibilityEnabled ? "✅" : "❌") + " ACCESSIBILITY_SERVICE");

        Log.d(TAG, "================================");
    }

    // ==================== PERMISSION HELPERS ====================

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
            int accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

                if (settingValue != null) {
                    return settingValue.toLowerCase().contains(service.toLowerCase());
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Accessibility setting not found", e);
        }
        return false;
    }

    // ==================== REQUEST PERMISSIONS (THEO THỨ TỰ) ====================

    /**
     * BƯỚC 1: Xin Phone Permissions
     */
    private void requestPhonePermissions() {
        boolean hasReadPhone = hasPermission(Manifest.permission.READ_PHONE_STATE);

        if (!hasReadPhone) {
            Log.d(TAG, "📱 Requesting Phone Permissions...");
            String[] permissions = {
                    Manifest.permission.READ_PHONE_STATE,
            };
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PHONE_PERMISSIONS);
        } else {
            Log.d(TAG, "✅ Phone Permissions already granted");
            // Chuyển sang bước tiếp theo
            requestAnswerCallsPermission();
        }
    }

    /**
     * BƯỚC 2: Xin Answer Phone Calls Permission
     */
    private void requestAnswerCallsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
                Log.d(TAG, "📞 Requesting ANSWER_PHONE_CALLS Permission...");
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ANSWER_PHONE_CALLS},
                        REQUEST_ANSWER_CALLS
                );
            } else {
                Log.d(TAG, "✅ ANSWER_PHONE_CALLS already granted");
                // Chuyển sang bước tiếp theo
                requestAudioReadPermission();
            }
        } else {
            // Android < 8: Không cần permission này
            requestAudioReadPermission();
        }
    }

    /**
     * BƯỚC 2.5: Xin quyền đọc audio (Recordings/Call) để STT đọc file ghi âm
     */
    private void requestAudioReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                Log.d(TAG, "🎧 Requesting READ_MEDIA_AUDIO Permission...");
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        REQUEST_AUDIO_PERMISSIONS
                );
            } else {
                Log.d(TAG, "✅ READ_MEDIA_AUDIO already granted");
                requestCallScreeningRole();
            }
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Log.d(TAG, "🗂️ Requesting READ_EXTERNAL_STORAGE Permission...");
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_AUDIO_PERMISSIONS
                );
            } else {
                Log.d(TAG, "✅ READ_EXTERNAL_STORAGE already granted");
                requestCallScreeningRole();
            }
        }
    }

    /**
     * BƯỚC 3: Xin Call Screening Role
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
                    Toast.makeText(this, "RoleManager not available", Toast.LENGTH_SHORT).show();
                    // Vẫn chuyển sang bước tiếp theo
                    openAccessibilitySettings();
                }
            } else {
                Log.d(TAG, "✅ Call Screening Role already granted");
                // Chuyển sang bước tiếp theo
                openAccessibilitySettings();
            }
        } else {
            // Android < 10: Không hỗ trợ Call Screening
            openAccessibilitySettings();
        }
    }

    /**
     * BƯỚC 4: Mở Accessibility Settings
     */
    private void openAccessibilitySettings() {
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "♿ Opening Accessibility Settings...");
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this,
                        "Please enable 'Auto Receive Spam Service'",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Cannot open Accessibility Settings", e);
                Toast.makeText(this, "Cannot open Accessibility Settings", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "✅ Accessibility Service already enabled");
            showAllPermissionsGranted();
        }
    }

    /**
     * Hiển thị thông báo khi đã có đủ permissions
     */
    private void showAllPermissionsGranted() {
        Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "🎉 ALL PERMISSIONS GRANTED!");
    }

    // ==================== CALLBACKS ====================

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        switch (requestCode) {
            case REQUEST_PHONE_PERMISSIONS:
                if (allGranted) {
                    Log.d(TAG, "✅ Phone Permissions GRANTED");
                    Toast.makeText(this, "✅ Phone permissions granted", Toast.LENGTH_SHORT).show();
                    // Tiếp tục bước tiếp theo
                    requestAnswerCallsPermission();
                } else {
                    Log.e(TAG, "❌ Phone Permissions DENIED");
                    Toast.makeText(this, "❌ Phone permissions denied - App may not work properly", Toast.LENGTH_LONG).show();
                    // Vẫn tiếp tục (user có thể cấp sau)
                    requestAnswerCallsPermission();
                }
                break;

            case REQUEST_ANSWER_CALLS:
                if (allGranted) {
                    Log.d(TAG, "✅ ANSWER_PHONE_CALLS GRANTED");
                    Toast.makeText(this, "✅ Answer calls permission granted", Toast.LENGTH_SHORT).show();
                    // Tiếp tục bước tiếp theo
                    requestAudioReadPermission();
                } else {
                    Log.e(TAG, "❌ ANSWER_PHONE_CALLS DENIED");
                    Toast.makeText(this, "❌ Answer calls permission denied", Toast.LENGTH_LONG).show();
                    // Vẫn tiếp tục
                    requestAudioReadPermission();
                }
                break;

            case REQUEST_AUDIO_PERMISSIONS:
                if (allGranted) {
                    Log.d(TAG, "✅ Audio read permission GRANTED");
                    Toast.makeText(this, "✅ Audio read permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "❌ Audio read permission DENIED");
                    Toast.makeText(this, "❌ Audio read permission denied - STT may not work", Toast.LENGTH_LONG).show();
                }
                // Dù granted hay không, vẫn tiếp tục flow chính
                requestCallScreeningRole();
                break;
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
            // Tiếp tục bước cuối cùng
            openAccessibilitySettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kiểm tra lại và log trạng thái mỗi khi quay về app
        Log.d(TAG, "=== onResume - Checking status ===");
        logPermissionStatus();
    }
}