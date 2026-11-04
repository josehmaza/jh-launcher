package com.jancar.btservice.bluetooth;

interface IBluetoothExecCallback {
    void onSuccess(String result);
    void onFailure(int code);
}
