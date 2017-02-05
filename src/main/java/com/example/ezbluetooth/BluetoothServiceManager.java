package com.example.ezbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by innocentevil on 17. 1. 30.
 */

public class BluetoothServiceManager {
    private static final String TAG = BluetoothServiceManager.class.getCanonicalName();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private ThreadPoolExecutor mPoolExecutor;
    private List<BluetoothServer> mServices;
    private SparseArray<BluetoothServerSocket> mServiceConnections;
    private WeakReference<Callback> wrCallback;



    public BluetoothServiceManager(Context context, Callback callback) {
        wrCallback = new WeakReference<Callback>(new SyncCallback(callback));
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    public synchronized void start(BluetoothServer...services) {
        mPoolExecutor = new ThreadPoolExecutor(services.length, services.length << 1, 1000L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(services.length << 1));
        mServices = Arrays.asList(services);
        mServiceConnections = new SparseArray<>(services.length);


        for(final BluetoothServer service : mServices) {
            final int svcId = mServices.indexOf(service);
            mPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    BluetoothServerSocket serverSocket;
                    final Callback callback = wrCallback.get();
                    try {
                        service.setId(svcId);
                        serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(service.getServiceName(), service.getServiceUuid());
                        synchronized (this) {
                            mServiceConnections.put(svcId, serverSocket);
                        }

                        service.onCreate(serverSocket);
                        callback.onServiceStarted(svcId);
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                        return;
                    }
                    while(service.isAlive()) {
                        try {
                            int clientId = service.onWaitClient();
                            if (callback.onClientConnected(svcId, clientId)) {
                                service.onHandleClient(clientId);
                            } else {
                                service.onRejectClient(clientId);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, e.getLocalizedMessage());
                            service.onServerError(e);
                            callback.onServiceError(svcId, e);
                        }
                    }
                    try {
                        serverSocket.close();
                        synchronized (this) {
                            mServiceConnections.delete(svcId);
                        }
                        callback.onServiceClosed(svcId);
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                }
            });
        }
    }

    public synchronized void stop() {
        for(final BluetoothServer service : mServices) {
            mPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    BluetoothServerSocket socket = mServiceConnections.get(service.getId());
                    service.onDestroy();
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                }
            });
        }
        mPoolExecutor.shutdown();
    }

    public synchronized void stopService(int svcId) {
        BluetoothServer serviceServer = mServices.remove(svcId);
        BluetoothServerSocket socket = mServiceConnections.get(svcId);
        if((serviceServer == null) || (socket == null)) {
            return;
        }
        mServiceConnections.remove(svcId);
        try {
            socket.close();
            serviceServer.onDestroy();
            if(mServiceConnections.size() == 0) {
                mPoolExecutor.shutdown();
            }
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    private static class SyncCallback implements Callback {
        private Callback mCallback;
        SyncCallback(Callback callback) {
            mCallback = callback;
        }

        @Override
        public synchronized void onServiceStarted(int svcId) {
            mCallback.onServiceStarted(svcId);
        }

        @Override
        public synchronized boolean onClientConnected(int svcId, int clientId) {
            return mCallback.onClientConnected(svcId, clientId);
        }

        @Override
        public synchronized void onServiceError(int svcId, IOException e) {
            mCallback.onServiceError(svcId, e);
        }

        @Override
        public synchronized void onServiceClosed(int svcId) {
            mCallback.onServiceClosed(svcId);
        }
    }


    public interface Callback {

        void onServiceStarted(int svcId);

        boolean onClientConnected(int svcId, int clientId);

        void onServiceError(int svcId, IOException e);

        void onServiceClosed(int svcId);

    }
}
