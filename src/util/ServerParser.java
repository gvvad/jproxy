package util;

import util.net.SocketAddressEx;
import util.net.ProxyType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerParser {
    private static Pattern ipv4pattern = Pattern.compile(
            "((?:(?:(?:1\\d\\d|2[0-4]\\d|25[0-5])|[1-9]\\d|\\d)\\.){3}(?:(?:1\\d\\d|2[0-4]\\d|25[0-5])|[1-9]\\d|\\d))(?:\\W*|[:])?(\\d{1,5})?",
            Pattern.CASE_INSENSITIVE);

    public static ProxyType extractType(String line) {
        Pattern patType = Pattern.compile("((https?)|(socks)|(ftp))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = patType.matcher(line);

        if (matcher.find()) {
            return new ProxyType(matcher.group(0));
        }
        return null;
    }

    public static SocketAddressEx extractAddress(String line) {
        Matcher matcher = ipv4pattern.matcher(line);

        SocketAddressEx res = new SocketAddressEx();
        if (matcher.find()) {
            res.setIp(matcher.group(1));
            try {
                res.setPort(Integer.parseInt(matcher.group(2)));
            } catch (NumberFormatException ignore) {
            }
        }
        return res;
    }
}
