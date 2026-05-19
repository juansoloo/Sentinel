package MVC.Models;

import MVC.Interfaces.Subject;

public class ReportModel implements Subject {
    private String reportName;

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getReportName() {
        return reportName;
    }

    @Override
    public void addObserver() {}

    @Override
    public void removeObserver() {}

    @Override
    public void notifyObservers() {}
}
