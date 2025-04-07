package wz.speed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class PermissionUtils implements EasyPermissions.PermissionCallbacks {

    private static final int RC_PERMISSION_REQUEST = 1001;
    private static final int RC_APP_SETTINGS = 1002;

    private final Activity activity;
    private PermissionCallback callback;
    private String[] permissionsToRequest;

    public interface PermissionCallback {
        void onPermissionsGranted();

        void onPermissionsDenied();

        void onPermanentlyDenied();
    }

    public PermissionUtils(Activity activity) {
        this.activity = activity;
    }

    public void requestPermissions(String[] permissions, String rationale, PermissionCallback callback) {
        this.callback = callback;
        this.permissionsToRequest = permissions;
        if (hasPermissions(activity, permissions)) {
            callback.onPermissionsGranted();
        } else {
            EasyPermissions.requestPermissions(activity, rationale, RC_PERMISSION_REQUEST, permissions);
        }
    }

    private boolean hasPermissions(Context context, String[] permissions) {
        return EasyPermissions.hasPermissions(context, permissions);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (requestCode == RC_PERMISSION_REQUEST) {
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (requestCode == RC_PERMISSION_REQUEST) {
            if (EasyPermissions.somePermissionPermanentlyDenied(activity, perms)) {
                showAppSettingsDialog();
                if (callback != null) {
                    callback.onPermanentlyDenied();
                }
            } else {
                if (callback != null) {
                    callback.onPermissionsDenied();
                }
            }
        }
    }

    private void showAppSettingsDialog() {
        new AppSettingsDialog.Builder(activity)
                .setTitle("权限设置")
                .setRationale("需要手动授予权限才能使用完整功能")
                .setPositiveButton("去设置")
                .setNegativeButton("取消")
                .build()
                .show();
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RC_APP_SETTINGS) {
            if (hasPermissions(activity, permissionsToRequest)) {
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            } else {
                if (callback != null) {
                    callback.onPermissionsDenied();
                }
            }
        }
    }
}