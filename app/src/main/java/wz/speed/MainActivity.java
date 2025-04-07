package wz.speed;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;

import pub.devrel.easypermissions.EasyPermissions;
import wz.speed.databinding.ActivityMainBinding;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;
    private String TAG = "MainActivity";

    private ActivityMainBinding binding;

    // UUID 定义
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    private static final String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic targetCharacteristic;

    private volatile String latestSpeedData = "";
    private BluetoothDevice connectDevice = null;
    private final Handler handler = new Handler();
    private Context context;
    private PermissionUtils permissionUtils;

    private int rLocationCheck = R.id.location_gnss;
    //  百度定位
    private LocationClient mClient;
    private BaiduLocationListener baiduLocationListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        context = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        permissionUtils = new PermissionUtils(this);

        Log.d(TAG, "onCreate: 注册广播");
        // 注册广播
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE_ACTION"));

        // 初始化视图监听
        initActionView();

        // 请求普通权限
        String[] permissions = {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
        };


        permissionUtils.requestPermissions(
                permissions,
                "需要定位和蓝牙权限才能使用完整功能",
                new PermissionUtils.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
//                        Toast.makeText(MainActivity.this, "权限授予成功", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionsDenied() {
                        Toast.makeText(MainActivity.this, "权限被拒绝，部分功能无法使用", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermanentlyDenied() {
                        Toast.makeText(MainActivity.this, "请手动授予必要权限", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // 处理蓝牙不可用的情况
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.enable(); // 无用户交互直接开启
            Toast.makeText(this, "无用户交互直接开启", Toast.LENGTH_SHORT).show();
            return;
        }
        startScan();
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        // 扫描设备（实际使用时需要过滤设备）
        bluetoothAdapter.startLeScan(leScanCallback);

        // 10秒后停止扫描
        handler.postDelayed(this::stopScan, 10000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        bluetoothAdapter.stopLeScan(leScanCallback);
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            // 这里需要根据实际设备名称或 MAC 地址过滤目标设备

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "无连接蓝牙权限! ", Toast.LENGTH_SHORT).show();
                return;
            }
            if (device.getName() != null && device.getName().contains("ESP32")) {
                stopScan();
                connectToDevice(device);
                connectDevice = device;
                Toast.makeText(context, "找到设备开始连接 ", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, true, gattCallback);
        Log.d("连接设备", "===>:");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("连接状态变更", "newState===>:" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("连接状态变更", "已连接,开始检查服务");

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("连接权限检查", "没有服务权限");
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 处理断开连接
                Log.d("处理断开连接", "处理断开连接");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(java.util.UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    targetCharacteristic = service.getCharacteristic(
                            java.util.UUID.fromString(CHARACTERISTIC_UUID)
                    );
                    if (targetCharacteristic != null) {
                        // 特征值可用，可以执行读写操作
                        Log.d("特征值检查", "特征值可用，可以执行读写操作");

                        // 检查特征是否可写
                        int properties = targetCharacteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                                (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                            // 特征不支持写入，处理错误
                            return;
                        }

                        // 设置写入类型
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            targetCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        } else {
                            targetCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }
                        writeToCharacteristic("speed88.88");


                    }

                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // 处理特征值变化通知
            byte[] data = characteristic.getValue();
            // 处理接收到的数据
        }
    };

    @SuppressLint("MissingPermission")
    public void writeToCharacteristic(String data) {
        if (targetCharacteristic != null) {
            // 转换字符串为UTF-8字节
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            // 设置特征值并写入
            targetCharacteristic.setValue(bytes);
            boolean success = bluetoothGatt.writeCharacteristic(targetCharacteristic);
            if (!success) Log.d("写入状态", "失败");
            Log.d("写入状态", "成功" + data);
        }
    }


    @SuppressLint("MissingPermission")
    private void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    private void initActionView() {


        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int finalValue = seekBar.getProgress();
                writeToCharacteristic("light" + finalValue);
                Toast.makeText(context, "亮度: " + finalValue, Toast.LENGTH_SHORT).show();
            }
        });

        // 下发 GPS Switch
        binding.switchBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isOpen = binding.switchBle.isChecked();
                LocationManager locationManager = (LocationManager) getSystemService(context.LOCATION_SERVICE);
                boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                Log.d(TAG, "isOpen: " + isOpen);
                if (isOpen) {
                    if (!isGpsEnabled) {
                        new AlertDialog.Builder(context)
                                .setTitle("需要开启GPS")
                                .setMessage("请前往设置中启用定位服务")
                                .setPositiveButton("去设置", (dialog, which) -> {
                                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        return;
                    }
                    binding.speedText.setText("开始");
                    binding.beidoucount.setText("正在搜星...");
                    startLocationService();
                } else {
                    binding.speedText.setText("结束");
                    binding.beidoucount.setText("服务停止");
                    stopLocationService();
                }
            }
        });


        // 设置 RadioGroup 的选中状态监听器
        binding.rgLed.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb_led0:
                        // 选项 1 被选中
                        writeToCharacteristic("led0");
                        break;
                    case R.id.rb_led1:
                        writeToCharacteristic("led1");
                        // 选项 2 被选中
                        break;
                    case R.id.rb_led2:
                        writeToCharacteristic("led2");
                        // 选项 3 被选中
                        break;
                }
            }
        });


        // 设置 RadioGroup 的选中状态监听器
        binding.rLocation.check(rLocationCheck);
        binding.rLocation.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                stopLocationService();
                rLocationCheck = checkedId;
                startLocationService();
            }
        });

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    private void initBaiduLocation() {
        NotificationUtils notificationUtils = new NotificationUtils(this);
        Notification.Builder builder = notificationUtils.getAndroidChannelNotification
                ("适配android 8限制后台定位功能", "正在后台定位");
        Notification mNotification = builder.build();
        mNotification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        try {
            LocationClient.setAgreePrivacy(true);
            // 创建定位客户端
            mClient = new LocationClient(this);
            baiduLocationListener = new BaiduLocationListener();
            // 注册定位监听
            mClient.registerLocationListener(baiduLocationListener);
            LocationClientOption mOption = new LocationClientOption();
            // 可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
            mOption.setScanSpan(1000);
            // 可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
            mOption.setCoorType("bd09ll");
            mOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            mOption.setOpenGnss(true);//  设置是否使用卫星定位，默认false
            mOption.setLocationNotify(true); // 设置是否当卫星定位有效时按照1S/1次频率输出卫星定位结果
            // 设置定位参数
            mClient.setLocOption(mOption);
            mClient.enableLocInForeground(1, mNotification);
            mClient.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void startLocationService() {
        if (connectDevice == null) initBluetooth();
        if (connectDevice != null) connectToDevice(connectDevice);
        String tag = "";
        if (rLocationCheck == R.id.location_gnss) {
            tag = "GNSS";
            Intent serviceIntent = new Intent(this, LocationService.class);
            startService(serviceIntent);
        }
        if (rLocationCheck == R.id.location_baidu) {
            tag = "百度";
            initBaiduLocation();
        }
        Log.d(TAG, "开启服务:" + tag);
    }

    private void stopLocationService() {
        disconnect();
        String tag = "";
        if (rLocationCheck == R.id.location_gnss) {
            tag = "GNSS";
            Intent serviceIntent = new Intent(this, LocationService.class);
            stopService(serviceIntent);
        }
        if (mClient != null && rLocationCheck == R.id.location_baidu) {
            tag = "百度";
            // 关闭前台定位服务
            mClient.disableLocInForeground(true);
            // 取消之前注册的 BDAbstractLocationListener 定位监听函数
            mClient.unRegisterLocationListener(baiduLocationListener);
            // 停止定位sdk
            mClient.stop();
        }
        Log.d(TAG, "onReceive: 停止服务:" + tag);
    }

    // 在 Activity 中注册广播接收器
    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String speedStr = intent.getStringExtra("speed");
            String beidouCount = intent.getStringExtra("beidoucount");
            String type = intent.getStringExtra("type");
            binding.speedText.setText(speedStr);
            binding.beidoucount.setText(beidouCount);
            Log.d(TAG, "onReceive:速度" + speedStr + "km/h," + "type:" + type);

            boolean isOpen = binding.switchBle.isChecked();
            if (isOpen && !latestSpeedData.equals(speedStr)) {
                latestSpeedData = speedStr;
                writeToCharacteristic("speed" + latestSpeedData);
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationService();
    }


    class BaiduLocationListener extends BDAbstractLocationListener {

        // 示例：通过广播传递位置数据
        Intent intent = new Intent("LOCATION_UPDATE_ACTION");

        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null) {
                return;
            }
            DecimalFormat df = new DecimalFormat("0.##");
            String speedStr = df.format(location.getSpeed());
            intent.putExtra("latitude", location.getLatitude());
            intent.putExtra("longitude", location.getLongitude());
            intent.putExtra("speed", speedStr);
            intent.putExtra("type", "百度");
            sendBroadcast(intent);
        }
    }
}