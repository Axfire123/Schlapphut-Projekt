/*
 * Arduino-Software des Schlapphut-Projekts
 * Version: 1.0
 * Autor: Axfire123
 * 
 * Funktion der Software (kurz und kanpp):
 * In der setup-Funktion werden alle wesentlichen Einstellungen getaetigt. Außerdem wird gepreuft, ob die Lichtmaschine an ist (also ob das Auto in Betrieb ist). Daraus ergeben sich zwei Operationsmodi:
 * Modus 1: Der Trackmodus: Das Auto ist in Betrieb, es wird im Track-OperationMode fortgefahren. Ist das Auto nicht in Betrieb, wird der Controller in den Schlafmodus versetzt, was einem Reset gleich kommt. Alle Variablen-Werte und Co. werden also geloescht.
 * Modus 2: Der Pointmodus: Ein Counter wird hochgezaehlt. Hat er einen bestimmten Wert erreicht, wird mit dem Point-OperationMode fortgefahren. DIES WURDE IN DER VERSION 1.0 NOCH NICHT IMPLEMENTIERT!!!
 * 
 * In der loop-Funktion wird eine Art Statemachine durchlaufen: 
 * 
 * State -1:  Anfangsstate. Hier wird nach GPS-Daten vom GPS-Modul gepollt. Außerdem wird geprueft, ob neue Daten/Befehle von einem verbundenen TCP-Client angekommen sind. Auf die Befehle wird entsprechend reagiert, z.B. SLP-Datei senden oder so.
 *            Ist eine definierte Zeitspanne (in timespan) abgelaufen, wird in den naechsten/gespeicherten State gesprungen. 
 *                          
 * State 0:   In diesem State wird auf den Erhalt eines ersten, gueltigen GPS-Fix gewartet. Wurde der Fix gefunden, wird aus diesem das Datum und die Uhrzeit extrahiert und der Name der zu verwendeten SLP-Datei erstellt.
 *            Wird x-Male kein GPS-Fix gefunden, wird er Controller in den DeepSleep-Modus gesetzt => Software-Reset. 
 *            
 * State 1:   Pruefung, ob Lichtmaschine noch an ist: Ja   => Bei jedem neuen Erhalt eines GPS-Fix wird dieser in der SLP-Datei auf der SD-Karte gespeichert. 
 *                                                    Nein => Zaehle Counter hoch. Wenn Counter ueber Schwellwert ist, setze Controller in DeepSlepp-Modus
 */


//-- Libraries Included --------------------------------------------------------------
  #include <ESP8266WiFi.h>
  #include <TinyGPS.h>
  #include <SoftwareSerial.h>
  #include <SPI.h>
  #include <SD.h>
  #include <EEPROM.h>
  
//-- Define I/O Pins ----------------------------------------------------------------------------------
  #define     LED0        2         // WIFI Module LED
  #define     limaPin     A0        // ADC Pin auf dem die Lima sitzt
  #define     ssRXpin     5         // RX Pin fuer SoftwareSerial
  #define     ssTXpin     4         // TX Pin fuer SoftwareSerial
  #define     loadSwitchPin    0    // GPIO 0 LoadSwitch ein/ausschalten

//-- Verzeichnisse ----------------------------------------------------------------------------------
  const String TRACKS_PATH = "/TRACKS";
  const String POINTS_PATH = "/POINTS";

//-- ENUM's und Co ----------------------------------------------------------------------------------
  enum TrackStopType        {LIMA_OFF = 1, NO_GPS, BAD_GPS};        // Beendingungsgreunde fuer einen Track
  enum OperationMode        {TRACK = 1, POINT};                         // Betriebsarten des Schlapphut

//-- Adressen fuer EEPROM ----------------------------------------------------------------------------------
  #define EEPROM_USED_BYTES                 0xFF  // Reservierter Speicher im EEPROM in Bytes. Bereich von 4 bis 4096 Bytes
  #define EEPROM_SCHEMA                     0xAA  // Ein Schema, um zu erkennen, dass im EEPROM gueltige Werte stehen
  #define EE_ADRS_SCHEMA                    0x00  // Byteadresse 
  #define EE_ADRS_WAKE_UP_COUNTER           0x01  // ...
  #define EE_ADRS_MAX_LIMA_OFF_LOW          0x02
  #define EE_ADRS_MAX_LIMA_OFF_HIGH         0x03
  #define EE_ADRS_MAX_INVALID_GPS_OFF_LOW   0x04
  #define EE_ADRS_MAX_INVALID_GPS_OFF_HIGH  0x05
  #define EE_ADRS_MAX_SD_CARD_ERROR         0x06
  #define EE_ADRS_SLEEP_TIME                0x07
  #define EE_ADRS_POINT_MODE_AVAILABLE      0x08
  
    
//-- Wifi an TCP Server ----------------------------------------------------------------------------------
  IPAddress local_IP(192,168,4,22);
  IPAddress gateway(192,168,4,9);
  IPAddress subnet(255,255,255,0);
  WiFiServer TCP_SERVER(9001);      // THE SERVER AND THE PORT NUMBER
  WiFiClient TCP_Client;            // Client
  bool clientIsConnected = false;  
  // Authentication Variables
  char*       ssid;              // SERVER WIFI NAME
  char*       password;          // SERVER PASSWORD
  bool newMessage = false;
  String message = "";
    
