package com.example.ezbluetooth.service;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;


import com.example.ezbluetooth.BluetoothServiceServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by innocentevil on 17. 1. 30.
 */

public abstract class AbsBluetoothServiceServer implements BluetoothServiceServer {

    private static final String TAG = AbsBluetoothServiceServer.class.getCanonicalName();

    private volatile boolean isAlive;
    private int svcId;
    private BluetoothServerSocket mServerSocket;
    private SparseArray<BluetoothSocket> mClients;
    private SparseBooleanArray mClientIsValidArray;
    private SparseArray<Runnable> mClientJobs;
    private int clientId;
    private ThreadPoolExecutor mPoolExecutor;


    public AbsBluetoothServiceServer(int maxClientCount) {
        mClients = new SparseArray<>();
        mClientJobs = new SparseArray<>();
        mClientIsValidArray = new SparseBooleanArray();
        mPoolExecutor = new ThreadPoolExecutor(maxClientCount, maxClientCount << 1, 1000L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(maxClientCount << 1));
        clientId = 0;
    }

    @Override
    public boolean isAlive() {
        return isAlive;
    }

    @Override
    public void onServerError(Exception e) {
        Log.e(TAG, e.getLocalizedMessage());
    }


    @Override
    public void onDestroy() {
        isAlive = false;
    }

    @Override
    public void onCreate(BluetoothServerSocket serverSocket) {
        mServerSocket = serverSocket;
        isAlive = true;
    }

    @Override
    public int onWaitClient() throws IOException {
        BluetoothSocket client = mServerSocket.accept();
        synchronized (this) {
            mClients.put(clientId, client);
        }
        return clientId++;
    }

    @Override
    public void onRejectClient(int clientId) throws IOException {
        BluetoothSocket client;
        synchronized (this) {
            client = mClients.get(clientId);
        }

        if(client == null) {
            return;
        }

        synchronized (this) {
            mClientIsValidArray.delete(clientId);
        }
        client.close();
    }

    @Override
    public void onHandleClient(final int clientId) {
        final BluetoothSocket client = mClients.get(clientId);
        if(client == null) {
            return;
        }
        final Runnable clientHandleTask = new Runnable() {
            @Override
            public void run() {
                synchronized (AbsBluetoothServiceServer.this) {
                    mClientIsValidArray.put(clientId, true);
                }
                int bufferSize = getReadSize();
                if(bufferSize <= 0 ) {
                    /**
                     *  guarantee buffer size is greater than zero
                     */
                    bufferSize = 1;
                }

                byte[] rxBuffer = new byte[bufferSize];
                byte[] txBuffer;
                try {
                    DataInputStream dis = new DataInputStream(client.getInputStream());
                    DataOutputStream dos = new DataOutputStream(client.getOutputStream());
                    while(mClientIsValidArray.get(clientId) && isAlive) {
                        if(dis.read(rxBuffer) > 0) {
                            txBuffer = onDataReceived(clientId, rxBuffer);
                            if(txBuffer != null) {
                                dos.write(txBuffer);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                } finally {
                    synchronized (AbsBluetoothServiceServer.this) {
                        mClients.remove(clientId);
                        mClientIsValidArray.delete(clientId);
                        AbsBluetoothServiceServer.this.notifyAll();
                    }
                }
            }
        };
        mClientJobs.put(clientId, clientHandleTask);
        mPoolExecutor.execute(clientHandleTask);
    }


    @Override
    public void setId(int svcId) {
        this.svcId = svcId;
    }

    @Override
    public int getId() {
        return svcId;
    }

    protected abstract int getReadSize();
    protected abstract byte[] onDataReceived(int clientId, byte[] data);
}
