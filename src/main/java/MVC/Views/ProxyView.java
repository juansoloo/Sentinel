package MVC.Views;

import MVC.Facade.ScannerFacade;
import MVC.Interfaces.Observer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ProxyView implements Observer {
    ScannerFacade scannerFacade = new ScannerFacade();

    private JPanel root;
    private JTextField proxyIP;
    private JTable endpointTable;
    private JTable requestTable;
    private JTable responseTable;
    private JScrollPane httpHistoryPane;
    private JScrollPane requestPane;
    private JScrollPane responsePane;
    private DefaultTableModel endpointTableModel;

    public ProxyView() {
        String[] columns = {
                "#",
                "Host",
                "Method",
                "URL",
                "Status Code",
                "Length",
                "MIME TYPE",
        };

        endpointTableModel = new DefaultTableModel(columns, 0);

        endpointTable.setModel(endpointTableModel);

        endpointTableModel.addRow(new Object[]{
            1,
            "https://www.google.com",
            "GET",
            "/search?q=test",
            200,
            93267,
            "HTML"
        });
    }

    public void displayScannerData() {}

    public void displayReport() {}

    public void displayError() {}

    @Override
    public void update() {}

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