//-- GPS ----------------------------------------------------------------------------------
  TinyGPS gps;
  SoftwareSerial ss(ssRXpin, ssTXpin);
  float                 lat;                            // aktuelle Latitude
  float                 lon;                            // aktuelle Longitude
  float                 latOld;                         // letzte Latitude
  float                 lonOld;                         // letzte Longitude
  unsigned long         age = 0;                        // Alter des aktuellen Fix in ms

//-- Standard Min. Max. Werte und Counter ----------------------------------------------------------------------------------
  unsigned int    MAX_LIMA_OFF;             // Max. Anzahl an Zyklen wo Lichtmaschine aus ist
  unsigned int    MAX_INVALID_GPS;          // Max. Anzahl kein GPS
  unsigned int    MAX_SD_CARD_ERROR;        // max. Anzahl an SD-Karten Schreibfehlern
  unsigned int 	  SLEEP_TIME;               // Zeit in ms die Schlapphut schlaeft
  byte            POINT_MODE_AVAILABLE;     // gibt an, ob der Betriebsmodus Point verfuegbar ist und wakeUpCounter raufgezaehlt werden darf
  
  unsigned int    idleCounter = 0;                // Zaehler der Fixes zaehlt, in denen Fahrzeug nicht bewegt wurde 
  unsigned int    limaOffCounter = 0;             // Zaehler welcher zaehlt, wie viele Zyklen die Lichtmaschine aus war
  unsigned int    invalidGpsCounter = 0;          // Counter ungueltige GPS
  unsigned int    sdCardErrorCounter = 0;         // Counter SD-Karten Schreibfehler
  byte   		  wakeUpCounter = 0;                  // Counter um zu ermitteln, wie oft der Controller aufgewacht ist. Wird zum periodischen speichern der Positionsspeicherung bei Fahrzeugstillstand verwendet.
  
//-- allgemeine globale Variablen ----------------------------------------------------------------------------------
  int                   state = -1;                      // State fuer Statemachine
  int                   stateSafe = -1;
  bool                  safeTime = true;
  char                  dateTimeString[40];             // String mit Datum und Uhrzeit des ersten GPS-Fix
  char                  fileName[40];                   // Name der aktuellen Textdatei in welche geschrieben wird
  char                  dataString [200];               // String in welchem Daten zum Speichern auf der SD Karte liegen
  char                  printString[200];               // String zur Ausgabe auf Serial Port
  long int              millisWaitForFirstGpsFix = 0;   // vergangene Millisekunden fuer ersten GPS-Fix
  OperationMode         operationMode = TRACK;          // aktueller Betriebsmodus des Schlapphut
  unsigned long start = 0;
  unsigned long timespan = 1000;                        // in Millisekunden

  struct Status{
    bool firstGpsFounded = false;
    unsigned int nGpsFix = 0;  
  };
  Status status;
  



void setup() {

  // Setting The Serial Port
  Serial.begin(115200);           // Computer Communication
  while(!Serial) { }              // Wait for serial to initialize.
  Serial.println("\nSchalpphut meldet sich zum Dienst");
  
  // Setting The Mode Of Pins
  pinMode(LED0, OUTPUT);          // WIFI OnBoard LED Light
  pinMode(limaPin, INPUT);       // ADC PIN - Lichtmaschine
   
  digitalWrite(LED0, LOW);      // Schalte LED an
  delay(200);
  digitalWrite(LED0, HIGH);      // Schalte LED aus

  // lese Werte aus EEPROM
  readEepromValues();
  // Werte auf UART ausgeben
  printSettings();
  
  // Pruefe ob Lichtmaschine laeuft
  if(!checkLima())
  { 
    Serial.println("Lima ist aus, ich gehe schlafen");
    schalpphutSlepp();
  }
  else
  {
    Serial.println("Lima ist an, ich fahre fort");
    operationMode = TRACK;
  }
    
  // Schalte LED an
  digitalWrite(LED0, LOW);      
  
  // Pin Config und LoadSwitch  anschalten
  pinMode(loadSwitchPin, OUTPUT);    // Load-Switch
  Serial.println("schalte LoadSwitch an");
  digitalWrite(loadSwitchPin, LOW);  // LoadSwitch an
  delay(500);

  // SD-Karte einbinden
  if (!SD.begin()) {
    Serial.println("Fehler beim Einbinden der SD-Karte");
    Serial.println("keine SD-Karte => Schlapphut geht schlafen");
    schalpphutSlepp();
  }
  Serial.println("SD-Karte eingebunden");

  // Preufe/Erstelle Verzeichnisse
  makeDirectories();

  // Software SerialPort starten
  ss.begin(9600);
  
  // Soft Access Point starten sofern richtiger Betriebsmodus
  if(operationMode == TRACK)
    SetWifi("Schlapphut", "");

  // Blinke kurz
  ledFlashTwoTimes(100, 100);

  // Schalte LED an
  digitalWrite(LED0, LOW);

  // Zeitreferenz speichern
  millisWaitForFirstGpsFix = millis();
  
  state = -1;
  stateSafe = 0;
  safeTime = true;
}

