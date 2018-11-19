using SharpKml.Base;
using SharpKml.Dom;
using SharpKml.Engine;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.IO;

namespace KmlConverter
{
    class Program
    {
        enum SlpType {None, Track, Point };

        static void Main(string[] args)
        {
            TextDatei textDatei = new TextDatei();

            int state = 0;
            bool exit = false;
            bool argsFertig = false;
            bool startWithArgs = false;
            
            string file_path_gesamt = "";       // Gesamter Pfad der Datei. Zum Beispiel:           "F:\\Dropbox\\Schlapphut-Projekt\\GPS-Files\\20180628.SLP"
            string dir_path = "";               // Pfad zum Verzeichnis der Datei. Zum Beispiel:    "F:\\Dropbox\\Schlapphut-Projekt\\GPS-Files"
            string file_name = "";              // Name der Datei. Zum Beispiel:                    "20180628"
            string file_type = "";              // Dateityp der Datei. Zum Beispiel:                "SLP"
            const string dir_kml = "\\KML";     // Unterverzeichnis, in welchem die konvertierte KML-Datei gespeichert wird

            SlpType slpFileType = SlpType.None; // Der Typ der SLP-Datei

            List<String> file_lines = new List<string>();

            // Fuer KML-Dokument
            var root = new Document();
            root.Open = true;       // Bei Google-Earth das Tree-Menu ausklappen

            // Erstelle Styles
            Style normalStyle = CreateNormalSyle();
            Style highlightStyle = CreateHighlightSyle();
            StyleSelector styleSelector = createPlacemarkLineStyleMap(normalStyle, highlightStyle);

            // Weise Styles zu
            root.AddStyle(normalStyle);
            root.AddStyle(highlightStyle);
            root.AddStyle(styleSelector);


            Console.ForegroundColor = ConsoleColor.DarkGray;
            Console.WriteLine("KML Converter v1.0 (c) Axfire123, June 2018\r\n");
            Console.ResetColor();



            while(!exit)
            {
                
                switch(state)
                {
                    case -1:    // Reset
                        {
                            Console.WriteLine("\r\n\r\n");
                            state = 0;
                            break;
                        }

                    case 0: // Dateipfad einlesen
                        {
                            // Prüfen, ob beim Programmaufruf eine oder mehrere
                            // Zeichenfolgen übergeben worden sind
                            if ( (args.Length == 0) || (argsFertig == true) )
                            {
                                Console.WriteLine("Bitte ziehe eine SLP-Datei in das Fenster und bestätige mit Enter\r\n");
                                //Console.ResetColor();
                                file_path_gesamt = Console.ReadLine();
                                if (file_path_gesamt == String.Empty)
                                {
                                    Console.ForegroundColor = ConsoleColor.Red;
                                    Console.WriteLine("Es wurde keine Datei ausgewählt!");
                                    state = -1;
                                }
                                else
                                {
                                    state = 1;
                                }
                            }
                            else
                            {
                                file_path_gesamt = args[0];
                                state = 1;
                                startWithArgs = true;
                            }
                            
                            break;
                        }

                    case 1: // Untersuche einzulesende Datei
                        {
                            dir_path = getDirFromPath(file_path_gesamt);
                            file_name = getNameFromPath(file_path_gesamt);
                            file_type = getFileTypeFromPath(file_path_gesamt);

                            // püfe auf richtiges Dateiformat
                            if (file_type.ToLower() != "slp")
                            {
                                Console.ForegroundColor = ConsoleColor.Red;
                                Console.WriteLine("Die gewählte Datei ist vom falschen Typ. Es werden nur .txt Dateien unterstützt!");
                                Console.ResetColor();
                                state = -1;
                            }
                            else
                            {
                                state = 2;
                            }
                            // 
                            break;
                        }

                    case 2: // Datei einlesen
                        {
                            try
                            {
                                // Lese Datei ein
                                for (int i = 0; i < textDatei.getNumberOfLines(file_path_gesamt); i++)
                                    file_lines.Add(textDatei.ReadLine(file_path_gesamt, i+1));


                                if (file_lines.Count != 0)
                                {
                                    Console.WriteLine("Datei \"{0}.slp\" erfolgreich eingelesen", file_name);
                                    state = 6;
                                }
                                else
                                {
                                    Console.WriteLine("Aus Datei \"{0}.slp\" konnte keine Zeilen gelesen werden", file_name);
                                    state = -1;
                                }
                            }
                            catch(Exception ex)
                            {
                                Console.WriteLine("Datei \"{0}.slp\" konnte nicht gelesen werden", file_name);
                                Console.WriteLine(ex.Message);
                                state = -1;
                            }
                            

                            break;
                        }

                    case 6: // Typ der SLP-Datei prüfen. Handelt es sich um Tracks oder Points?
                        {
                            // In der ersten Zeile der SLP-Datei sollte der Typ stehen
                            if(file_lines[0] == "TYPE=TRACK")
                            {
                                state = 3;
                                slpFileType = SlpType.Track;
                                Console.WriteLine("TYPE=TRACK in \"{0}.slp\" gefunden", file_name);
                            }
                            else if (file_lines[0] == "TYPE=POINT")
                            {
                                state = 7;
                                slpFileType = SlpType.Point;
                                Console.WriteLine("TYPE=POINT in \"{0}.slp\" gefunden", file_name);
                            }
                            else
                            {
                                state = 3;
                                slpFileType = SlpType.Track;
                                Console.WriteLine("Keine Version in \"{0}.slp\" gefunden. Gehe von TYPE=TRACK aus", file_name);
                            }

                            break;
                        }

                    case 3: // Version Track: Zeilen der Datei konvertieren und zu Track hinzufuegen
                        {
                            double lat;
                            double lon;
                            DateTime dateTime;
                            bool trackAnfang;
                            bool ersterTrackGefunden = false;

                            int placemarkNumber = 1;
                            var track = new SharpKml.Dom.GX.Track();
                            Placemark placemark = new Placemark();
                            placemark.Name = "Track " + placemarkNumber;
                            placemark.Open = true;      // Bei Google-Earth das Tree-Menu ausklappen

                            for (int i = 0; i < file_lines.Count; i++)
                            {
                                if(parseLine(file_lines[i],out lat, out lon, out dateTime, out trackAnfang))
                                {
                                    // Wurde ein Trackanfang gefunden
                                    if(trackAnfang)
                                    {
                                        // Handel es sich um den ertsen Track der Datei
                                        if (ersterTrackGefunden == false)
                                            // ja => mache nichts
                                            ersterTrackGefunden = true;
                                        else
                                        {
                                            // nein => speicher aktuellen Track ab

                                            // hat Track ueberhaupt Inhalt?
                                            if (track.Coordinates.Count() > 0)
                                            {
                                                // Weise Styleselector zu
                                                placemark.StyleUrl = new Uri(String.Format("#{0}", styleSelector.Id), UriKind.Relative);

                                                // Placemark Description
                                                DateTime start = track.When.ElementAt(0);     // Hole das Startdatum des Tracks
                                                DateTime ende = track.When.ElementAt<DateTime>(track.When.Count() - 1);     // Hole das Enddatum des Tracks

                                                SharpKml.Dom.Description description = new Description();
                                                description.Text = String.Format("Start: {0}\nEnde: {1}\nZeit: {2} Minuten", start.ToLongTimeString(), ende.ToLongTimeString(), Convert.ToInt16(ende.Subtract(start).TotalMinutes));
                                                placemark.Description = description;

                                                // Placemark Geometry / Inhalt
                                                placemark.Geometry = track;

                                                // Fuege Track hinzu
                                                root.AddFeature(placemark);

                                                // Placemark Nummer erhoehen
                                                placemarkNumber++;
                                            }

                                            // Neue Instanzen bilden
                                            track = new SharpKml.Dom.GX.Track();
                                            placemark = new Placemark();
                                            placemark.Name = "Track " + placemarkNumber;
                                        }
                                    }
                                    else
                                    {
                                        // speicher Koordinaten-Daten in Track
                                        var vector = new Vector(lat, lon);
                                        track.AddCoordinate(vector);
                                        track.AddWhen(dateTime);
                                    }
                                }
                            }

                            // speicher ggf. letzten Track ab
                            if(track.When.Count() > 0)
                            {
                                // Weise Styleselector zu
                                placemark.StyleUrl = new Uri(String.Format("#{0}", styleSelector.Id), UriKind.Relative);

                                // Placemark Description
                                DateTime start = track.When.ElementAt(0);     // Hole das Startdatum des Tracks
                                DateTime ende = track.When.ElementAt<DateTime>(track.When.Count() - 1);     // Hole das Enddatum des Tracks

                                SharpKml.Dom.Description description = new Description();
                                description.Text = String.Format("Start: {0}\nEnde: {1}\nZeit: {2} Minuten", start.ToLongTimeString(), ende.ToLongTimeString(), Convert.ToInt16(ende.Subtract(start).TotalMinutes));
                                placemark.Description = description;

                                // Placemark Geometry / Inhalt
                                placemark.Geometry = track;

                                // Fuege Track hinzu
                                root.AddFeature(placemark);
                            }

                            state = 4;
                            break;
                        }


                    case 7: // Type Point: Zeilen der Datei konvertieren und zu Point hinzufuegen
                        {
                            double lat;
                            double lon;
                            DateTime dateTime;
                            bool trackAnfang;           // Auch wenn es beim Poit keinen Trackanfang gibt, wird die Variabel fuer die Funktion gebraucht

                            int placemarkNumber = 1;
                            
                            

                            for (int i = 0; i < file_lines.Count; i++)
                            {
                                if (parseLine(file_lines[i], out lat, out lon, out dateTime, out trackAnfang))
                                {
                                    // Wurde ein Trackanfang gefunden
                                    if (trackAnfang)
                                    {
                                        continue;   // es gibt beim Point keinen Trackanfang...
                                    }
                                    else
                                    {
                                        var point = new SharpKml.Dom.Point();
                                        Placemark placemark = new Placemark();
                                        placemark.Name = "Point " + placemarkNumber++;
                                        placemark.Open = true;      // Bei Google-Earth das Tree-Menu ausklappen

                                        // Speicher Daten in Point
                                        Vector vector = new Vector(lat, lon);
                                        point.Coordinate = vector;

                                        // Erzeuge Placemark-Beschreibung
                                        SharpKml.Dom.Description description = new Description();
                                        description.Text = String.Format("Datum: {0}\nUhrzeit: {1}", dateTime.ToLongDateString(), dateTime.ToLongTimeString());
                                        placemark.Description = description;
                                        // Fuege den Point dem Placemark hinzu
                                        placemark.Geometry = point;

                                        // Fuege den Placemark dem Dokument hinzu
                                        root.AddFeature(placemark);
                                    }
                                }
                            }                           

                            state = 4;
                            break;

                        }

                    case 4: // KML-File erstellen
                        {
                            
                            KmlFile kml = KmlFile.Create(root, false);

                            try
                            {
                                String kmlDirPath = dir_path + dir_kml;                         // Pfad zum Verzeichnis

                                // KML-File Name
                                // konvertiere den "yyMMdd" Namen der SLP-Datei in das besser lesbare "yy-MM-dd" Format
                                String kml_file_name = String.Empty;
                                try
                                {
                                    DateTime dateTime = DateTime.ParseExact(file_name, "yyyyMMdd", System.Globalization.CultureInfo.InvariantCulture);
                                    kml_file_name = dateTime.ToString("yyyy-MM-dd");
                                }
                                catch
                                {
                                    kml_file_name = file_name;
                                }
                                //

                                // SLP-Typ als Name anhaengen
                                if (slpFileType == SlpType.Track)
                                    kml_file_name += " Track";
                                else if (slpFileType == SlpType.Point)
                                    kml_file_name += " Point";

                                String kmlFilePath = kmlDirPath + "\\" + kml_file_name + ".kml";    // Pfad zur Datei

                                // Directory erzeugen, falls nicht vorhanden
                                if (!Directory.Exists(kmlDirPath))
                                    Directory.CreateDirectory(kmlDirPath);

                                // Speicher
                                using (FileStream stream = File.OpenWrite(kmlFilePath))
                                {
                                    kml.Save(stream);
                                }
                                Console.WriteLine("KML-Datei \"{0}.kml\" wurde erfolgreich gespeichert", file_name);
                            }
                            catch(Exception ex)
                            {
                                Console.WriteLine("KML-Datei \"{0}.kml\" konnte nicht gespeichert werden", file_name);
                                Console.WriteLine(ex.Message);
                            }

                            root = new Document();
                            root.Open = true;       // Bei Google-Earth das Tree-Menu ausklappen

                            state = 5;
                            break;
                        }

                    case 5: // Wie gehts weiter?

                        if (startWithArgs)
                        {
                            exit = true;
                            // warte x Sekunden, bis Programm geschlossen wird
                            Console.Write("Programm wird beendet ");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write(".");
                            System.Threading.Thread.Sleep(100);
                            Console.Write("\r\nBey!");
                            System.Threading.Thread.Sleep(500);

                        }
                        else
                        {
                            state = -1;
                        }
                        break;
                }
            }           
        }

