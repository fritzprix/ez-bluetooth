package com.example.ezbluetooth.client.impl;

import android.util.Log;


import com.example.ezbluetooth.client.AbsBluetoothServiceClient;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * Created by innocentevil on 17. 2. 4.
 */

public class SimpleEchoClient extends AbsBluetoothServiceClient {

    private static final String TAG = SimpleEchoClient.class.getCanonicalName();
    private static final UUID SVC_UUID = UUID.fromString("604388f7-2241-45fa-9e78-e472b90b62d6");
    private static final String SVC_NAME = "SimpleEchoServer";
    private static final byte[] TEST_DATA;

    static {
        TEST_DATA = new byte[256];
        for (int i = 0;i < TEST_DATA.length; i++) {
            TEST_DATA[i] = (byte) i;
        }
    }

    private long lastTimeStamp;
    private Thread mClientThread;
    private int totalReceived;
    private int totalErrorCount;
    private WeakReference<Callback> wrCallback;

    public SimpleEchoClient(Callback callback) {
        wrCallback = new WeakReference<Callback>(callback);
    }

    @Override
    protected int getReadSize() {
        return TEST_DATA.length;
    }

    @Override
    protected byte[] onDataReceived(byte[] rxBuffer) {
        byte nextRx = rxBuffer[0];
        int errCnt = 0;
        final Callback callback = wrCallback.get();
        for(byte rx : rxBuffer) {
            if(nextRx != rx) {
                errCnt++;
            }
            nextRx = (byte) (rx + 1);
        }
        totalReceived += rxBuffer.length;
        totalErrorCount += errCnt;
        if(totalReceived % (TEST_DATA.length << 3) == 0) {
            long timeStamp = System.currentTimeMillis();
            if(callback != null) {
                callback.onScoreUpdate(totalErrorCount, TEST_DATA.length << 3, timeStamp - lastTimeStamp);
            }
            lastTimeStamp = timeStamp;
            totalErrorCount = 0;
        }
        return null;
    }

    @Override
    protected void onServiceReady() {
        totalReceived = 0;
        totalErrorCount = 0;
        lastTimeStamp = System.currentTimeMillis();
        mClientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(isConnected()) {
                    try {
                        write(TEST_DATA);
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                }
            }
        });
        mClientThread.start();
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        try {
            mClientThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    @Override
    public void onConnected() {
        Log.e(TAG, "Connected");
        super.onConnected();
    }

    @Override
    public UUID getServiceUuid() {
        return SVC_UUID;
    }

    @Override
    public String getServiceName() {
        return SVC_NAME;
    }

    public interface Callback {
        void onScoreUpdate(int errCount, int totalReceived, long updateInterval);
    }
}
