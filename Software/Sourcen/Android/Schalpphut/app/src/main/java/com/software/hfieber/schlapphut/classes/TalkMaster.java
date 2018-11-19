package com.software.hfieber.schlapphut.classes;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;

import static com.software.hfieber.schlapphut.classes.SlpFile.SlpType.LogFile;
import static com.software.hfieber.schlapphut.classes.SlpFile.SlpType.Point;
import static com.software.hfieber.schlapphut.classes.SlpFile.SlpType.Track;

public class TalkMaster{

    private String serverIP;
    private int serverPort;
    private TCPClient mTcpClient;

    private Handler mHandler;

    public int mState;


    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    // public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device


    private ConnectThread mConnectThread;
    private TalkFileListThread mTalkFileListThread;
    private TalkFileThread mTalkFileThread;
    private TalkRemoveAllFilesThread mTalkRemoveAllFilesThread;
    private SendParameterSettingsThread mSendParameterSettingsThread;
    private GetParameterSettingsThread mGetParameterSettingsThread;


    public TalkMaster(){
        mState = STATE_NONE;
        mTcpClient = new TCPClient(OnMessageListener);
    }

    public void SetActivityHandler(Handler handler)
    {
        mHandler = handler;
    }



    public TCPClient.OnMessage OnMessageListener = new TCPClient.OnMessage() {
        @Override
        public void messageReceived(String message) {

        }

        @Override
        public void messageStatus(int status) {

        }
    };

    public boolean isConnected(){
        return mTcpClient.isConnected();
    }



    public synchronized void connect(String serverIP, int serverPort) {

        this.serverIP = serverIP;
        this.serverPort = serverPort;

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread();
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }


    public synchronized void getTrackFileList()
    {
        mTalkFileListThread = new TalkFileListThread(1,"",5000);
        //mTalkFileListThread.run();    // wird die run-Methode der Threadklasse direkt vom Main-Thread aufgerufen, laeuft die Methode auch im Main-Thread
        mTalkFileListThread.start();    // mit der start-Methode wird ein neuer Tread erzeugt, in welchem run laeuft
    }

    public synchronized void getPointFileList()
    {
        mTalkFileListThread = new TalkFileListThread(2,"",5000);
        //mTalkFileListThread.run();    // wird die run-Methode der Threadklasse direkt vom Main-Thread aufgerufen, laeuft die Methode auch im Main-Thread
        mTalkFileListThread.start();    // mit der start-Methode wird ein neuer Tread erzeugt, in welchem run laeuft
    }

    public synchronized void getTrackFile(SlpFile file)
    {
        mTalkFileThread = new TalkFileThread(file);
        mTalkFileThread.start();
    }

    public synchronized void getPointFile(SlpFile file)
    {
        mTalkFileThread = new TalkFileThread(file);
        mTalkFileThread.start();
    }

    public synchronized void getLogFile()
    {
        mTalkFileThread = new TalkFileThread(new SlpFile("logger.txt",LogFile));
        mTalkFileThread.start();
    }


    public synchronized void removeSlpFiles(ArrayList<SlpFile> files)
    {
        mTalkRemoveAllFilesThread = new TalkRemoveAllFilesThread(files);
        mTalkRemoveAllFilesThread.start();
    }



    private class ConnectThread extends Thread {
        public ConnectThread()
        {

        }
        public void run(){
            boolean res = mTcpClient.connect(serverIP, serverPort);
            if(res)
                setState(STATE_CONNECTED);
            else{
                mState = STATE_NONE;
                connectionFailed();
            }

        }
        public void cancel(){
            mTcpClient.reset();
        }
    }


    private class TalkFileListThread extends Thread {

        // Befehl, welcher zum Server geschickt wird
        String command;
        // Anzahl der zu empfangenen Zeilen
        int nLines = 0;
        // Liste in welche die Files gespeichert werden
        ArrayList<SlpFile> Files = new ArrayList<>();
        // Timeout in Millisekunden
        long timeOut = 0;
        // Extra
        String extra;

        int talkAbout;

        SlpFile.SlpType slpType;

        public TalkFileListThread(int talkAbout, String extra, long timeOut)
        {
            switch (talkAbout)
            {
                case 1: this.command = "GTFL"; slpType = Track; break;    // Get Track File List
                case 2: this.command = "GPFL"; slpType = Point; break;    // Get Point File List
            }
            this.extra = extra;
            this.timeOut = timeOut;
            this.talkAbout = talkAbout;
        }

