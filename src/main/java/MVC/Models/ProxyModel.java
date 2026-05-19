package MVC.Models;

import MVC.Interfaces.Subject;

public class ProxyModel implements Subject {
    private String scannerName;

    public void setScannerName(String scannerName) {
        this.scannerName = scannerName;
    }

    public String getScannerName() {
        return scannerName;
    }
    @Override
    public void addObserver() {

    }

    @Override
    public void removeObserver() {

    }

    @Override
    public void notifyObservers() {

    }
}
