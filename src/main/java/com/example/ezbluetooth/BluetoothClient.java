package com.example.ezbluetooth;

import android.bluetooth.BluetoothDevice;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.UUID;

/**
 *
 * Created by innocentevil on 17. 1. 30.
 */

public interface BluetoothClient extends Parcelable {

    UUID getServiceUuid();

    int onBindDevice(BluetoothDevice device);

    @Nullable BluetoothDevice getBluetoothDevice(int devId);

    void onConnected();

    void onDisconnected();

    String getServiceName();

    void start(int devId) throws IllegalStateException, InvalidParameterException ,IOException;

    void stop();

}
