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

import java.net.IDN;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

public abstract class SocketNodes {
    /**
     * Equivalent of CPython's {@code socketmodule.c:getsockaddrarg}.
     */
    public static abstract class GetSockAddrArgNode extends PNodeWithRaise {
        public abstract UniversalSockAddr execute(VirtualFrame frame, PSocket socket, Object address, String caller);

        @Specialization
        UniversalSockAddr getSockAddr(VirtualFrame frame, PSocket socket, Object address, String caller,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("context.getPosixSupport()") Object posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary sockAddrLib,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached SetIpAddrNode setIpAddrNode) {
            if (socket.getFamily() == AF_INET.value) {
                if (!(address instanceof PTuple)) {
                    throw raise(TypeError, "%s(): AF_INET address must be tuple, not %s", caller, address);
                }
                Object[] hostAndPort = getObjectArrayNode.execute(address);
                if (hostAndPort.length != 2) {
                    throw raise(TypeError, "AF_INET address must be a pair (host, port)");
                }
                String host = idnaConverter.execute(frame, hostAndPort[0]);
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
                UniversalSockAddr addr = setIpAddrNode.execute(frame, host, AF_INET.value);
                return posixLib.createUniversalSockAddr(posixSupport, new Inet4SockAddr(port, sockAddrLib.asInet4SockAddr(addr).getAddress()));
            } else if (socket.getFamily() == AF_INET6.value) {
                if (!(address instanceof PTuple)) {
                    throw raise(TypeError, "%s(): AF_INET6 address must be tuple, not %s", caller, address);
                }
                Object[] hostAndPort = getObjectArrayNode.execute(address);
                if (hostAndPort.length < 2 || hostAndPort.length > 4) {
                    throw raise(TypeError, "AF_INET6 address must be a tuple (host, port[, flowinfo[, scopeid]])");
                }
                String host = idnaConverter.execute(frame, hostAndPort[0]);
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
                int flowinfo = 0;
                if (hostAndPort.length > 2) {
                    flowinfo = asIntNode.execute(frame, hostAndPort[2]);
                    if (flowinfo > 0xfffff) {
                        throw raise(OverflowError, "%s(): flowinfo must be 0-1048575.");
                    }
                }
                int scopeid = 0;
                if (hostAndPort.length > 3) {
                    scopeid = asIntNode.execute(frame, hostAndPort[3]);
                }
                UniversalSockAddr addr = setIpAddrNode.execute(frame, host, AF_INET6.value);
                return posixLib.createUniversalSockAddr(posixSupport, new Inet6SockAddr(port, sockAddrLib.asInet6SockAddr(addr).getAddress(), flowinfo, scopeid));
            } else {
                throw raise(OSError, "%s(): bad family", caller);
            }
        }
    }

    /**
     * Equivalent of CPython's {@code socketmodule.c:setipaddr}.
     */
    public static abstract class SetIpAddrNode extends PNodeWithRaise {
        public abstract UniversalSockAddr execute(VirtualFrame frame, String name, int family);

