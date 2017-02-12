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
import java.util.HashMap;
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
    static {
        BT_FILTER = new IntentFilter();
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        BT_FILTER.addAction(BluetoothDevice.ACTION_FOUND);
    }

    private List<BluetoothClient> mServices;
    private List<BluetoothDevice> mDevices;
    private SparseArray<BluetoothClient> mServiceMap;
    private BluetoothAdapter mBluetoothAdapter;
    private WeakReference<Callback> wrCallback;

    public BluetoothServiceScanner(Callback callback) {
        wrCallback = new WeakReference<>(callback);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mServiceMap = new SparseArray<>();
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
        } else {
            Log.e(TAG, "Unexpected Action is received");
        }
    }

    private void onDiscoveryStarted(Context context) {
        final Callback callback = wrCallback.get();
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery started /w %d Devices found", mDevices.size()));
        callback.onDiscoveryStarted();
    }

    private void onDiscoveryFinished(Context context) {
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery Finished /w %d Devices found", mDevices.size()));
        final Callback callback = wrCallback.get();
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
            }
        }
        callback.onDiscoveryFinished();
    }

    private void onDeviceFound(Context context, BluetoothDevice device, short rssi) {
        mDevices.add(device);
        wrCallback.get().onDeviceFound(device, rssi);
        Log.d(TAG, String.format(Locale.getDefault(), "%s", device.getName()));
        Toast.makeText(context, String.format(Locale.getDefault(), "%s", device.getName()), Toast.LENGTH_SHORT).show();
    }

    public void startDiscovery(BluetoothClient...services) {
        mServices = Arrays.asList(services);
        mDevices = new LinkedList<>();
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : devices) {
            mDevices.add(device);
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

    public interface Callback {

        void onServiceReady(BluetoothClient service, int devId);

        void onDeviceFound(BluetoothDevice device, short rssi);

        void onDiscoveryFinished();

        void onDiscoveryStarted();
    }

}
