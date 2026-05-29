package MVC.Views;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import MVC.Facade.ScannerFacade;
import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpTransaction;

public class ProxyView implements ProxyModelListener {
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

    public JPanel getRoot() {
        return root;
    }

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

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    @Override
    public void update(HttpTransaction transaction) {
        SwingUtilities.invokeLater(() -> endpointTableModel.addRow(new Object[]{
                endpointTableModel.getRowCount() + 1,
                transaction.request().host(),
                transaction.request().method(),
                transaction.request().path(),
                transaction.response().statusCode(),
                "",
                ""
        }));
    }
}
