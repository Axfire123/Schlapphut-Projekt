package com.software.hfieber.schlapphut.classes;

import java.io.Serializable;

public class ParameterSettings implements Serializable {

    int aufwachIntervall;
    int pointSpeicherIntervall;
    int schlafenNachLimaAus;
    int sdCardWriteError;
    boolean isPointModusAvailable;

    ParameterSettings()
    {
        aufwachIntervall = 0;
        pointSpeicherIntervall = 0;
        schlafenNachLimaAus = 0;
        sdCardWriteError = 0;
        isPointModusAvailable = false;
    }

    public int getAufwachIntervall() {
        return aufwachIntervall;
    }

    public void setAufwachIntervall(int aufwachIntervall) {
        this.aufwachIntervall = aufwachIntervall;
    }

    public int getPointSpeicherIntervall() {
        return pointSpeicherIntervall;
    }

    public void setPointModusAvailable(boolean pointModusAvailable) {
        isPointModusAvailable = pointModusAvailable;
    }

    public int getSchlafenNachLimaAus() {
        return schlafenNachLimaAus;
    }

    public void setSchlafenNachLimaAus(int schlafenNachLimaAus) {
        this.schlafenNachLimaAus = schlafenNachLimaAus;
    }

    public boolean isPointModusAvailable() {
        return isPointModusAvailable;
    }

    public void setPointSpeicherIntervall(int pointSpeicherIntervall) {
        this.pointSpeicherIntervall = pointSpeicherIntervall;
    }

    public int getSdCardWriteError() {
        return sdCardWriteError;
    }

    public void setSdCardWriteError(int sdCardWriteError) {
        this.sdCardWriteError = sdCardWriteError;
    }
}
