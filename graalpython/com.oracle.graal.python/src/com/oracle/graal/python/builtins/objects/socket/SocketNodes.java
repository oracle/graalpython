package com.oracle.graal.python.builtins.objects.socket;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SocketGAIError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.AI_PASSIVE;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_BROADCAST;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.FamilySpecificSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

public abstract class SocketNodes {
    /**
     * Equivalent of CPython's {@code getsockaddrarg}.
     */
    public static abstract class GetSockAddrArgNode extends PNodeWithRaise {
        public abstract UniversalSockAddr execute(VirtualFrame frame, Object posixSupport, PSocket socket, Object address, String caller);

        @Specialization
        UniversalSockAddr getSockAddr(VirtualFrame frame, Object posixSupport, PSocket socket, Object address, String caller,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode) {
            try {
                if (socket.getFamily() == AF_INET.value) {
                    if (!(address instanceof PTuple)) {
                        throw raise(TypeError, "%s(): AF_INET address must be tuple, not %s", caller, address);
                    }
                    Object[] hostAndPort = getObjectArrayNode.execute(address);
                    if (hostAndPort.length != 2) {
                        throw raise(TypeError, "AF_INET address must be a pair (host, port)");
                    }
                    // TODO convert IDNA
                    String host = (String) hostAndPort[0];
                    int port;
                    try {
                        port = asIntNode.execute(frame, hostAndPort[1]);
                    } catch (PException e) {
                        e.expect(OverflowError, errorProfile);
                        port = -1;
                    }
                    if (port < 0 || port > 0xffff) {
                        throw raise(OverflowError, "%s(): port must be 0-65535.", caller);
                    }
                    return setipaddr(this, posixSupport, host, port, AF_INET.value);
                    // TODO AF_INET6
                } else {
                    throw raise(OSError, "%s(): bad family", caller);
                }
            } catch (GetAddrInfoException e) {
                throw constructAndRaiseNode.executeWithArgsOnly(frame, SocketGAIError, new Object[]{e.getErrorCode(), e.getMessage()});
            }
        }
    }

