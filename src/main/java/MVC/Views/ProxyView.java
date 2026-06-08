package MVC.Views;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpHeader;
import Proxy.HttpTransaction;
import Proxy.ProxyRequest;
import Proxy.ProxyResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProxyView implements ProxyModelListener {
    private final JPanel root;
    private JTable endpointTable;
    private JTextArea requestTextArea;
    private JTextArea responseTextArea;
    private JScrollPane httpHistoryPane;
    private JScrollPane requestPane;
    private JScrollPane responsePane;
    private DefaultTableModel endpointTableModel;
    private List<HttpTransaction> record = new ArrayList<>();
    private TableRowSorter<DefaultTableModel> sorter;
    private DefaultListModel<String> listModel;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    public JPanel getRoot() {
        return root;
    }

    public TableRowSorter<DefaultTableModel> getSorter() {
        return sorter;
    }

    public DefaultListModel<String> getListModel() {
        return listModel;
    }

    public JTable getEndpointTable() {
        return endpointTable;
    }

    public ProxyView() {
        endpointTable = new JTable();

        requestTextArea = new JTextArea(10,50);
        requestTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestTextArea.setEditable(false);
        requestPane = new JScrollPane(requestTextArea);

        httpHistoryPane = new JScrollPane(endpointTable);
        responseTextArea = new JTextArea(10,50);
        responseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseTextArea.setEditable(false);

        listModel = new DefaultListModel<>();

        responsePane = new JScrollPane(responseTextArea);

        root = new JPanel(new BorderLayout());

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

        endpointTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        sorter = new TableRowSorter<>(endpointTableModel);
        endpointTable.setModel(endpointTableModel);
        endpointTable.setRowSorter(sorter);

        endpointTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = endpointTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = endpointTable.convertRowIndexToModel(row);
                    if (modelRow >= 0 && modelRow < record.size()) {
                        HttpTransaction tx = record.get(modelRow);
                        requestTextArea.setText(renderRequest(tx.request()));
                        requestTextArea.setCaretPosition(0);

                        responseTextArea.setText(renderResponse(tx.response()));
                        responseTextArea.setCaretPosition(0);
                    }
                }
            }
        });
    }

    public void clearHistory() {
        endpointTableModel.setRowCount(0);

        requestTextArea.setText("");
        responseTextArea.setText("");

        record.clear();
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

        String contentType = "";

        for (String header : req.headers()) {
            if (header.toLowerCase().startsWith("content-type:")) {
                contentType = header.substring(header.indexOf(":") + 1).trim();
            }
        }

        if (req.body() != null && req.body().length > 0) {
            String body = new String(req.body(), StandardCharsets.UTF_8);
            if (contentType.contains("application/json")) {
                try {
                    JsonElement parsed = JsonParser.parseString(body);
                    sb.append(PRETTY_GSON.toJson(parsed));
                } catch (Exception e) {
                    sb.append(body);
                }
            } else {
                sb.append(body);
            }
        }

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

        String contentType = "";

        for (HttpHeader header : res.headers()) {
            if (header.name().equalsIgnoreCase("Content-Type")) {
                contentType = header.value();
            }
        }

        if (res.body() != null && res.body().length > 0) {
            String body = new String(res.body(), StandardCharsets.UTF_8);
            if (contentType.contains("application/json")) {
                try {
                    JsonElement parsed = JsonParser.parseString(body);
                    sb.append(PRETTY_GSON.toJson(parsed));
                } catch (Exception e) {
                    sb.append(body);
                }
            } else {
                sb.append(body);
            }
        }

        return sb.toString();
    }

    public void displayScannerData() {}

    public void displayReport() {}

    public void displayError() {}

    public void update(HttpTransaction transaction) {
        SwingUtilities.invokeLater(() -> {
            record.add(transaction);

            String length = "";
            String mimeType = "";

            for (HttpHeader header : transaction.response().headers()) {
                if (header.name().equalsIgnoreCase("Content-Length")) {
                    length = header.value();
                }

                if (header.name().equalsIgnoreCase("Content-Type")) {
                    mimeType = header.value();
                }
            }

            endpointTableModel.addRow(new Object[]{
                    endpointTableModel.getRowCount() + 1,
                    transaction.request().host(),
                    transaction.request().method(),
                    transaction.request().path(),
                    transaction.response().statusCode(),
                    length,
                    mimeType
            });
        });
    }
}
