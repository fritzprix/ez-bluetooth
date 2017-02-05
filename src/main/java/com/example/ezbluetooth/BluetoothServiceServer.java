package com.example.ezbluetooth;

import android.bluetooth.BluetoothServerSocket;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by innocentevil on 17. 1. 30.
 */

public interface BluetoothServiceServer {

    String getServiceName();

    UUID getServiceUuid();

    boolean isAlive();

    void onServerError(Exception e);

    void onDestroy();

    void onCreate(BluetoothServerSocket serverSocket);

    int onWaitClient() throws IOException ;

    void onRejectClient(int clientId) throws IOException ;

    void onHandleClient(int clientId);

    void setId(int svcId);

    int getId();

}
