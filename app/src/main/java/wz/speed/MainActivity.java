package wz.notifi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;

import wz.notifi.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);


        Log.d(TAG, "onCreate: 注册广播");
        // 注册广播
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE_ACTION"));
        initActionView();
    }

    public void startGps(boolean isOpen) {
        // 获取 LocationManager 实例
        LocationManager locationManager = (LocationManager) getSystemService(this.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isGpsEnabled) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启GPS")
                    .setMessage("请前往设置中启用定位服务")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }

        if (isOpen) {
            binding.speedText.setText("开始");
            // 请求权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        PERMISSION_REQUEST_CODE);

                Log.d(TAG, "onCreate: 请求权限");
            } else {
                Log.d(TAG, "onCreate: 开始服务");
                startLocationService();
            }

        } else {
            binding.speedText.setText("结束");

        }
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
                startGps(isOpen);
                if (isOpen) {

                    if (connectDevice == null) initBluetooth();
                    if (connectDevice != null) connectToDevice(connectDevice);
                }
                Toast.makeText(context, "蓝牙下发: " + (isOpen ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
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

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService();
            }
        }
    }

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);
        Log.d(TAG, "onReceive: 开启服务");

    }

    // 在 Activity 中注册广播接收器
    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String speedStr = intent.getStringExtra("speed");
            binding.speedText.setText(speedStr);
            Log.d(TAG, "onReceive:速度" + speedStr + "km/h");

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
        disconnect();
    }
}