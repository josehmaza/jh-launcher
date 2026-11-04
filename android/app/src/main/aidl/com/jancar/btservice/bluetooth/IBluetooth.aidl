package com.jancar.btservice.bluetooth;

import com.jancar.btservice.bluetooth.IBluetoothCallback;
import com.jancar.btservice.bluetooth.IBluetoothExecCallback;
import com.jancar.btservice.bluetooth.IBluetoothLinkDeviceCallback;
import com.jancar.btservice.bluetooth.IBluetoothStatusCallback;
import com.jancar.btservice.bluetooth.IBluetoothVCardCallback;
import com.jancar.btservice.bluetooth.IDeviceCallback;
import com.jancar.btservice.bluetooth.ISearchDeviceCallback;

/** Reconstruido del SDK de Jancar: el orden de los métodos es el de sus códigos de transacción (1, 2, 3…). */
interface IBluetooth {
    void getBluetoothVer(IBluetoothExecCallback cb);                    // 1
    int openBluetoothModule(IBluetoothExecCallback cb);                 // 2
    void resetBluetoothModule(IBluetoothExecCallback cb);               // 3
    void closeBluetoothModule(IBluetoothExecCallback cb);               // 4
    void getBluetoothModuleStatus(IBluetoothStatusCallback cb);         // 5
    void modifyModuleName(String name, IBluetoothExecCallback cb);      // 6
    void modifyModulePIN(String pin, IBluetoothExecCallback cb);        // 7
    void nextBtMusic(IBluetoothExecCallback cb);                        // 8
    void prevBtMusic(IBluetoothExecCallback cb);                        // 9
    void playAndPause(IBluetoothExecCallback cb);                       // 10
    void playBtMusic(IBluetoothExecCallback cb);                        // 11
    void pauseBtMusic(IBluetoothExecCallback cb);                       // 12
    void stopBtMusic(IBluetoothExecCallback cb);                        // 13
    void getBtMusicId3Info(IBluetoothExecCallback cb);                  // 14
    void setBtMusicVolumePercent(float percent, IBluetoothExecCallback cb); // 15
    void linkDevice(String addr, IBluetoothExecCallback cb);            // 16
    void unlinkDevice(IBluetoothExecCallback cb);                       // 17
    void deleteDevice(String addr, IBluetoothExecCallback cb);          // 18
    void deleteAllDevice(IBluetoothExecCallback cb);                    // 19
    void addVolume(IBluetoothExecCallback cb);                          // 20
    void delVolume(IBluetoothExecCallback cb);                          // 21
    void muteBluetoothModule(boolean mute, IBluetoothExecCallback cb); // 22
    void callPhone(String number, IBluetoothExecCallback cb);           // 23
    void recallPhone(IBluetoothExecCallback cb);                        // 24
    void hangPhone(IBluetoothExecCallback cb);                          // 25
    void rejectPhone(IBluetoothExecCallback cb);                        // 26
    void listenPhone(IBluetoothExecCallback cb);                        // 27
    void voiceDial(IBluetoothExecCallback cb);                          // 28
    void transferCall(IBluetoothExecCallback cb);                       // 29
    void waitCall(int a, IBluetoothExecCallback cb);                    // 30
    void requestDTMF(int code, IBluetoothExecCallback cb);              // 31
    void muteMic(boolean mute, IBluetoothExecCallback cb);              // 32
    void getDeviceBattery(IBluetoothExecCallback cb);                   // 33
    void getDeviceSignalStrength(IBluetoothExecCallback cb);            // 34
    void getBluetoothState(IBluetoothLinkDeviceCallback cb);            // 35
    void requestA2DPConnect(boolean connect, IBluetoothExecCallback cb); // 36
    void requestHFPConnect(boolean connect, IBluetoothExecCallback cb); // 37
    void requestBluetoothListener(IBluetoothCallback cb);               // 38
    void unrequestBluetoothListener(IBluetoothCallback cb);             // 39
    void getPhoneContacts(IBluetoothVCardCallback cb);                  // 40
    void getReceivedCallRecord(IBluetoothVCardCallback cb);             // 41
    void getDialedCallRecord(IBluetoothVCardCallback cb);               // 42
    void getMissedCallRecord(IBluetoothVCardCallback cb);               // 43
    void getAllCallRecord(IBluetoothVCardCallback cb);                  // 44
    void stopContactOrHistoryLoad(IBluetoothExecCallback cb);           // 45
    void getSearchedDeviceNum(IBluetoothExecCallback cb);               // 46
    void getSearchedDeviceName(int index, IBluetoothExecCallback cb);   // 47
    void getSearchedDeviceAddr(int index, IBluetoothExecCallback cb);   // 48
    void searchNewDevice(int a, ISearchDeviceCallback cb);              // 49
    void stopSearchNewDevice(int a, IBluetoothExecCallback cb);         // 50
    void getPairedDevice(int a, IDeviceCallback cb);                    // 51
    void getCurrentDeviceName(IBluetoothExecCallback cb);               // 52
    void getCurrentDeviceAddr(IBluetoothExecCallback cb);               // 53
    void getBluetoothName(IBluetoothExecCallback cb);                   // 54
    String getBluetoothDeviceName();                                    // 55
    void getBluetoothPin(IBluetoothExecCallback cb);                    // 56
    String getBluetoothDevicePin();                                     // 57
    void stopCurrentCommand();                                          // 58
    void threePartyCallCtrl(int a, IBluetoothExecCallback cb);          // 59
    void powerOn();                                                     // 60
    void powerOff();                                                    // 61
    void setRingVolume(float volume);                                   // 62
    void muteBtMusic(boolean mute, IBluetoothExecCallback cb);          // 63
    boolean isAlreadyPaired(String addr);                               // 64
    boolean pairingRequest(String addr);                                // 65
    void openRecording(IBluetoothExecCallback cb);                      // 66
    void closeRecording(IBluetoothExecCallback cb);                     // 67
    void setAutoLink(boolean on, IBluetoothExecCallback cb);            // 68
    boolean isAutoLinkOn();                                             // 69
    boolean isPowerOn();                                                // 70
    boolean enterBtUpdateMode(boolean enter);                           // 71
    boolean isBlueToothAddrConnected(String addr);                      // 72
    void setNumberKey(int key);                                         // 73
}