        @Specialization
        UniversalSockAddr setipaddr(VirtualFrame frame, String name, int family,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("context.getPosixSupport()") Object posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoLib,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode,
                        @Cached GilNode gil) {
            try {
                if (name.isEmpty()) {
                    gil.release(true);
                    try {
                        // TODO getaddrinfo lock?
                        AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, null, posixLib.createPathFromString(posixSupport, "0"),
                                        family, SOCK_DGRAM.value, 0, AI_PASSIVE.value);
                        try {
                            if (addrInfoLib.next(cursor)) {
                                throw raise(OSError, "wildcard resolved to multiple address");
                            }
                            return addrInfoLib.getSockAddr(cursor);
                        } finally {
                            addrInfoLib.release(cursor);
                        }
                    } finally {
                        gil.acquire();
                    }
                }
                /* special-case broadcast - inet_addr() below can return INADDR_NONE for this */
                if (name.equals("255.255.255.255") || name.equals("<broadcast>")) {
                    if (family != AF_INET.value && family != AF_UNSPEC.value) {
                        throw raise(OSError, "address family mismatched");
                    }
                    return posixLib.createUniversalSockAddr(posixSupport, new Inet4SockAddr(0, INADDR_BROADCAST.value));
                }
                /* avoid a name resolution in case of numeric address */
                /* check for an IPv4 address */
                if (family == AF_INET.value || family == AF_UNSPEC.value) {
                    try {
                        byte[] bytes = posixLib.inet_pton(posixSupport, AF_INET.value, posixLib.createPathFromString(posixSupport, name));
                        return posixLib.createUniversalSockAddr(posixSupport, new Inet4SockAddr(0, bytes));
                    } catch (PosixException | InvalidAddressException e) {
                        // fallthrough
                    }
                }
                /*
                 * check for an IPv6 address - if the address contains a scope ID, we fallback to
                 * getaddrinfo(), which can handle translation from interface name to interface
                 * index
                 */
                if ((family == AF_INET6.value || family == AF_UNSPEC.value) && !hasScopeId(name)) {
                    try {
                        byte[] bytes = posixLib.inet_pton(posixSupport, AF_INET6.value, posixLib.createPathFromString(posixSupport, name));
                        return posixLib.createUniversalSockAddr(posixSupport, new Inet6SockAddr(0, bytes, 0, 0));
                    } catch (PosixException | InvalidAddressException e) {
                        // fallthrough
                    }
                }
                /* perform a name resolution */
                gil.release(true);
                try {
                    // TODO getaddrinfo lock?
                    AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, posixLib.createPathFromString(posixSupport, name), null,
                                    family, 0, 0, 0);
                    try {
                        return addrInfoLib.getSockAddr(cursor);
                    } finally {
                        addrInfoLib.release(cursor);
                    }
                } finally {
                    gil.acquire();
                }
            } catch (GetAddrInfoException e) {
                throw constructAndRaiseNode.executeWithArgsOnly(frame, SocketGAIError, new Object[]{e.getErrorCode(), e.getMessage()});
            }
        }

        @TruffleBoundary
        private static boolean hasScopeId(String name) {
            return name.contains("%");
        }
    }

    /**
     * Equivalent of CPython's {@code makesockaddr}
     */
    public abstract static class MakeSockAddrNode extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, UniversalSockAddr addr);

        @Specialization(limit = "1")
        Object makeSockAddr(VirtualFrame frame, UniversalSockAddr addr,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("context.getPosixSupport()") Object posixSupport,
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

    public abstract static class IdnaFromStringOrBytesConverterNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final String builtinName;
        private final int argumentIndex;

        public IdnaFromStringOrBytesConverterNode(String builtinName, int argumentIndex) {
            this.builtinName = builtinName;
            this.argumentIndex = argumentIndex;
        }

        @Override
        public abstract String execute(VirtualFrame frame, Object value);

        @Specialization
        String convert(String value) {
            return idna(value);
        }

        @Specialization
        String convert(PString value,
                        @Cached CastToJavaStringNode cast) {
            return convert(cast.execute(value));
        }

        @Specialization
        String convert(PBytesLike value,
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            return PythonUtils.newString(toBytesNode.execute(value));
        }

        @Fallback
        String error(Object value) {
            if (builtinName != null) {
                throw raise(TypeError, "%s() argument %d must be str, bytes or bytearray, not %p", builtinName, argumentIndex, value);
            } else {
                throw raise(TypeError, "str, bytes or bytearray expected, not %p", value);
            }
        }

        @TruffleBoundary
        private String idna(String name) {
            try {
                return IDN.toASCII(name);
            } catch (IllegalArgumentException e) {
                throw raise(PythonBuiltinClassType.UnicodeError, ErrorMessages.IDN_ENC_FAILED, e.getMessage());
            }
        }

        @ClinicConverterFactory
        public static IdnaFromStringOrBytesConverterNode create(@ClinicConverterFactory.BuiltinName String builtinName, @ClinicConverterFactory.ArgumentIndex int argumentIndex) {
            return SocketNodesFactory.IdnaFromStringOrBytesConverterNodeGen.create(builtinName, argumentIndex);
        }

        public static IdnaFromStringOrBytesConverterNode create() {
            return SocketNodesFactory.IdnaFromStringOrBytesConverterNodeGen.create(null, 0);
        }
    }
}
