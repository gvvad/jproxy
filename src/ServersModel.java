import util.net.SocketAddressEx;
import util.net.ProxyType;
import util.ServerParser;

import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;

/**
 * Stored server status: {new, ok, fail, testing, queued, cancelled}
 * ping - connect time to server, showing in string replication
 * emsg - error message showing in string with fail status
 * attempt - connect attempt
 */
class ServerStatus {
    private Status status;
    private int ping = 0;
    private String emsg;
    private int attempt = 0;

    static class Comparator implements java.util.Comparator<ServerStatus> {
        @Override
        public int compare(ServerStatus o1, ServerStatus o2) {
            int statusDelta = o1.getStatusValue().ordinal() - o2.getStatusValue().ordinal();
            if (statusDelta == 0) {
                return o1.getPing() - o2.getPing();
            }
            return statusDelta;
        }
    }

    enum Status {
        NEW,
        OK,
        FAIL,
        TESTING,
        QUEUED,
        CANCELLED
    }

    /**
     * Used for highlighting table rows
     * @param status - Server status
     * @return
     */
    public static Color statusToColor(Status status) {
        switch (status) {
            case OK:
                return new Color(152, 251, 152);
            case FAIL:
                return Color.PINK;
            case TESTING:
                return new Color(230, 230, 250);
            case QUEUED:
                return new Color(255, 250, 205);
            case CANCELLED:
                return new Color(255, 228, 196);
            default:
                return Color.WHITE;
        }
    }

    public void setErrorMessage(String msg) {
        this.emsg = msg;
    }

    public ServerStatus(Status status) {
        this.status = status;
    }

    public ServerStatus() {
        this.status = Status.NEW;
    }

    public void setStatusValue(Status status) {
        this.status = status;
    }

    public void setOk(int ping) {
        setPing(ping);
        setStatusValue(Status.OK);
    }

    public void setFail(String emsg) {
        this.emsg = emsg;
        setStatusValue(Status.FAIL);
    }

    public Status getStatusValue() {
        return status;
    }

    public Color getStatusColor() {
        return ServerStatus.statusToColor(this.status);
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    public int getPing() {
        return ping;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    private String _stringifyAttempt() {
        if (attempt > 1) {
            return String.format("# %d: ", attempt);
        }
        return "";
    }

    @Override
    public String toString() {
        switch (status) {
            case OK:
                return String.format("%sOK %.2f sec", _stringifyAttempt(), (float) ping / 1000);
            case NEW:
                return "New";
            case FAIL:
                return _stringifyAttempt() + "Test fail." + ((emsg != null) ? " " + emsg : "");
            case TESTING:
                return _stringifyAttempt() + "Testing...";
            case QUEUED:
                return "Queued";
            case CANCELLED:
                return "Cancelled";
            default:
                return "Unknown";
        }
    }
}

/**
 * Stored server connection data {proxy type, ip address and port, server status}
 */
class ServerModelItem extends Vector<Object> {
    private SocketAddressEx address;

    static class IpAddressUtil implements Supplier<SocketAddressEx> {
        SocketAddressEx data;

        IpAddressUtil(SocketAddressEx data) {
            this.data = data;
        }

        @Override
        public SocketAddressEx get() {
            return data;
        }

        @Override
        public String toString() {
            return data.getIp();
        }
    }

    static class PortUtil implements Supplier<SocketAddressEx> {
        SocketAddressEx data;

        PortUtil(SocketAddressEx data) {
            this.data = data;
        }

        @Override
        public SocketAddressEx get() {
            return data;
        }

        @Override
        public String toString() {
            return "" + data.getPort();
        }
    }

    public static class PortComparator implements java.util.Comparator<PortUtil> {
        @Override
        public int compare(PortUtil o1, PortUtil o2) {
            return o1.data.getPort() - o2.data.getPort();
        }
    }

    ServerModelItem(ProxyType type, String ip, int port, ServerStatus serverStatus) {
        super();
        address = new SocketAddressEx(ip, port);
        add(type);
        add(new IpAddressUtil(address));
        add(new PortUtil(address));
        add(serverStatus);
    }


    public ProxyType getType() {
        return (ProxyType) get(0);
    }

    public SocketAddressEx getSocketAddress() {
        return address;
    }

    public ServerStatus getServerStatus() {
        Object obj = this.get(3);
        if (obj instanceof ServerStatus) {
            return (ServerStatus) obj;
        }
        return null;
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerModelItem that = (ServerModelItem) o;
        return this.getType().equals(that.getType()) &&
                this.getSocketAddress().equals(that.getSocketAddress());
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hash(getType(), getSocketAddress());
    }
}

public class ServersModel extends DefaultTableModel implements Iterable<ServerModelItem> {

    private Vector<ServerModelItem> data = new Vector<>();

    @Override
    public void setValueAt(Object aValue, int row, int column) {
        switch (column) {
            case 0:
                data.get(row).getType().setData(aValue.toString());
                break;
            case 1:
                SocketAddressEx address = ServerParser.extractAddress((String) aValue);
                if (!address.getIp().isEmpty()) {
                    data.get(row).getSocketAddress().setIp(address.getIp());
                }
                break;
            case 2:
                try {
                    data.get(row).getSocketAddress().setPort(Integer.parseInt((String) aValue));
                } catch (NumberFormatException ignored) {
                }
                break;
            default:
                super.setValueAt(aValue, row, column);
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column != 3;
    }

    @Override
    public Iterator<ServerModelItem> iterator() {
        return data.iterator();
    }

    ServersModel() {
        super();
        Vector<Object> columNames = new Vector<>(Arrays.asList("Type", "Ip", "Port", "Status"));
        setDataVector(data, columNames);
    }

    boolean addEntry(ServerModelItem item) {
        if (item != null && data.indexOf(item) == -1) {
            addRow(item);
            return true;
        }
        return false;
    }

    boolean addEntry(ProxyType type, String ip, int port, ServerStatus serverStatus) {
        ServerModelItem item = new ServerModelItem(type, ip, port, serverStatus);
        return addEntry(item);
    }

    static ServerModelItem parseEntry(String line) throws DataFormatException {
        ProxyType type = ServerParser.extractType(line);
        SocketAddressEx address = ServerParser.extractAddress(line);
        if (type != null) {
            return new ServerModelItem(type, address.getIp(), address.getPort(), new ServerStatus());
        }
        throw new DataFormatException();
    }

    ServerModelItem getEntry(int index) {
        return data.get(index);
    }

    ServerModelItem getEntry(ServerStatus.Status statusValue) {
        for (ServerModelItem item : data) {
            if (item.getServerStatus().getStatusValue() == statusValue) {
                return item;
            }
        }
        return null;
    }

    void removeItemsSafely(int[] items) {
        Arrays.sort(items);

        for (int i = items.length - 1; i >= 0; i--) {
            if (data.get(items[i]).getServerStatus().getStatusValue() != ServerStatus.Status.TESTING) {
                data.remove(items[i]);
            }
        }
    }
}
