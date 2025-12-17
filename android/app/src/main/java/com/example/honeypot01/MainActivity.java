package com.example.honeypot01;

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

import com.example.honeypot01.service.AutoReceiveSpamService;

import io.flutter.embedding.android.FlutterActivity;

public class MainActivity extends FlutterActivity {

    private static final String TAG = "MainActivity";

    // Request codes
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_CALL_SCREENING = 101;
    private static final int REQUEST_ALL_FILES_ACCESS = 102;

    private Handler handler;

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

        if (!hasDangerousPermissions()) {
            requestDangerousPermissions();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess()) {
            requestAllFilesAccess();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasCallScreeningRole()) {
            requestCallScreeningRole();
        } else {
            startService();
        }
    }

    private void logAllPermissions() {
        Log.d(TAG, "=== PERMISSION STATUS ===");
        Log.d(TAG, (hasPermission(Manifest.permission.READ_PHONE_STATE) ? "ok " : "miss ") + " READ_PHONE_STATE");
        Log.d(TAG, (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS) ? "ok " : "miss ") + " ANSWER_PHONE_CALLS");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ? "ok " : "miss ") + " READ_MEDIA_AUDIO");
        } else {
            Log.d(TAG, (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ? "ok " : "miss ") + " READ_EXTERNAL_STORAGE");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, (hasAllFilesAccess() ? "ok " : "miss ") + " ALL_FILES_ACCESS");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, (hasCallScreeningRole() ? "ok " : "miss ") + " CALL_SCREENING_ROLE");
        }

        Log.d(TAG, "========================");
    }

    // ═══════════════════════════════════════════════════════════════════
    // DANGEROUS PERMISSIONS
    // ═══════════════════════════════════════════════════════════════════

    private boolean hasDangerousPermissions() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return false;
        if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void requestDangerousPermissions() {
        Log.d(TAG, "Requesting dangerous permissions...");

        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_CALL_LOG
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
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
    // CALL SCREENING ROLE
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
        Log.d(TAG, "ALL PERMISSIONS OK - Starting service...");

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
                Log.d(TAG, (granted ? "ok " : "miss ") + " " + permissions[i]);
                if (!granted) allGranted = false;
            }

            if (allGranted) {
                Log.d(TAG, "All dangerous permissions granted");
                checkPermissions();
            } else {
                Log.e(TAG, "Some permissions denied");
                Toast.makeText(this, "App needs all permissions to work", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ALL_FILES_ACCESS) {
            handler.postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) {
                    Log.d(TAG, "All Files Access granted");
                } else {
                    Log.w(TAG, "⚠️ All Files Access not granted");
                }
                checkPermissions();
            }, 500);

        } else if (requestCode == REQUEST_CALL_SCREENING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasCallScreeningRole()) {
                    Log.d(TAG, "Call Screening Role granted");
                } else {
                    Log.e(TAG, "Call Screening Role denied");
                }
            }
            checkPermissions();
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