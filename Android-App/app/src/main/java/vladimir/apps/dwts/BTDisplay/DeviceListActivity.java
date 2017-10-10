/*
 * Copyright (C) 2017 Vladimir Zhelezarov
 * Licensed under MIT License.
 * 
 * This file is based on "Android Bluetooth Chat":
 *
 * Copyright (C) 2009 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 */

package vladimir.apps.dwts.BTDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * This Activity appears as a dialog. It lists any paired devices and devices
 * detected in the area after discovery. When a device is chosen by the user,
 * the MAC address of the device is sent back to the parent Activity in the
 * result Intent.
 */
public class DeviceListActivity extends Activity {

    static {
        System.loadLibrary("groove");
    }
    private native void q(Context context);

    private static final int REQUEST_BLUETOOTH_SCAN = 33;
    // "BTDisplay":
    private final byte [] btName = {66, 88, 67, 108, 113, 114, 105, 98, 117};
    // "00:06:66" means "Roving Networks":
    private final byte [] btAddr = {48, 52, 57, 51, 52, 60, 51, 55};

    private ProgressBar progressBar;

    // Debugging
    private static final String TAG = "DeviceListActivity";

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Member fields
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    private List<String> devicesListP = new ArrayList<>();
    private List<String> devicesListN = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        setContentView(R.layout.activity_device_list);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        q(this);

        progressBar = (ProgressBar) findViewById(R.id.prBarDeviceList);

        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscoveryCaller();
                v.setVisibility(View.GONE);
            }
        });

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        ArrayAdapter<String> mPairedDevicesArrayAdapter = new ArrayAdapter<>(this,
                R.layout.support_simple_spinner_dropdown_item);
        mNewDevicesArrayAdapter = new ArrayAdapter<>(this,
                R.layout.support_simple_spinner_dropdown_item);

        // Find and set up the ListView for paired devices
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListenerP);

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListenerN);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().contains(MyDebug.d(btName))
                        && device.getAddress().contains(MyDebug.d(btAddr))) {
                    devicesListP.add(device.getAddress());
                    mPairedDevicesArrayAdapter.add(device.getName());
                }
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired)
                    .toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }

    private void doDiscoveryCaller () {
        // the fucking Marshmallow :<=
        if ((ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED)
                ) {
            doDiscovery();
        }
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        if (MyDebug.LOG)
            Log.d(TAG, "doDiscovery()");

        // Indicate scanning in the title
        Toast.makeText(this,R.string.scanning, Toast.LENGTH_LONG).show();

        progressBar.setVisibility(View.VISIBLE);

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    // The on-click listener for all devices in the paired devices view
    private OnItemClickListener mDeviceClickListenerP = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long id) {
            // Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();
            progressBar.setVisibility(View.GONE);
            String address = devicesListP.get(position);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    // The on-click listener for all devices in the new devices view
    private OnItemClickListener mDeviceClickListenerN = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long id) {
            mBtAdapter.cancelDiscovery();
            progressBar.setVisibility(View.GONE);
            String address = devicesListN.get(position);
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed
                // already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (device.getName() != null && device.getAddress() != null)
                        if (device.getName().contains(MyDebug.d(btName))
                                && device.getAddress().contains(MyDebug.d(btAddr))) {
                            devicesListN.add(device.getAddress());
                            mNewDevicesArrayAdapter.add(device.getName());
                        }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(),R.string.select_device,
                        Toast.LENGTH_SHORT).show();
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(
                            R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

}
