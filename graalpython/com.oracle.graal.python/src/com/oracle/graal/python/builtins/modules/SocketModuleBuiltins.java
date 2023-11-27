/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SocketGAIError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SocketHError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.J__SOCKET;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SOCKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_ZERO;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.AI_CANONNAME;
import static com.oracle.graal.python.runtime.PosixConstants.AI_NUMERICHOST;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_ANY;
import static com.oracle.graal.python.runtime.PosixConstants.NI_DGRAM;
import static com.oracle.graal.python.runtime.PosixConstants.NI_NAMEREQD;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.socket.SocketNodes;
import com.oracle.graal.python.builtins.objects.socket.SocketNodes.IdnaFromStringOrBytesConverterNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__SOCKET)
public final class SocketModuleBuiltins extends PythonBuiltins {

    public static HiddenKey DEFAULT_TIMEOUT_KEY = new HiddenKey("default_timeout");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketModuleBuiltinsFactory.getFactories();
    }

    @TruffleBoundary
    static int findProtocolByName(Node raisingNode, String protocolName) {
        String protoConstant = "IPPROTO_" + protocolName.toUpperCase();
        for (PosixConstants.IntConstant constant : PosixConstants.ipProto) {
            if (constant.defined && constant.name.equals(protoConstant)) {
                return constant.getValueIfDefined();
            }
        }
        throw PRaiseNode.raiseUncached(raisingNode, OSError, ErrorMessages.SERVICE_PROTO_NOT_FOUND);
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("SocketType", PythonBuiltinClassType.PSocket);
        addBuiltinConstant("error", PythonBuiltinClassType.OSError);
        addBuiltinConstant("timeout", PythonBuiltinClassType.TimeoutError);
        addBuiltinConstant("has_ipv6", true);

        addConstant(PosixConstants.SOL_SOCKET);
        // These constants don't come from any header, CPython also defines them literally
        addBuiltinConstant("SOL_IP", 0);
        addBuiltinConstant("SOL_TCP", 6);
        addBuiltinConstant("SOL_UDP", 17);

        addConstant(PosixConstants.SOMAXCONN);

        addConstants(PosixConstants.socketType);
        addConstants(PosixConstants.socketFamily);
        addConstants(PosixConstants.socketOptions);
        addConstants(PosixConstants.gaiFlags);
        addConstants(PosixConstants.gaiErrors);
        addConstants(PosixConstants.niFlags);
        addConstants(PosixConstants.ipProto);
        addConstants(PosixConstants.tcpOptions);
        addConstants(PosixConstants.shutdownHow);
        addConstants(PosixConstants.ip4Address);
        addConstants(PosixConstants.ipv6Options);
    }

    @Override
    public void postInitialize(Python3Core core) {
        PythonModule module = core.lookupBuiltinModule(T__SOCKET);
        module.setAttribute(DEFAULT_TIMEOUT_KEY, -1L);
        if (PosixSupportLibrary.getUncached().getBackend(core.getContext().getPosixSupport()).toJavaStringUncached().equals("java")) {
            module.setAttribute(toTruffleStringUncached(PosixConstants.AF_UNIX.name), PNone.NO_VALUE);
        }
    }

    private void addConstants(PosixConstants.IntConstant[] constants) {
        for (PosixConstants.IntConstant constant : constants) {
            addConstant(constant);
        }
    }

    private void addConstant(PosixConstants.IntConstant constant) {
        if (constant.defined) {
            addBuiltinConstant(constant.name, constant.getValueIfDefined());
        }
    }

    // socket(family=AF_INET, type=SOCK_STREAM, proto=0, fileno=None)
    @Builtin(name = "socket", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSocket)
    @GenerateNodeFactory
    public abstract static class SocketNode extends PythonVarargsBuiltinNode {
        // All the "real" work is done by __init__
        @Specialization
        Object socket(Object cls) {
            return factory().createSocket(cls);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (self == PNone.NO_VALUE && arguments.length > 0) {
                return socket(arguments[0]);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
        }
    }

    @Builtin(name = "getdefaulttimeout", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class GetDefaultTimeoutNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(PythonModule module,
                        @Cached ReadAttributeFromObjectNode readNode) {
            long timeout = (long) readNode.execute(module, DEFAULT_TIMEOUT_KEY);
            return timeout < 0 ? PNone.NONE : TimeUtils.pyTimeAsSecondsDouble(timeout);
        }
    }

    @Builtin(name = "setdefaulttimeout", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class SetDefaultTimeoutNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object set(VirtualFrame frame, PythonModule module, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached SocketNodes.ParseTimeoutNode parseTimeoutNode,
                        @Cached WriteAttributeToObjectNode writeNode) {
            long timeout = parseTimeoutNode.execute(frame, inliningTarget, value);
            writeNode.execute(module, DEFAULT_TIMEOUT_KEY, timeout);
            return PNone.NONE;
        }
    }

    @Builtin(name = "gethostname")
    @GenerateNodeFactory
    public abstract static class GetHostnameNode extends PythonBuiltinNode {
        @Specialization
        TruffleString doGeneric(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "socket.gethostname");
            try {
                gil.release(true);
                try {
                    return posixLib.getPathAsString(getPosixSupport(), posixLib.gethostname(getPosixSupport()));
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "gethostbyaddr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByAddrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object ip,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoCursorLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary sockAddrLibrary,
                        @Bind("this") Node inliningTarget,
                        @Cached("createIdnaConverter()") IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached SocketNodes.SetIpAddrNode setIpAddrNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached SocketNodes.MakeIpAddrNode makeIpAddrNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PythonObjectFactory factory) {
            /*
             * TODO this uses getnameinfo and getaddrinfo to emulate the legacy gethostbyaddr. We
             * might want to use the legacy API in the future
             */
            auditNode.audit(inliningTarget, "socket.gethostbyaddr", ip);
            UniversalSockAddr addr = setIpAddrNode.execute(frame, idnaConverter.execute(frame, ip), AF_UNSPEC.value);
            int family = sockAddrLibrary.getFamily(addr);
            try {
                Object[] getnameinfoResult = posixLib.getnameinfo(getPosixSupport(), addr, NI_NAMEREQD.value);
                TruffleString hostname = posixLib.getPathAsString(getPosixSupport(), getnameinfoResult[0]);

                SequenceStorage storage = new ObjectSequenceStorage(5);

                try {
                    AddrInfoCursor cursor;
                    gil.release(true);
                    try {
                        cursor = posixLib.getaddrinfo(getPosixSupport(), getnameinfoResult[0], posixLib.createPathFromString(getPosixSupport(), T_ZERO),
                                        family, 0, 0, 0);
                    } finally {
                        gil.acquire();
                    }
                    try {
                        do {
                            UniversalSockAddr forwardAddr = addrInfoCursorLib.getSockAddr(cursor);
                            storage = appendNode.execute(inliningTarget, storage, makeIpAddrNode.execute(frame, inliningTarget, forwardAddr), SequenceStorageNodes.ListGeneralizationNode.SUPPLIER);
                        } while (addrInfoCursorLib.next(cursor));
                    } finally {
                        addrInfoCursorLib.release(cursor);
                    }
                } catch (GetAddrInfoException e1) {
                    // Ignore failing forward lookup and return at least the hostname
                }
                return factory.createTuple(new Object[]{hostname, factory.createList(), factory.createList(storage)});
            } catch (GetAddrInfoException e) {
                // TODO convert error code from gaierror to herror
                throw constructAndRaiseNode.get(inliningTarget).executeWithArgsOnly(frame, SocketHError, new Object[]{1, e.getMessageAsTruffleString()});
            }
        }

        @NeverDefault
        protected static IdnaFromStringOrBytesConverterNode createIdnaConverter() {
            return IdnaFromStringOrBytesConverterNode.create("gethostbyname", 1);
        }
    }

    @Builtin(name = "gethostbyname", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"name"})
    @GenerateNodeFactory
    public abstract static class GetHostByNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString getHostByName(VirtualFrame frame, Object nameObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary addrLib,
                        @Bind("this") Node inliningTarget,
                        @Cached("createIdnaConverter()") IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached SocketNodes.SetIpAddrNode setIpAddrNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            byte[] name = idnaConverter.execute(frame, nameObj);
            auditNode.audit(inliningTarget, "socket.gethostbyname", factory.createTuple(new Object[]{nameObj}));
            UniversalSockAddr addr = setIpAddrNode.execute(frame, name, AF_INET.value);
            Inet4SockAddr inet4SockAddr = addrLib.asInet4SockAddr(addr);
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.inet_ntop(getPosixSupport(), AF_INET.value, inet4SockAddr.getAddressAsBytes()));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @NeverDefault
        protected static IdnaFromStringOrBytesConverterNode createIdnaConverter() {
            return IdnaFromStringOrBytesConverterNode.create("gethostbyname", 1);
        }
    }

    @Builtin(name = "gethostbyname_ex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByNameExNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, Object nameObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary addrLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoCursorLib,
                        @Bind("this") Node inliningTarget,
                        @Cached("createIdnaConverter()") IdnaFromStringOrBytesConverterNode idnaConverter,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            byte[] name = idnaConverter.execute(frame, nameObj);
            // The event name is really without the _ex, it's not a copy-paste error
            auditNode.audit(inliningTarget, "socket.gethostbyname", factory.createTuple(new Object[]{nameObj}));
            /*
             * TODO this uses getaddrinfo to emulate the legacy gethostbyname. It doesn't support
             * aliases and multiple addresses. We might want to use the legacy gethostbyname_r API
             * in the future
             */
            try {
                AddrInfoCursor cursor = posixLib.getaddrinfo(getPosixSupport(), posixLib.createPathFromBytes(getPosixSupport(), name),
                                null, AF_INET.value, 0, 0, AI_CANONNAME.value);
                try {
                    TruffleString canonName = posixLib.getPathAsString(getPosixSupport(), addrInfoCursorLib.getCanonName(cursor));
                    Inet4SockAddr inet4SockAddr = addrLib.asInet4SockAddr(addrInfoCursorLib.getSockAddr(cursor));
                    TruffleString addr = posixLib.getPathAsString(getPosixSupport(), posixLib.inet_ntop(getPosixSupport(), AF_INET.value, inet4SockAddr.getAddressAsBytes()));
                    // getaddrinfo doesn't support aliases
                    PList aliases = factory.createList();
                    // we support just one address for now
                    PList addrs = factory.createList(new Object[]{addr});
                    return factory.createTuple(new Object[]{canonName, aliases, addrs});
                } finally {
                    addrInfoCursorLib.release(cursor);
                }
            } catch (GetAddrInfoException e) {
                throw constructAndRaiseNode.get(inliningTarget).executeWithArgsOnly(frame, SocketHError, new Object[]{e.getMessageAsTruffleString()});
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @NeverDefault
        protected static IdnaFromStringOrBytesConverterNode createIdnaConverter() {
            return IdnaFromStringOrBytesConverterNode.create("gethostbyname_ex", 1);
        }
    }

    @Builtin(name = "getservbyname", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"servicename", "protocolname"})
    @ArgumentClinic(name = "servicename", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "protocolname", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NO_VALUE")
    @GenerateNodeFactory
    public abstract static class GetServByNameNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object getServByName(TruffleString serviceName, Object protocolNameObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile noneProtocol,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoCursorLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary sockAddrLibrary,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString protocolName;
            if (noneProtocol.profile(inliningTarget, PGuards.isNoValue(protocolNameObj))) {
                protocolName = null;
            } else {
                // clinic should ensure that it can only be a TruffleString
                protocolName = (TruffleString) protocolNameObj;
            }

            /*
             * TODO this uses getaddrinfo to emulate the legacy getservbyname. We might want to use
             * the legacy API in the future
             */
            auditNode.audit(inliningTarget, "socket.getservbyname", serviceName, protocolName != null ? protocolName : "");

            int protocol = 0;
            if (protocolName != null) {
                protocol = findProtocolByName(inliningTarget, toJavaStringNode.execute(protocolName));
            }

            try {
                gil.release(true);
                AddrInfoCursor cursor;
                try {
                    PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                    cursor = posixLib.getaddrinfo(posixSupport, null, posixLib.createPathFromString(posixSupport, serviceName), AF_INET.value, 0, protocol, 0);
                } finally {
                    gil.acquire();
                }
                try {
                    UniversalSockAddr addr = addrInfoCursorLib.getSockAddr(cursor);
                    return sockAddrLibrary.asInet4SockAddr(addr).getPort();
                } finally {
                    addrInfoCursorLib.release(cursor);
                }
            } catch (GetAddrInfoException e) {
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.SERVICE_PROTO_NOT_FOUND);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.GetServByNameNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getservbyport", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"port", "protocolname"})
    @ArgumentClinic(name = "port", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "protocolname", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NO_VALUE")
    @GenerateNodeFactory
    public abstract static class GetServByPortNode extends PythonBinaryClinicBuiltinNode {

        public static final TruffleString T_UDP = tsLiteral("udp");

        @Specialization
        Object getServByPort(int port, Object protocolNameObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile nonProtocol,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString protocolName;
            if (nonProtocol.profile(inliningTarget, PGuards.isNoValue(protocolNameObj))) {
                protocolName = null;
            } else {
                // argument clinic should ensure that it can only be a TruffleString
                protocolName = (TruffleString) protocolNameObj;
            }

            /*
             * TODO this uses getnameinfo to emulate the legacy getservbyport. We might want to use
             * the legacy API in the future
             */
            if (port < 0 || port > 0xffff) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.S_PORT_RANGE, "getservbyport");
            }
            auditNode.audit(inliningTarget, "socket.getservbyport", port, protocolName != null ? protocolName : "");

            try {
                gil.release(true);
                try {
                    UniversalSockAddr addr = posixLib.createUniversalSockAddrInet4(getPosixSupport(), new Inet4SockAddr(port, INADDR_ANY.value));
                    int flags = 0;
                    if (protocolName != null && equalNode.execute(protocolName, T_UDP, TS_ENCODING)) {
                        flags |= NI_DGRAM.value;
                    }
                    Object[] result = posixLib.getnameinfo(getPosixSupport(), addr, flags);
                    TruffleString name = posixLib.getPathAsString(getPosixSupport(), result[1]);
                    checkName(name);
                    return name;
                } finally {
                    gil.acquire();
                }
            } catch (GetAddrInfoException e) {
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.SERVICE_PROTO_NOT_FOUND);
            }
        }

        @TruffleBoundary
        private void checkName(TruffleString name) {
            if (name.toJavaStringUncached().matches("^\\d+$")) {
                throw PRaiseNode.raiseUncached(this, OSError, ErrorMessages.SERVICE_PROTO_NOT_FOUND);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.GetServByPortNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getnameinfo", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"sockaddr", "flags"})
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class GetNameInfoNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static Object getNameInfo(VirtualFrame frame, PTuple sockaddr, int flags,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary addrInfoCursorLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary sockAddrLibrary,
                        @Cached GilNode gil,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached CastToTruffleStringNode castAddress,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.FromLongNode fromLongNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            SequenceStorage addr = sockaddr.getSequenceStorage();
            int addrLen = addr.length();
            if (addrLen < 2 || addrLen > 4) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ILLEGAL_SOCKET_ADDR_ARG, "getnameinfo()");
            }
            TruffleString address;
            int port, flowinfo = 0, scopeid = 0;
            Object arg0 = getItem.execute(inliningTarget, addr, 0);
            try {
                address = castAddress.execute(inliningTarget, arg0);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.MUST_BE_STR_NOT_P, arg0);
            }
            port = asIntNode.execute(frame, inliningTarget, getItem.execute(inliningTarget, addr, 1));
            if (addrLen > 2) {
                flowinfo = asIntNode.execute(frame, inliningTarget, getItem.execute(inliningTarget, addr, 2));
                if (flowinfo < 0 || flowinfo > 0xfffff) {
                    throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.S_FLOWINFO_RANGE, "getnameinfo");
                }
            }
            if (addrLen > 3) {
                scopeid = asIntNode.execute(frame, inliningTarget, getItem.execute(inliningTarget, addr, 3));
            }

            auditNode.audit(inliningTarget, "socket.getnameinfo", sockaddr);

            try {
                UniversalSockAddr resolvedAddr;
                int family;
                // TODO getaddrinfo lock?
                gil.release(true);
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                try {
                    AddrInfoCursor cursor = posixLib.getaddrinfo(posixSupport, posixLib.createPathFromString(posixSupport, address),
                                    posixLib.createPathFromString(posixSupport, fromLongNode.execute(port, TS_ENCODING, false)),
                                    AF_UNSPEC.value, SOCK_DGRAM.value, 0, AI_NUMERICHOST.value);
                    try {
                        family = addrInfoCursorLib.getFamily(cursor);
                        resolvedAddr = addrInfoCursorLib.getSockAddr(cursor);
                        if (addrInfoCursorLib.next(cursor)) {
                            throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.SOCKADDR_RESOLVED_TO_MULTIPLE_ADDRESSES);
                        }
                    } finally {
                        addrInfoCursorLib.release(cursor);
                    }
                } finally {
                    gil.acquire();
                }

                UniversalSockAddr queryAddr;
                if (family == AF_INET.value) {
                    if (addrLen != 2) {
                        throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.IPV4_MUST_BE_2_TUPLE);
                    }
                    queryAddr = posixLib.createUniversalSockAddrInet4(posixSupport, new Inet4SockAddr(port, sockAddrLibrary.asInet4SockAddr(resolvedAddr).getAddress()));
                } else if (family == AF_INET6.value) {
                    queryAddr = posixLib.createUniversalSockAddrInet6(posixSupport, new Inet6SockAddr(port, sockAddrLibrary.asInet6SockAddr(resolvedAddr).getAddress(), flowinfo, scopeid));
                } else {
                    throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.UNKNOWN_FAMILY);
                }

                Object[] getnameinfo = posixLib.getnameinfo(posixSupport, queryAddr, flags);
                TruffleString host = posixLib.getPathAsString(posixSupport, getnameinfo[0]);
                TruffleString service = posixLib.getPathAsString(posixSupport, getnameinfo[1]);
                return factory.createTuple(new Object[]{host, service});
            } catch (GetAddrInfoException e) {
                throw constructAndRaiseNode.get(inliningTarget).executeWithArgsOnly(frame, SocketGAIError, new Object[]{e.getErrorCode(), e.getMessageAsTruffleString()});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object sockaddr, Object flags,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.GETNAMEINFO_ARG1_MUST_BE_TUPLE);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.GetNameInfoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getaddrinfo", minNumOfPositionalArgs = 2, parameterNames = {"host", "port", "family", "type", "proto", "flags"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC.value")
    @ArgumentClinic(name = "type", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "proto", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    public abstract static class GetAddrInfoNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object getAddrInfo(VirtualFrame frame, Object hostObject, Object portObject, int family, int type, int proto, int flags,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") AddrInfoCursorLibrary cursorLib,
                        @Cached InlinedExactClassProfile profile,
                        @Cached("createIdna()") IdnaFromStringOrBytesConverterNode idna,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached CastToTruffleStringNode castToString,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached TruffleString.FromLongNode fromLongNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object host = null;
            PosixSupport posixSupport = PosixSupport.get(inliningTarget);
            if (hostObject != PNone.NONE) {
                host = posixLib.createPathFromBytes(posixSupport, idna.execute(frame, hostObject));
            }

            Object port;
            Object portObjectProfiled = profile.profile(inliningTarget, portObject);
            if (PGuards.canBeInteger(portObjectProfiled)) {
                port = posixLib.createPathFromString(posixSupport, fromLongNode.execute(asLongNode.execute(frame, inliningTarget, portObjectProfiled), TS_ENCODING, false));
            } else if (PGuards.isString(portObjectProfiled)) {
                port = posixLib.createPathFromString(posixSupport, castToString.execute(inliningTarget, portObjectProfiled));
            } else if (PGuards.isBytes(portObjectProfiled)) {
                port = posixLib.createPathFromBytes(posixSupport, toBytes.execute(frame, portObjectProfiled));
            } else if (portObject == PNone.NONE) {
                port = null;
            } else {
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.INT_OR_STRING_EXPECTED);
            }

            auditNode.audit(inliningTarget, "socket.getaddrinfo", hostObject, portObjectProfiled, family, type, proto, flags);

            AddrInfoCursor cursor;
            try {
                // TODO getaddrinfo lock
                gil.release(true);
                try {
                    cursor = posixLib.getaddrinfo(posixSupport, host, port, family, type, proto, flags);
                } finally {
                    gil.acquire();
                }
            } catch (GetAddrInfoException e) {
                throw constructAndRaiseNode.get(inliningTarget).executeWithArgsOnly(frame, SocketGAIError, new Object[]{e.getErrorCode(), e.getMessageAsTruffleString()});
            }
            try {
                SequenceStorage storage = new ObjectSequenceStorage(5);
                do {
                    Object addr = makeSockAddrNode.execute(frame, inliningTarget, cursorLib.getSockAddr(cursor));
                    TruffleString canonName = T_EMPTY_STRING;
                    if (cursorLib.getCanonName(cursor) != null) {
                        canonName = posixLib.getPathAsString(posixSupport, cursorLib.getCanonName(cursor));
                    }
                    PTuple tuple = factory.createTuple(new Object[]{cursorLib.getFamily(cursor), cursorLib.getSockType(cursor), cursorLib.getProtocol(cursor), canonName, addr});
                    storage = appendNode.execute(inliningTarget, storage, tuple, SequenceStorageNodes.ListGeneralizationNode.SUPPLIER);
                } while (cursorLib.next(cursor));
                return factory.createList(storage);
            } finally {
                cursorLib.release(cursor);
            }
        }

        @NeverDefault
        protected static IdnaFromStringOrBytesConverterNode createIdna() {
            return IdnaFromStringOrBytesConverterNode.create("getaddrinfo", 1);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.GetAddrInfoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, Object fdObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached GilNode gil,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int fd = asIntNode.execute(frame, inliningTarget, fdObj);
            try {
                gil.release(true);
                try {
                    posixLib.close(getPosixSupport(), fd);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                // CPython ignores ECONNRESET on close
                if (e.getErrorCode() != OSErrorEnum.ECONNRESET.getNumber()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "dup", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    abstract static class DupNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, Object fdObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached GilNode gil,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int fd = asIntNode.execute(frame, inliningTarget, fdObj);
            try {
                gil.release(true);
                try {
                    int dup = posixLib.dup(getPosixSupport(), fd);
                    try {
                        posixLib.setInheritable(getPosixSupport(), dup, false);
                    } catch (PosixException e1) {
                        try {
                            posixLib.close(getPosixSupport(), dup);
                        } catch (PosixException e2) {
                            // ignore
                        }
                    }
                    return dup;
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "inet_aton", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"addr"})
    @ArgumentClinic(name = "addr", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class InetAtoNNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static PBytes doConvert(TruffleString addr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                int converted = posixLib.inet_aton(posixSupport, posixLib.createPathFromString(posixSupport, addr));
                byte[] bytes = new byte[4];
                ByteArraySupport.bigEndian().putInt(bytes, 0, converted);
                return factory.createBytes(bytes);
            } catch (PosixSupportLibrary.InvalidAddressException e) {
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.ILLEGAL_IP_ADDR_STRING_TO_INET_ATON);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.InetAtoNNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "inet_ntoa", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InetNtoANode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static TruffleString doGeneric(VirtualFrame frame, Object addr,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("addr") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(addr, frame, indirectCallData);
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                if (len != 4) {
                    throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.PACKED_IP_WRONG_LENGTH, "inet_ntoa");
                }
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                Object result = posixLib.inet_ntoa(posixSupport, ByteArraySupport.bigEndian().getInt(bytes, 0));
                return posixLib.getPathAsString(posixSupport, result);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "inet_pton", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"family", "addr"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "addr", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class InetPtoNNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PBytes doConvert(VirtualFrame frame, int family, TruffleString addr,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                byte[] bytes = posixLib.inet_pton(posixSupport, family, posixLib.createPathFromString(posixSupport, addr));
                return factory.createBytes(bytes);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } catch (PosixSupportLibrary.InvalidAddressException e) {
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.ILLEGAL_IP_ADDR_STRING_TO_INET_PTON);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.InetPtoNNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "inet_ntop", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"family", "packed_ip"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class InetNtoPNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static TruffleString doGeneric(VirtualFrame frame, int family, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("obj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(obj, frame, indirectCallData);
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                if (family == AF_INET.value) {
                    if (len != 4) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ILLEGAL_LENGTH_OF_PACKED_IP_ADDRS);
                    }
                } else if (family == AF_INET6.value) {
                    if (len != 16) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ILLEGAL_LENGTH_OF_PACKED_IP_ADDRS);
                    }
                } else {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.UNKNOWN_ADDR_FAMILY, family);
                }
                try {
                    PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                    Object result = posixLib.inet_ntop(posixSupport, family, bytes);
                    return posixLib.getPathAsString(posixSupport, result);
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketModuleBuiltinsClinicProviders.InetNtoPNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "ntohs", minNumOfPositionalArgs = 1)
    @Builtin(name = "htons", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NToHSNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int convert(VirtualFrame frame, Object xObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int x = asIntNode.execute(frame, inliningTarget, xObj);
            if (x < 0) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.NTOHS_CANT_CONVERT_NEG_PYTHON_INT);
            }
            if (x > 0xFFFF) {
                warnNode.warnEx(frame, DeprecationWarning, ErrorMessages.NTOH_PYTHON_STRING_TOO_LARGE_TO_CONVERT, 1);
            }
            short i = (short) x;
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                i = Short.reverseBytes(i);
            }
            return Short.toUnsignedInt(i);
        }
    }

    @Builtin(name = "ntohl", minNumOfPositionalArgs = 1)
    @Builtin(name = "htonl", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NToHLNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long convert(VirtualFrame frame, Object xObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            long x = asLongNode.execute(frame, inliningTarget, xObj);
            if (x < 0) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.CANNOT_CONVERT_NEGATIVE_VALUE_TO_UNSIGNED_INT);
            }
            if (x > 0xFFFFFFFFL) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.INT_LATGER_THAN_32_BITS);
            }
            int i = (int) x;
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                i = Integer.reverseBytes(i);
            }
            return Integer.toUnsignedLong(i);
        }
    }
}
