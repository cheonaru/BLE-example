package techtown.org;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    //// GUI variables
    // text view for status
    private TextView tv_status_;
    // text view for read
    private TextView tv_read_;
    // button for start scan
    private Button btn_scan_;
    // button for stop connection
    private Button btn_stop_;
    // button for send data
    private Button btn_send_;
    // button for show paired devices
    private Button btn_show_;

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

    private BluetoothGatt mGatt;
    private boolean isConnected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //// get instances of gui objects
        // status textview
        tv_status_ = findViewById(R.id.tv_status);
        // read textview
        tv_read_ = findViewById(R.id.tv_read);
        // scan button
        btn_scan_ = findViewById(R.id.btn_scan);
        // stop button
        btn_stop_ = findViewById(R.id.btn_stop);
        // send button
        btn_send_ = findViewById(R.id.btn_send);
        // show button
        btn_show_ = findViewById(R.id.btn_show);

        //// set click event handler
        btn_scan_.setOnClickListener((v) -> {
            startScan(v);
        });

        // finish app if the BLE is not supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }

        // ble manager
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        // set ble adapter
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    /*
        Start BLE scan
         */
    private void startScan(View v) {
        tv_status_.setText("Scanning...");
        // check ble adapter and ble enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestEnableBLE();
            tv_status_.setText("Scanning Failed: ble not enabled");
            return;
        }
        // check if location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            tv_status_.setText("Scanning Failed: no fine location permission");
            return;
        }

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
        private Map<String, BluetoothDevice> cb_scan_results_;

        /*
        Constructor
         */
        BleScanCallback(Map<String, BluetoothDevice> _scan_results) {
            cb_scan_results_ = _scan_results;
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
            String device_address = bleDevice.getAddress();
            // add the device to the result list
            cb_scan_results_.put(device_address, bleDevice);
            // log
            Log.d(TAG, "scan results device: " + bleDevice);
            tv_status_.setText("add scanned device: " + device_address);

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
    }
}
