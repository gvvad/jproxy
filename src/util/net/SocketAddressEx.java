package util.net;

import java.net.SocketAddress;
import java.util.Objects;

public class SocketAddressEx extends SocketAddress {
    private String ip;
    private int port;

    public SocketAddressEx() {
        this.ip = "";
        this.port = 0;
    }

    public SocketAddressEx(String ip, int port) {
        this.ip = ip.toLowerCase();
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip.toLowerCase();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SocketAddressEx that = (SocketAddressEx) o;
        return port == that.port &&
                Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
