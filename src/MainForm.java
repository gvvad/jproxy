import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import util.net.ProxyTester;
import util.net.ProxyType;
import util.ActionListenerEx;

class CustomTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        ServersModel model = (ServersModel) table.getModel();
        Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        Color color = model.getEntry(table.convertRowIndexToModel(row)).getServerStatus().getStatusColor();
        if (isSelected) {
            color = color.darker();
        }
        component.setBackground(color);
        return component;
    }
}

public class MainForm {
    private JFrame mainFrame;
    private JPanel panelMain;
    private JButton addServerButton;
    private JButton removeServerButton;
    private JButton parseServersButton;
    private JTextField urlTextField;
    private JButton startButton;
    private JTable serversTable;
    private JSpinner spinnerAttempts;
    private JSpinner spinnerThreads;
    private JSpinner spinnerTimeout;
    private JMenuBar mainMenuBar;
    private ArrayList<Thread> taskThreads = new ArrayList<>();

    private SpinnerNumberModel spinnerThreadsModel = new SpinnerNumberModel(8, 1, 32, 1);
    private SpinnerNumberModel spinnerAttemptsModel = new SpinnerNumberModel(3, 1, 10, 1);
    private SpinnerNumberModel spinnerTimeoutModel = new SpinnerNumberModel(20, 1, 60, 1);

    private final ServersModel model = new ServersModel();

    /**
     * Show error message gialog
     * @param message
     */
    public void showError(String message) {
        JOptionPane.showMessageDialog(mainFrame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Show info message dialog
     * @param message
     */
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(mainFrame, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Show yes no question dialog
     * @param message
     * @return
     */
    public boolean showQuestion(String message) {
        int res = JOptionPane.showConfirmDialog(mainFrame, message, "Question", JOptionPane.YES_NO_OPTION);
        return res == JOptionPane.YES_OPTION;
    }

    /**
     * Import file action
     */
    private ActionListener actionFileImport = new ActionListenerEx(() -> {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                AtomicInteger count = new AtomicInteger();
                Files.lines(file.toPath()).forEach((line) -> {
                    try {
                        if (model.addEntry(ServersModel.parseEntry(line))) {
                            count.getAndIncrement();
                        }
                    } catch (DataFormatException ignored) {
                    }
                });
                showInfo(String.format("Servers imported: %d", count.get()));
            } catch (IOException e) {
                showError(e.getMessage());
            }
            model.fireTableDataChanged();
        }
    });

    /**
     * Export file action
     */
    private ActionListener actionFileExport = new ActionListenerEx(() -> {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file.exists()) {
                if (!showQuestion(String.format("File '%s' exist.\nRewrite file?", file.getName()))) {
                    return;
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
                for (ServerModelItem item : model) {
                    writer.write(String.format("%s\t%10s:%d\t%10s\n",
                            item.getType(),
                            item.getSocketAddress().getIp(),
                            item.getSocketAddress().getPort(),
                            item.getServerStatus()));
                }
            } catch (IOException ex) {
                showError(ex.getMessage());
            }
        }
    });

    /**
     * Start test tasks action
     */
    private ActionListener actionStartTest = new ActionListenerEx(() -> {
        synchronized (model) {
            for (ServerModelItem item : model) {
                if (item.getServerStatus().getStatusValue() != ServerStatus.Status.TESTING) {
                    item.getServerStatus().setStatusValue(ServerStatus.Status.QUEUED);
                }
            }
            model.fireTableDataChanged();
        }

        Runnable task = () -> {
            ServerModelItem item;
            while (true) {
                synchronized (model) {
                    item = model.getEntry(ServerStatus.Status.QUEUED);
                    if (item == null) break;
                    item.getServerStatus().setStatusValue(ServerStatus.Status.TESTING);
                }

                ProxyTester proxyTest = new ProxyTester(item.getSocketAddress(), item.getType());
                proxyTest.setTimeout((int) spinnerTimeoutModel.getValue() * 1000);
                for (int i = 1; i <= (int) spinnerAttemptsModel.getValue(); i++) {
                    try {
                        synchronized (model) {
                            item.getServerStatus().setAttempt(i);
                            item.getServerStatus().setStatusValue(ServerStatus.Status.TESTING);
                            model.fireTableDataChanged();
                        }
                        proxyTest.syncTest(urlTextField.getText());
                        item.getServerStatus().setOk(proxyTest.getPing());
                        i = (int) spinnerAttemptsModel.getValue() + 1;
                    } catch (ProxyTester.ProxyTestUrlException |
                            ProxyTester.ProxyTestWrongSettingsException |
                            ProxyTester.ProxyTestUnexpectedException e) {
                        item.getServerStatus().setFail(e.getMessage());
                        i = (int) spinnerAttemptsModel.getValue() + 1;
                    } catch (ProxyTester.ProxyTestTimeoutException |
                            ProxyTester.ProxyTestConnectException e) {
                        item.getServerStatus().setFail(e.getMessage());
                    }
                    synchronized (model) {
                        model.fireTableDataChanged();
                    }
                }
            }
        };

        for (int i = taskThreads.size(); i < (int) spinnerThreadsModel.getValue(); i++) {
            taskThreads.add(new Thread(task));
        }

        for (int i = 0; i < (int) spinnerThreadsModel.getValue(); i++) {
            if (taskThreads.get(i).isAlive()) {
                continue;
            }
            try {
                taskThreads.get(i).start();
            } catch (IllegalThreadStateException ignored) {
                taskThreads.set(i, new Thread(task));
                taskThreads.get(i).start();
            }
        }
    });

