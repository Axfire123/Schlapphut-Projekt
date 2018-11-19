package com.software.hfieber.schlapphut;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.software.hfieber.schlapphut.classes.Constants;
import com.software.hfieber.schlapphut.classes.FileIO;
import com.software.hfieber.schlapphut.classes.GlobalState;
import com.software.hfieber.schlapphut.classes.SlpFile;
import com.software.hfieber.schlapphut.classes.TalkMaster;

import java.io.File;
import java.util.ArrayList;

public class DowloadFilesActivity extends AppCompatActivity {

    int fileListType = 0;
    ArrayList<SlpFile> filesToDownload = new ArrayList<>();
    GlobalState globalState;
    TalkMaster talkMaster;
    FileIO fileIO;

    ProgressBar progressBarLines;
    ProgressBar progressBarFiles;
    TextView textViewLines;
    TextView textViewFiles;
    TextView textViewFileName;

    int filesDownloadedCounter = 0;
    int numberOfFileLines = 0;

    Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dowload_files);

        // Display dauerhaft eingeschaltet lassen. Quelle (Stand 02.10.2017): https://developer.android.com/training/scheduling/wakelock.html
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Extras abholen
        Bundle bundle = this.getIntent().getExtras();
        if(bundle != null)
        {
            fileListType = bundle.getInt("fileListType");
            filesToDownload = (ArrayList<SlpFile>)bundle.getSerializable("filesToDownload");
        }
        else
        {
            Toast.makeText(this,"Activity hat keine Daten empfangen!", Toast.LENGTH_SHORT).show();
        }

        globalState = (GlobalState) getApplicationContext();
        talkMaster = globalState.talkMaster;
        talkMaster.SetActivityHandler(mHandler);

        fileIO = new FileIO(this, mHandlerFileIO);


        progressBarLines = findViewById(R.id.progressBar);
        progressBarFiles = findViewById(R.id.progressBar2);
        textViewLines = findViewById(R.id.textView2);
        textViewFiles = findViewById(R.id.textView4);
        textViewFileName = findViewById(R.id.textView6);

        filesDownloadedCounter = 0;
        progressBarLines.setProgress(0);
        textViewLines.setText("0/0");

        progressBarFiles.setMax(filesToDownload.size());
        progressBarFiles.setProgress(filesDownloadedCounter);
        textViewFiles.setText("0/" + filesToDownload.size());

        SlpFile slpFile = filesToDownload.get(filesDownloadedCounter);
        if(fileListType == 1)
            talkMaster.getTrackFile(slpFile);
        else if(fileListType == 2)
            talkMaster.getPointFile(slpFile);
        else if(fileListType == 3)
            talkMaster.getLogFile();

        textViewFileName.setText(slpFile.getName());

    }


    void ConvertSlpToTxt(int tOP, ArrayList<String> slpFile, String fileName)
    {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < slpFile.size(); i++)
        {
            sb.append(slpFile.get(i));
            sb.append("\r\n");
        }

        fileIO.WriteTxt(sb.toString(),fileName, tOP);
    }



    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            //super.handleMessage(msg);
            Intent intent;
            android.support.v7.app.AlertDialog.Builder alert;
            android.support.v7.app.AlertDialog dialog;

            switch (msg.what) {

                case Constants.MESSAGE_TOAST:
                    //String txt = (String)msg.obj;
                    Toast.makeText(getApplicationContext(), (String)msg.obj,Toast.LENGTH_SHORT).show();
                    break;

                case Constants.STUFF_FROM_SERVER:
                    switch (msg.arg1)
                    {
                        case Constants.NO_STUFF_NOT_CONNECTED:
                            Toast.makeText(getApplicationContext(), "keine Verbindung zum Server",Toast.LENGTH_SHORT).show();
                            break;

                        case Constants.NO_STUFF_TIMEOUT:
                            //Toast.makeText(getApplicationContext(), "Timeout beim Empfangen",Toast.LENGTH_SHORT).show();

                            alert = new android.support.v7.app.AlertDialog.Builder(context);
                            alert.setTitle("Fehler beim Downloaden");
                            // this is set the view from XML inside AlertDialog
                            alert.setMessage("Timeout bei der Übertragung!");
                            // disallow cancel of AlertDialog on click of back button and outside touch
                            alert.setCancelable(false);
                            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                            dialog = alert.create();
                            dialog.show();

                            break;

                        case Constants.NO_STUFF_UNKNOWN_ERROR:
                            //Toast.makeText(getApplicationContext(), "ein Fehler ist aufgetreten...",Toast.LENGTH_SHORT).show();

                            alert = new android.support.v7.app.AlertDialog.Builder(context);
                            alert.setTitle("Fehler beim Downloaden");
                            // this is set the view from XML inside AlertDialog
                            alert.setMessage("Sonstiger Fehler bei der Übertragung!");
                            // disallow cancel of AlertDialog on click of back button and outside touch
                            alert.setCancelable(false);
                            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                            dialog = alert.create();
                            dialog.show();
                            break;

                        case Constants.NO_STUFF_NOT_FOUND:
                            Toast.makeText(getApplicationContext(), "Datei oder Verzeichnis nicht gefunden", Toast.LENGTH_SHORT).show();
                            filesDownloadedCounter++;
                            textViewFiles.setText("" + filesDownloadedCounter + "/" + filesToDownload.size());
                            progressBarFiles.setProgress(filesDownloadedCounter);

                            if(filesDownloadedCounter < filesToDownload.size())
                            {
                                SlpFile slpFile = filesToDownload.get(filesDownloadedCounter);
                                if(fileListType == 1)
                                    talkMaster.getTrackFile(slpFile);
                                else if(fileListType == 2)
                                    talkMaster.getPointFile(slpFile);

                                textViewFileName.setText(slpFile.getName());
                            }
                            break;

                        case Constants.STUFF_TRACK_FILE_LIST:

                            break;

                        case Constants.STUFF_POINT_FILE_LIST:

                            break;

                        case Constants.STUFF_TRACK_SLP:
                            SlpFile file = (SlpFile) msg.obj;
                            ConvertSlpToTxt(1,file.getLines(),file.getName());
                            break;

                        case Constants.STUFF_POINT_SLP:
                            SlpFile file2 = (SlpFile) msg.obj;
                            ConvertSlpToTxt(2,file2.getLines(),file2.getName());
                            break;

                        case Constants.STUFF_LOG_FILE:
                            SlpFile file3 = (SlpFile) msg.obj;
                            ConvertSlpToTxt(3,file3.getLines(),file3.getName());
                            break;

                        case Constants.STUFF_FILE_NUMBER_OF_LINES:
                            numberOfFileLines = msg.arg2;
                            progressBarLines.setMax(numberOfFileLines);
                            break;

                        case Constants.STUFF_FILE_CURRENT_LINE:
                            textViewLines.setText("" + msg.arg2 + "/" + numberOfFileLines);
                            progressBarLines.setProgress(msg.arg2);
                            break;
                    }
                    break;


            }
        }
    };



    /**
     * The Handler that gets information back from the FileIO
     */
    private final Handler mHandlerFileIO = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            boolean res;
            int err;
            switch (msg.what) {


                case FileIO.WRITE_TXT:
                    res = msg.getData().getBoolean(FileIO.RESULT_KEY);
                    err = msg.getData().getInt(FileIO.ERROR_KEY);
                    if(res){
                        Toast.makeText(getApplicationContext(), "Textdatei gespeichert",Toast.LENGTH_SHORT).show();
                        filesDownloadedCounter++;
                        textViewFiles.setText("" + filesDownloadedCounter + "/" + filesToDownload.size());
                        progressBarFiles.setProgress(filesDownloadedCounter);
                    }
                    else {
                        if(err == FileIO.ERROR_NO_STORAGE_PERMISSION){
                            Toast.makeText(getApplicationContext(), "App hat keine Berechtigung auf um den Speicher zugreifen zu dürfen",Toast.LENGTH_SHORT).show();
                            CheckStoragePermission();
                        }
                    }

                    // wenn alle Daten uebertragen wurden, gehe zurück
                    if(filesDownloadedCounter >= filesToDownload.size())
                    {
                        Toast.makeText(getApplicationContext(), "alle Files übertragen",Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    else {
                        if(filesDownloadedCounter < filesToDownload.size())
                        {
                            SlpFile slpFile = filesToDownload.get(filesDownloadedCounter);
                            if(fileListType == 1)
                                talkMaster.getTrackFile(slpFile);
                            else if(fileListType == 2)
                                talkMaster.getPointFile(slpFile);

                            textViewFileName.setText(slpFile.getName());
                        }
                    }

                    break;
            }
        }
    };


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

}
