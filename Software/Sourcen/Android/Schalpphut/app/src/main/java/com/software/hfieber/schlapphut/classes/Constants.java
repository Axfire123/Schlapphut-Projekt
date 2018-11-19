/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.software.hfieber.schlapphut.classes;


public interface Constants {


    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;  // Connection to a remote device lost - von Hauke
    public static final int STUFF_FROM_SERVER = 7;
    public static final int NO_STUFF_NOT_CONNECTED = 8;
    public static final int NO_STUFF_TIMEOUT = 9;
    public static final int STUFF_TRACK_FILE_LIST = 10;
    public static final int STUFF_TRACK_SLP = 11;
    public static final int STUFF_POINT_SLP = 12;
    public static final int NO_STUFF_UNKNOWN_ERROR = 13;
    public static final int STUFF_POINT_FILE_LIST = 14;
    public static final int STUFF_FILE_NUMBER_OF_LINES = 15;
    public static final int STUFF_FILE_CURRENT_LINE = 16;
    public static final int NO_STUFF_NOT_FOUND = 17;
    public static final int STUFF_REMOVE_FILE_COMPLETE = 18;
    public static final int STUFF_REMOVE_FILE_ERROR = 19;
    public static final int STUFF_WIFI = 20;
    public static final int STUFF_WIFI_CONNECTED = 21;
    public static final int STUFF_WIFI_CONNECTION_ERROR = 22;
    public static final int STUFF_WIFI_CONNECTING = 23;
    public static final int STUFF_PARAMETER_SETTINGS_RECEIVED = 24;
    public static final int STUFF_PARAMETER_SETTINGS_SENDED = 25;
    public static final int STUFF_PARAMETER_SETTINGS_RECEIVING_ERROR = 26;
    public static final int STUFF_PARAMETER_SETTINGS_SENDING_ERROR = 27;
    public static final int STUFF_LOG_FILE = 28;


    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADRESS = "device_adress"; // von Hauke hinzugefügt
    public static final String TOAST = "toast";

    // Key Names für Dangerous-Permissions
    public  static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;
    public  static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 2;


    // Pfade für Speichern auf External Storage
    public static final String PATH_TRACKS_SLP = "/Schlapphut-Files/TRACKS";
    public static final String PATH_POINTS_SLP = "/Schlapphut-Files/POINTS";
    public static final String PATH_LOG_FILE = "/Schlapphut-Files/LOG";


    // Daten fuer Zugfahrzeug

    public static final int UPDATE_DATA_AND_VIEW = 8;
    public static final String UPDATE_DATA_AND_VIEW_KEY = "update_fahrzeug";

    // Gibt an, ob sich mit dem Zugfahrzeug oder Anhaenger verbunden werden soll
    public static final int REQUEST_CONNECT = 0;
    public static final String REQUEST_CONNECT_TYPE = "fahrzeug-type";
}
