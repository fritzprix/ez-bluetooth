package com.example.ezbluetooth;

import android.bluetooth.BluetoothDevice;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by innocentevil on 17. 1. 30.
 */

public interface BluetoothServiceClient {

    UUID getServiceUuid();

    boolean onBindDevice(BluetoothDevice device);

    BluetoothDevice getBluetoothDevice();

    void onConnected();

    void onDisconnected();

    String getServiceName();

    void start() throws IllegalStateException, IOException;

    void stop();

}