        /// <summary>
        /// Untersucht eine Zeile und extrahiert die Latitude, Longitude, Zeit und ggf. den Anfang eines Tracks
        /// </summary>
        /// <param name="line"></param>
        /// <param name="lat"></param>
        /// <param name="lon"></param>
        /// <param name="dateTime"></param>
        /// <param name="trackAnfang"></param>
        /// <returns></returns>
        static bool parseLine(string line, out double lat, out double lon, out DateTime dateTime, out bool trackAnfang)
        {
            // Aufbau einer Zeile
            // Latitude/Longitude/DateTime/HDOP/AGE
            // 53.060184/8.762852/20180622045701/230/539

            // Aufbau eines Anfangs eines Tracks
            // Startzeichen: vergangene Sekunden fuer den ersten GPS-Fix
            // #123


            int state = 0;

            lat = 0;
            lon = 0;
            dateTime = new DateTime();
            trackAnfang = false;

            StringBuilder sLat = new StringBuilder();
            StringBuilder sLon = new StringBuilder();
            StringBuilder sDateTime = new StringBuilder();
            StringBuilder sHDOP = new StringBuilder();
            StringBuilder sAge = new StringBuilder();

            // Wenn in Zeile keine Daten stehen, muss auch nicht geparst werden...
            if ((line == "") || (line == "\r\n"))
                return false;

            for(int i = 0; i < line.Length; i++)
            {
                char c = line[i];

                if(c == '#')        // Pruefe ob ein Anfang von einem Track gefunden wurde
                {
                    trackAnfang = true;
                    return true;
                }
                else if ((c == '\r') || (c == '\n'))    // Pruefe auf Ende einer Zeile
                    break;
                else if (c == '/')          // Pruefe auf Sperator der Daten
                {
                    state++;
                    continue;
                }

                switch(state)
                {
                    case 0:         // Latitude
                        sLat.Append(c);
                        break;

                    case 1:         // Longitude
                        sLon.Append(c);
                        break;

                    case 2:         // DateTime
                        sDateTime.Append(c);
                        break;

                    case 3:         // HDOP
                        sHDOP.Append(c);
                        break;

                    case 4:         // Age
                        sAge.Append(c);
                        break;
                }                
            }

            

            try
            {
                // Konvertiere 
                System.Globalization.NumberStyles style = System.Globalization.NumberStyles.AllowDecimalPoint;
                if (!double.TryParse(sLat.ToString(), style, System.Globalization.CultureInfo.InvariantCulture, out lat))
                    return false;

                if (!double.TryParse(sLon.ToString(), style, System.Globalization.CultureInfo.InvariantCulture, out lon))
                    return false;

                DateTime dateTimeUTC = DateTime.ParseExact(sDateTime.ToString(), "yyyyMMddHHmmss", System.Globalization.CultureInfo.InvariantCulture);
                DateTime.SpecifyKind(dateTimeUTC, DateTimeKind.Utc);
                // UTC Zeit in Local Zeit umwandeln
                dateTime = dateTimeUTC.ToLocalTime();
            }
            catch
            {
                return false;
            }
                        

            return true;
        }


