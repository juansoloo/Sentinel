package MVC.Views;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

public class RepeaterView {
    private final JPanel root;
    private JTextArea requestTextArea;
    private JTextArea responseTextArea;
    private JButton repeaterBtn;
    private JSplitPane mainSplit;
    private JScrollPane requestScrollPane;
    private JScrollPane responseScrollPane;
    private JPanel topBar;
    private Runnable onSend;

    public RepeaterView() {
        root = new JPanel(new BorderLayout());

        requestTextArea = new JTextArea(40,50);
        requestScrollPane = new JScrollPane(requestTextArea);
        requestTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestTextArea.setEditable(true);

        responseTextArea = new JTextArea(40, 50);
        responseScrollPane = new JScrollPane(responseTextArea);
        responseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseTextArea.setEditable(false);

        repeaterBtn = new JButton("SEND");

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestScrollPane, responseScrollPane);

        root.add(mainSplit, BorderLayout.CENTER);

        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(repeaterBtn);
        root.add(topBar, BorderLayout.NORTH);

        repeaterBtn.addActionListener(e -> {
            if (onSend != null) {
                onSend.run();
            }
        });
    }

    public String getRequestText() {
        return requestTextArea.getText();
    }

    public void loadRequest(String rawRequest) {
        requestTextArea.setText(rawRequest);
        requestTextArea.setCaretPosition(0);
    }

    public void displayResponse(String responseText) {
        responseTextArea.setText(responseText);
        responseTextArea.setCaretPosition(0);
    }

    public JPanel getRoot() {
        return root;
    }

    public void setOnSend(Runnable onSend) {
        this.onSend = onSend;
    }
}
