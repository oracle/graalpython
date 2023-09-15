/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.socket;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SocketGAIError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.StringLiterals.T_IDNA;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_ZERO;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNIX;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.AI_PASSIVE;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_BROADCAST;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.arrayCopyOf;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Arrays;

import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidUnixSocketPathException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnixSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public abstract class SocketNodes {
    /**
     * Equivalent of CPython's {@code socketmodule.c:getsockaddrarg}.
     */
    public abstract static class GetSockAddrArgNode extends PNodeWithRaiseAndIndirectCall {
        public abstract UniversalSockAddr execute(VirtualFrame frame, PSocket socket, Object address, String caller);

        @Specialization(guards = "isInet(socket)")
        UniversalSockAddr doInet(VirtualFrame frame, @SuppressWarnings("unused") PSocket socket, Object address, String caller,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") @Shared("posixLib") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") @Shared("sockAddrLib") UniversalSockAddrLibrary sockAddrLib,
                        @Cached @Shared("getObjectArray") SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached @Shared("asInt") PyLongAsIntNode asIntNode,
                        @Cached @Shared("idnaConverter") IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached @Shared("errorProfile") IsBuiltinObjectProfile errorProfile,
                        @Cached @Shared("setIpAddr") SetIpAddrNode setIpAddrNode) {
            PythonContext context = PythonContext.get(this);
            if (!(address instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.S_AF_INET_VALUES_MUST_BE_TUPLE_NOT_P, caller, address);
            }
            Object[] hostAndPort = getObjectArrayNode.execute(inliningTarget, address);
            if (hostAndPort.length != 2) {
                throw raise(TypeError, ErrorMessages.AF_INET_VALUES_MUST_BE_PAIR);
            }
            byte[] host = idnaConverter.execute(frame, hostAndPort[0]);
            int port = parsePort(frame, caller, asIntNode, inliningTarget, errorProfile, hostAndPort[1]);
            UniversalSockAddr addr = setIpAddrNode.execute(frame, host, AF_INET.value);
            Object posixSupport = context.getPosixSupport();
            return posixLib.createUniversalSockAddrInet4(posixSupport, new Inet4SockAddr(port, sockAddrLib.asInet4SockAddr(addr).getAddress()));
        }

        @Specialization(guards = "isInet6(socket)")
        UniversalSockAddr doInet6(VirtualFrame frame, @SuppressWarnings("unused") PSocket socket, Object address, String caller,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") @Shared("posixLib") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") @Shared("sockAddrLib") UniversalSockAddrLibrary sockAddrLib,
                        @Cached @Shared("getObjectArray") SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached @Shared("asInt") PyLongAsIntNode asIntNode,
                        @Cached @Shared("idnaConverter") IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached @Shared("errorProfile") IsBuiltinObjectProfile errorProfile,
                        @Cached @Shared("setIpAddr") SetIpAddrNode setIpAddrNode) {
            PythonContext context = PythonContext.get(this);
            if (!(address instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.S_AF_INET_VALUES_MUST_BE_TUPLE_NOT_S, caller, address);
            }
            Object[] hostAndPort = getObjectArrayNode.execute(inliningTarget, address);
            if (hostAndPort.length < 2 || hostAndPort.length > 4) {
                throw raise(TypeError, ErrorMessages.AF_INET6_ADDR_MUST_BE_TUPLE);
            }
            byte[] host = idnaConverter.execute(frame, hostAndPort[0]);
            int port = parsePort(frame, caller, asIntNode, inliningTarget, errorProfile, hostAndPort[1]);
            int flowinfo = 0;
            if (hostAndPort.length > 2) {
                flowinfo = asIntNode.execute(frame, inliningTarget, hostAndPort[2]);
                if (flowinfo < 0 || flowinfo > 0xfffff) {
                    throw raise(OverflowError, ErrorMessages.S_FLOWINFO_RANGE, caller);
                }
            }
            int scopeid = 0;
            if (hostAndPort.length > 3) {
                scopeid = asIntNode.execute(frame, inliningTarget, hostAndPort[3]);
            }
            UniversalSockAddr addr = setIpAddrNode.execute(frame, host, AF_INET6.value);
            Object posixSupport = context.getPosixSupport();
            return posixLib.createUniversalSockAddrInet6(posixSupport, new Inet6SockAddr(port, sockAddrLib.asInet6SockAddr(addr).getAddress(), flowinfo, scopeid));
        }

        @Specialization(guards = "isUnix(socket)")
        @SuppressWarnings("truffle-static-method")
        UniversalSockAddr doUnix(VirtualFrame frame, @SuppressWarnings("unused") PSocket socket, Object address, String caller,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @CachedLibrary(limit = "1") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary(limit = "1") @Shared("posixLib") PosixSupportLibrary posixLib) {
            byte[] path;
            if (unicodeCheckNode.execute(inliningTarget, address)) {
                // PyUnicode_EncodeFSDefault
                TruffleString utf8 = switchEncodingNode.execute(toTruffleStringNode.execute(inliningTarget, address), Encoding.UTF_8);
                path = copyToByteArrayNode.execute(utf8, Encoding.UTF_8);
            } else {
                Object buffer = bufferAcquireLib.acquireReadonly(address, frame, this);
                try {
                    path = bufferLib.getCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
            if (!PosixConstants.IS_LINUX || (path.length > 0 && path[0] != 0)) {
                // not a linux "abstract" address -> needs a terminating zero
                path = arrayCopyOf(path, path.length + 1);
            }
            PythonContext context = PythonContext.get(this);
            Object posixSupport = context.getPosixSupport();
            try {
                return posixLib.createUniversalSockAddrUnix(posixSupport, new UnixSockAddr(path));
            } catch (UnsupportedPosixFeatureException e) {
                throw raise(OSError, ErrorMessages.AF_UNIX_NOT_SUPPORTED, caller);
            } catch (InvalidUnixSocketPathException e) {
                throw raise(OSError, ErrorMessages.AF_UNIX_PATH_TOO_LONG, caller);
            }
        }

        @Specialization(guards = {"!isInet(socket)", "!isInet6(socket)", "!isUnix(socket)"})
        @SuppressWarnings("unused")
        UniversalSockAddr getSockAddr(VirtualFrame frame, PSocket socket, Object address, String caller) {
            throw raise(OSError, ErrorMessages.BAD_FAMILY, caller);
        }

        static boolean isInet(PSocket socket) {
            return socket.getFamily() == AF_INET.value;
        }

        static boolean isInet6(PSocket socket) {
            return socket.getFamily() == AF_INET6.value;
        }

        static boolean isUnix(PSocket socket) {
            return socket.getFamily() == AF_UNIX.value;
        }

        private int parsePort(VirtualFrame frame, String caller, PyLongAsIntNode asIntNode, Node inliningTarget, IsBuiltinObjectProfile errorProfile, Object portObj) {
            int port;
            try {
                port = asIntNode.execute(frame, inliningTarget, portObj);
            } catch (PException e) {
                e.expect(inliningTarget, OverflowError, errorProfile);
                port = -1;
            }
            if (port < 0 || port > 0xffff) {
                throw raise(OverflowError, ErrorMessages.S_PORT_RANGE, caller);
            }
            return port;
        }
    }

    /**
     * Equivalent of CPython's {@code socketmodule.c:setipaddr}.
     */
    public abstract static class SetIpAddrNode extends PNodeWithRaise {

        private static final byte[] BROADCAST_IP = "255.255.255.255".getBytes();
        private static final byte[] BROADCAST = "<broadcast>".getBytes();

        public abstract UniversalSockAddr execute(VirtualFrame frame, byte[] name, int family);

        @Specialization
        UniversalSockAddr setipaddr(VirtualFrame frame, byte[] name, int family,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoLib,
                        @Cached InetPtoNCachedPNode inetPtoNCachedPNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached GilNode gil) {
            PythonContext context = PythonContext.get(this);
            Object posixSupport = context.getPosixSupport();
            try {
                if (name.length == 0) {
                    gil.release(true);
                    try {
                        // TODO getaddrinfo lock?
                        AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, null, posixLib.createPathFromString(posixSupport, T_ZERO),
                                        family, SOCK_DGRAM.value, 0, AI_PASSIVE.value);
                        try {
                            if (addrInfoLib.next(cursor)) {
                                throw raise(OSError, ErrorMessages.WILD_CARD_RESOLVED_TO_MULTIPLE_ADDRESS);
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
                if (Arrays.equals(name, BROADCAST_IP) || Arrays.equals(name, BROADCAST)) {
                    if (family != AF_INET.value && family != AF_UNSPEC.value) {
                        throw raise(OSError, ErrorMessages.ADDRESS_FAMILY_MISMATCHED);
                    }
                    return posixLib.createUniversalSockAddrInet4(posixSupport, new Inet4SockAddr(0, INADDR_BROADCAST.value));
                }
                /* avoid a name resolution in case of numeric address */
                /* check for an IPv4 address */
                if (family == AF_INET.value || family == AF_UNSPEC.value) {
                    byte[] bytes = inetPtoNCachedPNode.execute(inliningTarget, posixLib, posixSupport, AF_INET.value, name);
                    if (bytes != null) {
                        return posixLib.createUniversalSockAddrInet4(posixSupport, new Inet4SockAddr(0, bytes));
                    }
                }
                /*
                 * check for an IPv6 address - if the address contains a scope ID, we fallback to
                 * getaddrinfo(), which can handle translation from interface name to interface
                 * index
                 */
                if ((family == AF_INET6.value || family == AF_UNSPEC.value) && !hasScopeId(name)) {
                    byte[] bytes = inetPtoNCachedPNode.execute(inliningTarget, posixLib, posixSupport, AF_INET6.value, name);
                    if (bytes != null) {
                        return posixLib.createUniversalSockAddrInet6(posixSupport, new Inet6SockAddr(0, bytes, 0, 0));
                    }
                }
                /* perform a name resolution */
                gil.release(true);
                try {
                    // TODO getaddrinfo lock?
                    AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, posixLib.createPathFromBytes(posixSupport, name), null,
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
                throw constructAndRaiseNode.get(inliningTarget).executeWithArgsOnly(frame, SocketGAIError, new Object[]{e.getErrorCode(), e.getMessage()});
            }
        }

        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(Arrays.class)
        abstract static class InetPtoNCachedPNode extends Node {
            abstract byte[] execute(Node inliningTarget, PosixSupportLibrary posixLib, Object posixSupport, int family, byte[] string);

            @Specialization(guards = {"family == cachedFamily", "equals(string, cachedString)"}, limit = "3")
            @SuppressWarnings("unused")
            static byte[] cached(PosixSupportLibrary posixLib, Object posixSupport, int family, byte[] string,
                            @Cached("family") int cachedFamily,
                            @Cached(value = "string", dimensions = 1) byte[] cachedString,
                            @Cached(value = "doParse(posixLib, posixSupport, family, string)", dimensions = 1) byte[] cachedResult) {
                return cachedResult;
            }

            @Specialization(replaces = "cached")
            static byte[] doParse(PosixSupportLibrary posixLib, Object posixSupport, int family, byte[] string) {
                assert family == AF_INET.value || family == AF_INET6.value;
                try {
                    return posixLib.inet_pton(posixSupport, family, posixLib.createPathFromBytes(posixSupport, string));
                } catch (PosixException | InvalidAddressException e) {
                    return null;
                }
            }
        }

        private static boolean hasScopeId(byte[] name) {
            for (byte b : name) {
                if (b == '%') {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Equivalent of CPython's {@code makesockaddr}
     */
    public abstract static class MakeSockAddrNode extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, UniversalSockAddr addr);

        @Specialization(limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object makeSockAddr(VirtualFrame frame, UniversalSockAddr addr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @CachedLibrary("addr") UniversalSockAddrLibrary addrLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                PythonContext context = PythonContext.get(this);
                int family = addrLib.getFamily(addr);
                if (family == AF_INET.value) {
                    Inet4SockAddr inet4SockAddr = addrLib.asInet4SockAddr(addr);
                    Object posixSupport = context.getPosixSupport();
                    TruffleString addressString = posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet4SockAddr.getAddressAsBytes()));
                    return factory.createTuple(new Object[]{addressString, inet4SockAddr.getPort()});
                } else if (family == AF_INET6.value) {
                    Inet6SockAddr inet6SockAddr = addrLib.asInet6SockAddr(addr);
                    Object posixSupport = context.getPosixSupport();
                    TruffleString addressString = posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet6SockAddr.getAddress()));
                    return factory.createTuple(new Object[]{addressString, inet6SockAddr.getPort(), inet6SockAddr.getFlowInfo(), inet6SockAddr.getScopeId()});
                } else if (family == AF_UNIX.value) {
                    UnixSockAddr unixSockAddr = addrLib.asUnixSockAddr(addr);
                    byte[] path = unixSockAddr.getPath();
                    if (PosixConstants.IS_LINUX && path.length > 0 && path[0] == 0) {
                        // linux-specific "abstract" address
                        return factory.createBytes(arrayCopyOf(path, path.length));
                    }
                    return bytesToString(path, fromByteArrayNode, switchEncodingNode);
                } else if (family == AF_UNSPEC.value) {
                    // Can be returned from recvfrom when used on a connected socket
                    return PNone.NONE;
                } else {
                    throw raise(NotImplementedError, toTruffleStringUncached("makesockaddr: unknown address family"));
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e.getErrorCode(), fromJavaStringNode.execute(e.getMessage(), TS_ENCODING), null, null);
            }
        }

        private static TruffleString bytesToString(byte[] path, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
            // PyUnicode_DecodeFSDefault
            int len = 0;
            while (len < path.length && path[len] != 0) {
                ++len;
            }
            return switchEncodingNode.execute(fromByteArrayNode.execute(path, 0, len, Encoding.UTF_8, true), TS_ENCODING);
        }

    }

    /**
     * Converts address to string, like CPython's {@code make_ipv4_addr} and {@code make_ipv6_addr}.
     */
    public abstract static class MakeIpAddrNode extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, UniversalSockAddr addr);

        @Specialization(limit = "1")
        @SuppressWarnings("truffle-static-method")
        Object makeAddr(VirtualFrame frame, UniversalSockAddr addr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @CachedLibrary("addr") UniversalSockAddrLibrary addrLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            try {
                PythonContext context = PythonContext.get(this);
                int family = addrLib.getFamily(addr);
                if (family == AF_INET.value) {
                    Inet4SockAddr inet4SockAddr = addrLib.asInet4SockAddr(addr);
                    Object posixSupport = context.getPosixSupport();
                    return posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet4SockAddr.getAddressAsBytes()));
                } else if (family == AF_INET6.value) {
                    Inet6SockAddr inet6SockAddr = addrLib.asInet6SockAddr(addr);
                    Object posixSupport = context.getPosixSupport();
                    return posixLib.getPathAsString(posixSupport, posixLib.inet_ntop(posixSupport, family, inet6SockAddr.getAddress()));
                } else {
                    throw raise(NotImplementedError, toTruffleStringUncached("makesockaddr: unknown address family"));
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, e.getErrorCode(), fromJavaStringNode.execute(e.getMessage(), TS_ENCODING), null, null);
            }
        }
    }

    public abstract static class IdnaFromStringOrBytesConverterNode extends Node {
        private final String builtinName;
        private final int argumentIndex;

        public IdnaFromStringOrBytesConverterNode(String builtinName, int argumentIndex) {
            this.builtinName = builtinName;
            this.argumentIndex = argumentIndex;
        }

        public abstract byte[] execute(VirtualFrame frame, Object value);

        @Specialization
        byte[] convert(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached BytesNodes.BytesLikeCheck bytesLikeCheck,
                        @Cached CastToTruffleStringNode castToString,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached CodecsModuleBuiltins.EncodeNode encodeNode,
                        @Cached PRaiseNode.Lazy raise) {
            Object bytes;
            if (unicodeCheckNode.execute(inliningTarget, value)) {
                TruffleString string = castToString.execute(inliningTarget, value);
                if (getCodeRangeNode.execute(string, TS_ENCODING) == TruffleString.CodeRange.ASCII) {
                    /*
                     * Skip IDNA for ASCII-only strings. Even though, technically, some ASCII string
                     * might still be invalid IDNA, CPython ignores this possibility and so do we.
                     */
                    return copyToByteArrayNode.execute(switchEncodingNode.execute(string, Encoding.US_ASCII), Encoding.US_ASCII);
                } else {
                    bytes = encodeNode.execute(frame, value, T_IDNA, T_STRICT);
                }
            } else if (bytesLikeCheck.execute(inliningTarget, value)) {
                bytes = value;
            } else {
                if (builtinName != null) {
                    throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_BYTELIKE_OR_BYTEARRAY, builtinName, argumentIndex, value);
                } else {
                    throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.STR_BYTES_OR_BYTEARRAY_EXPECTED, value);
                }
            }
            return bufferLib.getCopiedByteArray(bytes);
        }

        @NeverDefault
        public static IdnaFromStringOrBytesConverterNode create(String builtinName, int argumentIndex) {
            return SocketNodesFactory.IdnaFromStringOrBytesConverterNodeGen.create(builtinName, argumentIndex);
        }

        @NeverDefault
        public static IdnaFromStringOrBytesConverterNode create() {
            return SocketNodesFactory.IdnaFromStringOrBytesConverterNodeGen.create(null, 0);
        }
    }

    /**
     * Equivalent of CPython's {@code socket_parse_timeout}
     */
    public abstract static class ParseTimeoutNode extends PNodeWithRaise {
        public abstract long execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNone(none)")
        static long parse(@SuppressWarnings("unused") PNone none) {
            return -1;
        }

        @Specialization(guards = "!isNone(seconds)")
        long parse(VirtualFrame frame, Object seconds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTimeFromObjectNode timeFromObjectNode) {
            long timeout = timeFromObjectNode.execute(frame, inliningTarget, seconds, RoundType.TIMEOUT, TimeUtils.SEC_TO_NS);
            if (timeout < 0) {
                throw raise(ValueError, ErrorMessages.TIMEOUT_VALUE_OUT_OF_RANGE);
            }
            return timeout;
        }
    }
}
