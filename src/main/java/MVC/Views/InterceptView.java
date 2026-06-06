package MVC.Views;

import Proxy.InterceptQueue;
import Proxy.InterceptedRequest;
import Proxy.ProxyRequest;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        interceptTextArea.setEditable(true);

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
            String edited = interceptTextArea.getText();
            ProxyRequest parsed = parseRequest(edited);

            if (selected == null) {
                JOptionPane.showMessageDialog(root, "Nothing was selected.");
            } else if (parsed == null) {
                JOptionPane.showMessageDialog(root, "Invalid request format.");
            } else {
                selected.forwardEdited(parsed);
            }
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

    private String[] splitThree(String lines) {
        if (lines == null) {
            return null;
        }

        String[] reqLine = lines.trim().split(" ", 3);

        if (reqLine.length != 3) {
            return null;
        }

        return reqLine;
    }

    private ProxyRequest parseRequest(String raw) {
        String[] lines = raw.split("\\r?\\n");
        String reqLines = lines[0];

        List<String> headers = new ArrayList<>();
        String[] tokens = splitThree(reqLines);

        if (tokens == null) {
            return null;
        }

        String method = tokens[0];
        String httpPath = tokens[1];
        String httpVersion = tokens[2];

        String host = null;
        int contentLength = 0;
        StringBuilder bodyBuilder = new StringBuilder();

        boolean inBody = false;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if(!inBody) {
                if (line.isEmpty()) {
                    inBody = true;
                } else {
                    headers.add(line);
                }

                int colonIndex = line.indexOf(":");

                if (colonIndex == -1) {
                    continue;
                }

                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                if (name.equalsIgnoreCase("Host")) {
                    host = value;
                }

                if (name.equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(value);
                }

            }

            if (inBody && !line.isEmpty()) {
                bodyBuilder.append(line).append("\n");
            }
        }

        byte[] bodyBytes = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);

        if (host == null) {
            return null;
        }

        return new ProxyRequest(
                method,
                httpPath,
                httpVersion,
                host,
                headers,
                contentLength,
                bodyBytes
        );
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

        if (req.body() != null && req.body().length > 0) {
            String body = new String(req.body(), StandardCharsets.UTF_8);
            sb.append(body);
        }

        return sb.toString();
    }
}
