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

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;

import java.text.DecimalFormat;
import java.util.concurrent.Executor;

public class GnssService extends Service {
    private String TAG = "GnssService";
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GnssStatus.Callback gnssStatusCallback;

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
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        Log.d(TAG, "北斗Location: Latitude = " + location.getLatitude() + ", Longitude = " + location.getLongitude());
                        Log.d(TAG, "北斗速度 = " + location.getSpeed());
                        DecimalFormat df = new DecimalFormat("0.##");
                        String speedStr = df.format(location.getSpeed());
                        intent.putExtra("latitude", location.getLatitude());
                        intent.putExtra("longitude", location.getLongitude());
                        intent.putExtra("speed", speedStr);
                        intent.putExtra("type", "北斗");
                        sendBroadcast(intent);
                    }
                }
            };


            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            // 创建一个在主线程执行的 Executor
            Executor executor = new Executor() {
                private final Handler handler = new Handler(Looper.getMainLooper());

                @Override
                public void execute(Runnable command) {
                    handler.post(command);
                }
            };

            try {
                LocationRequest.Builder builder = new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, // 定位优先级
                        1000L // 更新间隔，单位为毫秒
                );
                builder
                        // 设置最快更新间隔，单位为毫秒
                        .setMinUpdateIntervalMillis(500L)
                        // 设置最大更新延迟，单位为毫秒
                        .setMaxUpdateDelayMillis(2000L)
                        // 设置定位请求的持续时间，单位为毫秒
                        .setDurationMillis(30000L)
                        // 设置定位请求的最大更新次数
                        .setMaxUpdates(10);

                LocationRequest locationRequest = builder.build();
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                locationManager.registerGnssStatusCallback(executor, gnssStatusCallback);
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
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
        }
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