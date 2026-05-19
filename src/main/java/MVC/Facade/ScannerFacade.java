package MVC.Facade;

import MVC.Models.LLMModel;
import MVC.Models.ProxyModel;
import MVC.Models.ReportModel;

public class ScannerFacade {
    ProxyModel scannerModel = new ProxyModel();
    ReportModel reportModel = new ReportModel();
    LLMModel llmModel = new LLMModel();

    public void scanDocument() {}

    public void generateReport() {}

    public void runAnalysis() {}
}
