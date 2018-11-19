package com.software.hfieber.schlapphut.classes;

import android.util.Log;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

import static android.os.SystemClock.elapsedRealtime;

public class TCPClient {

    public static final int NOT_CONNECTED = 0;
    public static final int CONNECTED = 1;
    public static final int CONNECTION_FAILED = 2;
    public static final int ANOTHER_ERROR = 3;

    private String serverMessage;
    private String serverIP;
    private int serverPort;
    private OnMessage mMessageListener = null;
    private boolean mRun = false;
    private Socket socket;

    PrintWriter out;
    BufferedReader in;

    /**
     *  Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TCPClient(OnMessage listener) {
        mMessageListener = listener;
    }

    /**
     * Sends the message entered by client to the server
     * @param message text entered by client
     */
    public void sendMessage(String message){
        if (out != null && !out.checkError()) {
            out.println(message);
            out.flush();
        }
    }

    public String readMessage(long timeOut)
    {

        long time1 = elapsedRealtime();   //  Returns milliseconds since boot, including time spent in sleep.
        long time2 = 0;
        long delta = 0;
        String msg;

        while (true)
        {
            time2 = elapsedRealtime();

            try{
                if(in.ready())
                {
                    msg = in.readLine();
                    if(msg != null)
                        return msg;
                }
                else
                {
                    //if( (time2 - time1) >= timeOut)
                    delta = time2 - time1;
                    if( (delta) >= timeOut)
                    {
                        return null;
                    }
                }
            }
            catch (Exception ex)
            {
                delta = time2 - time1;
                //if( (time2 - time1) >= timeOut)
                if( (delta) >= timeOut)
                {
                    return null;
                }
            }
        }
    }

    public void clearBuffer()
    {
        /*
        try{
            while (in.ready())
                in.read();
        }
        catch (Exception ex)
        {

        }
        */
    }

    public void stopClient(){
        mRun = false;
    }

    public void reset()
    {
        socket = null;
        in = null;
        out = null;
    }


    public boolean isConnected(){
        if(socket != null)
        {
            return socket.isConnected();
        }
        else
            return false;
    }


    public boolean connect(String ip, int port) {

        this.serverIP = ip;
        this.serverPort = port;


        try {
            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(serverIP);

            Log.e("TCP Client", "C: Connecting...");

            //create a socket to make the connection with the server
            this.socket = new Socket(serverAddr, serverPort);

            //send the message to the server
            this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream())), true);

            Log.e("TCP Client", "C: Sent.");

            Log.e("TCP Client", "C: Done.");

            //receive the message which the server sends back
            this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

        } catch (Exception e) {

            Log.e("TCP", "C: Error", e);
            return false;
        }
        return true;
    }


    public void connectAndRun(String ip, int port) {

        this.serverIP = ip;
        this.serverPort = port;

        mRun = true;

        try {
            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(serverIP);

            Log.e("TCP Client", "C: Connecting...");

            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, serverPort);

            // Statusmeldung ueber Verbindung rausgeben
            if(mMessageListener != null)
                mMessageListener.messageStatus(CONNECTED);

            try {

                //send the message to the server
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                Log.e("TCP Client", "C: Sent.");

                Log.e("TCP Client", "C: Done.");

                //receive the message which the server sends back
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                //in this while the client listens for the messages sent by the server
                while (mRun) {
                    serverMessage = in.readLine();

                    if (serverMessage != null && mMessageListener != null) {
                        //call the method messageReceived from MyActivity class
                        mMessageListener.messageReceived(serverMessage);
                    }
                    serverMessage = null;

                }

                Log.e("RESPONSE FROM SERVER", "S: Received Message: '" + serverMessage + "'");

            } catch (Exception e) {

                Log.e("TCP", "S: Error", e);

                // Statusmeldung ueber Fehler rausgeben
                if(mMessageListener != null)
                    mMessageListener.messageStatus(ANOTHER_ERROR);

            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                socket.close();

                // Statusmeldung ueber Verbindung rausgeben
                if(mMessageListener != null)
                    mMessageListener.messageStatus(NOT_CONNECTED);
            }

        } catch (Exception e) {

            Log.e("TCP", "C: Error", e);

            // Statusmeldung ueber Verbindung rausgeben
            if(mMessageListener != null)
                mMessageListener.messageStatus(CONNECTION_FAILED);

        }

    }


    /*
    original

    public void connectAndRun() {

        mRun = true;

        try {
            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(serverIP);

            Log.e("TCP Client", "C: Connecting...");

            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, serverPort);

            try {

                //send the message to the server
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                Log.e("TCP Client", "C: Sent.");

                Log.e("TCP Client", "C: Done.");

                //receive the message which the server sends back
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                //in this while the client listens for the messages sent by the server
                while (mRun) {
                    serverMessage = in.readLine();

                    if (serverMessage != null && mMessageListener != null) {
                        //call the method messageReceived from MyActivity class
                        mMessageListener.messageReceived(serverMessage);
                    }
                    serverMessage = null;

                }

                Log.e("RESPONSE FROM SERVER", "S: Received Message: '" + serverMessage + "'");

            } catch (Exception e) {

                Log.e("TCP", "S: Error", e);

            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                socket.close();
            }

        } catch (Exception e) {

            Log.e("TCP", "C: Error", e);

        }

    }
    */

    //Declare the interface. The method messageReceived(String message) will must be implemented in the MyActivity
    //class at on asynckTask doInBackground
    public interface OnMessage {
        public void messageReceived(String message);
        public void messageStatus(int status);
    }
}
