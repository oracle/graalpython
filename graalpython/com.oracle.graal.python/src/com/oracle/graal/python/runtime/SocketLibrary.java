package com.oracle.graal.python.runtime;

import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class SocketLibrary extends Library {

    /**
     * There are two kinds of socket addresses - all subclasses except {@link SockAddrStorage} are
     * represented by POJOs (shared by all backends) and each corresponds to a particular socket
     * family. {@link SockAddrStorage} can represent an address of any socket family. It is also
     * already encoded for immediate use by the given backend, making it suitable for repeated use
     * or when the socket family is not known. For example, a UDP server that responds to an
     * incoming packet by sending multiple packets might want to use it and does not even need to
     * know whether it is using IPv4 or IPv6. The disadvantage of {@link SockAddrStorage} is that it
     * needs to be explicitly deallocated since it is stored in the native heap (in the NFI
     * backend). Using the concrete addresses is preferred when the socket family is known and the
     * address is used just once, since the native allocation can be avoided.
     */
    public interface SockAddr {
    }

    @ValueType
    public static final class SockAddrIn implements SockAddr {
        private int port;           // host order, 0 - 65535
        private int address;        // host order, e.g. INADDR_LOOPBACK

        public SockAddrIn(int port, int address) {
            assert port >= 0 && port <= 65535;
            this.port = port;
            this.address = address;
        }

        public SockAddrIn() {
        }

        public int getPort() {
            return port;
        }

        public int getAddress() {
            return address;
        }

        public void setPort(int port) {
            assert port >= 0 && port <= 65535;
            this.port = port;
        }

        public void setAddress(int address) {
            this.address = address;
        }
    }

    public interface SockAddrStorage extends SockAddr {
    }

    public abstract int socket(Object receiver, int domain, int type, int protocol) throws PosixException;

    // addr is an input parameter
    public abstract void bind(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // addr is an output parameter
    public abstract void getsockname(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // Unlike POSIX sendto(), we don't support destAddr == null. Use plain send instead.
    // destAddr is an input parameter
    public abstract int sendto(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr destAddr) throws PosixException;

    // Unlike POSIX recvfrom(), we don't support srcAddrStorage == null. Use plain recv instead.
    // srcAddr is an input parameter
    public abstract int recvfrom(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr srcAddr) throws PosixException;

    /**
     * Allocates a new {@link SockAddrStorage} and sets its family to
     * {@link PosixConstants#AF_UNSPEC}. It can be either filled by
     * {@link #fillSockAddrStorage(Object, SockAddrStorage, SockAddr)} or used in a call that
     * returns an address, such as {@link #getsockname(Object, int, SockAddr)} or
     * {@link #recvfrom(Object, int, byte[], int, int, SockAddr)}. The returned object must be
     * explicitly deallocated exactly once by using
     * {@link #freeSockAddrStorage(Object, SockAddrStorage)}.
     */
    public abstract SockAddrStorage allocSockAddrStorage(Object receiver);

    // TODO move the following messages to a TruffleLibrary dedicated to SockAddrStorage

    public abstract void freeSockAddrStorage(Object receiver, SockAddrStorage sockAddrStorage);

    /**
     * Returns the socket family of the address.
     */
    public abstract int getSockAddrStorageFamily(Object receiver, SockAddrStorage sockAddrStorage);

    /**
     * Fills {@code sockAddrStorage} by the address represented by {@code src}. Note that
     * {@code src} can itself be an instance of {@link SockAddrStorage}, in which case a direct copy
     * is made.
     */
    public abstract void fillSockAddrStorage(Object receiver, SockAddrStorage sockAddrStorage, SockAddr src);

    /**
     * Converts a {@link SockAddrStorage} into a {@link SockAddrIn} instance. This is only possible
     * if the socket family of the address is {@link PosixConstants#AF_INET} (otherwise an assertion
     * is raised and garbage returned).
     * TODO are assertions the right way to report contract breaches?
     */
    public abstract SockAddrIn sockAddrStorageAsSockAddrIn(Object receiver, SockAddrStorage sockAddrStorage);

    static final LibraryFactory<SocketLibrary> FACTORY = LibraryFactory.resolve(SocketLibrary.class);

    public static LibraryFactory<SocketLibrary> getFactory() {
        return FACTORY;
    }

    public static SocketLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