        static Style CreateNormalSyle()
        {
            Style newStyle = new Style();
            newStyle.Id = "normalStyle";

            LineStyle lineStyle = new LineStyle();
            lineStyle.Color = Color32.Parse("ff00ff00");
            lineStyle.Width = 4;

            var iconStyle = new IconStyle();
            iconStyle.Color = Color32.Parse("ff00ff00");
            iconStyle.Scale = 1;
            iconStyle.Icon = new IconStyle.IconLink(new Uri("http://earth.google.com/images/kml-icons/track-directional/track-0.png"));

            newStyle.Line = lineStyle;
            newStyle.Icon = iconStyle;

            return newStyle;
        }


        static Style CreateHighlightSyle()
        {
            Style newStyle = new Style();
            newStyle.Id = "highlightStyle";

            LineStyle lineStyle = new LineStyle();
            lineStyle.Color = Color32.Parse("ff00ff00");
            lineStyle.Width = 8;

            var iconStyle = new IconStyle();
            iconStyle.Color = Color32.Parse("ff00ff00");
            iconStyle.Scale = 1.2;
            iconStyle.Icon = new IconStyle.IconLink(new Uri("http://earth.google.com/images/kml-icons/track-directional/track-0.png"));

            newStyle.Line = lineStyle;
            newStyle.Icon = iconStyle;

            return newStyle;
        }


