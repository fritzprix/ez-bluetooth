package com.example.ezbluetooth.client.impl;

import android.os.Parcel;
import android.util.Log;

import com.example.ezbluetooth.client.AbsBluetoothClient;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.UUID;

/**
 *
 * Created by innocentevil on 17. 2. 4.
 */

public class SimpleEchoClient extends AbsBluetoothClient {

    public static final Creator<SimpleEchoClient> CREATOR = new Creator<SimpleEchoClient>() {
        @Override
        public SimpleEchoClient createFromParcel(Parcel source) {
            SimpleEchoClient echoClient = new SimpleEchoClient(source);
            return echoClient;
        }

        @Override
        public SimpleEchoClient[] newArray(int size) {
            return new SimpleEchoClient[0];
        }
    };

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

    public SimpleEchoClient() {
        super();
        init();
    }

    private void init() {
        totalErrorCount = 0;
        totalReceived = 0;
    }

    private SimpleEchoClient(Parcel source) {
        super(source);
        init();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags){
        if(isConnected()) {
            stop();
        }
        super.saveToParcel(dest,flags);
    }

    public void addCallback(Callback callback) {
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
        if(wrCallback == null) {
            wrCallback = new WeakReference<Callback>(new Callback() {
                @Override
                public void onScoreUpdate(int errCount, int totalReceived, long updateInterval) {
                    Log.d(TAG, String.format(Locale.getDefault(), "Updated Score : (%d / %d) @ %d", errCount, totalReceived, updateInterval));
                }
            });
        }
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
        if(mClientThread == null) {
            return;
        }
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