void loop() {
       
  switch(state)
  {

    case -1:
      if(safeTime)
      {
        safeTime = false;
        start = millis();  
      }

      if( (millis() - start) < timespan) 
      {
        
        // Neue GPS-Daten pollen
        while (ss.available())
        {
          char c = ss.read();
          //Serial.print(c);
          gps.encode(c);
        }

        // TCP-Client bedienen
        HandleClients();

        // Nachricht vom Client empfangen?
        if(newMessage)
        {
          newMessage = false;

          ss.end();   // Software Serial stoppen, da Daten nicht abgeholt werden koennen und dies zum Buffer-Overflow fuehrt => WLAN Probleme!!
      
          // Inhalt pruefen
          if(strncmp(message,"GTFL",4))        // Get Track File List
          {
            // Eine Liste mit allen Track SLP-Dateien wurde von der App angefordert
            Serial.println("Get Track File List");
            SendFileList(1);
          }
          else if(strncmp(message,"GPFL",4))  // Get Point File List
          {
            // Eine Liste mit allen Point SLP-Dateien wurde von der App angefordert
            Serial.println("Get Point File List");
            SendFileList(2);
          }
          else if(strncmp(message,"GTSLP",5)) // Get Track SLP Datei
          {
            // Eine benannte Track SLP-Datei wurde angefordert
            Serial.println("Get Track SLP Datei");
            SendFile(1,message);
          }
          else if(strncmp(message,"GPSLP",5)) // Get Point SLP Datei
          {
            // Eine benannte Point SLP-Datei wurde angefordert
            Serial.println("Get Point SLP Datei");
            SendFile(2,message);
          }
          else if(strncmp(message,"GLOG",4)) // Get Log Datei
          {
            // Die Log-Datei wurde angefordert
            Serial.println("Get Log Datei");
            SendFile(3,message);
          }
          else if(strncmp(message,"RMT",3)) // Remove Track SLP Datei
          {
            // Eine benannte Track SLP-Datei soll geloescht werden
            Serial.println("Remove Track SLP Datei");
            RemoveFile(1, message);
          }
          else if(strncmp(message,"RMP",3)) // Remove Point SLP Datei
          {
            // Eine benannte Point SLP-Datei soll geloescht werden
            Serial.println("Remove Point SLP Datei");
            RemoveFile(2, message);
          }
          else if(strncmp(message,"RML",3)) // Remove Log Datei
          {
            // Log-Datei soll geloescht werden
            Serial.println("Remove Point SLP Datei");
            RemoveFile(3, message);
          }
          else if(strncmp(message,"GSTAT",5)) // Get Status
          {
            // Statusinformationen werden angefordert
            Serial.println("Get Status");
            PrintStatus();
          }
		      else if(strncmp(message,"GSETT",5)) // Get Stettings
          {
            Serial.println("Get Stettings");
            sendSettings();
          }
		      else if(strncmp(message,"SSETT",5)) // Set Stettings
          {
            Serial.println("Set Stettings");
            parseReceivedSettings(message);
            TCP_Client.println("OK");
			      printSettings();
			      writeEepromValues();
          }

          ss.begin(9600); // Software Serial wieder starten
        }
      }
      else
      {
        state = stateSafe;
        safeTime = true;
        
         //Serial.println("millis(): " + millis());
         //Serial.println("start: " + start);
      }      
      break;

    
    case 0:   // warte auf ersten GPS-Fix
      // Hole GPS-Daten aus Klasse und pruefe sie auf Gueltigkeit
      gps.f_get_position(&lat, &lon, &age);
      if(lat != TinyGPS::GPS_INVALID_F_ANGLE)
      {
        sendStatusMessage("Ersten GPX-Fix gefunden\n");
        invalidGpsCounter = 0;
        if(getDateTimeFileName(gps,fileName))
        {
          Serial.print("File-Name: ");
          Serial.println(fileName);
          // Pruefe/Erstelle SLP-Datei
          if(checkSlpFile(operationMode, fileName))
          {
            sprintf(printString, "Operation Mode: %d\tFile Name: %s \n",operationMode, fileName);
            sendStatusMessage(printString);  

            // logge auf SD-Karte mit
            sprintf(printString, "%s\r\n",fileName);
            sdPrint("","logger.txt",printString);

            status.firstGpsFounded = true;
          }
          else
          {
            sendStatusMessage("SLP-Datei konnte nicht erstellt werden. Gehe Schlafen...\n");
            schalpphutSlepp();  
          }          

          if(operationMode == TRACK)
          {
            // Speicher Findung und benoetigte Zeit in Sekunden des ersten GPS-Fix auf SD-Karte. Track-Header
            sprintf(dataString, "#S%d\r\n", ((millis() - millisWaitForFirstGpsFix)/1000));
            sdPrint(TRACKS_PATH, fileName, dataString);        
            
            // Blinke kurz
            ledFlashTwoTimes(200, 100);
  
            latOld = lat;
            lonOld = lon;
          }
           state = -1;
           stateSafe = 1;  
        }
      }
      else
      {
        sprintf(printString, "Suche ersten GPS-Fix %d/%d\n", invalidGpsCounter + 1, MAX_INVALID_GPS);
        sendStatusMessage(printString);
        if(++invalidGpsCounter >= MAX_INVALID_GPS)
        {
          sendStatusMessage("Kein ersten GPS-Fix gefunden, gehe schlafen\n");
          // logge auf SD-Karte mit
          sprintf(printString, "kein GPS-Fix gefunden ... gehe schlafen\r\n");
          sdPrint("","logger.txt",printString);
          schalpphutSlepp();
        }
        state = -1;
        stateSafe = 0; 
      }
      break;

    case 1:   // erster GPS-Fix wurde gefunden, speicher die nachfolgenden ab
      
      // Pruefe ob Lima noch laeuft
      if( (operationMode == TRACK) && (!checkLima()) )
      {
        sprintf(printString, "Lima ist aus %d/%d ...\n", limaOffCounter + 1, MAX_LIMA_OFF);
        sendStatusMessage(printString);
        if(++limaOffCounter >= MAX_LIMA_OFF){
          sendStatusMessage("... gehe schlafen\n");
          schalpphutSlepp();
        }
        state = -1;
        stateSafe = 1; 
        break;
      }
      else
      {
        limaOffCounter = 0;
      }
      //

      // Hole GPS-Position
      gps.f_get_position(&lat, &lon, &age);

      // Pruefe auf Gueltigkeit der Position
      if(lat != TinyGPS::GPS_INVALID_F_ANGLE)
      {		
    		invalidGpsCounter = 0;
    		status.nGpsFix++;
    		  
    		// Speicher Fix ab
    		latOld = lat;
    		lonOld = lon;
    		int hdop = gps.hdop();
    		  
    		// Datum in String erzeugen
    		getDateTimeString(gps,dateTimeString);
    		// Daten zusammenfassen
    		sprintf(dataString, "%f/%f/%s/%d/%d\r\n", lat, lon, dateTimeString, hdop, age);
    
    		// SLP File pruefen und je nach Betriebsmodus Daten auf SD-Karte schreiben
    		if(checkSlpFile(operationMode, fileName))
    		{
    		  if(operationMode == TRACK)
    		  {
    		    sdPrint(TRACKS_PATH, fileName, dataString);
    		    // print to the serial port too:
    			  sendStatusMessage(dataString);  
    			}
    			else if(operationMode == POINT)
    			{
    			  sdPrint(POINTS_PATH, fileName, dataString);
    			  // print to the serial port too:
    			  sendStatusMessage(dataString);
    			  state = 3;
    			}
    	  }
    	  else
    	  {
      		sendStatusMessage("SLP-Datei konnte nicht erstellt werden. Gehe Schlafen...\n");
      		schalpphutSlepp();  
    	  }         
      }
      else
      {
        sprintf(printString, "kein GPS-Fix gefunden %d/%d ...\n", invalidGpsCounter + 1, MAX_INVALID_GPS);
        sendStatusMessage(printString);
        if(++invalidGpsCounter >= MAX_INVALID_GPS)
        {
          // Track-Footer abspeichern
          sprintf(dataString, "#E%d\r\n", NO_GPS);
          sdPrint(TRACKS_PATH, fileName, dataString);
          sendStatusMessage(dataString);
          // logge auf SD-Karte mit
          sdPrint("","logger.txt","kein GPS gefunden");
          // gehe Schlafen
          sendStatusMessage("... gehe schlafen\n");
          schalpphutSlepp();
        }
      }

      state = -1;
      stateSafe = 1; 
      break;
         
  } // end switch(state) 
 
} // end loop


