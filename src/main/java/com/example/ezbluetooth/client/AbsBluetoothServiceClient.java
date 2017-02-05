package com.example.ezbluetooth.client;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;


import com.example.ezbluetooth.BluetoothServiceClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Created by innocentevil on 17. 2. 1.
 *
 */

public abstract class AbsBluetoothServiceClient implements BluetoothServiceClient {

    private static final String TAG = AbsBluetoothServiceClient.class.getCanonicalName();

    private BluetoothDevice mDevice;
    private volatile Thread mClientThread;
    private BluetoothSocket mClientSocket;
    private boolean isConnected;
    private DataOutputStream mOutputStream;

    @Override
    public boolean onBindDevice(BluetoothDevice device) {
        if(mDevice != null) {
            return false;
        }

        mDevice = device;
        return true;
    }

    @Override
    public void onConnected() {
        isConnected = true;
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
    }

    public synchronized void start() throws IllegalStateException, IOException {
        if(mDevice == null) {
            throw new IllegalStateException(String.format(Locale.getDefault(), "No device is bound to service %s", getServiceName()));
        }

        isConnected = false;
        mOutputStream = null;
        mClientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mClientSocket = mDevice.createRfcommSocketToServiceRecord(getServiceUuid());
                    mClientSocket.connect();
                    onConnected();
                    isConnected = true;
                    mOutputStream = new DataOutputStream(mClientSocket.getOutputStream());
                    DataInputStream dis = new DataInputStream(mClientSocket.getInputStream());
                    Log.e(TAG, "Connected");
                    onServiceReady();
                    Log.e(TAG, "Service Ready");
                    byte[] rxBuffer = new byte[getReadSize()];
                    while(isConnected) {
                        if(dis.read(rxBuffer) > 0) {
                            byte[] txData = onDataReceived(rxBuffer);
                            if (txData != null) {
                                mOutputStream.write(txData);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                } finally {
                    if(mClientSocket.isConnected()) {
                        try {
                            mClientSocket.close();
                            synchronized (AbsBluetoothServiceClient.this) {
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
        if(mDevice == null ||
           mClientThread == null ||
           mClientSocket == null) {
            return;
        }

        try {
            mClientSocket.close();
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    public String getDeviceName() throws IllegalStateException {
        if(mDevice == null) {
            throw new IllegalStateException(String.format(Locale.getDefault(), "No device is bound to service %s", getServiceName()));
        }
        return mDevice.getName();
    }

    @Override
    public final BluetoothDevice getBluetoothDevice() {
        return mDevice;
    }

    protected abstract int getReadSize();
    protected abstract byte[] onDataReceived(byte[] rxBuffer);
    protected abstract void onServiceReady();

}
