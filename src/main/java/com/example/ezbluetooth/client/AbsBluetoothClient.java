package com.example.ezbluetooth.client;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Parcel;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.example.ezbluetooth.BluetoothClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Locale;

/**
 * Created by innocentevil on 17. 2. 1.
 *
 */

public abstract class AbsBluetoothClient implements BluetoothClient {

    private static final String TAG = AbsBluetoothClient.class.getCanonicalName();

    private volatile Thread mClientThread;
    private BluetoothSocket mClientSocket;
    private boolean isConnected;
    private DataOutputStream mOutputStream;
    private int mDevId;
    private SparseArray<BluetoothDevice> mDevices;
    private HashSet<Integer> mDevIds;
    private int currentDevId;


    protected AbsBluetoothClient() {
        mDevId = 0;
        currentDevId = 0;
        mDevices = new SparseArray<>();
        mDevIds = new HashSet<>();
    }

    public AbsBluetoothClient(Parcel source) {
        int devId, size = source.readInt();
        mDevices = new SparseArray<>(size);
        mDevIds = new HashSet<>(size);
        currentDevId = 0;
        for(int idx = 0;idx < size; idx++) {
            devId = source.readInt();
            BluetoothDevice device = source.readParcelable(BluetoothDevice.class.getClassLoader());
            mDevIds.add(devId);
            mDevices.put(devId, device);
            if(idx == size - 1) {
                /**
                 *  guarantee last device Id with value larger than the largest among the saved.
                 */
                mDevId = devId + 1;
            }
        }
    }

    @Override
    public int onBindDevice(BluetoothDevice device) {
        if(device == null) {
            return -1;
        }
        mDevices.put(mDevId, device);
        mDevIds.add(mDevId);
        return mDevId++;
    }

    @Override
    public void onConnected() {
        isConnected = true;
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
    }

    @Override
    public synchronized void start(int devId) throws IllegalStateException, IOException, InvalidParameterException {

        if(!mDevIds.contains(devId)) {
            Log.e(TAG, String.format(Locale.getDefault(),"Invalid device Id (%d)", devId));
            throw new InvalidParameterException(String.format(Locale.getDefault(),"Invalid device Id (%d)", devId));
        }

        final BluetoothDevice device = mDevices.get(devId);
        if(device == null) {
            Log.e(TAG, String.format(Locale.getDefault(),"No device is bound to service %s", getServiceName()));
            throw new IllegalStateException(String.format(Locale.getDefault(), "No device is bound to service %s", getServiceName()));
        }

        isConnected = false;
        mOutputStream = null;
        synchronized (this) {
            if(mClientThread != null) {
                throw new IllegalStateException(String.format(Locale.getDefault(), "Client is already started %d", currentDevId));
            }
            mClientThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mClientSocket = device.createRfcommSocketToServiceRecord(getServiceUuid());
                        Log.e(TAG, "Created Socket");
                        mClientSocket.connect();
                        Log.e(TAG, "Socket Connected");
                        onConnected();
                        isConnected = true;
                        mOutputStream = new DataOutputStream(mClientSocket.getOutputStream());
                        DataInputStream dis = new DataInputStream(mClientSocket.getInputStream());
                        onServiceReady();
                        Log.e(TAG, "Service Ready");
                        byte[] rxBuffer = new byte[getReadSize()];
                        while (isConnected) {
                            if (dis.read(rxBuffer) > 0) {
                                byte[] txData = onDataReceived(rxBuffer);
                                if (txData != null) {
                                    mOutputStream.write(txData);
                                }
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    } finally {
                        if (mClientSocket.isConnected()) {
                            try {
                                mClientSocket.close();
                                synchronized (AbsBluetoothClient.this) {
                                    mClientSocket = null;
                                }
                            } catch (IOException e) {
                                Log.e(TAG, e.getLocalizedMessage());
                            }
                        }
                        onDisconnected();
                    }
                }
            });
            mClientThread.start();
            currentDevId = devId;
        }
    }

    protected void write(byte[] data) throws IOException {
        if(mOutputStream == null) {
            throw new IOException("OutputStream is not ready");
        }
        mOutputStream.write(data);
    }

    protected void write(int b) throws IOException {
        if(mOutputStream == null) {
            throw new IOException("OutputStream is not ready");
        }
        mOutputStream.write(b);
    }

    protected boolean isConnected() {
        return isConnected;
    }

    public synchronized void stop() {
        if(mClientThread == null ||
           mClientSocket == null) {
            return;
        }

        synchronized (this) {
            try {
                mClientSocket.close();
                mClientThread = null;
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }

    @Override
    @Nullable public final BluetoothDevice getBluetoothDevice(int id) {
        if(!mDevIds.contains(id)) {
            return null;
        }
        return mDevices.get(id);
    }

    protected abstract int getReadSize();
    protected abstract byte[] onDataReceived(byte[] rxBuffer);
    protected abstract void onServiceReady();

    protected void saveToParcel(Parcel dest, int flags) {
        dest.writeInt(mDevices.size());                     // save the count of client devices
        for(int devId : mDevIds) {
            BluetoothDevice device = mDevices.get(devId);
            dest.writeInt(devId);                          // save the device Id
            dest.writeParcelable(device, flags);           // save the corresponding device to the Id
        }
    }
}
