/*
 * Copyright (C) 2017 Vladimir Zhelezarov
 * Licensed under MIT License.
 * 
 * This file is based on "Android Bluetooth Chat":
 *
 * Copyright (C) 2009 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 */

package vladimir.apps.dwts.BTDisplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static vladimir.apps.dwts.BTDisplay.MainActivity.csNone;
import static vladimir.apps.dwts.BTDisplay.MainActivity.csAwaitChall;
import static vladimir.apps.dwts.BTDisplay.MainActivity.csReceiveSeq;
import static vladimir.apps.dwts.BTDisplay.MainActivity.csAwaitAnswer;
import static vladimir.apps.dwts.BTDisplay.MainActivity.csAllOk;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for incoming
 * connections, a thread for connecting with a device, and a thread for
 * performing data transmissions when connected.
 */
class BluetoothChatService {
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final String DEB = "Debugging---";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID
            .fromString("0001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    static final int STATE_NONE = 0; // we're doing nothing
    static final int STATE_LISTEN = 1; // now listening for incoming
    // connections
    static final int STATE_CONNECTING = 2; // now initiating an outgoing
    // connection
    static final int STATE_CONNECTED = 3; // now connected to a remote
    // device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param handler
     *            A Handler to send messages back to the UI Activity
     */
    BluetoothChatService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state
     *            An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (MyDebug.LOG)
            Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1)
                .sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    synchronized void start() {
        if (MyDebug.LOG)
            Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device
     *            The BluetoothDevice to connect
     */
    synchronized void connect(BluetoothDevice device) {
        if (MyDebug.LOG)
            Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket
     *            The BluetoothSocket on which the connection was made
     * @param device
     *            The BluetoothDevice that has been connected
     */
    private synchronized void connected(BluetoothSocket socket,
                                        BluetoothDevice device) {
        if (MyDebug.LOG)
            Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    synchronized void stop() {
        if (MyDebug.LOG)
            Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out
     *            The bytes to write
     * @see ConnectedThread#write(byte)
     */
    void write(byte out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Verbindung fehlgeschalten!");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Verbindung verloren!");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_NONE);

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    /**
     * This thread runs while attempting to make an outgoing connection with a
     * device. It runs straight through; the connection either succeeds or
     * fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                if (MyDebug.LOG) Log.e(TAG, "Socket Type create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            if (MyDebug.LOG) Log.i(TAG, "BEGIN mConnectThread ");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    if (MyDebug.LOG) Log.e(
                            TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (MyDebug.LOG) Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device. It handles all
     * incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            if (MyDebug.LOG) Log.d(TAG, "create ConnectedThread ");
            mmSocket = socket;

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (MyDebug.LOG) Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

        }

        public void run() {
            if (MyDebug.LOG) Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[128];
            int bytes = 0;
            int timeOut = 500;
            int currTime = 0;
            int interval = 50;
            // we sleep just one time after received byte and then go into blocking read
            boolean sleptAlready = false;
            int toBeRead = 0;
            boolean toReadInits = false;
            int lastInitState = MainActivity.csAwaitChall;
            // Keep listening to the InputStream
            while (true) {
                try {

                    // skip byte
                    if ( (MainActivity.cState.get() == csNone) && (mmInStream.available() > 0) )
                        mmInStream.skip(1);

                    // normal operation
                    else if ( MainActivity.cState.get() == csAllOk ) {
                        // this gets disabled when the phone's display is off/sleeping to spare battery
                        if (MainActivity.goRead) {

                            // we have incoming byte
                            if ((mmInStream.available() > 0) || sleptAlready) {
                                buffer[bytes] = (byte) mmInStream.read();
                                if (MyDebug.LOG) Log.e(DEB,"Arrived: " + buffer[bytes]);
                                currTime = 0;           // byte arrived - reset elapsed time
                                sleptAlready = false;

                                // comm arrived - send all we have
                                if (buffer[bytes] == MainActivity.d) {
                                    // NOTE: the first comm byte is the last byte we send to
                                    // the MainActivity but byte == buffer.length -1, so we
                                    // do not read the last byte
                                    mHandler.obtainMessage(
                                            MainActivity.MESSAGE_READ, bytes, 0, buffer.clone()
                                    ).sendToTarget();
                                    if (MyDebug.LOG) Log.d(DEB, "Send normal bytes: " + bytes);
                                    bytes = 0;
                                } else if (bytes == 127) {      // buffer overflow
                                    if (MyDebug.LOG) Log.e(DEB, "Input too damn long!!!!");
                                    bytes = 0;
                                } else bytes++;

                            } else if (currTime < timeOut) {    // we wait
                                try {
                                    Thread.sleep(interval);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                currTime += interval;
                            } else {                            // timeout detected
                                if (bytes > 0) {                // something small arrived
                                    if (MyDebug.LOG)
                                        Log.d(DEB, "timeout detected, and we have something");
                                    mHandler.obtainMessage(
                                            MainActivity.MESSAGE_READ, bytes, 1, buffer.clone()
                                    ).sendToTarget();
                                    bytes = 0;              // reset byte count
                                } else {
                                    if (MyDebug.LOG)
                                        Log.d(DEB, "timeout detected, and we have nothing");
                                }
                                // next cycle goes into the blocking read
                                sleptAlready = true;
                            }
                        } else mmInStream.skip(1);
                    }

                    // init states
                    else {
                        // sub cases to handle incoming byte INIT
                        if (MainActivity.cState.get() == csAwaitChall) {
                            if (MyDebug.LOG) Log.e(DEB, "receiving challenge");
                            toBeRead = 16;
                            toReadInits = true;
                            lastInitState = MainActivity.csAwaitChall;
                            MainActivity.cState.set(csReceiveSeq);

                            // sub cases to handle incoming byte INIT
                        } else if (MainActivity.cState.get() == csReceiveSeq) {
                            if (MyDebug.LOG) Log.e(DEB, "receiving custom sequence");
                            toBeRead = MainActivity.ddCount;
                            toReadInits = true;
                            lastInitState = MainActivity.csReceiveSeq;
                            MainActivity.cState.set(csAwaitAnswer);

                            // sub cases to handle incoming byte INIT
                        } else if (MainActivity.cState.get() == csAwaitAnswer) {
                            if (MyDebug.LOG) Log.e(DEB, "receiving answer of our challenge ");
                            toBeRead = 8;
                            toReadInits = true;
                            lastInitState = MainActivity.csAwaitAnswer;
                            MainActivity.cState.set(csNone);

                        }

                        if (toReadInits) {  // DRY approach to the INIT States
                            bytes = 0;
                            for (int i = 0; i < toBeRead; i++) {
                                buffer[bytes] = (byte) mmInStream.read();
                                if (MyDebug.LOG)
                                    Log.e(DEB, "init byte " + bytes + " / " +
                                            toBeRead + " : " + buffer[bytes]);
                                bytes++;
                            }
                            mHandler.obtainMessage(
                                    MainActivity.MESSAGE_INIT, bytes, lastInitState, buffer.clone()
                            ).sendToTarget();
                            bytes = 0;
                            toReadInits = false;
                            if (MyDebug.LOG)
                                Log.e(DEB, "init answer from wt: " + Arrays.toString(buffer));
                        }
                    } // enf of init states


                } catch (IOException e) {
                    if (MyDebug.LOG) Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothChatService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer
         *            The bytes to write
         */
        void write(byte buffer) {
            try {
                if (MyDebug.LOG) Log.e(DEB, "Sending: " + String.valueOf(buffer));
                byte mess[] = {buffer};
                mmOutStream.write(mess);
            } catch (IOException e) {
                if (MyDebug.LOG) Log.e(TAG, "Exception during write", e);
            }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (MyDebug.LOG) Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
