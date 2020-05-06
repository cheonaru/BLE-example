package techtown.org;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    //// GUI variables
    // text view for status
    private TextView mTextViewStatus;
    // text view for read
    private TextView mTextViewRead;
    // button for start scan
    private Button mBtnStartScan;
    // button for stop scan
    private Button mBtnStopScan;
    // button for stop scan
    private Button mBtnStopConn;
    // button for show paired devices

    // Tag name for Log message
    private final static String TAG = "Central";
    // used to identify adding bluetooth names
    private final static int REQUEST_ENABLE_BT = 1;
    // used to request fine location permission
    private final static int REQUEST_FINE_LOCATION = 2;
    // scan period in milliseconds
    private final static int SCAN_PERIOD = 5000;
    // ble adapter
    private BluetoothAdapter mBluetoothAdapter;
    // flag for scanning
    private boolean isScanning = false;
    // flag for connection
    private boolean connected_ = false;
    // scan results
    private Map<String, BluetoothDevice> mScanResult;
    // scan callback
    private ScanCallback mScanCallback;
    // ble scanner
    private BluetoothLeScanner mBluetoothLeScanner;
    // scan handler
    private Handler scanHandler;
    private Handler linkHandler;

    public static String SERVICE_STRING = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_TDCS_SERVICE = UUID.fromString(SERVICE_STRING);
    public static String CHARACTERISTIC_COMMAND_STRING = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_CTRL_COMMAND = UUID.fromString(CHARACTERISTIC_COMMAND_STRING);
    public static String CHARACTERISTIC_RESPONSE_STRING = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_CTRL_RESPONSE = UUID.fromString(CHARACTERISTIC_RESPONSE_STRING);
    public final static String MAC_ADDR = "E7:34:CC:2B:4A:7F";
    public static String CLIENT_CONFIGURATION_DESCRIPTOR_STRING = "00002902-0000-1000-8000-00805f9b34fb";
    public static UUID CLIENT_CONFIGURATION_DESCRIPTOR_UUID = UUID.fromString(CLIENT_CONFIGURATION_DESCRIPTOR_STRING);

    public static final String CLIENT_CONFIGURATION_DESCRIPTOR_SHORT_ID = "2902";

    private BluetoothGatt mGatt;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ble manager
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        // set ble adapter
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //// get instances of gui objects
        // status textview
        mTextViewStatus = findViewById(R.id.tv_status);
        // read textview
        mTextViewRead = findViewById(R.id.tv_read);
        // start scan button
        mBtnStartScan = findViewById(R.id.btn_start_scan);
        // stop scan button
        mBtnStopScan = findViewById(R.id.btn_stop_scan);
        // stop conn button
        mBtnStopConn = findViewById(R.id.btn_stop_conn);

        //// set click event handler
        mBtnStartScan.setOnClickListener((v) -> {
            startScan(v);
        });
        //// set click event handler
        mBtnStopScan.setOnClickListener((v) -> {
            stopScan();
        });
        mBtnStopScan.setOnClickListener((v) -> {
            stopScan();
        });

        // finish app if the BLE is not supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check device supports ble
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }
    }

    /*
            Start BLE scan
             */
    private void startScan(View v) {
        mTextViewStatus.setText("Scanning...");
        // check ble adapter and ble enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestEnableBLE();
            mTextViewStatus.setText("Scanning Failed: ble not enabled");
            return;
        }
        // check if location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            mTextViewStatus.setText("Scanning Failed: no fine location permission");
            return;
        }

        new GattClientCallback().disconnectGattServer();

