package MVC.Views;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.function.IntConsumer;

import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpHeader;
import Proxy.HttpMessageFormatter;
import Proxy.HttpTransaction;

public class ProxyView implements ProxyModelListener {
    private final JPanel root;
    private JTable endpointTable;
    private JTextArea requestTextArea;
    private JTextArea responseTextArea;
    private JScrollPane httpHistoryPane;
    private JScrollPane requestPane;
    private JScrollPane responsePane;
    private DefaultTableModel endpointTableModel;
    private IntConsumer onRowSelected;
    private TableRowSorter<DefaultTableModel> sorter;
    private DefaultListModel<String> listModel;

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
                
                if (row >= 0 && onRowSelected != null) {
                    int modelRow = endpointTable.convertRowIndexToModel(row);
                    onRowSelected.accept(modelRow);
                }
            }
        });
    }

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

    public void setOnRowSelected(IntConsumer onRowSelected) {
        this.onRowSelected = onRowSelected;
    }

    public void displayTransaction(HttpTransaction transaction) {
        if (transaction == null) {
            requestTextArea.setText("");
            responseTextArea.setText("");
            return;
        }

        requestTextArea.setText(HttpMessageFormatter.renderRequest(transaction.request()));
        requestTextArea.setCaretPosition(0);

        responseTextArea.setText(HttpMessageFormatter.renderResponse(transaction.response()));
        responseTextArea.setCaretPosition(0);
    }

    public void clearHistoryDisplay() {
        endpointTableModel.setRowCount(0);
        requestTextArea.setText("");
        responseTextArea.setText("");
    }

    public void update(HttpTransaction transaction) {
        SwingUtilities.invokeLater(() -> {

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
