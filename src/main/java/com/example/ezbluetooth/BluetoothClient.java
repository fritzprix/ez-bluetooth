package com.example.ezbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;
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
 * Created by lucifer on 17. 1. 29.
 */

public class BluetoothClient extends BroadcastReceiver {

    private static IntentFilter BT_FILTER;
    static {
        BT_FILTER = new IntentFilter();
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        BT_FILTER.addAction(BluetoothDevice.ACTION_FOUND);
//        BT_FILTER.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
    }

    private List<BluetoothServiceClient> mServices;
    private List<BluetoothDevice> mDevices;
    private HashMap<BluetoothDevice, BluetoothServiceClient> mServiceMap;
    private BluetoothAdapter mBluetoothAdapter;
    private WeakReference<Callback> wrCallback;

    public BluetoothClient(Callback callback) {
        wrCallback = new WeakReference<Callback>(callback);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mServiceMap = new HashMap<>();
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
//        } else if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
//            final int connectionState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,BluetoothAdapter.STATE_DISCONNECTED);
//            final int prevConnState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);
//            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//            onDeviceConnectionChanged(device, prevConnState, connectionState);
        } else {
            Log.e(TAG, "Unexpected Action");
        }
    }



    private void onDeviceConnectionChanged(BluetoothDevice device, int prevConnState, int newConnState) {

        Log.e(TAG, String.format(Locale.getDefault(), "Connection State of Device (%s) is %d ", device.getName(), newConnState));
        final BluetoothServiceClient service = mServiceMap.get(device);
        switch(newConnState) {
            case BluetoothAdapter.STATE_DISCONNECTING:
                break;
            case BluetoothAdapter.STATE_CONNECTING:
                break;
            case BluetoothAdapter.STATE_CONNECTED:
                if(service != null) {
                    service.onConnected();
                }
                break;
            case BluetoothAdapter.STATE_DISCONNECTED:
                if(service != null) {
                    service.onDisconnected();
                }
                break;
        }
    }

    private void onDiscoveryStarted(Context context) {
        final Callback callback = wrCallback.get();
        callback.onDiscoveryStarted();
    }

    private void onDiscoveryFinished(Context context) {
        Log.e(TAG, String.format(Locale.getDefault(), "Discovery Finished /w %d Devices found", mDevices.size()));
        Toast.makeText(context, String.format(Locale.getDefault(), "Discovery Finished /w %d Devices found", mDevices.size()), Toast.LENGTH_SHORT).show();
        final Callback callback = wrCallback.get();
        for(BluetoothDevice device : mDevices) {
            /**
             *  try fetch uuid(s) from remote device
             */
            if(device.fetchUuidsWithSdp()) {
                for(BluetoothServiceClient service : mServices) {
                    if(searchUuid(device.getUuids(),service.getServiceUuid())) {
                        if(service.onBindDevice(device)) {
                            mServiceMap.put(device, service);
                            callback.onServiceReady(service);
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
        Log.e(TAG, String.format(Locale.getDefault(), "%s", device.getName()));
        Toast.makeText(context, String.format(Locale.getDefault(), "%s", device.getName()), Toast.LENGTH_SHORT).show();
    }

    public void startDiscovery(BluetoothServiceClient...services) {
        mServices = Arrays.asList(services);
        mDevices = new LinkedList<>();
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : devices) {
            mDevices.add(device);
        }
        mBluetoothAdapter.startDiscovery();
    }


    private boolean searchUuid(ParcelUuid[] uuids, UUID doodUuid) {
        if(uuids == null) {
            Log.e(TAG, "null UUIDs");
            return false;
        }
        for(ParcelUuid uuid : uuids) {
            Log.e(TAG, uuid.toString());
            if(uuid.getUuid().compareTo(doodUuid) == 0)
                return true;
        }
        return false;
    }

    public void cancelDiscovery() {
        mBluetoothAdapter.cancelDiscovery();
    }

    public void register(Context context) {
        context.registerReceiver(this, BT_FILTER);
    }

    public void unregister(Context context) {
        context.unregisterReceiver(this);
    }

    public interface Callback {

        void onServiceReady(BluetoothServiceClient service);

        void onDeviceFound(BluetoothDevice device, short rssi);

        void onDiscoveryFinished();

        void onDiscoveryStarted();
    }

}