/*
 * Schreibt den aktzuellen Status auf den UART und den TCP-Client
 */
void PrintStatus()
{
  sprintf(printString, "%d/%d", status.firstGpsFounded, status.nGpsFix);
  TCP_Client.println(printString);
}


/*
 * Loescht eine SLP-Datei von der SD-Karte
 * tOrP: handelt es sich bei der zu loeschenden SLP-Datei um eine Track oder Point SLP-Datei? Wichtig fuer den Pfad
 * com: der vom TCP-Client empfangene Befehl, hier steht der Name der zu loeschenden SLP-Datei
 */
void RemoveFile(int tOrP, String com)
{
  String fullPath;
  bool res = false;
  String fileName = "";

  // in com steht der Befehl, z.B: RMT/20180711.SLP
  // aus com muss der Name geholt werden, also 20180711.SLP
  // die Dateieindung .SLP darf nicht gross geschrieben werden, lese daher nur bis zum Punkt
  for(int i = 4; i < com.length(); i++)
  {
    if(com[i] == '.')
      break;
    else
      fileName += com[i];
  }
    
  // den kompletten Pfad zu Datei erstellen
  if(tOrP == 1)
    fullPath = TRACKS_PATH + "/" + fileName + ".slp";
  else if(tOrP == 2)
    fullPath = POINTS_PATH + "/" + fileName + ".slp";
  else if(tOrP == 3)
    fullPath = "logger.txt";

  Serial.print("Datei:");
  Serial.println(fullPath);

  // preufen ob Datei existiert
  if(SD.exists(fullPath))
  {
    // loeaschen
    res = SD.remove(fullPath);
  }
  else
  {
    Serial.println("Datei nicht gefunden");
  }

  delay(1); // Controller soll Wlan-Sachen machen

  // Antwort schicken
  if(res)
  {
    Serial.println("Datei geloescht");
    TCP_Client.println("OK");
  }
  else
  {
    Serial.println("Fehler beim Loeschen der Datei");
    TCP_Client.println("NOF");
  }   
}

/*
 * Sendet eine angeforderte SLP-Datei an den TCP-Client
 * tOrP: Track oder Point SLP-Datei
 * vom TCP-Client emfangener Befehl, hier steht Name der angeforderten SLP-Datei
 */
