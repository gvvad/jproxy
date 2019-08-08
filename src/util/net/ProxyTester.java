package util.net;

import java.io.IOException;
import java.net.*;

public class ProxyTester {
    private final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/76.0.3809.87 Safari/537.36";

    private String ip;
    private int port;
    private Proxy.Type type;
    private String userAgent = DEFAULT_USER_AGENT;
    private String requestMethod = "GET";
    private int timeout = 25 * 1000;
    private int readTimeout = 25 * 1000;
    private int pingTime;
    private boolean testResult = false;

    public static class ProxyTestTimeoutException extends Exception {
        ProxyTestTimeoutException(String message) {
            super(message);
        }
    }

    public static class ProxyTestUrlException extends Exception {
        ProxyTestUrlException(String message) {
            super(message);
        }
    }

    public static class ProxyTestUnexpectedException extends Exception {
        ProxyTestUnexpectedException(String message) {
            super(message);
        }
    }

    public static class ProxyTestConnectException extends Exception {
        ProxyTestConnectException(String message) {
            super(message);
        }
    }

    public static class ProxyTestWrongSettingsException extends Exception {
        ProxyTestWrongSettingsException(String message) {
            super(message);
        }
    }

    public ProxyTester(SocketAddressEx address, ProxyType type) {
        this.ip = address.getIp();
        this.port = address.getPort();
        this.type = type.getAsProxyType();
    }

    public ProxyTester(String ip, int port, ProxyType type) {
        this.ip = ip;
        this.port = port;
        this.type = type.getAsProxyType();
    }

    public ProxyTester(String ip, int port, Proxy.Type type) {
        this.ip = ip;
        this.port = port;
        this.type = type;
    }

    public void syncTest(String url) throws ProxyTestUrlException, ProxyTestTimeoutException, ProxyTestConnectException, ProxyTestWrongSettingsException, ProxyTestUnexpectedException {
        try {
            this.testResult = false;
            URL _url = new URL(url);
            Proxy webProxy = new Proxy(this.type, new InetSocketAddress(this.ip, this.port));

            HttpURLConnection connection = (HttpURLConnection) _url.openConnection(webProxy);
            connection.setConnectTimeout(this.timeout);
            connection.setReadTimeout(this.readTimeout);
            connection.setRequestMethod(this.requestMethod);
            connection.setRequestProperty("User-agent", this.userAgent);

            long start = System.nanoTime();
            connection.connect();
            this.pingTime = (int) ((System.nanoTime() - start) / 1e6);

            if (connection.getResponseCode() != 200) {
                throw new ProxyTestConnectException("Response code:" + connection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            throw new ProxyTestUrlException(e.getMessage());
        } catch (SocketTimeoutException e) {
            throw new ProxyTestTimeoutException(e.getMessage());
        } catch (IOException e) {
            throw new ProxyTestConnectException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ProxyTestWrongSettingsException(e.getMessage());
        } catch (RuntimeException e) {
            throw new ProxyTestUnexpectedException(e.getMessage());
        }
        this.testResult = true;
    }

    public boolean isPass() {
        return this.testResult;
    }

    public int getPing() {
        return pingTime;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        this.readTimeout = timeout;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setType(Proxy.Type type) {
        this.type = type;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Proxy.Type getType() {
        return type;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }
}
