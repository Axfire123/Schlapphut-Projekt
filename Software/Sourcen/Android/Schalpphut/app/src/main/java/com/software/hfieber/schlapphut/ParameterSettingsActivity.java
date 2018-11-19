package com.software.hfieber.schlapphut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.software.hfieber.schlapphut.classes.Constants;
import com.software.hfieber.schlapphut.classes.GlobalState;
import com.software.hfieber.schlapphut.classes.ParameterSettings;
import com.software.hfieber.schlapphut.classes.TalkMaster;

public class ParameterSettingsActivity extends AppCompatActivity {

    Button buttonSenden;
    Button buttonAbbrechen;
    EditText editTextAufwachintervall;
    EditText editTextPointSpeicherIntervall;
    EditText editTextSchlafenNachLimaAus;
    EditText editTextSdCardWriteError;
    CheckBox checkBoxIsPointModusAvailable;

    ParameterSettings parameterSettings;

    Context context = this;

    TalkMaster talkMaster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parameter_settings);

        // Extras abholen
        Bundle bundle = this.getIntent().getExtras();
        if(bundle != null)
        {
            parameterSettings = (ParameterSettings) bundle.getSerializable("parameterSettings");
        }
        else
        {
            Toast.makeText(this,"Activity hat keine Daten empfangen!", Toast.LENGTH_SHORT).show();
        }

        GlobalState gb = (GlobalState) getApplicationContext();
        talkMaster = gb.talkMaster;
        talkMaster.SetActivityHandler(mHandler);

        buttonSenden = findViewById(R.id.button);
        buttonAbbrechen = findViewById(R.id.button6);
        editTextAufwachintervall = findViewById(R.id.editText4);
        editTextPointSpeicherIntervall = findViewById(R.id.editText2);
        editTextSchlafenNachLimaAus = findViewById(R.id.editText3);
        editTextSdCardWriteError = findViewById(R.id.editText5);
        checkBoxIsPointModusAvailable = findViewById(R.id.checkBox);

        buttonSenden.setOnClickListener(OnClickButtonSenden);
        buttonAbbrechen.setOnClickListener(OnClickButtonAbbrechen);

        UpdateView();
    }


    View.OnClickListener OnClickButtonSenden = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            CheckInputAndSend();
        }
    };


    View.OnClickListener OnClickButtonAbbrechen = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ActivityFinish(false);
        }
    };

    void UpdateView()
    {
        if(parameterSettings == null)
            return;

        editTextAufwachintervall.setText(Integer.toString(parameterSettings.getAufwachIntervall()));
        editTextPointSpeicherIntervall.setText(Integer.toString(parameterSettings.getPointSpeicherIntervall()));
        editTextSchlafenNachLimaAus.setText(Integer.toString(parameterSettings.getSchlafenNachLimaAus()));
        checkBoxIsPointModusAvailable.setChecked(parameterSettings.isPointModusAvailable());
        editTextSdCardWriteError.setText(Integer.toString(parameterSettings.getSdCardWriteError()));
    }


    void CheckInputAndSend()
    {
        int val = 0;
        String message = "";
        boolean showDialog = false;

        val =  Integer.parseInt(editTextAufwachintervall.getText().toString());
        if( (val < 0) || (val > 255))
        {
            message = "Aufwachintervall muss zwischen 0 und 255 Sekunden liegen";
            showDialog = true;
        }

        val =  Integer.parseInt(editTextPointSpeicherIntervall.getText().toString());
        if( (val < 0) || (val > 255))
        {
            message = "Point-Intervall muss zwischen 0 und 255 Sekunden liegen";
            showDialog = true;
        }

        val =  Integer.parseInt(editTextSchlafenNachLimaAus.getText().toString());
        if( (val < 0) || (val > 65535))
        {
            message = "Schlafen nachdem Lichtmaschine aus muss zwischen 0 und 65535 Sekunden liegen";
            showDialog = true;
        }

        val =  Integer.parseInt(editTextSdCardWriteError.getText().toString());
        if( (val < 0) || (val > 255))
        {
            message = "Anzahl maximaler SD-Kartenschreibfehler muss zwischen 0 und 255 Sekunden liegen";
            showDialog = true;
        }

        if(showDialog)
        {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            builder1.setMessage(message);
            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    "Yes",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
        }
        else
        {
            parameterSettings.setAufwachIntervall(Integer.parseInt(editTextAufwachintervall.getText().toString()));
            parameterSettings.setPointModusAvailable(checkBoxIsPointModusAvailable.isChecked());
            parameterSettings.setPointSpeicherIntervall(Integer.parseInt(editTextPointSpeicherIntervall.getText().toString()));
            parameterSettings.setSchlafenNachLimaAus(Integer.parseInt(editTextSchlafenNachLimaAus.getText().toString()));
            parameterSettings.setSdCardWriteError(Integer.parseInt(editTextSdCardWriteError.getText().toString()));

            // Sende
            talkMaster.SendParameterSettings(parameterSettings);
        }

    }

    private void ActivityFinish(boolean result){

        if(result)
        {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putSerializable("parameterSettings", parameterSettings);
            intent.putExtras(bundle);
            setResult(Activity.RESULT_OK, intent);
        }
        else
        {
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        return;
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case Constants.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), (String)msg.obj,Toast.LENGTH_SHORT).show();
                    break;

                case Constants.STUFF_PARAMETER_SETTINGS_SENDED:
                    Toast.makeText(getApplicationContext(), "Parameter erfolgreich gesendet",Toast.LENGTH_SHORT).show();
                    ActivityFinish(true);
                    break;

                case Constants.STUFF_PARAMETER_SETTINGS_SENDING_ERROR:
                    Toast.makeText(getApplicationContext(), "Das Senden ist fehlgeschlagen!",Toast.LENGTH_SHORT).show();
                    break;

                case Constants.STUFF_FROM_SERVER:
                    if(msg.arg1 == Constants.NO_STUFF_TIMEOUT)
                    {
                        Toast.makeText(getApplicationContext(), "Server antwortet nicht, Senden fehlgeschlagen!",Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

}
