package MVC.Views;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpHeader;
import Proxy.HttpTransaction;
import Proxy.ProxyRequest;
import Proxy.ProxyResponse;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProxyView implements ProxyModelListener {
    private JPanel root;
    private JPanel topBar;
    private JTextField proxyIP;
    private JTable endpointTable;
    private JTextArea requestTextArea;
    private JTextArea responseTextArea;
    private JScrollPane httpHistoryPane;
    private JScrollPane requestPane;
    private JScrollPane responsePane;
    private DefaultTableModel endpointTableModel;
    private List<HttpTransaction> record = new ArrayList<>();

    public JPanel getRoot() {
        return root;
    }

    public ProxyView() {
        proxyIP = new JTextField();
        proxyIP.setEditable(false);
        endpointTable = new JTable();

        requestTextArea = new JTextArea(10,50);
        requestTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestTextArea.setEditable(false);
        requestPane = new JScrollPane(requestTextArea);

        httpHistoryPane = new JScrollPane(endpointTable);
        responseTextArea = new JTextArea(10,50);
        responseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseTextArea.setEditable(false);

        responsePane = new JScrollPane(responseTextArea);

        root = new JPanel(new BorderLayout());
        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(proxyIP);
        root.add(topBar, BorderLayout.NORTH);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPane, responsePane);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, httpHistoryPane, bottomSplit);
        mainSplit.setResizeWeight(0.6);

        root.add(mainSplit, BorderLayout.CENTER);

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
        endpointTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = endpointTable.getSelectedRow();
                if (row >= 0 && row < record.size()) {
                    HttpTransaction tx = record.get(row);
                    requestTextArea.setText(renderRequest(tx.request()));
                    responseTextArea.setText(renderResponse(tx.response()));
                }

            }
        });
    }

    private String renderRequest(ProxyRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.method())
                .append(" ")
                .append(req.path())
                .append(" ")
                .append(req.httpVersion())
                .append("\r\n");
        for (String header : req.headers()) {
            sb.append(header).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private String renderResponse(ProxyResponse res) {
        StringBuilder sb = new StringBuilder();
        sb.append(res.httpVersion())
                .append(" ")
                .append(res.statusCode())
                .append(" ")
                .append(res.reasonPhrase())
                .append("\r\n");
        for (HttpHeader header : res.headers()) {
            sb.append(header.name())
                    .append(": ")
                    .append(header.value())
                    .append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    public void displayScannerData() {}

    public void displayReport() {}

    public void displayError() {}

    public void update(HttpTransaction transaction) {
        SwingUtilities.invokeLater(() -> {
            record.add(transaction);
            endpointTableModel.addRow(new Object[]{
                    endpointTableModel.getRowCount() + 1,
                    transaction.request().host(),
                    transaction.request().method(),
                    transaction.request().path(),
                    transaction.response().statusCode(),
                    "",
                    ""
            });
        });
    }
}