    // equivalent of CPython's socketmodule.c:setipaddr
    @TruffleBoundary
    @SuppressWarnings("try")
    private static UniversalSockAddr setipaddr(Node node, Object posixSupport, String name, int port, int family) throws GetAddrInfoException {
        PosixSupportLibrary posixLib = PosixSupportLibrary.getFactory().getUncached(posixSupport);
        if (name.isEmpty()) {
            try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                // TODO getaddrinfo lock?
                AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, null, posixLib.createPathFromString(posixSupport, "0"),
                                family, SOCK_DGRAM.value, 0, AI_PASSIVE.value);
                AddrInfoCursorLibrary addrInfoLib = AddrInfoCursorLibrary.getFactory().getUncached(cursor);
                try {
                    if (addrInfoLib.next(cursor)) {
                        throw PRaiseNode.raiseUncached(node, OSError, "wildcard resolved to multiple address");
                    }
                    UniversalSockAddr addr = addrInfoLib.getSockAddr(cursor);
                    return createAddrWithPort(node, posixLib, posixSupport, port, addr);
                } finally {
                    addrInfoLib.release(cursor);
                }
            }
        }
        /* special-case broadcast - inet_addr() below can return INADDR_NONE for this */
        if (name.equals("255.255.255.255") || name.equals("<broadcast>")) {
            if (family != AF_INET.value && family != AF_UNSPEC.value) {
                throw PRaiseNode.raiseUncached(node, OSError, "address family mismatched");
            }
            return posixLib.createUniversalSockAddr(posixSupport, new Inet4SockAddr(INADDR_BROADCAST.value, port));
        }
        /* avoid a name resolution in case of numeric address */
        /* check for an IPv4 address */
        if (family == AF_INET.value || family == AF_UNSPEC.value) {
            try {
                byte[] bytes = posixLib.inet_pton(posixSupport, AF_INET.value, posixLib.createPathFromString(posixSupport, name));
                return posixLib.createUniversalSockAddr(posixSupport, new Inet4SockAddr(port, bytes));
            } catch (PosixException | InvalidAddressException e) {
                // fallthrough
            }
        }
        /*
         * check for an IPv6 address - if the address contains a scope ID, we fallback to
         * getaddrinfo(), which can handle translation from interface name to interface index
         */
        if ((family == AF_INET6.value || family == AF_UNSPEC.value) && !name.contains("%")) {
            try {
                byte[] bytes = posixLib.inet_pton(posixSupport, AF_INET6.value, posixLib.createPathFromString(posixSupport, name));
                return posixLib.createUniversalSockAddr(posixSupport, new Inet6SockAddr(port, bytes, 0, 0));
            } catch (PosixException | InvalidAddressException e) {
                // fallthrough
            }
        }
        /* perform a name resolution */
        try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
            // TODO getaddrinfo lock?
            AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, posixLib.createPathFromString(posixSupport, name), null,
                            family, 0, 0, 0);
            AddrInfoCursorLibrary addrInfoLib = AddrInfoCursorLibrary.getFactory().getUncached(cursor);
            try {
                UniversalSockAddr addr = addrInfoLib.getSockAddr(cursor);
                return createAddrWithPort(node, posixLib, posixSupport, port, addr);
            } finally {
                addrInfoLib.release(cursor);
            }
        }
    }

    private static UniversalSockAddr createAddrWithPort(Node node, PosixSupportLibrary posixLib, Object posixSupport, int port, UniversalSockAddr addr) {
        UniversalSockAddrLibrary sockAddrLib = UniversalSockAddrLibrary.getFactory().getUncached(addr);
        int addrFamily = sockAddrLib.getFamily(addr);
        FamilySpecificSockAddr familyAddress;
        if (addrFamily == AF_INET.value) {
            Inet4SockAddr inet4SockAddr = sockAddrLib.asInet4SockAddr(addr);
            familyAddress = new Inet4SockAddr(port, inet4SockAddr.getAddress());
        } else if (addrFamily == AF_INET6.value) {
            Inet6SockAddr inet6SockAddr = sockAddrLib.asInet6SockAddr(addr);
            familyAddress = new Inet6SockAddr(port, inet6SockAddr.getAddress(), 0, 0);
        } else {
            throw PRaiseNode.raiseUncached(node, OSError, "unsupported address family");
        }
        return posixLib.createUniversalSockAddr(posixSupport, familyAddress);
    }

    /**
     * Equivalent of CPython's {@code makesockaddr}
     */
    public abstract static class MakeSockAddrNode extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, Object posixSupport, UniversalSockAddr addr);

        @Specialization(limit = "1")
        Object makeSockAddr(VirtualFrame frame, Object posixSupport, UniversalSockAddr addr,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @CachedLibrary("addr") UniversalSockAddrLibrary addrLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode) {
            try {
                int family = addrLib.getFamily(addr);
                if (family == AF_INET.value) {
                    Inet4SockAddr inet4SockAddr = addrLib.asInet4SockAddr(addr);
                    String addressString = posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet4SockAddr.getAddressAsBytes()));
                    return factory.createTuple(new Object[]{addressString, inet4SockAddr.getPort()});
                } else if (family == AF_INET6.value) {
                    Inet6SockAddr inet6SockAddr = addrLib.asInet6SockAddr(addr);
                    String addressString = posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet6SockAddr.getAddress()));
                    return factory.createTuple(new Object[]{addressString, inet6SockAddr.getPort(), inet6SockAddr.getFlowInfo(), inet6SockAddr.getScopeId()});
                } else {
                    throw raise(NotImplementedError, "makesockaddr: unknown address family");
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.raiseOSError(frame, e.getErrorCode(), e.getMessage(), null, null);
            }
        }
    }
}
