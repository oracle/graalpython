/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EBADF;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINPROGRESS;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINTR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EISCONN;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ENOTSOCK;
import static com.oracle.graal.python.builtins.objects.socket.PSocket.INVALID_FD;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SOCKET;
import static com.oracle.graal.python.nodes.HiddenAttr.DEFAULT_TIMEOUT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.PosixConstants.SOL_SOCKET;
import static com.oracle.graal.python.runtime.PosixConstants.SO_ERROR;
import static com.oracle.graal.python.runtime.PosixConstants.SO_PROTOCOL;
import static com.oracle.graal.python.runtime.PosixConstants.SO_TYPE;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils.TimeoutHelper;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSocket)
public final class SocketBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketBuiltinsFactory.getFactories();
    }

    private static void checkSelectable(Node inliningTarget, PRaiseNode.Lazy raiseNode, PSocket socket) {
        if (!isSelectable(socket)) {
            throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.UNABLE_TO_SELECT_ON_SOCKET);
        }
    }

    private static boolean isSelectable(PSocket socket) {
        return socket.getTimeoutNs() <= 0 || socket.getFd() < PosixConstants.FD_SETSIZE.value;
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "family", "type", "proto", "fileno"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "type", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "proto", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object init(VirtualFrame frame, PSocket self, int familyIn, int typeIn, int protoIn, @SuppressWarnings("unused") PNone fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Exclusive @Cached HiddenAttr.ReadNode readNode,
                        @Cached GilNode gil,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit(inliningTarget, "socket.__new__", self, familyIn, typeIn, protoIn);
            int family = familyIn;
            if (family == -1) {
                family = PosixConstants.AF_INET.value;
            }
            int type = typeIn;
            if (type == -1) {
                type = PosixConstants.SOCK_STREAM.value;
            }
            int proto = protoIn;
            if (proto == -1) {
                proto = 0;
            }
            PythonContext context = PythonContext.get(inliningTarget);
            try {
                // TODO SOCK_CLOEXEC?
                int fd;
                gil.release(true);
                try {
                    fd = posixLib.socket(context.getPosixSupport(), family, type, proto);
                } finally {
                    gil.acquire();
                }
                try {
                    posixLib.setInheritable(context.getPosixSupport(), fd, false);
                    sockInit(inliningTarget, context, posixLib, readNode, self, fd, family, type, proto);
                } catch (Exception e) {
                    // If we failed before giving the fd to python-land, close it
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    try {
                        posixLib.close(context.getPosixSupport(), fd);
                    } catch (PosixException posixException) {
                        // ignore
                    }
                    throw e;
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(fileno)")
        static Object init(VirtualFrame frame, PSocket self, int familyIn, int typeIn, int protoIn, Object fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary addrLib,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Exclusive @Cached HiddenAttr.ReadNode readNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit(inliningTarget, "socket.__new__", self, familyIn, typeIn, protoIn);
            PythonContext context = PythonContext.get(inliningTarget);

            int fd = asIntNode.execute(frame, inliningTarget, fileno);
            if (fd < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEG_FILE_DESC);
            }
            int family = familyIn;
            try {
                UniversalSockAddr addr = posixLib.getsockname(context.getPosixSupport(), fd);
                if (family == -1) {
                    family = addrLib.getFamily(addr);
                }
            } catch (PosixException e) {
                if (family == -1 || e.getErrorCode() == EBADF.getNumber() || e.getErrorCode() == ENOTSOCK.getNumber()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            try {
                int type = typeIn;
                if (type == -1) {
                    type = getIntSockopt(context.getPosixSupport(), posixLib, fd, SOL_SOCKET.value, SO_TYPE.value);
                }
                int proto = protoIn;
                if (SO_PROTOCOL.defined) {
                    if (proto == -1) {
                        proto = getIntSockopt(context.getPosixSupport(), posixLib, fd, SOL_SOCKET.value, SO_PROTOCOL.getValueIfDefined());
                    }
                } else {
                    proto = 0;
                }
                sockInit(inliningTarget, context, posixLib, readNode, self, fd, family, type, proto);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private static void sockInit(Node inliningTarget, PythonContext context, PosixSupportLibrary posixLib, HiddenAttr.ReadNode readNode,
                        PSocket self, int fd, int family, int type, int proto) throws PosixException {
            self.setFd(fd);
            self.setFamily(family);
            // TODO remove SOCK_CLOEXEC and SOCK_NONBLOCK
            self.setType(type);
            self.setProto(proto);
            long defaultTimeout = (long) readNode.execute(inliningTarget, context.lookupBuiltinModule(T__SOCKET), DEFAULT_TIMEOUT, null);
            self.setTimeoutNs(defaultTimeout);
            if (defaultTimeout >= 0) {
                posixLib.setBlocking(context.getPosixSupport(), fd, false);
            }
        }

        private static int getIntSockopt(PosixSupport posixSupport, PosixSupportLibrary posixLib, int fd, int level, int option) throws PosixException {
            byte[] tmp = new byte[4];
            int len = posixLib.getsockopt(posixSupport, fd, level, option, tmp, tmp.length);
            assert len == tmp.length;
            return PythonUtils.ARRAY_ACCESSOR.getInt(tmp, 0);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(PSocket self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<socket object, fd=%d, family=%d, type=%d, proto=%d>", self.getFd(), self.getFamily(), self.getType(), self.getProto());
        }
    }

    // accept()
    @Builtin(name = "_accept", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcceptNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object accept(VirtualFrame frame, PSocket self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkSelectable(inliningTarget, raiseNode, self);

            try {
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                PosixSupportLibrary.AcceptResult acceptResult = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, posixSupport, gil, self,
                                (p, s) -> p.accept(s, self.getFd()),
                                false, false);
                try {
                    Object pythonAddr = makeSockAddrNode.execute(frame, inliningTarget, acceptResult.sockAddr);
                    posixLib.setInheritable(posixSupport, acceptResult.socketFd, false);
                    return factory.createTuple(new Object[]{acceptResult.socketFd, pythonAddr});
                } catch (Exception e) {
                    // If we failed before giving the fd to python-land, close it
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    try {
                        posixLib.close(posixSupport, acceptResult.socketFd);
                    } catch (PosixException posixException) {
                        // ignore
                    }
                    throw e;
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // bind(address)
    @Builtin(name = "bind", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class BindNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object bind(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLibrary,
                        @Bind("this") Node inliningTarget,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            UniversalSockAddr addr = getSockAddrArgNode.execute(frame, self, address, "bind");
            auditNode.audit(inliningTarget, "socket.bind", self, address);

            try {
                gil.release(true);
                try {
                    posixLibrary.bind(getPosixSupport(), self.getFd(), addr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // close()
    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, PSocket socket,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int fd = socket.getFd();
            if (fd != INVALID_FD) {
                try {
                    socket.setFd(INVALID_FD);
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
            }
            return PNone.NONE;
        }
    }

    // connect(address)
    @Builtin(name = "connect", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConnectNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object connect(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached GilNode gil,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            UniversalSockAddr connectAddr = getSockAddrArgNode.execute(frame, self, address, "connect");

            auditNode.audit(inliningTarget, "socket.connect", self, address);

            try {
                doConnect(frame, inliningTarget, constructAndRaiseNode, posixLib, getPosixSupport(), gil, self, connectAddr);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        static void doConnect(Frame frame, Node inliningTarget, PConstructAndRaiseNode.Lazy constructAndRaiseNode, PosixSupportLibrary posixLib, Object posixSupport, GilNode gil, PSocket self,
                        UniversalSockAddr connectAddr) throws PosixException {
            try {
                gil.release(true);
                try {
                    posixLib.connect(posixSupport, self.getFd(), connectAddr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                boolean waitConnect;
                if (e.getErrorCode() == EINTR.getNumber()) {
                    PythonContext.triggerAsyncActions(constructAndRaiseNode);
                    waitConnect = self.getTimeoutNs() != 0 && isSelectable(self);
                } else {
                    waitConnect = self.getTimeoutNs() > 0 && e.getErrorCode() == EINPROGRESS.getNumber() && isSelectable(self);
                }
                if (waitConnect) {
                    SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, posixSupport, gil, self,
                                    (p, s) -> {
                                        byte[] tmp = new byte[4];
                                        p.getsockopt(s, self.getFd(), SOL_SOCKET.value, SO_ERROR.value, tmp, tmp.length);
                                        int err = PythonUtils.ARRAY_ACCESSOR.getInt(tmp, 0);
                                        if (err != 0 && err != EISCONN.getNumber()) {
                                            throw new PosixException(err, p.strerror(s, err));
                                        }
                                        return null;
                                    },
                                    true, true);
                } else {
                    throw e;
                }
            }
        }
    }

    // connect_ex(address)
    @Builtin(name = "connect_ex", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConnectExNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object connectEx(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached GilNode gil,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            UniversalSockAddr connectAddr = getSockAddrArgNode.execute(frame, self, address, "connect_ex");

            auditNode.audit(inliningTarget, "socket.connect", self, address); // sic! connect

            try {
                ConnectNode.doConnect(frame, inliningTarget, constructAndRaiseNode, posixLib, getPosixSupport(), gil, self, connectAddr);
            } catch (PosixException e) {
                return e.getErrorCode();
            }
            return 0;
        }
    }

    // getpeername()
    @Builtin(name = "getpeername", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetPeerNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PSocket socket,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                UniversalSockAddr addr;
                gil.release(true);
                try {
                    addr = posixLib.getpeername(getPosixSupport(), socket.getFd());
                } finally {
                    gil.acquire();
                }
                return makeSockAddrNode.execute(frame, inliningTarget, addr);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // getsockname()
    @Builtin(name = "getsockname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSockNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PSocket socket,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                UniversalSockAddr addr;
                gil.release(true);
                try {
                    addr = posixLib.getsockname(getPosixSupport(), socket.getFd());
                } finally {
                    gil.acquire();
                }
                return makeSockAddrNode.execute(frame, inliningTarget, addr);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // getblocking()
    @Builtin(name = "getblocking", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetBlockingNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static boolean get(PSocket socket) {
            return socket.getTimeoutNs() != 0;
        }
    }

    // gettimeout
    @Builtin(name = "gettimeout", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTimeoutNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSocket socket) {
            if (socket.getTimeoutNs() < 0) {
                return PNone.NONE;
            } else {
                return TimeUtils.pyTimeAsSecondsDouble(socket.getTimeoutNs());
            }
        }
    }

    // listen
    @Builtin(name = "listen", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "backlog"})
    @ArgumentClinic(name = "backlog", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "128")
    @GenerateNodeFactory
    abstract static class ListenNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object listen(VirtualFrame frame, PSocket self, int backlogIn,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int backlog = backlogIn;
            if (backlog < 0) {
                backlog = 0;
            }
            try {
                gil.release(true);
                try {
                    posixLib.listen(getPosixSupport(), self.getFd(), backlog);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.ListenNodeClinicProviderGen.INSTANCE;
        }
    }

    // recv(bufsize[, flags])
    @Builtin(name = "recv", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object recv(VirtualFrame frame, PSocket socket, int recvlen, int flags,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (recvlen < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECV);
            }
            checkSelectable(inliningTarget, raiseNode, socket);
            if (recvlen == 0) {
                return factory.createEmptyBytes();
            }

            byte[] bytes;
            try {
                bytes = new byte[recvlen];
            } catch (OutOfMemoryError error) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }

            try {
                int outlen = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, getPosixSupport(), gil, socket,
                                (p, s) -> p.recv(s, socket.getFd(), bytes, 0, bytes.length, flags),
                                false, false);
                if (outlen == 0) {
                    return factory.createEmptyBytes();
                }
                // TODO maybe resize if much smaller?
                return factory.createBytes(bytes, outlen);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvNodeClinicProviderGen.INSTANCE;
        }
    }

    // recvfrom(bufsize[, flags])
    @Builtin(name = "recvfrom", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvFromNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object recvFrom(VirtualFrame frame, PSocket socket, int recvlen, int flags,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (recvlen < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECVFROM);
            }
            checkSelectable(inliningTarget, raiseNode, socket);

            byte[] bytes;
            try {
                bytes = new byte[recvlen];
            } catch (OutOfMemoryError error) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }

            try {
                RecvfromResult result = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, getPosixSupport(), gil, socket,
                                (p, s) -> p.recvfrom(s, socket.getFd(), bytes, 0, bytes.length, flags),
                                false, false);
                PBytes resultBytes;
                if (result.readBytes == 0) {
                    resultBytes = factory.createEmptyBytes();
                } else {
                    // TODO maybe resize if much smaller?
                    resultBytes = factory.createBytes(bytes, result.readBytes);
                }
                return factory.createTuple(new Object[]{resultBytes, makeSockAddrNode.execute(frame, inliningTarget, result.sockAddr)});
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvFromNodeClinicProviderGen.INSTANCE;
        }
    }

    // recv_into(bufsize[, flags])
    @Builtin(name = "recv_into", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvIntoNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static Object recvInto(VirtualFrame frame, PSocket socket, Object bufferObj, int recvlenIn, int flags,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireWritable(bufferObj, frame, indirectCallData);
            try {
                if (recvlenIn < 0) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECV_INTO);
                }
                int buflen = bufferLib.getBufferLength(buffer);
                int recvlen = recvlenIn;
                if (recvlen == 0) {
                    recvlen = buflen;
                }
                if (buflen < recvlen) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.BUFF_TOO_SMALL);
                }

                checkSelectable(inliningTarget, raiseNode, socket);

                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    try {
                        bytes = new byte[recvlen];
                    } catch (OutOfMemoryError error) {
                        throw raiseNode.get(inliningTarget).raise(MemoryError);
                    }
                }

                final int len = recvlen;
                try {
                    int outlen = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, PosixSupport.get(inliningTarget), gil, socket,
                                    (p, s) -> p.recv(s, socket.getFd(), bytes, 0, len, flags),
                                    false, false);
                    if (!directWrite) {
                        bufferLib.writeFromByteArray(buffer, 0, bytes, 0, outlen);
                    }
                    return outlen;
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(bufferObj, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    // recvfrom_into(buffer[, nbytes [,flags]])
    @Builtin(name = "recvfrom_into", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvFromIntoNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static Object recvFromInto(VirtualFrame frame, PSocket socket, Object bufferObj, int recvlenIn, int flags,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireWritable(bufferObj, frame, indirectCallData);
            try {
                if (recvlenIn < 0) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECVFROM_INTO);
                }
                int buflen = bufferLib.getBufferLength(buffer);
                int recvlen = recvlenIn;
                if (recvlen == 0) {
                    recvlen = buflen;
                }
                if (buflen < recvlen) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NBYTES_GREATER_THAT_BUFF);
                }

                checkSelectable(inliningTarget, raiseNode, socket);

                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    try {
                        bytes = new byte[recvlen];
                    } catch (OutOfMemoryError error) {
                        throw raiseNode.get(inliningTarget).raise(MemoryError);
                    }
                }

                try {
                    RecvfromResult result = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, PosixSupport.get(inliningTarget), gil, socket,
                                    (p, s) -> p.recvfrom(s, socket.getFd(), bytes, 0, bytes.length, flags),
                                    false, false);
                    if (!directWrite) {
                        bufferLib.writeFromByteArray(buffer, 0, bytes, 0, result.readBytes);
                    }
                    return factory.createTuple(new Object[]{result.readBytes, makeSockAddrNode.execute(frame, inliningTarget, result.sockAddr)});
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvFromIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    // send(bytes[, flags])
    @Builtin(name = "send", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer", "flags"})
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class SendNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static int send(VirtualFrame frame, PSocket socket, Object bufferObj, int flags,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, indirectCallData);
            try {
                checkSelectable(inliningTarget, raiseNode, socket);

                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                try {
                    return SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, PosixSupport.get(inliningTarget), gil, socket,
                                    (p, s) -> p.send(s, socket.getFd(), bytes, 0, len, flags),
                                    true, false);
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SendNodeClinicProviderGen.INSTANCE;
        }
    }

    // sendall(bytes[, flags])
    @Builtin(name = "sendall", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "buffer", "flags"})
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class SendAllNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(limit = "3")
        static Object sendAll(VirtualFrame frame, PSocket socket, Object bufferObj, int flags,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, indirectCallData);
            try {
                checkSelectable(inliningTarget, raiseNode, socket);

                int offset = 0;
                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                long timeout = socket.getTimeoutNs();
                TimeoutHelper timeoutHelper = null;
                if (timeout > 0) {
                    timeoutHelper = new TimeoutHelper(timeout);
                }

                while (true) {
                    try {
                        final int offset1 = offset;
                        final int len1 = len;
                        int outlen = SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, PosixSupport.get(inliningTarget), gil, socket,
                                        (p, s) -> p.send(s, socket.getFd(), bytes, offset1, len1, flags),
                                        true, false, timeoutHelper);
                        offset += outlen;
                        len -= outlen;
                        if (len <= 0) {
                            return PNone.NONE;
                        }
                        // This can loop for a potentially long time
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } catch (PosixException e) {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SendAllNodeClinicProviderGen.INSTANCE;
        }
    }

    // sendto(bytes, address)
    // sendto(bytes, flags, address)
    @Builtin(name = "sendto", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SendToNode extends PythonBuiltinNode {
        @Specialization(limit = "3")
        static Object sendTo(VirtualFrame frame, PSocket socket, Object bufferObj, Object flagsOrAddress, Object maybeAddress,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile hasFlagsProfile,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int flags;
            Object address;
            if (hasFlagsProfile.profile(inliningTarget, maybeAddress == PNone.NO_VALUE)) {
                address = flagsOrAddress;
                flags = 0;
            } else {
                address = maybeAddress;
                flags = asIntNode.execute(frame, inliningTarget, flagsOrAddress);
            }

            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, indirectCallData);
            try {
                checkSelectable(inliningTarget, raiseNode, socket);

                UniversalSockAddr addr = getSockAddrArgNode.execute(frame, socket, address, "sendto");
                auditNode.audit(inliningTarget, "socket.sendto", socket, address);

                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                try {
                    return SocketUtils.callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, PosixSupport.get(inliningTarget), gil, socket,
                                    (p, s) -> p.sendto(s, socket.getFd(), bytes, 0, len, flags, addr),
                                    true, false);
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "setblocking", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "blocking"})
    @ArgumentClinic(name = "blocking", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    public abstract static class SetBlockingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PNone doBoolean(VirtualFrame frame, PSocket socket, boolean blocking,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), blocking);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            socket.setTimeoutNs(blocking ? -1 : 0);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SetBlockingNodeClinicProviderGen.INSTANCE;
        }
    }

    // settimeout(value)
    @Builtin(name = "settimeout", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetTimeoutNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setTimeout(VirtualFrame frame, PSocket socket, Object seconds,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.ParseTimeoutNode parseTimeoutNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long timeout = parseTimeoutNode.execute(frame, inliningTarget, seconds);
            socket.setTimeoutNs(timeout);
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), timeout < 0);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // shutdown(how)
    @Builtin(name = "shutdown", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "how"})
    @ArgumentClinic(name = "how", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ShutdownNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object shutdown(VirtualFrame frame, PSocket socket, int how,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.shutdown(getPosixSupport(), socket.getFd(), how);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.ShutdownNodeClinicProviderGen.INSTANCE;
        }
    }

    // family
    @Builtin(name = "family", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketFamilyNode extends PythonUnaryBuiltinNode {
        @Specialization
        int family(PSocket socket) {
            return socket.getFamily();
        }
    }

    // type
    @Builtin(name = "type", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        int type(PSocket socket) {
            return socket.getType();
        }
    }

    // proto
    @Builtin(name = "proto", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SockProtoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int proto(PSocket socket) {
            return socket.getProto();
        }
    }

    // fileno
    @Builtin(name = "fileno", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockFilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int fileno(PSocket socket) {
            return socket.getFd();
        }
    }

    // detach
    @Builtin(name = "detach", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockDetachNode extends PythonUnaryBuiltinNode {
        @Specialization
        int detach(PSocket socket) {
            int fd = socket.getFd();
            socket.setFd(INVALID_FD);
            return fd;
        }
    }

    @Builtin(name = "setsockopt", minNumOfPositionalArgs = 4, numOfPositionalOnlyArgs = 5, parameterNames = {"$self", "level", "optname", "flag1", "flag2"})
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "optname", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetSockOptNode extends PythonClinicBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object setInt(VirtualFrame frame, PSocket socket, int level, int option, Object value, @SuppressWarnings("unused") PNone none,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyLongAsIntNode asIntNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            byte[] bytes;
            int len;
            try {
                int flag = asIntNode.execute(frame, inliningTarget, value);
                bytes = new byte[4];
                len = bytes.length;
                PythonUtils.ARRAY_ACCESSOR.putInt(bytes, 0, flag);
            } catch (PException e) {
                Object buffer = bufferAcquireLib.acquireReadonly(value, frame, indirectCallData);
                try {
                    len = bufferLib.getBufferLength(buffer);
                    bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, indirectCallData);
                }

            }
            try {
                posixLib.setsockopt(PosixSupport.get(inliningTarget), socket.getFd(), level, option, bytes, len);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(none)")
        static Object setNull(VirtualFrame frame, PSocket socket, int level, int option, @SuppressWarnings("unused") PNone none, Object buflenObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyLongAsIntNode asIntNode,
                        @Exclusive @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int buflen = asIntNode.execute(frame, inliningTarget, buflenObj);
            if (buflen < 0) {
                // GraalPython-specific because we don't have unsigned integers
                throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.SETSECKOPT_BUFF_OUT_OFRANGE);
            }
            try {
                posixLib.setsockopt(PosixSupport.get(inliningTarget), socket.getFd(), level, option, null, buflen);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object level, Object option, Object flag1, Object flag2,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.SETSECKOPT_REQUIRERS_3RD_ARG_NULL);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SetSockOptNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getsockopt", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 4, parameterNames = {"$self", "level", "optname", "buflen"})
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "optname", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "buflen", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class GetSockOptNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization
        Object getSockOpt(VirtualFrame frame, PSocket socket, int level, int option, int buflen,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                if (buflen == 0) {
                    byte[] result = new byte[4];
                    posixLib.getsockopt(getPosixSupport(), socket.getFd(), level, option, result, result.length);
                    return PythonUtils.ARRAY_ACCESSOR.getInt(result, 0);
                } else if (buflen > 0 && buflen < 1024) {
                    byte[] result = new byte[buflen];
                    int len = posixLib.getsockopt(getPosixSupport(), socket.getFd(), level, option, result, result.length);
                    return factory.createBytes(result, len);
                } else {
                    throw raiseNode.get(inliningTarget).raise(OSError, ErrorMessages.GETSECKOPT_BUFF_OUT_OFRANGE);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.GetSockOptNodeClinicProviderGen.INSTANCE;
        }
    }
}
