package com.software.hfieber.schlapphut.classes;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;


import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created on 11.12.2017.
 */

public class FileIO {

    private final Handler mHandler;
    private Context mContext;

    public static final int READ_TXT = 1;
    public static final int READ_XML = 2;
    public static final int READ_GPX = 3;
    public static final int WRITE_TXT = 4;
    public static final int WRITE_XML = 5;
    public static final int WRITE_GPX = 6;

    public static final String RESULT_KEY = "result";
    public static final String ERROR_KEY = "error";
    public static final String EXTRA_KEY = "extra";

    public static final int ERROR_NULL = 0;
    public static final int ERROR_NO_STORAGE_PERMISSION = 1;
    public static final int ERROR_STORAGE_NOT_WRITEABLE = 2;
    public static final int ERROR_STORAGE_NOT_READABLE = 3;
    public static final int ERROR_WRITING = 4;
    public static final int ERROR_READING = 5;
    public static final int ERROR_FILE_NOT_EXIST = 6;


    public FileIO(Context context, Handler handler){
        mContext = context;
        mHandler = handler;
    }


    /**
     * Versendet eine Nachricht über den Handler an eine Activity
     * @param action
     * @param res
     */
    private void SendMessage(int action, int error, boolean res){
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(action);
        Bundle bundle = new Bundle();
        bundle.putBoolean(RESULT_KEY, res);
        bundle.putInt(ERROR_KEY, error);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }


    private void SendMessage(int action, int error, boolean res, File writedFile){
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(action);
        Bundle bundle = new Bundle();
        bundle.putBoolean(RESULT_KEY, res);
        bundle.putInt(ERROR_KEY, error);
        bundle.putSerializable(EXTRA_KEY, writedFile);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }








    public void WriteTxt(String txt, String fileName, int type){
        // Wenn keine Berechtigung zum schreiben auf Speicher, breche ab
        if(CheckStoragePermission() == false){
            SendMessage(WRITE_TXT,ERROR_NO_STORAGE_PERMISSION,false);
            return;
        }
        // Wenn External Storage momentan nicht beschreibar ist, breche ab
        if(!isExternalStorageWritable()){
            SendMessage(WRITE_TXT,ERROR_STORAGE_NOT_WRITEABLE,false);
            return;
        }

        // Thread erstellen und starten, welcher File schreibt
        TxtWriterThread thread = new TxtWriterThread(txt,fileName, type);
        thread.start();
    }



    public boolean DeleteAllTXT(){

        // Pfad erstellen
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + Constants.PATH_TRACKS_SLP);

        return DeleteFile(myDir);
    }


    private boolean DeleteFile(File file){

        if(!CheckStoragePermission())
            return false;
        if(!isExternalStorageWritable())
            return false;

        if (file.exists ()){

            if (file.isDirectory())
            {
                // lösche alle Dateien in dem Verzeichnis
                String[] children = file.list();
                for (int i = 0; i < children.length; i++)
                {
                    new File(file, children[i]).delete();
                }
            }

            // lösche des File/Verzeichnis
            return file.delete();
        }
        else
            return false;
    }


    /**
     * Püft ob Storage Permission vorliegt, wenn nicht, wird der Nutzer nach der Erteilung gefragt
     * @return True, wenn Permission vorliegt
     */
    private boolean CheckStoragePermission(){
        if(ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            return true;
        }
        else {
            // Frage User nach Erteilung der Berechtigung
            //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            return false;
        }
    }


    private String getDateTimeString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }











    /**
     * Thread, welcher in eine TXT Datei schreibt
     */
    private class TxtWriterThread extends Thread{

        private String txtToSave;
        private String fileName;
        private int type;

        public  TxtWriterThread(String txt, String fileName, int type){
            txtToSave = txt;
            this.fileName = fileName;
            this.type = type;
        }

        public void run(){
            // Pfad erstellen und ggf. Verzeichnis erstellen
            String root = Environment.getExternalStorageDirectory().toString();
            File myDir;
            if(type == 1)
                myDir = new File(root + Constants.PATH_TRACKS_SLP);
            else if(type == 2)
                myDir = new File(root + Constants.PATH_POINTS_SLP);
            else
                myDir = new File(root + Constants.PATH_LOG_FILE);
            myDir.mkdirs();

            File file = new File (myDir, fileName);
            if (file.exists ())
                file.delete ();
            try {
                FileOutputStream out = new FileOutputStream(file);
                out.write(txtToSave.getBytes());
                out.flush();
                out.close();

                // Tell the media scanner about the new file so that it is immediately available to the user.
                MediaScannerConnection.scanFile(mContext, new String[] { file.toString() }, null, new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        //Log.i("ExternalStorage", "Scanned " + path + ":");
                        //Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
                SendMessage(WRITE_TXT,ERROR_NULL,true, file);
                return;
            } catch (Exception e) {
                SendMessage(WRITE_TXT,ERROR_WRITING,false);
                return;
            }
        }
    }

}

