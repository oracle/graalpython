package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public class ALPNHelper {
    // The ALPN API is only available from JDK 8u252, use reflection to detect its presence
    private static final Method setApplicationProtocols;
    private static final Method getApplicationProtocol;

    static {
        Method setApplicationProtocolsMethod;
        Method getApplicationProtocolMethod;
        try {
            setApplicationProtocolsMethod = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            getApplicationProtocolMethod = SSLEngine.class.getMethod("getApplicationProtocol");
        } catch (NoSuchMethodException e) {
            setApplicationProtocolsMethod = null;
            getApplicationProtocolMethod = null;
        }
        setApplicationProtocols = setApplicationProtocolsMethod;
        getApplicationProtocol = getApplicationProtocolMethod;
    }

    public static boolean hasAlpn() {
        return setApplicationProtocols != null;
    }

    @TruffleBoundary
    public static void setApplicationProtocols(SSLParameters parameters, String[] protocols) {
        try {
            setApplicationProtocols.invoke(parameters, (Object) protocols);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    @TruffleBoundary
    public static String getApplicationProtocol(SSLEngine engine) {
        try {
            return (String) getApplicationProtocol.invoke(engine);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }
}
