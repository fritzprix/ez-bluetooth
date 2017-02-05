package com.example.ezbluetooth.service.impl;


import com.example.ezbluetooth.service.AbsBluetoothServer;

import java.util.UUID;


/**
 *
 * Created by innocentevil on 17. 1. 30.
 */

public class SimpleEchoServer extends AbsBluetoothServer {

    private static final String TAG = SimpleEchoServer.class.getCanonicalName();
    private static final UUID SVC_UUID = UUID.fromString("604388f7-2241-45fa-9e78-e472b90b62d6");
    private static final String SVC_NAME = "SimpleEchoServer";

    public SimpleEchoServer() {
        super(1);
    }

    @Override
    protected int getReadSize() {
        return 1;
    }

    @Override
    protected byte[] onDataReceived(int clientId, byte[] data) {
        return data;
    }

    @Override
    public String getServiceName() {
        return SVC_NAME;
    }

    @Override
    public UUID getServiceUuid() {
        return SVC_UUID;
    }
}
