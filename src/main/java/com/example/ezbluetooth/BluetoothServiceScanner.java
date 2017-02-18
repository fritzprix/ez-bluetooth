package com.example.ezbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
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
import java.util.concurrent.ThreadPoolExecutor;

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
        BT_FILTER.addAction(BluetoothDevice.ACTION_UUID);
        BT_FILTER.addAction(BluetoothDevice.ACTION_FOUND);
    }

    private List<BluetoothClient> mServices;
    private List<BluetoothDevice> mDevices;
    private HashMap<BluetoothDevice, LinkedList<BluetoothServiceWrapper>> mServiceMap;
    private BluetoothAdapter mBluetoothAdapter;
    private WeakReference<DiscoveryListener> wrCallback;
    private int uuidsWaitQueue;
    private boolean isDiscoveryStarted = false;

    public BluetoothServiceScanner() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mServiceMap = new HashMap<>();
        mDevices = new LinkedList<>();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final DiscoveryListener discoveryListener = wrCallback.get();
        Log.e(TAG, action);
        if(action.equalsIgnoreCase(BluetoothDevice.ACTION_FOUND)) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,(short) 0);
            onDeviceFound(context, device, rssi);
        } else if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
            onDiscoveryFinished();
        } else if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
            onDiscoveryStarted();
        } else if(action.equalsIgnoreCase(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            onDeviceBondStateChanged(device, bondState);
        } else if(action.equalsIgnoreCase(BluetoothDevice.ACTION_UUID)){
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.e(TAG, String.format(Locale.getDefault(), "UUIDs are fetched from Device %s ", device.getName()));
            tryBindDevice(device.getUuids(), device);
            synchronized (this) {
                uuidsWaitQueue--;
            }
            if(uuidsWaitQueue == 0) {
                discoveryListener.onDiscoveryFinished();
            }
        } else {
            Log.e(TAG, "Unexpected Action is received");
        }
    }

    private void onDeviceBondStateChanged(BluetoothDevice device, int bondState) {
        switch(bondState) {
            case BluetoothDevice.BOND_BONDED:
                Log.d(TAG, String.format(Locale.getDefault(), "Device %s is bonded!" , device.getName()));
                LinkedList<BluetoothServiceWrapper> serviceWrappers = mServiceMap.get(device);
                if(serviceWrappers == null) {
                    return;
                }
                for(BluetoothServiceWrapper serviceWrapper : serviceWrappers) {
                    serviceWrapper.notifyReady();
                }
                break;
            case BluetoothDevice.BOND_BONDING:
                Log.d(TAG, String.format(Locale.getDefault(), "Device %s is bonding" , device.getName()));
                break;
        }
    }

    private void tryBindDevice(ParcelUuid[] uuids, BluetoothDevice device) {
        int devId;
        final DiscoveryListener discoveryListener = wrCallback.get();
        for(BluetoothClient service : mServices) {
            if(searchUuid(uuids, service.getServiceUuid())) {
                if((devId = service.onBindDevice(device)) >= 0) {
                    LinkedList<BluetoothServiceWrapper> serviceWrappers = mServiceMap.get(device);
                    if(serviceWrappers == null) {
                        serviceWrappers = new LinkedList<>();
                        mServiceMap.put(device, serviceWrappers);
                    }
                    BluetoothServiceWrapper serviceWrapper = new BluetoothServiceWrapper(service, devId);
                    serviceWrappers.add(serviceWrapper);
                    discoveryListener.onServiceFound(serviceWrapper);
                }
            }
        }
    }

    private void onDiscoveryStarted() {
        if(isDiscoveryStarted) {
            return;
        }
        isDiscoveryStarted = true;
        final DiscoveryListener callback = wrCallback.get();
        uuidsWaitQueue = 0;
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery started /w %d Devices found", mDevices.size()));
        callback.onDiscoveryStarted();
    }

    private void onDiscoveryFinished() {
        if(!isDiscoveryStarted) {
            return;
        }
        isDiscoveryStarted = false;
        Log.d(TAG, String.format(Locale.getDefault(), "Discovery Finished /w %d Devices found", mDevices.size()));
        final DiscoveryListener callback = wrCallback.get();
        for(BluetoothDevice device : mDevices) {
            ParcelUuid uuids[] = device.getUuids();
            if(uuids == null || !(uuids.length > 0)) {
                if(device.fetchUuidsWithSdp()) {
                    uuidsWaitQueue++;
                    Log.e(TAG, String.format(Locale.getDefault(), "Fetching UUID from %d device(s)", uuidsWaitQueue + 1));
                } else {
                    Log.e(TAG, String.format(Locale.getDefault(), "Fetching UUID Fail : %s", device.getName()));
                }
            } else {
                tryBindDevice(uuids, device);
            }
        }
        if(uuidsWaitQueue > 0) {
            return;
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

    public void startDiscovery(DiscoveryListener listener, BluetoothClient...services) {
        wrCallback = new WeakReference<>(listener);
        mDevices.clear();
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

    public static class BluetoothServiceWrapper implements Parcelable {

        public static final Creator<BluetoothServiceWrapper> CREATOR = new Creator<BluetoothServiceWrapper>() {
            @Override
            public BluetoothServiceWrapper createFromParcel(Parcel source) {
                return new BluetoothServiceWrapper(source);
            }

            @Override
            public BluetoothServiceWrapper[] newArray(int size) {
                return new BluetoothServiceWrapper[0];
            }
        };

        private BluetoothClient client;
        private int devId;
        private WeakReference<Callback> mCallback;
        private boolean isPreparing;

        private BluetoothServiceWrapper(Parcel source) {
            client = source.readParcelable(BluetoothClient.class.getClassLoader());
            devId = source.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(client,flags);
            dest.writeInt(devId);
        }

        private BluetoothServiceWrapper(BluetoothClient client, int devId){
            this.client = client;
            this.devId = devId;
            isPreparing = false;
        }

        public synchronized void prepare(Callback callback) {
            if(isPreparing) {
                return;
            }
            isPreparing = true;
            mCallback = new WeakReference<>(callback);
            final BluetoothDevice bluetoothDevice = client.getBluetoothDevice(devId);
            switch (bluetoothDevice.getBondState()) {
                case BluetoothDevice.BOND_NONE:
                    Log.e(TAG, "NOT_BONDED");
                    bluetoothDevice.createBond();
                    break;
                default:
                    isPreparing = false;
                    callback.onReady(client, devId);
            }
        }

        private synchronized void notifyReady() {
            if(!isPreparing) {
                return;
            }
            isPreparing = false;
            final Callback callback = mCallback.get();
            if(callback == null) {
                return;
            }
            callback.onReady(client, devId);
        }

        public BluetoothDevice getBluetoothDevice() {
            return client.getBluetoothDevice(devId);
        }

        public String getServiceName() {
            return client.getServiceName();
        }

        public interface Callback {
            void onReady(BluetoothClient service, int devId);
        }
    }

    public interface DiscoveryListener {

        void onServiceFound(BluetoothServiceWrapper service);

        void onDeviceFound(BluetoothDevice device, short rssi, boolean isbonded);

        void onDiscoveryFinished();

        void onDiscoveryStarted();
    }

}