        @Override
        public void run(){
            if(mState != STATE_CONNECTED)
            {
                // Breche ab, da nicht mit Server verbunden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_CONNECTED, -1).sendToTarget();
                return;
            }

            String message;

            try{

                // Loesche zunaechst den Eingangsbuffer
                mTcpClient.clearBuffer();

                // Send Command
                mTcpClient.sendMessage(command);

                // Lese Antwort mit Anzahl der Zeilen. Zum Beispiel: OK/536
                message = mTcpClient.readMessage(timeOut);
                if(message == null)
                {
                    mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                    return;
                }
                else if(!strncmp(message, "OK",2))
                {
                    // Prüfe ob Antwort No File ist -> File oder Direktory wurde nicht gefunden
                    if(strncmp(message, "NOF",3)){
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_FOUND, -1).sendToTarget();
                        return;
                    }
                }

                int indexSlasch = message.indexOf('/');     // hole Index von '/'
                String num = message.substring(++indexSlasch, message.length());   // Hole 536 als String
                nLines = Integer.parseInt(num); // String zu int

                // Antwort Senden
                mTcpClient.sendMessage("OK");



                // Zeilen einlesen
                for(int i = 0; i < nLines; i++)
                {
                    SlpFile slpFile = new SlpFile();
                    String line = mTcpClient.readMessage(timeOut);
                    if(line == null)
                    {
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                        return;
                    }
                    else
                    {

                        // in line steht der Filename und die Size in Byte, z.B: 20180712/123456


                        indexSlasch = line.indexOf('/');     // hole Index von '/'
                        String name = line.substring(0, indexSlasch);          // Hole 20180712 als String
                        num = line.substring(++indexSlasch, line.length());   // Hole 123456 als String
                        int size = Integer.parseInt(num); // String zu int

                        slpFile.setName(name);
                        slpFile.setSize(size);
                        slpFile.setType(slpType);

                        Files.add(slpFile);
                        mTcpClient.sendMessage("OK");
                    }
                }

                // Ergbiss und eingelesene Sachen melden
                switch (talkAbout)
                {
                    case 1: // Get File List
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_TRACK_FILE_LIST, -1, Files).sendToTarget();
                        break;

                    case 2: // Get File List
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_POINT_FILE_LIST, -1, Files).sendToTarget();
                        break;
                }

            }
            catch (Exception ex){
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_UNKNOWN_ERROR, -1).sendToTarget();
                return;
            }
        }
        public void cancel(){

        }
    }


    private class TalkRemoveAllFilesThread extends Thread {

        // Befehl, welcher zum Server geschickt wird
        String command;
        // Timeout in Millisekunden
        long timeOut = 0;

        ArrayList<SlpFile> files;


        public TalkRemoveAllFilesThread(ArrayList<SlpFile> files)
        {
            this.files = files;
            this.timeOut = 5000;
        }

        @Override
        public void run(){

            if(mState != STATE_CONNECTED)
            {
                // Breche ab, da nicht mit Server verbunden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_CONNECTED, -1).sendToTarget();
                return;
            }


            String message;
            int numDeletedFiles = 0;

            try
            {
                // Loesche zunaechst den Eingangsbuffer
                mTcpClient.clearBuffer();

                for(int i = 0; i < files.size(); i++)
                {
                    SlpFile file = files.get(i);

                    switch (file.getType())
                    {
                        case Track: this.command = String.format("RMT/%s", file.getName()); break;    // Remove Track SLP Dateien
                        case Point: this.command = String.format("RMP/%s", file.getName()); break;    // Remove Point SLP Dateien
                    }

                    // Send Command
                    mTcpClient.sendMessage(command);

                    // Lese Antwort mit Anzahl der Zeilen. Zum Beispiel: OK/536
                    message = mTcpClient.readMessage(timeOut);
                    if(message == null)
                    {
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                    }
                    else if(!strncmp(message, "OK",2))
                    {
                        // Prüfe ob Antwort No File ist -> File oder Direktory wurde nicht gefunden
                        if(strncmp(message, "NOF",3)){
                            mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_REMOVE_FILE_ERROR, -1, file.getName()).sendToTarget();
                        }
                    }
                    else if(strncmp(message, "OK",2))
                    {
                        numDeletedFiles++;
                    }

                    Thread.sleep(100);
                }

                if(numDeletedFiles > 0)
                    mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_REMOVE_FILE_COMPLETE, files.size(), numDeletedFiles).sendToTarget();


            }
            catch (Exception ex){
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_UNKNOWN_ERROR, -1).sendToTarget();
                return;
            }

        }
        public void cancel(){

        }
    }


    private class TalkFileThread extends Thread {

        // Befehl, welcher zum Server geschickt wird
        String command;
        // Anzahl der zu empfangenen Zeilen
        int nLines = 0;

        // Timeout in Millisekunden
        long timeOut = 0;

        SlpFile slpFile;

        public TalkFileThread(SlpFile slpFile)
        {
            this.slpFile = slpFile;
            switch (slpFile.getType())
            {
                case Track: this.command = "GTSLP/" + slpFile.getName(); break;    // Get Track SLP Datei
                case Point: this.command = "GPSLP/"+ slpFile.getName() ; break;    // Get Point SLP Datei
                case LogFile: this.command = "GLOG"; break;                         // Get Log-File
            }
            this.timeOut = 5000;
        }


        @Override
        public void run(){
            if(mState != STATE_CONNECTED)
            {
                // Breche ab, da nicht mit Server verbunden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_CONNECTED, -1).sendToTarget();
                return;
            }


            String message;

            try{
                // Loesche zunaechst den Eingangsbuffer
                mTcpClient.clearBuffer();

                // Send Command
                mTcpClient.sendMessage(command);

                // Lese Antwort mit Anzahl der Zeilen. Zum Beispiel: OK/536
                message = mTcpClient.readMessage(timeOut);
                if(message == null)
                {
                    mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                    return;
                }
                else if(!strncmp(message, "OK",2))
                {
                    // Prüfe ob Antwort No File ist -> File oder Direktory wurde nicht gefunden
                    if(strncmp(message, "NOF",3)){
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_FOUND, -1).sendToTarget();
                        return;
                    }
                }

                int indexSlasch = message.indexOf('/');     // hole Index von '/'
                String num = message.substring(++indexSlasch, message.length());   // Hole 536 als String
                nLines = Integer.parseInt(num); // String zu int

                // Antwort Senden
                mTcpClient.sendMessage("OK");

                // Max Files an GUI senden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_FILE_NUMBER_OF_LINES, nLines).sendToTarget();


                // Zeilen einlesen
                for(int i = 0; i < nLines; i++)
                {

                    String line = mTcpClient.readMessage(timeOut);
                    if(line == null)
                    {
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                        return;
                    }
                    else
                    {

                        slpFile.addLine(line);
                        mTcpClient.sendMessage("OK");

                        // Current Line an GUI senden
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_FILE_CURRENT_LINE, i+1).sendToTarget();
                    }
                }

                // Ergbiss und eingelesene Sachen melden
                switch (slpFile.getType())
                {
                    case Track: // Get Track SLP Datei
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_TRACK_SLP, -1, slpFile).sendToTarget();
                        break;

                    case Point: // Get Point SLP Datei
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_POINT_SLP, -1, slpFile).sendToTarget();
                        break;

                    case LogFile:
                        mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.STUFF_LOG_FILE, -1, slpFile).sendToTarget();
                        break;
                }

            }
            catch (Exception ex){
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_UNKNOWN_ERROR, -1).sendToTarget();
                return;
            }

        }
        public void cancel(){

        }
    }


    public void SendParameterSettings(ParameterSettings parameterSettings)
    {
        mSendParameterSettingsThread = new SendParameterSettingsThread(parameterSettings);
        mSendParameterSettingsThread.start();
    }

    public class SendParameterSettingsThread extends Thread{

        ParameterSettings parameterSettings;

        // Befehl, welcher zum Server geschickt wird
        StringBuilder command = new StringBuilder();

        // Timeout in Millisekunden
        long timeOut = 0;

        SendParameterSettingsThread(ParameterSettings parameterSettings){
            this.parameterSettings = parameterSettings;
            this.timeOut = 5000;
        }

        @Override
        public void run() {

            if(mState != STATE_CONNECTED)
            {
                // Breche ab, da nicht mit Server verbunden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_CONNECTED, -1).sendToTarget();
                return;
            }


            String message;

            try{
                // Loesche zunaechst den Eingangsbuffer
                mTcpClient.clearBuffer();

                // Command befuellen
                command.append("SSETT");
                command.append(Integer.toString(parameterSettings.getPointSpeicherIntervall()));    // im Arduinocode: wakeUpCounter
                command.append("/");
                command.append(Integer.toString(parameterSettings.getSchlafenNachLimaAus()));       // im Arduinocode: MAX_LIMA_OFF
                command.append("/");
                command.append(Integer.toString(parameterSettings.getSdCardWriteError()));          // im Arduinocode: MAX_SD_CARD_ERROR
                command.append("/");
                command.append(Integer.toString(parameterSettings.getAufwachIntervall()));          // im Arduinocode: SLEEP_TIME
                command.append("/");
                if(parameterSettings.isPointModusAvailable())                                       // im Arduinocode: POINT_MODE_AVAILABLE
                    command.append("1/");
                else
                    command.append("0/");


                // Send Command
                mTcpClient.sendMessage(command.toString());

                // Lese Antwort mit Anzahl der Zeilen. Zum Beispiel: OK/536
                message = mTcpClient.readMessage(timeOut);
                if(message == null)
                {
                    mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                    return;
                }
                else if(!strncmp(message, "OK",2))
                {
                    mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_SENDING_ERROR).sendToTarget();
                    return;
                }
                else {
                    mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_SENDED).sendToTarget();
                    return;
                }
            }
            catch (Exception ex){
                mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_SENDING_ERROR).sendToTarget();
                return;
            }
        }

        public void cancel(){

        }
    }


    public void GetParameterSettings(){
        mGetParameterSettingsThread = new GetParameterSettingsThread();
        mGetParameterSettingsThread.start();
    }

    public class GetParameterSettingsThread extends Thread{

        ParameterSettings parameterSettings;
        String command;
        int timeOut = 5000;

        GetParameterSettingsThread(){
            parameterSettings = new ParameterSettings();
            command = "GSETT";
        }

        @Override
        public void run() {
            if(mState != STATE_CONNECTED)
            {
                // Breche ab, da nicht mit Server verbunden
                mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_NOT_CONNECTED, -1).sendToTarget();
                return;
            }

            String message;

            try{

                // Loesche zunaechst den Eingangsbuffer
                mTcpClient.clearBuffer();

                // Send Command
                mTcpClient.sendMessage(command);

                // Lese Antwort mit Anzahl der Zeilen. Zum Beispiel: OK/536
                message = mTcpClient.readMessage(timeOut);
                if(message == null)
                {
                    mHandler.obtainMessage(Constants.STUFF_FROM_SERVER, Constants.NO_STUFF_TIMEOUT, -1).sendToTarget();
                    return;
                }
                else if(strncmp(message, "SETT",4))
                {
                    int state = 0;
                    int offset = 5;
                    String value_s = "";

                    // Stringaufbau: "SETT wakeUpCounter/MAX_LIMA_OFF/MAX_SD_CARD_ERROR/SLEEP_TIME/POINT_MODE_AVAILABLE/"
                    for(int i = 5; i < message.length(); i++)
                    {
                        if(message.charAt(i) == '/'){
                            value_s = message.substring(offset,i);
                            offset = i+1;

                            switch (state){
                                case 0: parameterSettings.setPointSpeicherIntervall(Integer.parseInt(value_s)); break;
                                case 1: parameterSettings.setSchlafenNachLimaAus(Integer.parseInt(value_s)); break;
                                case 2: parameterSettings.setSdCardWriteError(Integer.parseInt(value_s)); break;
                                case 3: parameterSettings.setAufwachIntervall(Integer.parseInt(value_s)); break;
                                case 4: parameterSettings.setPointModusAvailable(Boolean.parseBoolean(value_s)); break;
                            }
                            state++;
                        }
                    }
                    mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_RECEIVED, parameterSettings).sendToTarget();
                }
                else{
                    mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_RECEIVING_ERROR).sendToTarget();
                }
            }
            catch (Exception ex){
                mHandler.obtainMessage(Constants.STUFF_PARAMETER_SETTINGS_RECEIVING_ERROR).sendToTarget();
                return;
            }
        }

    }


    /// <summary>
    /// Vergleicht zwei Strings nZeichen lang
    /// </summary>
    /// <param name="text1">String 1</param>
    /// <param name="text2">String 2</param>
    /// <param name="nZeichen">Anzahl der zu vergleichenden Zeichen</param>
    /// <returns></returns>
    boolean strncmp(String text1, String text2, int nZeichen)
    {
        char c1;
        char c2;

        for (int i = 0; i < nZeichen; i++)
        {
            c1 = text1.charAt(i);
            c2 = text2.charAt(i);

            if (c1 != c2)
                return false;
        }

        return true;
    }


    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        //Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }


    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Verbindungsaufbau gescheitert");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

    }









}
