package MVC.Views;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProxyTableContextMenu {
    private JPopupMenu actionWindow;
    private Runnable applyFilter;
    private JTable table;
    private DefaultListModel<String> listModel;
    private String pendingHost;


    public ProxyTableContextMenu(JTable table,
                                 DefaultListModel<String> listModel,
                                 Runnable applyFilter,
                                 Runnable clearHistory) {
        this.table = table;
        this.listModel = listModel;
        this.applyFilter = applyFilter;

        actionWindow = new JPopupMenu();
        JMenuItem filterItem = new JMenuItem("Add to host filter");
        JMenuItem clearItem = new JMenuItem("Clear History");
        actionWindow.add(filterItem);
        actionWindow.add(clearItem);

        filterItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!listModel.contains(pendingHost)) {
                    listModel.addElement(pendingHost);
                    applyFilter.run();
                }
            }
        });

        clearItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearHistory.run();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int rowClicked = table.rowAtPoint(e.getPoint());
                    if (rowClicked >= 0) {
                        table.setRowSelectionInterval(rowClicked,rowClicked);
                        pendingHost = table.getValueAt(rowClicked, 1).toString();
                        actionWindow.show(table, e.getX(), e.getY());
                    }
                }
            }
        });
    }
}