/*        // *****scan filter BY UUID*****
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter scan_filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID_TDCS_SERVICE))
                .build();
        filters.add(scan_filter);*/


        // *****scan filter BY MAC*****
        List<ScanFilter> filters = new ArrayList<>();
        // create a scan filter with device mac address
        ScanFilter scan_filter = new ScanFilter.Builder()
                .setDeviceAddress(MAC_ADDR)
                .build();
        // add the filter to the list
        filters.add(scan_filter);


        //// scan settings
        // set low power scan mode
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mScanResult = new HashMap<>();
        mScanCallback = new BleScanCallback(mScanResult);


        //// now ready to scan
        // start scan
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        // set scanning flag
        isScanning = true;
        scanHandler = new Handler();
        scanHandler.postDelayed(this::stopScan, SCAN_PERIOD);
    }

    private void stopScan() {
        if (isScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }
        mScanCallback = null;
        isScanning = false;
        scanHandler = null;
    }

    private void stopConn() {
        new GattClientCallback().disconnectGattServer();
    }

    private void scanComplete() {
        if (mScanResult.isEmpty()) {
            return;
        }
        for (String deviceAddress : mScanResult.keySet()) {
            Log.d(TAG, "Found device: " + deviceAddress);
        }
    }

    /*
    Request BLE enable
    */
    private void requestEnableBLE() {
        Intent ble_enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(ble_enable_intent, REQUEST_ENABLE_BT);

    }

    /*
    Request Fine Location permission
     */
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    // BLE Scan Callback class
    private class BleScanCallback extends ScanCallback {
        private Map<String, BluetoothDevice> mScanResult;

        /*
        Constructor
         */
        BleScanCallback(Map<String, BluetoothDevice> _scan_results) {
            mScanResult = _scan_results;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed with code " + errorCode);
        }

        /*
        Add scan result
         */
        private void addScanResult(ScanResult result) {
            // get scanned device
            BluetoothDevice bleDevice = result.getDevice();
            // get scanned device MAC address
            String deviceAddress = bleDevice.getAddress();
            // add the device to the result list
            mScanResult.put(deviceAddress, bleDevice);
            // log
            Log.d(TAG, "scan results device: " + bleDevice);
            mTextViewStatus.setText("add scanned device: " + deviceAddress);

            connectDevice(bleDevice);
        }
    }

    private void connectDevice(BluetoothDevice bleDevice) {
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = bleDevice.connectGatt(this, false, gattClientCallback);
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }

        public void disconnectGattServer() {
            isConnected = false;
            if (mGatt != null) {
                mGatt.disconnect();
                mGatt.close();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "Characteristic changed, " + characteristic.getUuid().toString());
            readCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, Integer.toString(status));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic read successfully");
                readCharacteristic(characteristic);
            } else {
                Log.d(TAG, "Characteristic read unsuccessful, status: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Device service discovery unsuccessful, status " + status);
                return;
            }

            List<BluetoothGattCharacteristic> matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt);
            if (matchingCharacteristics.isEmpty()) {
                Log.d(TAG, "Unable to find characteristics.");
                return;
            }

            Log.d(TAG, "Initializing: setting write type and enabling notification");
            for (BluetoothGattCharacteristic characteristic : matchingCharacteristics) {
                readCharacteristic(characteristic);
/*                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                enableCharacteristicNotification(gatt, characteristic);*/
                //////////

            }
        }

        private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            List<BluetoothGattDescriptor> descriptorList = characteristic.getDescriptors();
            BluetoothGattDescriptor descriptor = BluetoothUtils.findClientConfigurationDescriptor(descriptorList);
            if (descriptor == null) {
                Log.d(TAG, "Unable to find Characteristic Configuration Descriptor");
                return;
            }

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean descriptorWriteInitiated = gatt.writeDescriptor(descriptor);
            if (descriptorWriteInitiated) {
                Log.d(TAG, "Characteristic Configuration Descriptor write initiated: " + descriptor.getUuid().toString());
            } else {
                Log.d(TAG, "Characteristic Configuration Descriptor write failed to initiate: " + descriptor.getUuid().toString());
            }
        }

        private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
            byte[] messageBytes = characteristic.getValue();
            String message = null;
            try {
                message = new String(messageBytes, "UTF-8");
                Log.d(TAG, "Received message: " + message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Unable to convert message bytes to string");
                e.printStackTrace();
            }
        }
    }

/*    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
        }
    };*/
}