void SendFile(int tOrP, String com)
{
  int nLines = 0;
  char printString[100];
  File dir;
  long time1 = 0;
  long TIMEOUT = 5000;
  String fileName = "";
  String fullPath = "";
  unsigned int num = 0;

  Serial.println("In SendFile");

  // in com steht der Befehl, z.B: GTSLP/20180711.SLP
  // aus com muss der Name geholt werden, also 20180711.SLP
  // die Dateieindung .SLP darf nicht gross geschrieben werden, lese daher nur bis zum Punkt
  for(int i = 6; i < com.length(); i++)
  {
    if(com[i] == '.')
      break;
    else
      fileName += com[i];
  }
    
  // den kompletten Pfad zu Datei erstellen
  if(tOrP == 1)
    fullPath = TRACKS_PATH + "/" + fileName + ".slp";
  else if(tOrP == 2)
    fullPath = POINTS_PATH + "/" + fileName + ".slp";
  else if(tOrP == 3)
    fullPath = "logger.txt";

  Serial.print("Datei: ");
  Serial.println(fullPath);

  // preufen ob Datei existiert
  if(!SD.exists(fullPath))
  {
    // Datei nicht gefunden => Meldeung rausgeben und abbrechen
    Serial.println("NOF");
    TCP_Client.println("NOF");
    return;
  }

  delay(0); // Controller soll Wlan-Sachen machen
  
  // Zaehlen, wie viele Zeilen Datei hat
  File myFile = SD.open(fullPath);
  if (myFile) {
    // read from the file until there's nothing else in it:
    while (myFile.available()) {
      myFile.readStringUntil('\n');
      nLines++;
    }
    // close the file:
    myFile.close();
  } else {
    // if the file didn't open, print an error:
    Serial.println("error opening " + fullPath);
    return;
  }
  //

  delay(0); // Controller soll Wlan-Sachen machen

  Serial.print("nLines: ");
  Serial.println(nLines);

  // Antwort mit Anzahl der Lines schicken
  sprintf(printString, "OK/%d", nLines);
  TCP_Client.println(printString);

  // auf Antwort vom Client warten
  time1 = millis();
  newMessage = false;
  while(true)
  {
    delay(0); // Controller soll Wlan-Sachen machen
    // neue Nachricht abholen
    HandleClients(); 
    // auf Timeout pruefen
    if((millis() - time1) >= TIMEOUT)
    {
      Serial.println("Timeout, breche ab");
      schalpphutSleppAfterTimeout();
    }
    // neue Nachricht erhalten
    if(newMessage)
    {
      newMessage = false;
      if(strncmp(message,"OK",2))
        break;
    }
  }

  // Zeilen der Datei senden
  myFile = SD.open(fullPath);
  if (myFile) 
  {
    String line;
  
    while(num++ < nLines)
    {
	  // Zeile einlesen...
      if(myFile.available())
      {
        line = myFile.readStringUntil('\n');
      }
      
      //... und senden     
      TCP_Client.print(line);
      delay(0);
  
      // auf Antwort vom Client warten
      time1 = millis();
      newMessage = false;
      while(true)
      {
        delay(0); // Controller soll Wlan-Sachen machen
        // neue Nachricht abholen
        HandleClients(); 
        // auf Timeout pruefen
        if((millis() - time1) >= TIMEOUT)
        {
          Serial.println("Timeout, breche ab");
          schalpphutSleppAfterTimeout();
        }
          
        // neue Nachricht erhalten
        if(newMessage)
        {
          newMessage = false;
          if(strncmp(message,"OK",2))
          { 
            break;
          }   
        }    
      }
      //
    }
    // close the file:
    myFile.close();
  } else {
    // if the file didn't open, print an error:
    Serial.println("error opening " + fullPath);
    return;
  }
  //
  Serial.println("Datei " + fullPath + " erfolgreich uebertragen!");
}


/*
 * Sendet eine Liste aller auf der SD-Karte gespeicherten SLP-Dateien
 * tOrP: Track oder Point SLP-Dateien?
 */
void SendFileList(int tOrP)
{
  int nFiles = 0;
  char printString[50];
  File dir;
  long time1 = 0;
  long TIMEOUT = 5000;

  if(tOrP == 1)
    dir = SD.open(TRACKS_PATH);
  else if(tOrP == 2)
    dir = SD.open(POINTS_PATH);

  // Zaehle erstmal selbst, wie viele Files es in dem Verzeichnis gibt
  while(true) {
     File entry =  dir.openNextFile();
     long fileSize = 0;
     if (! entry) {
       // no more files
       dir.rewindDirectory(); // internen Zeiger fuer openNextFile() zurueck setzen
       break;
     }
     nFiles++;  
   }

  //sprintf(printString, "Anzahl der Dateien: %d\r\n", nFiles);
  //Serial.print(printString);

  // Antwort mit Anzahl der Files schicken
  sprintf(printString, "OK/%d", nFiles);
  TCP_Client.println(printString);

  // auf Antwort vom Client warten
  time1 = millis();
  newMessage = false;
  while(true)
  {
    delay(0); // Controller soll Wlan-Sachen machen
    // neue Nachricht abholen
    HandleClients(); 
    // auf Timeout pruefen
    if((millis() - time1) >= TIMEOUT)
      return;
    // neue Nachricht erhalten
    if(newMessage)
    {
      newMessage = false;
      if(strncmp(message,"OK",2))
        break;
    }    
  }

  // Namen der Files senden
  while(true)
  {
    File entry =  dir.openNextFile();
    long fileSize = 0;
    if (! entry) {
     break;
    }   
    // Name und Groesse senden
    fileSize = entry.size();
    sprintf(printString, "%s/%d", entry.name(), fileSize);
    TCP_Client.println(printString);       

    // auf Antwort vom Client warten
    time1 = millis();
    newMessage = false;
    while(true)
    {
      delay(0); // Controller soll Wlan-Sachen machen
      // neue Nachricht abholen
      HandleClients(); 
      // auf Timeout pruefen
      if((millis() - time1) >= TIMEOUT)
      {
        Serial.println("Timeout, breche ab");
        schalpphutSleppAfterTimeout();
      }
      // neue Nachricht erhalten
      if(newMessage)
      {
        newMessage = false;
        if(strncmp(message,"OK",2))
          break;
      }    
    }
    //
  }
  Serial.println("Dateiliste erfolgreich uebertragen!");
}

