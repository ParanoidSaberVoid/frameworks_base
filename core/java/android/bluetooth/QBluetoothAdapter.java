/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.bluetooth;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.bluetooth.IQBluetoothAdapterCallback;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the local device BLE adapter. The {@link QBluetoothAdapter}
 * lets you perform fundamental Bluetooth tasks,
 *  such as register and remove LPP clients.
 *
 * <p>To get a {@link QBluetoothAdapter} representing this specific Bluetooth
 * adapter, call the
 * static {@link #getDefaultAdapter} method; when running on JELLY_BEAN_MR2 and
 * higher. Once you have the local adapter, you can register for LPP clients
 * and receive alerts based on proximity of the remote device
 */
 /** @hide */
public final class QBluetoothAdapter {
    private static final String TAG = "QBluetoothAdapter";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private static QBluetoothAdapter sAdapter;
    private static BluetoothAdapter mAdapter;

    private final IBluetoothManager mManagerService;
    private IBluetooth mService;
    private IQBluetooth mQService;

    private final Map<LeLppCallback, LeLppClientWrapper> mLppClients = new HashMap<LeLppCallback, LeLppClientWrapper>();
    /**
     * Get a handle to the default local QBluetooth adapter.
     * <p>Currently Android only supports one QBluetooth adapter, but the API
     * could be extended to support more. This will always return the default
     * adapter.
     * @return the default local adapter, or null if Bluetooth is not supported
     *         on this hardware platform
     */
    public static synchronized QBluetoothAdapter getDefaultAdapter() {
        mAdapter=BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager managerService=mAdapter.getBluetoothManager();
        sAdapter = new QBluetoothAdapter(managerService);
        return sAdapter;
    }

    /**
     * Use {@link #getDefaultAdapter} to get the BluetoothAdapter instance.
     */
    QBluetoothAdapter(IBluetoothManager managerService){
        if (managerService == null) {
            throw new IllegalArgumentException("bluetooth manager service is null");
        }
        try {
            //mService = managerService.registerAdapter(mManagerCallback);
            mService=mAdapter.getBluetoothService(mAdapterServiceCallback);
           //mQService=managerService.getQBluetooth();
           mQService=managerService.registerQAdapter(mManagerCallback);
           Log.i(TAG,"mQService= :" + mQService);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        mManagerService = managerService;
        /* read property value to check if the bluetooth controller support le extended scan */
     }

    public interface LeLppCallback {
        public void onWriteRssiThreshold(int status);

        public void onReadRssiThreshold(int low,int upper, int alert, int status);

        public void onEnableRssiMonitor(int enable, int status);

        public void onRssiThresholdEvent(int evtType, int rssi);

        public boolean onUpdateLease();
    };

    /** @hide */
    public boolean registerLppClient(LeLppCallback client, String address, boolean add) {
        synchronized(mLppClients) {
            if (add) {
                if(mLppClients.containsKey(client)) {
                    Log.e(TAG, "Lpp Client has been already registered");
                    return false;
                }

                LeLppClientWrapper wrapper = new LeLppClientWrapper(this, mQService, address, client);
                if(wrapper != null && wrapper.register2(true)) {
                    mLppClients.put(client, wrapper);
                    return true;
                }
                return false;
            } else {
                LeLppClientWrapper wrapper = mLppClients.remove(client);
                if (wrapper != null) {
                    wrapper.register2(false);
                    return true;
                }
                return false;
            }
        }
    }

   /**
     * Write the rssi threshold for a connected remote device.
     *
     * <p>The {@link BluetoothGattCallback#onWriteRssiThreshold} callback will be
     * invoked when the write request has been sent.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param min    The lower limit of rssi threshold
     * @param max    The upper limit of rssi threshold
     * @return true, if the rssi threshold writing request has been sent tsuccessfully
     */
    /** @hide */
    public boolean writeRssiThreshold(LeLppCallback client, int min, int max) {
        LeLppClientWrapper wrapper = null;
        synchronized(mLppClients) {
            wrapper = mLppClients.get(client);
        }
        if (wrapper == null) return false;

        wrapper.writeRssiThreshold((byte)min, (byte)max);
        return true;
    }

    /**
     * Enable rssi monitoring for a connected remote device.
     *
     * <p>The {@link BluetoothGattCallback#onEnableRssiMonitor} callback will be
     * invoked when the rssi monitor enable/disable request has been sent.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param enable disable/enable rssi monitor
     * @return true, if the rssi monitoring request has been sent tsuccessfully
     */
     /** @hide */
    public boolean enableRssiMonitor(LeLppCallback client, boolean enable){
        LeLppClientWrapper wrapper = null;
        synchronized(mLppClients) {
            wrapper = mLppClients.get(client);
        }
        if (wrapper == null) return false;

        wrapper.enableMonitor(enable);
        return true;
    }

   /**
     * Read the rssi threshold for a connected remote device.
     *
     * <p>The {@link BluetoothGattCallback#onReadRssiThreshold} callback will be
     * invoked when the rssi threshold has been read.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @return true, if the rssi threshold has been requested successfully
     */
     /** @hide */
    public boolean readRssiThreshold(LeLppCallback client) {
        LeLppClientWrapper wrapper = null;
        synchronized(mLppClients) {
            wrapper = mLppClients.get(client);
        }
        if (wrapper == null) return false;

        wrapper.readRssiThreshold();
        return true;
    }

    protected void finalize() throws Throwable {
        try {
            mManagerService.unregisterQAdapter(mManagerCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        } finally {
            super.finalize();
        }
    }

    private static class LeLppClientWrapper extends IQBluetoothAdapterCallback.Stub {
            private final WeakReference<QBluetoothAdapter> mAdapter;
            private final IQBluetooth mQBluetoothAdapterService;
            private final String mDevice;
            private final LeLppCallback client;

            public LeLppClientWrapper (QBluetoothAdapter adapter, IQBluetooth adapterService,
                                       String address, LeLppCallback callback) {
                this.mAdapter = new WeakReference<QBluetoothAdapter> (adapter);
                this.mQBluetoothAdapterService = adapterService;
                this.mDevice = address;
                this.client = callback;
            }

            public boolean register2(boolean add) {
                if(mQBluetoothAdapterService != null) {
                    try {
                        return mQBluetoothAdapterService.registerLeLppRssiMonitorClient(mDevice, this, add);
                    } catch (RemoteException e) {
                        Log.w(TAG, "", e);
                    }
                }
                return false;
            }

            public void writeRssiThreshold(byte min, byte max) {
                if(mQBluetoothAdapterService != null) {
                    try {
                        mQBluetoothAdapterService.writeLeLppRssiThreshold(mDevice, min, max);
                    } catch (RemoteException e) {
                        Log.w(TAG, "", e);
                    }
                }
            }

            public void enableMonitor(boolean enable) {
                if(mQBluetoothAdapterService != null) {
                    try {
                        mQBluetoothAdapterService.enableLeLppRssiMonitor(mDevice, enable);
                    } catch (RemoteException e) {
                        Log.w(TAG, "", e);
                    }
                }
            }

            public void readRssiThreshold() {
                if(mQBluetoothAdapterService != null) {
                    try {
                        mQBluetoothAdapterService.readLeLppRssiThreshold(mDevice);
                    } catch (RemoteException e) {
                        Log.w(TAG, "", e);
                    }
                }
            }

           /**
             * Rssi threshold has been written
             * @hide
             */
            public void onWriteRssiThreshold(String address, int status){
                if (client == null) {
                    return;
                }
                try {
                    client.onWriteRssiThreshold(status);
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                }
            }

            /**
             * RSSI threshold has been read
             * @hide
             */
            public void onReadRssiThreshold(String address, int low, int upper,
                                            int alert, int status){
                if (client == null) {
                    return;
                }
                try {
                    client.onReadRssiThreshold(low, upper, alert, status);
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                }
            }

           /**
             * Remote Device RSSI monitoring has been enabled/disabled
             * @hide
             */
            public void onEnableRssiMonitor(String address, int enable, int status){
                if (client == null) {
                    return;
                }
                try {
                    client.onEnableRssiMonitor(enable, status);
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                }
            }

            /**
             * RSSI threshold event reported
             * @hide
             */
            public void onRssiThresholdEvent(String address, int evtType, int rssi){
                if (client == null) {
                    return;
                }
                try {
                    client.onRssiThresholdEvent(evtType, rssi);
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                }
            }

            public boolean onUpdateLease() {
                if (client == null) return false;
                try {
                    return client.onUpdateLease();
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                    return false;
                }
            }
    }

    final private IBluetoothManagerCallback mAdapterServiceCallback =
        new IBluetoothManagerCallback.Stub() {
            public void onBluetoothServiceUp(IBluetooth bluetoothService) {
                synchronized (mAdapterServiceCallback) {
                    //initialize the global params again
                    mService = bluetoothService;
                    Log.i(TAG,"onBluetoothServiceUp Adapter ON: mService: "+ mService + " mQService: " + mQService+ " ManagerService:" + mManagerService);
                }
            }

            public void onBluetoothServiceDown() {
                synchronized (mAdapterServiceCallback) {
                    mService = null;
                    Log.i(TAG,"onBluetoothServiceDown Adapter OFF: mService: "+ mService + " mQService: " + mQService);
                }
            }
    };


    final private IQBluetoothManagerCallback mManagerCallback =
        new IQBluetoothManagerCallback.Stub() {
            public void onQBluetoothServiceUp(IQBluetooth qcbluetoothService) {
                if (VDBG) Log.i(TAG, "on QBluetoothServiceUp: " + qcbluetoothService);
                synchronized (mManagerCallback) {
                    //initialize the global params again
                    mQService = qcbluetoothService;
                    Log.i(TAG,"onQBluetoothServiceUp: Adapter ON: mService: "+ mService + " mQService: " + mQService+ " ManagerService:" + mManagerService);
                }
            }

            public void onQBluetoothServiceDown() {
                if (VDBG) Log.i(TAG, "onQBluetoothServiceDown: " + mService);
                synchronized (mManagerCallback) {
                    mQService=null;
                    Log.i(TAG,"onQBluetoothServiceDown: Adapter OFF: mService: "+ mService + " mQService: " + mQService);
                }
            }
    };

}
