package wz.speed;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.DecimalFormat;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationService extends Service {
    private String TAG = "GnssService";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        initLocationRequest();
        Log.d(TAG, "onCreate: GnssService服务启动--------------");
    }

    private void initLocationRequest() {
        // 4. 请求位置更新（需先检查权限）
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 示例：通过广播传递位置数据
            Intent intent = new Intent("LOCATION_UPDATE_ACTION");

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            executorService = Executors.newSingleThreadExecutor();
            try {
                LocationRequest.Builder builder = new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, // 定位优先级
                        1000L // 更新间隔，单位为毫秒
                );

                builder.setMinUpdateIntervalMillis(500L);
                // 设置最大更新延迟，单位为毫秒
                builder.setMaxUpdateDelayMillis(1000L);

                LocationRequest locationRequest = builder.build();

                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        for (Location location : locationResult.getLocations()) {
                            // 处理位置更新
                            if (location != null) {
                                Log.d(TAG, "融合Location: Latitude = " + location.getLatitude() + ", Longitude = " + location.getLongitude());
                                Log.d(TAG, "融合速度米/s = " + location.getSpeed());
                                float speed = location.getSpeed() * 3.6f;
                                DecimalFormat df = new DecimalFormat("0.##");
                                String speedStr = df.format(speed);
                                intent.putExtra("latitude", location.getLatitude());
                                intent.putExtra("longitude", location.getLongitude());
                                intent.putExtra("speed", speedStr);
                                intent.putExtra("type", "北斗");
                                sendBroadcast(intent);
                            }
                        }
                    }
                };

                // 请求定位更新
                fusedLocationClient.requestLocationUpdates(locationRequest, executorService, locationCallback);

            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while starting location updates: " + e.getMessage());
            }
            Log.d(TAG, "initLocationRequest: 权限检查通过请求位置更新");
        } else {
            Log.d(TAG, "initLocationRequest: 权限检查未通过");
        }

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
        executorService.shutdown();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationUtils notificationUtils = new NotificationUtils(this);
        Notification.Builder builder = notificationUtils.getAndroidChannelNotification("适配android12限制后台定位功能", "正在后台定位");
        Notification mNotification = builder.build();
        startForeground(1, mNotification);
        return START_STICKY; // 服务被杀死后自动重启
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 若无需绑定，返回 null
    }
}