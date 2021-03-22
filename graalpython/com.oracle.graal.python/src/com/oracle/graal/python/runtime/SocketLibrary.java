package com.oracle.graal.python.runtime;

import java.nio.ByteOrder;

import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class SocketLibrary extends Library {

    public abstract int socket(Object receiver, int domain, int type, int protocol) throws PosixException;

    public abstract void bind(Object receiver, int sockfd, Object addr) throws PosixException;

    public abstract void getsockname(Object receiver, int sockfd, Object addr) throws PosixException;

    public abstract int sendto(Object receiver, int sockfd, byte[] buf, int len, int flags, Object destAddr) throws PosixException;

    public abstract int recvfrom(Object receiver, int sockfd, byte[] buf, int len, int flags, Object srcAddr) throws PosixException;

    // sockaddr_in, used by AF_INET
    public abstract Object createSockaddrIn(Object receiver);

    // port is in network order
    public abstract short sockaddrInGetPort(Object receiver, Object sockaddrIn);

    // addr is in network order
    public abstract int sockaddrInGetAddr(Object receiver, Object sockaddrIn);

    // port must be in network order
    public abstract void sockaddrInSetPort(Object receiver, Object sockaddrIn, short port);

    // addr must be in network order
    public abstract void sockaddrInSetAddr(Object receiver, Object sockaddrIn, int addr);

    public static short htons(short v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return Short.reverseBytes(v);
        }
        return v;
    }

    public static short ntohs(short v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return Short.reverseBytes(v);
        }
        return v;
    }

    public static int htonl(int v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return Integer.reverseBytes(v);
        }
        return v;
    }

    public static int ntohl(int v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return Integer.reverseBytes(v);
        }
        return v;
    }

    static final LibraryFactory<SocketLibrary> FACTORY = LibraryFactory.resolve(SocketLibrary.class);

    public static LibraryFactory<SocketLibrary> getFactory() {
        return FACTORY;
    }

    public static SocketLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
