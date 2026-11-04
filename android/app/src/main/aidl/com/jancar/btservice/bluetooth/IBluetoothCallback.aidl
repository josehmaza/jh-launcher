package com.jancar.btservice.bluetooth;

interface IBluetoothCallback {
    void onConnectStatus(int status, String name, String addr);
    void onCallStatus(int status, String number, String name);
    void onVoiceChange(int value);
    void onA2DPConnectStatus(int status, boolean connected);
    void onBtMusicId3Info(String title, String artist, String album, long duration);
    void onBtBatteryValue(int value);
    void onBtSignalValue(int value);
    void onPowerStatus(boolean on);
}
