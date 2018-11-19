package com.software.hfieber.schlapphut.classes;

import android.app.Application;

public class GlobalState extends Application {


    public TalkMaster talkMaster;

    @Override
    public void onCreate() {
        super.onCreate();

        talkMaster = new TalkMaster();
    }

}
