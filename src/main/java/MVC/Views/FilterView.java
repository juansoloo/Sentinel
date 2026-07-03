package MVC.Views;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterView {
    private JPanel root;
    private JTextField searchField;
    private JCheckBox caseSensitiveBox;
    private JList<String> hostList;
    private JButton removeHost;
    private JButton applyFilter;
    private TableRowSorter<DefaultTableModel> sorter;
    private DefaultListModel<String> listModel;

    public FilterView(TableRowSorter<DefaultTableModel> sorter,
                      DefaultListModel<String> listModel) {
        this.sorter = sorter;
        this.listModel = listModel;

        root = new JPanel(new BorderLayout());

        searchField = new JTextField(20);
        caseSensitiveBox = new JCheckBox();
        hostList = new JList<>(listModel);
        removeHost = new JButton("REMOVE");
        applyFilter = new JButton("APPLY");

        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        northPanel.add(searchField);
        northPanel.add(caseSensitiveBox);
        northPanel.add(applyFilter);

        JScrollPane scrollPane = new JScrollPane(hostList);

        JPanel southPanel = new JPanel(new FlowLayout());
        southPanel.add(removeHost);

        root.add(northPanel, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.WEST);
        root.add(southPanel, BorderLayout.SOUTH);

        removeHost.addActionListener(e -> {
            String itemRemoved = hostList.getSelectedValue();
            if (itemRemoved != null) {
                listModel.removeElement(itemRemoved);
            }

            applyFilter();
        });

        applyFilter.addActionListener(e -> {
            applyFilter();
        });

        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        };

        searchField.getDocument().addDocumentListener(documentListener);
        caseSensitiveBox.addActionListener(e -> applyFilter());

    }

    public void addHostFilter(String host) {
        if (host == null || host.isBlank()) {
            return;
        }

        if (!listModel.contains(host)) {
            listModel.addElement(host);
        }

        applyFilter();
    }

    public void applyFilter() {
        try {
            List<RowFilter<Object, Object>> searchParams = new ArrayList<>();
            List<RowFilter<Object, Object>> hostFilters = new ArrayList<>();

            for (int i = 0; i < listModel.size(); i++) {
                String listELem = listModel.getElementAt(i).toString();
                hostFilters.add(RowFilter.regexFilter(Pattern.quote(listELem), 1));
            }

            if (!hostFilters.isEmpty()) {
                searchParams.add(RowFilter.orFilter(hostFilters));
            }

            String searchInput = searchField.getText();

            if (!searchInput.isEmpty()) {
                String pattern = "(?i)" + searchInput;
                try {
                    if (caseSensitiveBox.isSelected()) {
                        searchParams.add(RowFilter.regexFilter(searchInput));
                    } else {
                        searchParams.add(RowFilter.regexFilter(pattern));
                    }
                } catch (PatternSyntaxException e) {
                    return;
                }
            }

            if (searchParams.isEmpty()) {
                sorter.setRowFilter(null);
            } else if (searchParams.size() == 1) {
                sorter.setRowFilter(searchParams.get(0));
            } else {
                sorter.setRowFilter(RowFilter.andFilter(searchParams));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JPanel getRoot() {
        return root;
    }
}