    /**
     * Abort test tasks action
     */
    private ActionListener actionAbortTest = new ActionListenerEx(() -> {
        synchronized (model) {
            for (ServerModelItem item : model) {
                ServerStatus serverStatus = item.getServerStatus();
                if (serverStatus.getStatusValue() == ServerStatus.Status.QUEUED) {
                    item.getServerStatus().setStatusValue(ServerStatus.Status.CANCELLED);
                }
            }
        }
        model.fireTableDataChanged();
    });

    /**
     * Add new server entry in model action
     */
    private ActionListener actionAddServer = new ActionListenerEx(() -> {
        model.addEntry(new ProxyType("http"), "0.0.0.0", 8080, new ServerStatus());
    });

    /**
     * Remove selected in table servers from model action
     */
    private ActionListener actionRemoveServer = new ActionListenerEx(() -> {
        int[] rows = serversTable.getSelectedRows();
        for (int i = 0; i < rows.length; i++) {
            rows[i] = serversTable.convertRowIndexToModel(rows[i]);
        }

        try {
            model.removeItemsSafely(rows);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException ex) {
            showError(ex.getMessage());
        }
        model.fireTableDataChanged();
    });

    /**
     * About dialog action
     */
    private ActionListener actionAbout = new ActionListenerEx(() -> {
        AboutDialog dlg = new AboutDialog(mainFrame);
        dlg.setVisible(true);
    });

    /**
     * Parse servers from text dialog action
     * txt - server description (type ip:port) per line
     */
    private ActionListener actionParseServers = new ActionListenerEx(() -> {
        TextDialog dialog = new TextDialog(mainFrame, (txt) -> {
            Scanner scanner = new Scanner(txt);
            int serversAddedCount = 0;
            int linesCount = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isEmpty()) {
                    continue;
                }
                linesCount++;
                try {
                    if (model.addEntry(ServersModel.parseEntry(line))) {
                        serversAddedCount++;
                    }
                } catch (DataFormatException ignored) {
                }
            }
            model.fireTableDataChanged();
            showInfo(String.format("Parsed lines: %d\nServers added: %d", linesCount, serversAddedCount));
        });
        dialog.setTitle("Parse servers");
        dialog.setVisible(true);
    });

    /**
     * Exit programm action
     */
    private ActionListener actionExit = new ActionListenerEx(() -> {
        System.exit(0);
    });

    MainForm() {
        mainFrame = new JFrame("jProxy");
        mainFrame.setContentPane(panelMain);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        mainMenuBar = new JMenuBar();
        JMenu menu;
        JMenuItem menuItem;

        menu = new JMenu("File");
        menuItem = new JMenuItem("Import");
        menuItem.addActionListener(actionFileImport);
        menu.add(menuItem);

        menuItem = new JMenuItem("Export");
        menuItem.addActionListener(actionFileExport);
        menu.add(menuItem);

        menuItem = new JMenuItem("Exit");
        menuItem.addActionListener(actionExit);
        menu.add(menuItem);
        mainMenuBar.add(menu);

        menu = new JMenu("Tools");
        menuItem = new JMenuItem("Parse servers");
        menuItem.addActionListener(actionParseServers);
        menu.add(menuItem);

        menuItem = new JMenuItem("Start test");
        menuItem.addActionListener(actionStartTest);
        menu.add(menuItem);

        menuItem = new JMenuItem("Abort test");
        menuItem.addActionListener(actionAbortTest);
        menu.add(menuItem);

        menuItem = new JMenuItem("Add server");
        menuItem.addActionListener(actionAddServer);
        menu.add(menuItem);

        menuItem = new JMenuItem("Remove server");
        menuItem.addActionListener(actionRemoveServer);
        menu.add(menuItem);
        mainMenuBar.add(menu);

        menu = new JMenu("Help");
        menuItem = new JMenuItem("About");
        menuItem.addActionListener(actionAbout);
        menu.add(menuItem);

        mainMenuBar.add(menu);

        mainFrame.setJMenuBar(mainMenuBar);

        serversTable.setModel(model);
        serversTable.setDefaultRenderer(Object.class, new CustomTableCellRenderer());
        serversTable.getColumnModel().getColumn(0).setMinWidth(45);
        serversTable.getColumnModel().getColumn(0).setMaxWidth(50);
        serversTable.getColumnModel().getColumn(0).setPreferredWidth(50);

        serversTable.getColumnModel().getColumn(1).setMinWidth(80);
        serversTable.getColumnModel().getColumn(1).setMaxWidth(280);
        serversTable.getColumnModel().getColumn(1).setPreferredWidth(100);

        serversTable.getColumnModel().getColumn(2).setMinWidth(55);
        serversTable.getColumnModel().getColumn(2).setMaxWidth(60);
        serversTable.getColumnModel().getColumn(2).setPreferredWidth(60);

        serversTable.setAutoCreateRowSorter(true);
        TableRowSorter tableRowSorter = new TableRowSorter(model);
        tableRowSorter.setComparator(3, new ServerStatus.Comparator());
        tableRowSorter.setComparator(2, new ServerModelItem.PortComparator());
        serversTable.setRowSorter(tableRowSorter);

        spinnerAttempts.setModel(spinnerAttemptsModel);
        spinnerThreads.setModel(spinnerThreadsModel);
        spinnerTimeout.setModel(spinnerTimeoutModel);

        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);

        addServerButton.addActionListener(actionAddServer);
        removeServerButton.addActionListener(actionRemoveServer);
        parseServersButton.addActionListener(actionParseServers);
        startButton.addActionListener(actionStartTest);

        mainFrame.setVisible(true);
    }
}