/*
 * Versendet Statusmeldungen ueber den SerialPort und an den TCP Client
 */
void sendStatusMessage(String message)
{
  Serial.print(message);
  //if(clientIsConnected)
  //  TCP_Client.print(message);
}

/*
 * Lasse die Onboard-LED zwei mal blinken
 * timeOn: Zeit LED an in ms
 * timeOff: Zeit LED aus in ms
 */
void ledFlashTwoTimes(int timeOn, int timeOff)
{
  digitalWrite(LED0, LOW);      // Schalte LED an
  delay(timeOn);
  digitalWrite(LED0, HIGH);      // Schalte LED aus
  delay(timeOff);
  digitalWrite(LED0, LOW);      // Schalte LED an
  delay(timeOn);
  digitalWrite(LED0, HIGH);      // Schalte LED aus
}


/*
 * Prueft ob das SLP-File in seinem Verzeichnis existiert. 
 * Falls SLP-Datei nicht exisitiert, wird diese erstellt und der Header wird beschrieben.
 * om: Operationsmodus, Track oder Point. Wichtig fuer Pfad zur Datei
 * fileName: Name der SLP-Datei
 */
bool checkSlpFile(OperationMode om, String fileName)
{
  String filePath = "";
  String slpType = "";
  
  switch(om)
  {
    case TRACK:
      filePath = TRACKS_PATH + "/" + fileName;
      slpType = "TYPE=TRACK\r\n";
      break;

    case POINT:
      filePath = POINTS_PATH + "/" + fileName;
      slpType = "TYPE=POINT\r\n";
      break;
  }

  if(SD.exists(filePath))
    return true;      // File existiert, gebe true zurueck
  else
  {
     // Erzeuge SLP-File auf SD-Karte
    File dataFile = SD.open(filePath, FILE_WRITE);
    if (dataFile) {
      dataFile.print(slpType);   // Schreibe SLP Version => schreibe den Header
      dataFile.close();
      return true;    // File wurde erstellt, gebe true zurueck
    }
    else
      return false;  // File wurde nicht erstellt, gebe false zurueck
  }
}

/*
 * Schreibt einen Text auf die SD-Karte
 * true: Schreiben war erfolgreich
 * false: Schreiben war nicht erfolgreich
 */
bool sdPrint(String dir, String file, String txt)
{
  String path = "";

  if(dir != "")
    path = dir + "/" + file;
  else
    path = file;
  
  // Speicher auf SD-Karte
  File dataFile = SD.open(path, FILE_WRITE);
  // if the file is available, write to it:
  if (dataFile) 
  {
    // Schreibe
    dataFile.print(txt);
    dataFile.close();
    
    // Blinke kurz
    ledFlashTwoTimes(50, 50);
    sdCardErrorCounter = 0;
    return true;
  }
  else 
  {
    char printString[100];
    sprintf(printString, "%s konnte nicht beschreiben werden %d/%d\n", path.c_str(), sdCardErrorCounter + 1, MAX_SD_CARD_ERROR);
    sendStatusMessage(printString);
    if(++sdCardErrorCounter >= MAX_SD_CARD_ERROR)
    {
      sendStatusMessage("Kann SD-Karte nicht mehr beschreiben, gehe schlafen\n");
      schalpphutSlepp();
    }
    return false;
  }
}


/*
 * Prueft/Erstellt das Track- und Pointverzeichnis
 */
void makeDirectories()
{
  if( !SD.exists(TRACKS_PATH) ) 
  {
    if( SD.mkdir(TRACKS_PATH) ) 
    {
      Serial.println("File directory created");
    } 
    else 
    {
      Serial.println("File directory not created");
    }
  }

  if( !SD.exists(POINTS_PATH) ) 
  {
    if( SD.mkdir(POINTS_PATH) ) 
    {
      Serial.println("File directory created");
    } 
    else 
    {
      Serial.println("File directory not created");
    }
  }
}


/*
 * Schaltet den Loadswitch aus und legt den Controller in den DeepSleep-Modus
 */
void schalpphutSlepp()
{
  // Blinke
  ledFlashTwoTimes(50, 50);
  ledFlashTwoTimes(50, 50);
  ledFlashTwoTimes(50, 50);

  // Schalte LoadSwitch aus
  digitalWrite(loadSwitchPin, HIGH);  
  
  // gehe fuer x Microsekunden schlafen
  ESP.deepSleep(SLEEP_TIME * 1e6);
}


void schalpphutSleppAfterTimeout()
{
  // Schalte LoadSwitch aus
  digitalWrite(loadSwitchPin, HIGH);  
  
  // gehe fuer x Microsekunden schlafen
  ESP.deepSleep(1e6);
}


/*
 * Prueft, ob die Lichtmaschine in betrieb ist => ob das Auto in betrieb ist
 * true: Lichtmaschine ist an => Auto ist in betrieb
 * false: Lima ist aus
 */
bool checkLima()
{
  int value = analogRead(limaPin);
  if(value > 900)
    return false;
  else
    return true;
}


/*
 * Stellt das Modem des Controllers in den Soft Access Point
 */
