package MVC.Views;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

public class ProxyTableContextMenu {
    private final JTable table;
    private final JPopupMenu actionWindow;

    public ProxyTableContextMenu(JTable table,
                                Runnable onAddHostFilter,
                                Runnable onClearHistory,
                                Runnable onSendToRepeater) {
        this.table = table;

        actionWindow = new JPopupMenu();

        JMenuItem filterItem = new JMenuItem("Add to host filter");
        JMenuItem clearItem = new JMenuItem("Clear History");
        JMenuItem repeaterItem = new JMenuItem("Send to Repeater");

        actionWindow.add(filterItem);
        actionWindow.add(clearItem);
        actionWindow.add(repeaterItem);

        filterItem.addActionListener(e -> onAddHostFilter.run());

        repeaterItem.addActionListener(e -> onSendToRepeater.run());

        clearItem.addActionListener(e -> onClearHistory.run());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int rowClicked = table.rowAtPoint(e.getPoint());
                    if (rowClicked >= 0) {
                        table.setRowSelectionInterval(rowClicked,rowClicked);
                        actionWindow.show(table, e.getX(), e.getY());
                    }
                }
            }
        });
    }
}
