package com.jancar.btservice.bluetooth;

/** Solo se pasa como null: no se usa desde JH Launcher. */
interface IBluetoothStatusCallback {
    void onSuccess(int a, int b, int c);
    void onFailure(int code);
}
