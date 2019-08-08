package util.net;

import java.net.Proxy;
import java.util.Objects;

public class ProxyType {
    private String data;

    public ProxyType(String type) {
        this.data = type.toLowerCase();
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data.toLowerCase();
    }

    Proxy.Type getAsProxyType() {
        switch (data.toLowerCase()) {
            default:
            case "ftp":
            case "http":
            case "https":
                return Proxy.Type.HTTP;
            case "":
            case "direct":
                return Proxy.Type.DIRECT;
            case "socks":
                return Proxy.Type.SOCKS;
        }
    }

    @Override
    public String toString() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyType proxyType = (ProxyType) o;
        return Objects.equals(data, proxyType.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }
}
