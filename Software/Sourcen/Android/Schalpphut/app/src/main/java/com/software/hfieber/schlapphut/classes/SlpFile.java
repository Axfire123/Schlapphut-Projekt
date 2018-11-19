package com.software.hfieber.schlapphut.classes;

import java.io.Serializable;
import java.util.ArrayList;

public class SlpFile implements Serializable {


    public static enum SlpType {Track, Point, LogFile}

    // Anmerkung: Ein Log-File ist eigentlich kein SLP-File. Aber ich bin gerade fauel um noch eine Klasse und co zu erstellen...

    private String name;
    private SlpType type;
    private int size = 0;
    private ArrayList<String> lines = new ArrayList<>();
    private boolean iAmNoSlpFileIAmALogFile = false;

    public SlpFile()
    {
    }

    public SlpFile(String name, SlpType type)
    {
        this.name = name;
        this.type = type;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SlpType getType() {
        return type;
    }

    public void setType(SlpType type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void addLine(String line)
    {
        lines.add(line);
    }

    public String getLine(int lineAt)
    {
        return lines.get(lineAt);
    }

    public int getLineSize()
    {
        return lines.size();
    }

    public ArrayList<String> getLines() {
        return lines;
    }

    @Override
    public String toString() {
        //return super.toString();
        double size = getSize() / 1024;
        String string = name + " - " + Math.round(size) + " kB";
        return string;
    }

    public boolean isiAmNoSlpFileIAmALogFile() {
        return iAmNoSlpFileIAmALogFile;
    }
}