void SetWifi(char* Name, char* Password)
{
  
  // Setting The Wifi Mode
  WiFi.mode(WIFI_AP_STA);
  Serial.println("WIFI Mode : AccessPoint Station");
 
  // Setting The AccessPoint Name & Password
  ssid      = Name;
  password  = Password;

  // Config the Access Point
  WiFi.softAPConfig(local_IP, gateway, subnet);
 
  // Starting The Access Point
  //WiFi.softAP(ssid, password, channel, hidden)
  WiFi.softAP(ssid, password, 1, true);
  Serial.println("WIFI < " + String(ssid) + " > ... Started");
 
  // Wait For Few Seconds
  delay(1000);
 
  // Getting Server IP
  IPAddress IP = WiFi.softAPIP();
 
  // Printing The Server IP Address
  Serial.print("AccessPoint IP : ");
  Serial.println(IP);

  // Starting Server
  TCP_SERVER.begin();
  Serial.println("Server Started");
}




/*
 * Prueft ob Verbindungsanfragen von einem Client anliegen, sofern noch kein Client verbunden ist.
 * Wenn ein Client verbunden ist, wird geprueft, ob neue Befehle eingelesen werden muessen
 */
void HandleClients()
{
  yield();        // den Controller Wlan-Sachen machen lassen => Sketch-Behind Code       
  
  if(!clientIsConnected)
  {
    if(TCP_SERVER.hasClient())
    {
      TCP_Client = TCP_SERVER.available();
      clientIsConnected = true;
      Serial.println("Client ist connected");
    }
  }
  else
  {
    //-----------------------------------------------------------------
    // Empfange neue Daten vom Client
    //---------------------------------------------------------------
    int bytesToRead = TCP_Client.available();
    //if(Client.available())
    if(bytesToRead)
    {
      //Serial.print("Anzahl der emfangenen Bytes: ");
      //Serial.println(bytesToRead);
      
      // Here We Read The Message
      //String Message = Client.readStringUntil('\r');
      //String Message = Client.readString();
      char Message[255];
      TCP_Client.readBytes(Message, bytesToRead);
      Message[bytesToRead] = '\0';
      
      // Here We Print The Message On The Screen
      //Serial.print("Message emfangen: ");
      //Serial.println(Message);
      
      TCP_Client.flush();

      // globale Variablen setzen
      message = Message;
      newMessage = true;
    }

    if(!TCP_Client.connected())
    {
      clientIsConnected = false;
      TCP_Client.stop();
      
      // Here We Turn Off The LED To Indicated The Its Disconnectted
      //digitalWrite(LED1, LOW);
      // Here We Jump Out Of The While Loop
      Serial.println("Client ist disconnected");
    }
  }  
}


/* 
 * Pollt ueber SoftSerial nach neuen GPS Daten  
*/
static void smartdelay(unsigned long ms)
{
  unsigned long start = millis();
  do 
  {
    while (ss.available())
    {
      char c = ss.read();
      //Serial.print(c);
      gps.encode(c);
    }
    HandleClients();
  } while (millis() - start < ms);
}


/*
 * Extrahiert aus dem GPS-Objekt das Datum und Uhrzeit und speichert diese in einen String
 */
bool getDateTimeString(TinyGPS &gps, char *string)
{
  int year;
  byte month, day, hour, minute, second, hundredths;
  unsigned long age;
  gps.crack_datetime(&year, &month, &day, &hour, &minute, &second, &hundredths, &age);
  if (age == TinyGPS::GPS_INVALID_AGE)
    return false;
  else
  {
    sprintf(string, "%02d%02d%02d%02d%02d%02d", year, month, day, hour, minute, second);
    return true;
  }
}


/*
 * Extrahiert aus dem GPS-Objekt das Datum und Uhrzeit und speichert diese in einen String. Optimiert fuer Dateiname
 */
bool getDateTimeFileName(TinyGPS &gps, char *fileName)
{
  int year;
  byte month, day, hour, minute, second, hundredths;
  unsigned long age;
  gps.crack_datetime(&year, &month, &day, &hour, &minute, &second, &hundredths, &age);
  if (age == TinyGPS::GPS_INVALID_AGE)
    return false;
  else
  {
    sprintf(fileName, "%02d%02d%02d.slp", year, month, day);
    return true;
  }
}


/*
 * Vergleicht zwei Strings n-Zeichen lang.
 * Bei n Uebereinstimmung wird true zureuck gegeben
 */
boolean strncmp(String text1, String text2, int nZeichen)
{
  for(int i = 0; i < nZeichen; i++)
  {
    if(text1[i] != text2[i])
      return false;
  }
  return true;
}

/*
 * Liest die Settings aus dem EEPROM
 */
