import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class Client extends JFrame {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_TCP_PORT = 6789;
    private static final int DEFAULT_UDP_PORT = 9871;
    private static final int UDP_TIMEOUT_MS = 2000;

    private final JTextField hostField = new JTextField(DEFAULT_HOST, 14);
    private final JSpinner tcpPortSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_TCP_PORT, 1, 65535, 1));
    private final JSpinner udpPortSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_UDP_PORT, 1, 65535, 1));
    private final JButton connectButton = new JButton("Connect");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JButton statusButton = new JButton("UDP Status");
    private final JTextArea logArea = new JTextArea();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Send");

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private Thread receiverThread;

    public Client() {
        super("Naval Battle Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(720, 460));
        setSize(720, 460);
        setLocationByPlatform(true);

        buildInterface();
        registerListeners();
        setConnected(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
        });
    }

    private void buildInterface() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel connectionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, 0, 8);
        constraints.gridy = 0;

        addLabeledField(connectionPanel, constraints, "Host", hostField);
        addLabeledField(connectionPanel, constraints, "TCP", tcpPortSpinner);
        addLabeledField(connectionPanel, constraints, "UDP", udpPortSpinner);

        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        connectionPanel.add(connectButton, constraints);
        connectionPanel.add(disconnectButton, constraints);
        connectionPanel.add(statusButton, constraints);
        constraints.weightx = 1;
        connectionPanel.add(Box.createHorizontalGlue(), constraints);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);

        JPanel messagePanel = new JPanel(new BorderLayout(8, 0));
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        content.add(connectionPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(messagePanel, BorderLayout.SOUTH);
    }

    private void addLabeledField(JPanel panel, GridBagConstraints constraints, String label, JComponent field) {
        panel.add(new JLabel(label), constraints);
        panel.add(field, constraints);
    }

    private void registerListeners() {
        connectButton.addActionListener(event -> connect());
        disconnectButton.addActionListener(event -> disconnect());
        statusButton.addActionListener(event -> requestUdpStatus());
        sendButton.addActionListener(event -> sendMessage());
        messageField.addActionListener(event -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                disconnect();
                dispose();
                System.exit(0);
            }
        });
    }

    private void connect() {
        if (isConnected()) {
            return;
        }

        String host = hostField.getText().trim();
        int tcpPort = (Integer) tcpPortSpinner.getValue();

        appendLog("Connecting to " + host + ":" + tcpPort + "...");
        setControlsEnabled(false);

        Thread connector = new Thread(() -> {
            try {
                Socket newSocket = new Socket(host, tcpPort);
                BufferedReader newInput = new BufferedReader(new InputStreamReader(newSocket.getInputStream()));
                PrintWriter newOutput = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream()), true);

                SwingUtilities.invokeLater(() -> {
                    socket = newSocket;
                    input = newInput;
                    output = newOutput;
                    setConnected(true);
                    appendLog("Connected.");
                    startReceiver();
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("Connection failed: " + exception.getMessage());
                    setConnected(false);
                });
            }
        }, "client-connector");

        connector.start();
    }

    private void startReceiver() {
        receiverThread = new Thread(() -> {
            try {
                String line;
                while ((line = input.readLine()) != null) {
                    String receivedLine = line;
                    SwingUtilities.invokeLater(() -> appendLog("< " + receivedLine));
                }
                SwingUtilities.invokeLater(() -> appendLog("Server closed the connection."));
            } catch (IOException exception) {
                if (isConnected()) {
                    SwingUtilities.invokeLater(() -> appendLog("Connection lost: " + exception.getMessage()));
                }
            } finally {
                SwingUtilities.invokeLater(() -> setConnected(false));
                closeSocket();
            }
        }, "client-receiver");

        receiverThread.start();
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty() || !isConnected()) {
            return;
        }

        output.println(message);
        appendLog("> " + message);
        messageField.setText("");

        if (message.equalsIgnoreCase("QUIT")) {
            disconnect();
        }
    }

    private void disconnect() {
        if (!isConnected()) {
            setConnected(false);
            return;
        }

        if (output != null) {
            output.println("QUIT");
        }

        appendLog("Disconnecting...");
        setConnected(false);
        closeSocket();
    }

    private void requestUdpStatus() {
        String host = hostField.getText().trim();
        int udpPort = (Integer) udpPortSpinner.getValue();

        appendLog("Requesting UDP status from " + host + ":" + udpPort + "...");

        Thread statusThread = new Thread(() -> {
            try (DatagramSocket datagramSocket = new DatagramSocket()) {
                datagramSocket.setSoTimeout(UDP_TIMEOUT_MS);

                byte[] requestData = "STATUS".getBytes(StandardCharsets.UTF_8);
                InetAddress address = InetAddress.getByName(host);
                DatagramPacket request = new DatagramPacket(requestData, requestData.length, address, udpPort);
                datagramSocket.send(request);

                byte[] responseData = new byte[1024];
                DatagramPacket response = new DatagramPacket(responseData, responseData.length);
                datagramSocket.receive(response);

                String responseText = new String(
                        response.getData(),
                        0,
                        response.getLength(),
                        StandardCharsets.UTF_8
                );

                SwingUtilities.invokeLater(() -> appendLog("< UDP " + responseText));
            } catch (SocketTimeoutException exception) {
                SwingUtilities.invokeLater(() -> appendLog("UDP status timed out."));
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> appendLog("UDP status failed: " + exception.getMessage()));
            }
        }, "client-udp-status");

        statusThread.start();
    }

    private boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void setConnected(boolean connected) {
        hostField.setEnabled(!connected);
        tcpPortSpinner.setEnabled(!connected);
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        messageField.setEnabled(connected);
        sendButton.setEnabled(connected);
        statusButton.setEnabled(!connected);

        if (connected) {
            messageField.requestFocusInWindow();
        }
    }

    private void setControlsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        tcpPortSpinner.setEnabled(enabled);
        connectButton.setEnabled(enabled);
        statusButton.setEnabled(enabled);
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            input = null;
            output = null;
        }
    }

    private void appendLog(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
