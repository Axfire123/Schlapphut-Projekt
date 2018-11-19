package com.software.hfieber.schlapphut;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.software.hfieber.schlapphut.classes.Constants;
import com.software.hfieber.schlapphut.classes.GlobalState;
import com.software.hfieber.schlapphut.classes.ParameterSettings;
import com.software.hfieber.schlapphut.classes.SlpFile;
import com.software.hfieber.schlapphut.classes.TalkMaster;

import java.util.ArrayList;
import java.util.List;

import static android.os.SystemClock.elapsedRealtime;

public class MainActivity extends AppCompatActivity {

    private GlobalState globalState;
    private TalkMaster talkMaster;

    private Button buttonClientConnect;
    private Button buttonTrackList;
    private Button buttonPointList;
    private Button buttonConnectWlan;
    private Button buttonParameterSettings;
    private Button buttonParameterAbfragen;
    private TextView textViewConnectedWifi;
    private Button buttonDowloadLogFile;

    private ParameterSettings parameterSettings;

    private static final String SCHLAPPHUT_SSID = "Schlapphut";

    private static final int REQUEST_PARAMETER_SETTINGS = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        globalState = (GlobalState) getApplicationContext();
        talkMaster = globalState.talkMaster;
        talkMaster.SetActivityHandler(mHandler);

        buttonClientConnect = findViewById(R.id.button1);
        buttonTrackList  = findViewById(R.id.button2);
        buttonPointList  = findViewById(R.id.button3);
        buttonConnectWlan = findViewById(R.id.button4);
        buttonParameterSettings = findViewById(R.id.button5);
        textViewConnectedWifi = findViewById(R.id.textView8);
        buttonParameterAbfragen = findViewById(R.id.button6);
        buttonDowloadLogFile = findViewById(R.id.button7);

        buttonClientConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectToServer();
            }
        });

        buttonTrackList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(CheckStoragePermission())
                {
                    Intent intent = new Intent(getApplicationContext(), FileListActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putInt("fileListType",1);
                    intent.putExtras(bundle);
                    startActivity(intent);
                }

            }
        });

        buttonPointList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(CheckStoragePermission())
                {
                    Intent intent = new Intent(getApplicationContext(), FileListActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putInt("fileListType",2);
                    intent.putExtras(bundle);
                    startActivity(intent);
                }

            }
        });


        buttonConnectWlan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectWlan();
            }
        });

        buttonParameterSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), ParameterSettingsActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("parameterSettings", parameterSettings);
                intent.putExtras(bundle);
                startActivityForResult(intent,REQUEST_PARAMETER_SETTINGS);
            }
        });

        buttonParameterAbfragen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                parameterSettings = null;
                InitButton();
                talkMaster.GetParameterSettings();
            }
        });

        buttonDowloadLogFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), DowloadFilesActivity.class);
                Bundle bundle = new Bundle();
                ArrayList<SlpFile> filesToDownload = new ArrayList<SlpFile>();
                filesToDownload.add(new SlpFile("logger.txt", SlpFile.SlpType.LogFile));
                bundle.putSerializable("filesToDownload",filesToDownload); // fuege "irgendein" SLP-File hinzu. SLP-File wird eigentlich nicht benötigt...
                bundle.putInt("fileListType",3);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        RefreshTextViewConnectedWifi();
        InitButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case REQUEST_PARAMETER_SETTINGS:
                if(resultCode == Activity.RESULT_OK){
                    // Extras abholen
                    Bundle bundle = data.getExtras();
                    if(bundle != null)
                        parameterSettings = (ParameterSettings) bundle.getSerializable("parameterSettings");
                    else
                        Toast.makeText(this,"Activity hat keine Daten empfangen!", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    void ConnectToServer()
    {
        talkMaster.connect("192.168.4.22", 9001);
    }


    void ConnectWlan()
    {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);

                // Ist Wifi im Handy eingeschaltet
                if(!wifiManager.isWifiEnabled())
                {
                    wifiManager.setWifiEnabled(true);
                }

                // Quelle (Stand 2018-07-24): https://stackoverflow.com/a/8818490
                String ssid = "Schlapphut";
                String key = "";

                WifiConfiguration wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = String.format("\"%s\"", ssid);
                //wifiConfig.preSharedKey = String.format("\"%s\"", key);               // WPA network
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);    // open network
                wifiConfig.hiddenSSID = true;
                wifiConfig.status = WifiConfiguration.Status.ENABLED;
                wifiConfig.priority = 9999;

                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                for( WifiConfiguration i : list ) {
                    if(i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                        wifiManager.disconnect();
                        mHandler.obtainMessage(Constants.STUFF_WIFI, Constants.STUFF_WIFI_CONNECTING, -1 , "").sendToTarget();
                        wifiManager.enableNetwork(i.networkId, true);
                        wifiManager.reconnect();
                        break;
                    }
                }

                long time1 = elapsedRealtime();   //  Returns milliseconds since boot, including time spent in sleep.
                long time2 = 0;

                while (true)
                {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String ssidConnection = wifiInfo.getSSID();
                    SupplicantState supplicantState = wifiInfo.getSupplicantState();

                    if(ssidConnection.contains(ssid))
                    {
                        if(supplicantState == SupplicantState.COMPLETED)
                        {
                            mHandler.obtainMessage(Constants.STUFF_WIFI, Constants.STUFF_WIFI_CONNECTED, -1, ssid).sendToTarget();
                            break;
                        }
                    }

                    time2 = elapsedRealtime();
                    if((time2 - time1) > 10000)
                    {
                        mHandler.obtainMessage(Constants.STUFF_WIFI, Constants.STUFF_WIFI_CONNECTION_ERROR, -1, ssid).sendToTarget();
                        break;
                    }
                }
            }
        });
        thread.start();
    }

    String getConnectedWifiSSID(){
        String ssid;
        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        ssid = wifiInfo.getSSID();
        if(ssid != null)
            return ssid;
        else
            return "";
    }

    boolean ConnectedWithRightWifi()
    {
       boolean res;
       String ssid = getConnectedWifiSSID().toLowerCase();
       String ssid2 = ("\"" + SCHLAPPHUT_SSID.toLowerCase() + "\"");
       //if(ssid == ssid2)
        if(ssid.contentEquals(ssid2))
           res = true;
       else
           res = false;
       return res;
    }

    void InitButton()
    {
        boolean isWifiConnected = ConnectedWithRightWifi();
        boolean isSocketConnected = talkMaster.isConnected();

        buttonConnectWlan.setEnabled(!isWifiConnected);

        if(isWifiConnected && (!isSocketConnected))
            buttonClientConnect.setEnabled(true);
        else
            buttonClientConnect.setEnabled(false);

        buttonParameterAbfragen.setEnabled(isSocketConnected);
        buttonTrackList.setEnabled(isSocketConnected);
        buttonPointList.setEnabled(isSocketConnected);
        buttonDowloadLogFile.setEnabled(isSocketConnected);

        if(parameterSettings != null)
        {
            buttonParameterSettings.setEnabled(true);
            if(parameterSettings.isPointModusAvailable())
                buttonPointList.setEnabled(true);
        }
        else
        {
            buttonParameterSettings.setEnabled(false);
        }
    }

    void RefreshTextViewConnectedWifi(){
        String ssid = getConnectedWifiSSID();
        if(ssid != "")
            textViewConnectedWifi.setText(ssid);
        else
            textViewConnectedWifi.setText("... mit keinem WLAN verbunden ...");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }




    /**
     * Püft ob Storage Permission vorliegt, wenn nicht, wird der Nutzer nach der Erteilung gefragt
     * @return True, wenn Permission vorliegt
     */
    private boolean CheckStoragePermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            return true;
        }
        else {
            // Frage User nach Erteilung der Berechtigung
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            return false;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Constants.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    Toast.makeText(this, "starte Track-Liste erneut", Toast.LENGTH_SHORT).show();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "ohne Berechtigung wird das hier nixx...", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }


    @Override
    public void onBackPressed() {

        // Dialog erstellen um Nutzer zu fragen, ob App wiklich beendet werden soll
        android.support.v7.app.AlertDialog.Builder alert = new android.support.v7.app.AlertDialog.Builder(this);
        alert.setTitle("Anwendung beenden");
        // this is set the view from XML inside AlertDialog
        alert.setMessage("Möchten Sie die App wirklich beenden?");
        // disallow cancel of AlertDialog on click of back button and outside touch
        alert.setCancelable(false);
        alert.setNegativeButton("Nein", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                //finish();
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        android.support.v7.app.AlertDialog dialog = alert.create();
        dialog.show();

        //super.onBackPressed();
    }


    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            //super.handleMessage(msg);
            Intent intent;
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case TalkMaster.STATE_CONNECTED:
                            Toast.makeText(getApplicationContext(), "Verbindung hergestellt",Toast.LENGTH_SHORT).show();
                            InitButton();
                            //talkMaster.GetParameterSettings();
                            break;
                        case TalkMaster.STATE_CONNECTING:
                            //Toast.makeText(getApplicationContext(), "Verbindung wird hergestellt",Toast.LENGTH_SHORT).show();
                            break;
                        case TalkMaster.STATE_NONE:
                            InitButton();
                            break;
                    }
                    break;

                case Constants.MESSAGE_TOAST:
                    Bundle bundle = msg.getData();
                    Toast.makeText(getApplicationContext(), bundle.getString(Constants.TOAST),Toast.LENGTH_SHORT).show();
                    break;

                case Constants.STUFF_FROM_SERVER:
                    switch (msg.arg1)
                    {
                        case Constants.NO_STUFF_NOT_CONNECTED:
                            break;

                        case Constants.NO_STUFF_TIMEOUT:
                            break;

                        case Constants.NO_STUFF_UNKNOWN_ERROR:
                            break;

                        case Constants.NO_STUFF_NOT_FOUND:
                            Toast.makeText(getApplicationContext(), "Datei oder Verzeichnis nicht gefunden", Toast.LENGTH_SHORT).show();
                            break;

                        case Constants.STUFF_REMOVE_FILE_COMPLETE:
                            Toast.makeText(getApplicationContext(), "Dateien wurden erfolgreich gelöscht", Toast.LENGTH_SHORT).show();
                            break;

                        case Constants.STUFF_REMOVE_FILE_ERROR:
                            Toast.makeText(getApplicationContext(), "fehler beim löschen der Dateien", Toast.LENGTH_SHORT).show();
                            break;

                    }
                    break;

                case Constants.STUFF_WIFI:
                    switch (msg.arg1)
                    {
                        case Constants.STUFF_WIFI_CONNECTED:
                            RefreshTextViewConnectedWifi();
                            InitButton();
                            String ssid = (String)msg.obj;
                            Toast.makeText(getApplicationContext(), "Verbunden mit " + ssid, Toast.LENGTH_SHORT).show();
                            break;

                        case Constants.STUFF_WIFI_CONNECTION_ERROR:
                            buttonConnectWlan.setEnabled(true);
                            ssid = (String)msg.obj;
                            Toast.makeText(getApplicationContext(), "Verbindung mit " + ssid + " konnte nicht hergestellt werden!", Toast.LENGTH_SHORT).show();
                            RefreshTextViewConnectedWifi();
                            break;

                        case Constants.STUFF_WIFI_CONNECTING:
                            buttonConnectWlan.setEnabled(false);
                            textViewConnectedWifi.setText("verbinde ...");
                            break;
                    }
                    break;

                case Constants.STUFF_PARAMETER_SETTINGS_RECEIVED:
                    parameterSettings = (ParameterSettings)msg.obj;
                    InitButton();
                    break;

                case Constants.STUFF_PARAMETER_SETTINGS_RECEIVING_ERROR:
                    Toast.makeText(getApplicationContext(), "Parameter Settings konnten nicht abgefragt werden!", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


}