void readEepromValues(void)
{
  byte schema = 0;
  byte lowNipple = 0;
  byte highNipple = 0;
  
  // EEPRAM initialisieren
  EEPROM.begin(EEPROM_USED_BYTES);    
  
  // lese Schema aus EEPROM
  schema = EEPROM.read(EE_ADRS_SCHEMA);
  
  // Schema auf gueltigkeit pruefen
  if(schema != EEPROM_SCHEMA)
  {
    Serial.println("keine gueltigen EEPROM Werte gefunden => reinitialize");
    // keine gueltigen Werte im EEPROM => reinitialize
    reinitializeEepromValues();
    return;
  }
  else
  {
    // Werte aus EEPROM lesen
    wakeUpCounter = EEPROM.read(EE_ADRS_WAKE_UP_COUNTER);
    lowNipple = EEPROM.read(EE_ADRS_MAX_LIMA_OFF_LOW);
    highNipple = EEPROM.read(EE_ADRS_MAX_LIMA_OFF_HIGH);
    MAX_LIMA_OFF = (uint16_t)(highNipple << 8) | lowNipple;
    lowNipple = EEPROM.read(EE_ADRS_MAX_INVALID_GPS_OFF_LOW);
    highNipple = EEPROM.read(EE_ADRS_MAX_INVALID_GPS_OFF_HIGH);
    MAX_INVALID_GPS = (uint16_t)(highNipple << 8) | lowNipple;
    MAX_SD_CARD_ERROR = EEPROM.read(EE_ADRS_MAX_SD_CARD_ERROR);
    SLEEP_TIME = EEPROM.read(EE_ADRS_SLEEP_TIME);
    POINT_MODE_AVAILABLE = EEPROM.read(EE_ADRS_POINT_MODE_AVAILABLE);
  }
}

/*
 * Speichert die Settings ins EEPROM
 */
void writeEepromValues(void)
{
  byte lowNipple = 0;
  byte highNipple = 0;
  
  // Werte ins EEPROM schreiben
  EEPROM.write(EE_ADRS_WAKE_UP_COUNTER, wakeUpCounter);
  lowNipple = (byte)(0x00FF & MAX_LIMA_OFF);
  highNipple = (byte)((MAX_LIMA_OFF & 0xFF00) >> 8);
  EEPROM.write(EE_ADRS_MAX_LIMA_OFF_LOW, lowNipple);
  EEPROM.write(EE_ADRS_MAX_LIMA_OFF_HIGH, highNipple);
  lowNipple = (byte)(0x00FF & MAX_INVALID_GPS);
  highNipple = (byte)((MAX_INVALID_GPS & 0xFF00) >> 8);
  EEPROM.write(EE_ADRS_MAX_INVALID_GPS_OFF_LOW, lowNipple);
  EEPROM.write(EE_ADRS_MAX_INVALID_GPS_OFF_HIGH, highNipple);
  EEPROM.write(EE_ADRS_MAX_SD_CARD_ERROR, MAX_SD_CARD_ERROR);  
  EEPROM.write(EE_ADRS_SLEEP_TIME, SLEEP_TIME);  
  EEPROM.write(EE_ADRS_POINT_MODE_AVAILABLE, POINT_MODE_AVAILABLE);  
  
  EEPROM.commit();
}


/*
 * Stellt Standardwerte der Settings her und speichert diese ins EEPROM
 */
void reinitializeEepromValues()
{
  // setze Standardwerte fuer die Konstanten 
  MAX_LIMA_OFF        = 300;             
  MAX_INVALID_GPS     = 600;          
  MAX_SD_CARD_ERROR   = 20;      
  SLEEP_TIME          = 30;
  POINT_MODE_AVAILABLE= 0;
  
  // Schema in EEPROM speichern
  EEPROM.write(EE_ADRS_SCHEMA, EEPROM_SCHEMA);
  EEPROM.commit(); 

  // Parameter ins EEPROM speichern
  writeEepromValues();
}

/*
 * Schickt die Settings ueber TCP an den Client
 */
void sendSettings(void)
{
  // Stringaufbau: "SETT wakeUpCounter/MAX_LIMA_OFF/MAX_SD_CARD_ERROR/SLEEP_TIME/POINT_MODE_AVAILABLE/"
  sprintf(printString, "SETT %d/%d/%d/%d/%d/", wakeUpCounter, MAX_LIMA_OFF, MAX_SD_CARD_ERROR, SLEEP_TIME, POINT_MODE_AVAILABLE);
  Serial.println(printString);
  TCP_Client.println(printString);
}


/*
 * Zerlegt den empfangen String und schreibt die Werte in die entsprechenden Variablen
 */
void parseReceivedSettings(String txt)
{
  // Stringaufbau: "SSETT wakeUpCounter/MAX_LIMA_OFF/MAX_SD_CARD_ERROR/SLEEP_TIME/POINT_MODE_AVAILABLE/"
  int state = 0;
  String value_s;
  int value_i = 0;
  int offset = 6;

  for(int i = 6; i < txt.length(); i++)
  {
    if(txt[i] == '/')
    {
      value_s = txt.substring(offset,i);
      offset = i+1;
      
      switch(state)
      {
      case 0: wakeUpCounter = value_s.toInt();          break;
      case 1: MAX_LIMA_OFF = value_s.toInt();           break;
      case 2: MAX_SD_CARD_ERROR = value_s.toInt();      break;
      case 3: SLEEP_TIME = value_s.toInt();             break;
      case 4: POINT_MODE_AVAILABLE = value_s.toInt();   break;
      }
      state++;
    }
  }
}

/*
 * Gibt die Settings auf den UART aus
 */
void printSettings(void)
{
  sprintf(printString, "wakeUpCounter:%d\n",wakeUpCounter);                 Serial.print(printString);
  sprintf(printString, "MAX_LIMA_OFF:%d\n",MAX_LIMA_OFF);                   Serial.print(printString);
  sprintf(printString, "MAX_SD_CARD_ERROR:%d\n",MAX_SD_CARD_ERROR);         Serial.print(printString);
  sprintf(printString, "SLEEP_TIME:%d\n",SLEEP_TIME);                       Serial.print(printString);
  sprintf(printString, "POINT_MODE_AVAILABLE:%d\n",POINT_MODE_AVAILABLE);   Serial.print(printString);
}
