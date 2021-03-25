package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;

import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class SocketLibrary extends Library {

    // region Socket addresses

    /**
     * Represents an address of a socket.
     *
     * Addresses are either specific to a particular socket family (subclasses of
     * {@link FamilySpecificSockAddr}) or universal ({@link UniversalSockAddr}). It is possible to
     * convert any family-specific address to a universal address. The conversion in opposite
     * direction is possible only if the target family-specific type matches the socket family of
     * the address stored in the source {@link UniversalSockAddr} instance.
     */
    public interface SockAddr {
    }

    /**
     * Base class for addresses specific to a particular socket family.
     *
     * The subclasses are simple POJOs whose definitions are common to all backends. A
     * family-specific address is convenient to use (compared to {@link UniversalSockAddr}), but the
     * backend needs to convert it to its internal representation every time it is used. The use of
     * family-specific addresses is appropriate when the socket family is known and when the address
     * is used just a couple of times. This class corresponds to POSIX {@code struct sockaddr}.
     */
    public static abstract class FamilySpecificSockAddr implements SockAddr {
        private final int family;

        protected FamilySpecificSockAddr(int family) {
            this.family = family;
        }

        public int getFamily() {
            return family;
        }
    }

    /**
     * A tagged union of all address types which is capable of holding an address of any socket
     * family.
     *
     * An universal socket address keeps the value in a representation used internally by the given
     * backend, therefore implementations of this interface are backend-specific (unlike
     * {@link FamilySpecificSockAddr} subclasses). This makes them suitable in situations where the
     * address needs to be used more than once or when the socket family is not known. For example,
     * a UDP server that responds to an incoming packet by sending multiple packets might want to
     * use universal address (and does not even need to know whether it is using IPv4 or IPv6). The
     * disadvantage of {@link UniversalSockAddr} is that it needs to be explicitly deallocated since
     * it is stored in the native heap (in the NFI backend). This interface corresponds to POSIX
     * {@code struct sockaddr_storage}.
     *
     * @see UniversalSockAddrLibrary
     */
    public interface UniversalSockAddr extends SockAddr {
    }

    /**
     * Represents an address for IPv4 sockets (the {@link PosixConstants#AF_INET} socket family).
     *
     * This is a higher level equivalent of POSIX {@code struct sockaddr_in} - the values are kept
     * in host byte order, conversion to network order ({@code htons/htonl}) is done automatically
     * by the backend.
     */
    @ValueType
    public static final class Inet4SockAddr extends FamilySpecificSockAddr {
        private int port;           // host order, 0 - 65535
        private int address;        // host order, e.g. INADDR_LOOPBACK

        public Inet4SockAddr(int port, int address) {
            super(AF_INET.value);
            assert port >= 0 && port <= 65535;
            this.port = port;
            this.address = address;
        }

        public Inet4SockAddr() {
            super(AF_INET.value);
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

    // endregion

    protected SocketLibrary() {
    }

    // region socket messages

    /**
     * Creates a new socket.
     *
     * @see "socket(2) man pages"
     * @see PosixConstants
     */
    public abstract int socket(Object receiver, int domain, int type, int protocol) throws PosixException;

    // addr is an input parameter
    public abstract void bind(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // addr is an output parameter
    public abstract void getsockname(Object receiver, int sockfd, SockAddr addr) throws PosixException;

    // Unlike POSIX sendto(), we don't support destAddr == null. Use plain send instead.
    // destAddr is an input parameter
    public abstract int sendto(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr destAddr) throws PosixException;

    // Unlike POSIX recvfrom(), we don't support srcAddr == null. Use plain recv instead.
    // srcAddr is an output parameter
    // throws IllegalArgumentException if the type of srcAddr does not match the actual socket
    // family of the packet's source address, in which case the state of the socket and buf is
    // unspecified.
    public abstract int recvfrom(Object receiver, int sockfd, byte[] buf, int len, int flags, SockAddr srcAddr) throws PosixException;

    // endregion

    /**
     * Allocates a new {@link UniversalSockAddr} and sets its family to
     * {@link PosixConstants#AF_UNSPEC}. It can be either filled by
     * {@link UniversalSockAddrLibrary#fill(UniversalSockAddr, SockAddr)} or used in a call that
     * returns an address, such as {@link #getsockname(Object, int, SockAddr)} or
     * {@link #recvfrom(Object, int, byte[], int, int, SockAddr)}. The returned object must be
     * explicitly deallocated exactly once using the
     * {@link UniversalSockAddrLibrary#release(UniversalSockAddr)} message.
     */
    public abstract UniversalSockAddr allocUniversalSockAddr(Object receiver);

    /**
     * Provides messages for manipulating {@link UniversalSockAddr}.
     */
    @GenerateLibrary
    public static abstract class UniversalSockAddrLibrary extends Library {

        /**
         * Releases resources associated with the address.
         *
         * This must be called exactly once on all instances returned from
         * {@link #allocUniversalSockAddr(Object)}. Released instances can no longer be used for any
         * purpose.
         */
        public abstract void release(UniversalSockAddr receiver);

        /**
         * Returns the socket family of the address (one of the {@code AF_xxx} values defined in
         * {@link PosixConstants}).
         */
        public abstract int getFamily(UniversalSockAddr receiver);

        /**
         * Fills the receiver with the backend-specific representation of the {@code src} address.
         * Note that {@code src} can itself be an instance of {@link UniversalSockAddr} (provided by
         * the same backend), in which case a direct copy is made.
         */
        public abstract void fill(UniversalSockAddr receiver, SockAddr src);

        /**
         * Converts the address represented by the receiver (which must be of the
         * {@link PosixConstants#AF_INET} family) into a {@link Inet4SockAddr} instance.
         *
         * @throws IllegalArgumentException if the socket family of the address is not
         *             {@link PosixConstants#AF_INET}
         */
        public abstract Inet4SockAddr asInet4SockAddr(UniversalSockAddr receiver);

        static final LibraryFactory<UniversalSockAddrLibrary> FACTORY = LibraryFactory.resolve(UniversalSockAddrLibrary.class);

        public static LibraryFactory<UniversalSockAddrLibrary> getFactory() {
            return FACTORY;
        }

        public static UniversalSockAddrLibrary getUncached() {
            return FACTORY.getUncached();
        }
    }

    static final LibraryFactory<SocketLibrary> FACTORY = LibraryFactory.resolve(SocketLibrary.class);

    public static LibraryFactory<SocketLibrary> getFactory() {
        return FACTORY;
    }

    public static SocketLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
