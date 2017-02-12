package com.example.ezbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

/**
 *
 * Created by fritzprix on 17. 1. 29.
 */

public class BluetoothServiceScanner extends BroadcastReceiver {

    private static IntentFilter BT_FILTER;
    private static BluetoothServiceScanner SINGLETON;
    static {
        SINGLETON = null;
        BT_FILTER = new IntentFilter();
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        BT_FILTER.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        BT_FILTER.addAction(BluetoothDevice.ACTION_FOUND);
    }

    private List<BluetoothClient> mServices;
    private List<BluetoothDevice> mDevices;
    private SparseArray<BluetoothClient> mServiceMap;
    private BluetoothAdapter mBluetoothAdapter;
    private WeakReference<DiscoveryListener> wrCallback;

    public BluetoothServiceScanner() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mServiceMap = new SparseArray<>();
        mDevices = new LinkedList<>();
    }

    public void setDiscoveryListener(DiscoveryListener discoveryListener) {
        wrCallback = new WeakReference<>(discoveryListener);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.e(TAG, action);
        if(action.equalsIgnoreCase(BluetoothDevice.ACTION_FOUND)) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,(short) 0);
            onDeviceFound(context, device, rssi);
        } else if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
            onDiscoveryFinished(context);
        } else if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
            onDiscoveryStarted(context);
        } else if(action.equalsIgnoreCase(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            onDeviceBondStateChanged(device, bondState);
        } else {
            Log.e(TAG, "Unexpected Action is received");
        }
    }

    private void onDeviceBondStateChanged(BluetoothDevice device, int bondState) {
        int devId;
        final DiscoveryListener discoveryListener = wrCallback.get();
        switch(bondState) {
            case BluetoothDevice.BOND_BONDED:
                if(device.fetchUuidsWithSdp()) {
                    for(BluetoothClient service : mServices) {
                        if(searchUuid(device.getUuids(), service.getServiceUuid())) {
                            if((devId = service.onBindDevice(device)) >= 0) {
                                mServiceMap.put(devId, service);
                                discoveryListener.onServiceReady(service, devId);
                            }
                        }
                    }
                }
                break;
            case BluetoothDevice.BOND_BONDING:
                Log.d(TAG, String.format(Locale.getDefault(), "Device %s is bonding" , device.getName()));
                break;
        }
    }

    private void onDiscoveryStarted(Context context) {
        final DiscoveryListener callback = wrCallback.get();
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery started /w %d Devices found", mDevices.size()));
        callback.onDiscoveryStarted();
    }

    private void onDiscoveryFinished(Context context) {
        mBluetoothAdapter.cancelDiscovery();
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery Finished /w %d Devices found", mDevices.size()));
        final DiscoveryListener callback = wrCallback.get();
        int devId;
        for(BluetoothDevice device : mDevices) {
            /**
             *  try fetch uuid(s) from remote device
             */
            if(device.fetchUuidsWithSdp()) {
                for(BluetoothClient service : mServices) {
                    if(searchUuid(device.getUuids(),service.getServiceUuid())) {
                        if((devId = service.onBindDevice(device)) >= 0) {
                            mServiceMap.put(devId, service);
                            callback.onServiceReady(service, devId);
                        }
                    }
                }
            } else {
                Log.e(TAG, String.format(Locale.getDefault(), "Fetching UUID Fail : %s", device.getName()));
            }
        }
        callback.onDiscoveryFinished();
    }

    private void onDeviceFound(Context context, BluetoothDevice device, short rssi) {
        mDevices.add(device);
        final DiscoveryListener discoveryListener = wrCallback.get();
        if(discoveryListener != null) {
            discoveryListener.onDeviceFound(device, rssi, device.getBondState() == BluetoothDevice.BOND_BONDED);
        }
        Log.d(TAG, String.format(Locale.getDefault(), "%s", device.getName()));
        Toast.makeText(context, String.format(Locale.getDefault(), "%s", device.getName()), Toast.LENGTH_SHORT).show();
    }

    public void startDiscovery(BluetoothClient...services) {
        mDevices.removeAll(mDevices);
        mServices = Arrays.asList(services);
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : devices) {
            mDevices.add(device);
        }
        while(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
        mBluetoothAdapter.startDiscovery();
    }


    private boolean searchUuid(ParcelUuid[] uuids, UUID svcUuid) {
        if(uuids == null) {
            return false;
        }
        for(ParcelUuid uuid : uuids) {
            Log.d(TAG, uuid.toString());
            if(uuid.getUuid().compareTo(svcUuid) == 0)
                return true;
        }
        return false;
    }

    public void cancelDiscovery() {
        Log.d(TAG, "Discovery canceled");
        mBluetoothAdapter.cancelDiscovery();
    }

    public void register(Context context) {
        Log.d(TAG, "listening on bluetooth action");
        context.registerReceiver(this, BT_FILTER);
    }

    public void unregister(Context context) {
        Log.d(TAG, "stop listening on bluetooth action");
        context.unregisterReceiver(this);
    }

    public static BluetoothServiceScanner getInstance() {
        if(SINGLETON == null) {
            SINGLETON = new BluetoothServiceScanner();
        }
        return SINGLETON;
    }

    public interface DiscoveryListener {

        void onServiceReady(BluetoothClient service, int devId);

        void onDeviceFound(BluetoothDevice device, short rssi, boolean isbonded);

        void onDiscoveryFinished();

        void onDiscoveryStarted();
    }

}
