package MVC.Views;

import Proxy.InterceptQueue;
import Proxy.InterceptedRequest;
import Proxy.ProxyRequest;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;

public class InterceptView implements InterceptQueue.InterceptListener {
    private JPanel root;
    private DefaultListModel<InterceptedRequest> listModel;
    private JList<InterceptedRequest> interceptList;
    private JTextArea interceptTextArea;
    private JButton forwardButton;
    private JButton dropButton;
    private JToggleButton interceptToggle;

    public InterceptView(InterceptQueue interceptQueue) {
        interceptQueue.setListener(this);

        root = new JPanel(new BorderLayout());
        listModel = new DefaultListModel<>();
        forwardButton = new JButton("Forward");
        dropButton = new JButton("Drop");
        interceptToggle = new JToggleButton("Intercept: OFF");
        interceptToggle.addActionListener(e -> {
            if (interceptToggle.isSelected()) {
                interceptToggle.setText("Intercept: ON");
                interceptQueue.setIntercepting(true);
            } else {
                interceptToggle.setText("Intercept: OFF");
                interceptQueue.setIntercepting(false);
            }
        });

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(interceptToggle);
        root.add(topBar, BorderLayout.NORTH);

        interceptList = new JList<>(listModel);

        JScrollPane listScrollPane = new JScrollPane(interceptList);

        interceptTextArea = new JTextArea(10, 50);
        interceptTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane textScrollPane = new JScrollPane(interceptTextArea);
        interceptTextArea.setEditable(false);

        JSplitPane interceptJSplit = new JSplitPane(HORIZONTAL_SPLIT, listScrollPane, textScrollPane);
        root.add(interceptJSplit, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(forwardButton);
        buttonRow.add(dropButton);

        root.add(buttonRow, BorderLayout.SOUTH);

        interceptList.setCellRenderer((list,
                                       value,
                                       index,
                                       isSelected,
                                       cellHasFocus) -> {
            String text = value.request().method() + " " +
                    value.request().host() +
                    value.request().path();

            JLabel label = new JLabel(text);
            label.setOpaque(true);
            label.setBackground(
                    isSelected
                        ? list.getSelectionBackground()
                        : list.getBackground()
            );
            label.setForeground(
                    isSelected
                        ? list.getSelectionForeground()
                        : list.getForeground()
            );
            return label;
        });

        interceptList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                InterceptedRequest selected = interceptList.getSelectedValue();
                if (selected != null) {
                    interceptTextArea.setText(renderRequest(selected.request()));
                }
            }
        });

        forwardButton.addActionListener(e -> {
            InterceptedRequest selected = interceptList.getSelectedValue();
            if (selected != null) selected.forward();
        });

        dropButton.addActionListener(e -> {
            InterceptedRequest selected = interceptList.getSelectedValue();
            if (selected != null) selected.drop();
        });
    }

    public JPanel getRoot() {
        return root;
    }

    @Override
    public void onQueueChange(List<InterceptedRequest> pending) {
        SwingUtilities.invokeLater(() -> {
            InterceptedRequest selected = interceptList.getSelectedValue();
            listModel.clear();

             for (InterceptedRequest req : pending) {
                 listModel.addElement(req);
             }

             if (selected != null && listModel.contains(selected)) {
                 interceptList.setSelectedValue(selected, true);
             } else {
                 interceptTextArea.setText("");
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
}