        static StyleSelector createPlacemarkLineStyleMap(Style normalStyle, Style highlightStyle)
        {
            // Set up style map
            StyleMapCollection styleMapCollection = new StyleMapCollection();
            styleMapCollection.Id = String.Format("stylemap");

            // Create the normal line pair
            Pair normalPair = new Pair();
            normalPair.StyleUrl = new Uri(String.Format("#{0}", normalStyle.Id), UriKind.Relative);
            normalPair.State = StyleState.Normal;

            // Create the highlight line pair
            Pair highlightPair = new Pair();
            highlightPair.StyleUrl = new Uri(String.Format("#{0}", highlightStyle.Id), UriKind.Relative);
            highlightPair.State = StyleState.Highlight;

            // Attach both pairs to the map
            styleMapCollection.Add(normalPair);
            styleMapCollection.Add(highlightPair);

            return styleMapCollection;
        }




        /// <summary>
        /// Gibt den Namen der Datei aus dem übergebenen Pfad zurück
        /// </summary>
        /// <param name="path"></param>
        /// <returns></returns>
        static string getNameFromPath(string path)
        {
            int index = path.LastIndexOf('\\');
            string name = String.Empty;

            for (int i = index + 1; i < path.Length; i++)
            {
                if (path[i] != '.')
                    name += path[i];
                else
                    break;
            }
            return name;
        }

        static string getFileTypeFromPath(string path)
        {
            int index = path.LastIndexOf('.');
            string filetype = String.Empty;

            for (int i = index + 1; i < index + 1 + 3; i++)
            {
                filetype += path[i];
            }
            return filetype;
        }


        /// <summary>
        /// Giebt den Pfad zum Verzeichnis der Datei zurück. Im Übergebenen String steht auch noch der Name der Datei
        /// </summary>
        /// <param name="path"></param>
        /// <returns></returns>
        static string getDirFromPath(string path)
        {
            int index = path.LastIndexOf('\\');
            string mypath = String.Empty;

            for (int i = 0; i < index; i++)
            {
                mypath += path[i];
            }
            return mypath;
        }

    }
}
