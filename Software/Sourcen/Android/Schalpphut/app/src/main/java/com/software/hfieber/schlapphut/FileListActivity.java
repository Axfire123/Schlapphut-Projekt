package com.software.hfieber.schlapphut;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.software.hfieber.schlapphut.classes.Constants;
import com.software.hfieber.schlapphut.classes.GlobalState;
import com.software.hfieber.schlapphut.classes.SlpFile;
import com.software.hfieber.schlapphut.classes.TalkMaster;

import java.util.ArrayList;

public class FileListActivity extends AppCompatActivity {


    ListView listViewFileList;

    TalkMaster talkMaster;
    ArrayAdapter adapter;
    ArrayList<SlpFile> FileList = new ArrayList<>();
    int fileListType = 0;
    ArrayList<SlpFile> filesToDownload = new ArrayList<>();
    ArrayList<SlpFile> filesToRemove = new ArrayList<>();
    final Context context = this;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        GlobalState gb = (GlobalState) getApplicationContext();
        talkMaster = gb.talkMaster;
        talkMaster.SetActivityHandler(mHandler);

        // Extras abholen
        Bundle bundle = this.getIntent().getExtras();
        if(bundle != null)
        {
            fileListType = bundle.getInt("fileListType");
        }
        else
        {
            Toast.makeText(this,"Activity hat keine Daten empfangen!", Toast.LENGTH_SHORT).show();
        }

        if(fileListType == 1)
            setTitle("Gefundene Track-Files");
        else if(fileListType == 2)
            setTitle("Gefundene Point-Files");


        listViewFileList = findViewById(R.id.listViewFileList);
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, FileList);
        listViewFileList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        listViewFileList.setAdapter(adapter);


        FloatingActionButton floatingActionButton = findViewById(R.id.fabDownloadFiles);
        FloatingActionButton floatingActionButtonRemoveFiles = findViewById(R.id.fabRemoveFiles);

        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                filesToDownload.clear();
                // markierte Items in neue Liste schreiben
                int len = listViewFileList.getCount();
                SparseBooleanArray checked = listViewFileList.getCheckedItemPositions();
                for (int i = 0; i < len; i++)
                    if (checked.get(i)) {
                        SlpFile item = FileList.get(i);
                        filesToDownload.add(item);
                    }
                //

                Intent intent = new Intent(getApplicationContext(), DowloadFilesActivity.class);
                Bundle bundle1 = new Bundle();
                bundle1.putSerializable("filesToDownload",filesToDownload);
                bundle1.putInt("fileListType",fileListType);
                intent.putExtras(bundle1);
                startActivity(intent);

            }
        });


        floatingActionButtonRemoveFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                filesToRemove.clear();
                // markierte Items in neue Liste schreiben
                int len = listViewFileList.getCount();
                SparseBooleanArray checked = listViewFileList.getCheckedItemPositions();
                for (int i = 0; i < len; i++)
                    if (checked.get(i)) {
                        SlpFile item = FileList.get(i);
                        filesToRemove.add(item);
                    }
                //
                if(filesToRemove.size() > 0)
                    removeSlpFilesDialog();

            }
        });


        getFileList();
    }

    void getFileList()
    {
        if(fileListType == 1)
            talkMaster.getTrackFileList();
        else if(fileListType == 2)
            talkMaster.getPointFileList();
    }


    void removeSlpFilesDialog()
    {
        String text = "Ausgewählte Dateien wirklich löschen?";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(text)
                .setPositiveButton("ja, ich will", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        talkMaster.removeSlpFiles(filesToRemove);
                    }
                })
                .setNegativeButton("ne, doch nicht", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case Constants.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), (String)msg.obj,Toast.LENGTH_SHORT).show();
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

                        case Constants.STUFF_TRACK_FILE_LIST:
                            FileList = (ArrayList<SlpFile>) msg.obj;
                            if(FileList.size() > 0)
                            {
                                //listViewFileList.invalidateViews();
                                //adapter.notifyDataSetChanged();
                                adapter = new ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, FileList);
                                listViewFileList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                                listViewFileList.setAdapter(adapter);

                            }
                            else
                            {
                                Toast.makeText(getApplicationContext(), "Keine Track-Files gefunden/empfangen", Toast.LENGTH_SHORT).show();
                            }

                            break;

                        case Constants.STUFF_POINT_FILE_LIST:
                            FileList = (ArrayList<SlpFile>) msg.obj;
                            if(FileList.size() > 0)
                            {
                                //listViewFileList.invalidateViews();
                                //adapter.notifyDataSetChanged();
                                adapter = new ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, FileList);
                                listViewFileList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                                listViewFileList.setAdapter(adapter);
                            }
                            else
                            {
                                Toast.makeText(getApplicationContext(), "Keine Point-Files gefunden/empfangen", Toast.LENGTH_SHORT).show();
                            }

                            break;

                        case Constants.STUFF_REMOVE_FILE_COMPLETE:
                            int numFiles = msg.arg2;
                            int deletedFiles = (int)msg.obj;
                            Toast.makeText(getApplicationContext(), String.format("Es wurden %d von %d Dateien gelöscht", deletedFiles, numFiles), Toast.LENGTH_SHORT).show();
                            getFileList();
                            break;

                        case Constants.STUFF_REMOVE_FILE_ERROR:
                            Toast.makeText(getApplicationContext(), "fehler beim löschen der Dateien", Toast.LENGTH_SHORT).show();
                            break;

                    }
                    break;
            }
        }
    };






}
