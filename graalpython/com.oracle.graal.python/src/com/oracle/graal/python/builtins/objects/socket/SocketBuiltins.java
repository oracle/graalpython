/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.lib.PyTimeFromObjectNode.SEC_TO_NS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils.TimeoutHelper;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSocket)
public class SocketBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "family", "type", "proto", "fileno"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "type", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "proto", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {
        @Specialization
        Object init(VirtualFrame frame, PSocket self, int family, int type, int proto, @SuppressWarnings("unused") PNone fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit("socket.__new__", self, family, type, proto);
            if (family == -1) {
                family = PosixConstants.AF_INET.value;
            }
            if (type == -1) {
                type = PosixConstants.SOCK_STREAM.value;
            }
            if (proto == -1) {
                proto = 0;
            }
            try {
                // TODO SOCK_CLOEXEC?
                int fd;
                gil.release(true);
                try {
                    fd = posixLib.socket(getPosixSupport(), family, type, proto);
                } finally {
                    gil.acquire();
                }
                // TODO _Py_set_inheritable?
                self.setFd(fd);
                self.setFamily(family);
                // TODO remove SOCK_CLOEXEC and SOCK_NONBLOCK
                self.setType(type);
                self.setProto(proto);
                // TODO default timeout & setblocking
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(fileno)")
        Object init(PSocket self, int family, int type, int proto, Object fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit("socket.__new__", self, family, type, proto);
            // TODO implement
            throw raise(NotImplementedError);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }
    }

    // accept()
    @Builtin(name = "_accept", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcceptNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        @SuppressWarnings("try")
        Object accept(PSocket socket) {
            if (socket.getServerSocket() == null) {
                throw raiseOSError(null, OSErrorEnum.EINVAL);
            }
            try {
                SocketChannel acceptSocket;
                try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                    acceptSocket = SocketUtils.accept(this, socket);
                }
                if (acceptSocket == null) {
                    throw raiseOSError(null, OSErrorEnum.EWOULDBLOCK);
                }
                InetSocketAddress remoteAddress = (InetSocketAddress) acceptSocket.getRemoteAddress();
                if (!acceptSocket.socket().isBound() || remoteAddress == null) {
                    throw raise(OSError);
                }
                // PSocket newSocket = factory().createSocket(socket.getFamily(), socket.getType(),
                // socket.getProto());
                PSocket newSocket = factory().createSocket(PythonBuiltinClassType.PSocket);
                int fd = getContext().getResources().openSocket(newSocket);
                newSocket.setFd(fd);
                newSocket.setSocket(acceptSocket);
                newSocket.setTimeout(socket.getTimeout());
                PTuple addressTuple = factory().createTuple(new Object[]{remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort()});
                return factory().createTuple(new Object[]{fd, addressTuple});
            } catch (IOException e) {
                throw raise(OSError);
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
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil) {
            UniversalSockAddr addr = getSockAddrArgNode.execute(frame, getPosixSupport(), self, address, "bind");
            auditNode.audit("socket.bind", self, address);

            try {
                gil.release(true);
                try {
                    posixLibrary.bind(getPosixSupport(), self.getFd(), addr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            if (socket.getFd() != PSocket.INVALID_FD) {
                try {
                    socket.setFd(PSocket.INVALID_FD);
                    gil.release(true);
                    try {
                        posixLib.close(getPosixSupport(), socket.getFd());
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    // CPython ignores ECONNRESET on close
                    if (e.getErrorCode() != OSErrorEnum.ECONNRESET.getNumber()) {
                        throw raiseOSErrorFromPosixException(frame, e);
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
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached GilNode gil,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            UniversalSockAddr connectAddr = getSockAddrArgNode.execute(frame, getPosixSupport(), self, address, "connect");

            auditNode.audit("socket.connect", self, address);

            // TODO timeout, nonblocking, EINTR
            try {
                gil.release(true);
                try {
                    posixLib.connect(getPosixSupport(), self.getFd(), connectAddr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // getpeername()
    @Builtin(name = "getpeername", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetPeerNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PSocket socket) {
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }

            try {
                return factory().createTuple(doGet(socket));
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
        }

        @TruffleBoundary
        private static Object[] doGet(PSocket socket) throws IOException {
            InetSocketAddress addr = (InetSocketAddress) socket.getSocket().getRemoteAddress();
            return new Object[]{addr.getAddress().getHostAddress(), addr.getPort()};
        }
    }

    // getsockname()
    @Builtin(name = "getsockname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSockNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(PSocket socket) {
            if (socket.getServerSocket() != null) {
                try {
                    InetSocketAddress addr = (InetSocketAddress) socket.getServerSocket().getLocalAddress();
                    return factory().createTuple(new Object[]{addr.getAddress().getHostAddress(), addr.getPort()});
                } catch (IOException e) {
                    throw raise(OSError);
                }
            }

            if (socket.getSocket() != null) {
                try {
                    InetSocketAddress addr = (InetSocketAddress) socket.getSocket().getLocalAddress();
                    return factory().createTuple(new Object[]{addr.getAddress().getHostAddress(), addr.getPort()});
                } catch (IOException e) {
                    throw raise(OSError);
                }
            }

            if (socket.serverHost != null) {
                return factory().createTuple(new Object[]{socket.serverHost, socket.serverPort});
            }

            return factory().createTuple(new Object[]{"0.0.0.0", 0});
        }
    }

    // getblocking()
    @Builtin(name = "getblocking", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetBlockingNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static boolean get(PSocket socket) {
            return socket.getTimeout() != 0;
        }
    }

    // gettimeout
    @Builtin(name = "gettimeout", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTimeoutNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSocket socket) {
            if (socket.getTimeout() < 0) {
                return PNone.NONE;
            } else {
                // TODO avoid rounding errors
                return socket.getTimeout() / SEC_TO_NS;
            }
        }
    }

    // listen
    @Builtin(name = "listen", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ListenNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        @SuppressWarnings("try")
        Object listen(PSocket socket, int backlog) {
            if (socket.getServerSocket() != null) {
                return PNone.NONE;
            }
            try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                InetAddress host = InetAddress.getByName(socket.serverHost);
                InetSocketAddress socketAddress = new InetSocketAddress(host, socket.serverPort);

                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                // calling bind with port 0 will take the first available
                // for some reason this only works on the ServerSocket not on the
                // ServerSocketChannel
                serverSocketChannel.socket().bind(socketAddress, backlog);

                socket.setServerSocket(serverSocketChannel);
                return PNone.NONE;
            } catch (IOException e) {
                throw raise(OSError);
            }
        }

        @Specialization
        @TruffleBoundary
        Object listen(PSocket socket, @SuppressWarnings("unused") PNone backlog) {
            return listen(socket, 50);
        }
    }

    // recv(bufsize[, flags])
    @Builtin(name = "recv", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object recv(VirtualFrame frame, PSocket socket, int bufsize, @SuppressWarnings("unused") int flags,
                        @Cached GilNode gil) {
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            try {
                gil.release(true);
                try {
                    ByteBuffer readBytes = PythonUtils.allocateByteBuffer(bufsize);
                    int length = SocketUtils.recv(this, socket, readBytes);
                    return factory().createBytes(PythonUtils.getBufferArray(readBytes), length);
                } finally {
                    gil.acquire();
                }
            } catch (NotYetConnectedException e) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN, e);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvNodeClinicProviderGen.INSTANCE;
        }
    }

    // recvfrom(bufsize[, flags])
    @Builtin(name = "recvfrom", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RecvFromNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // recv_into(bufsize[, flags])
    @Builtin(name = "recv_into", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, needsFrame = true)
    @GenerateNodeFactory
    abstract static class RecvIntoNode extends PythonTernaryBuiltinNode {
        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create("cannot happen: non-byte store in socket.recv_into");
        }

        @Specialization
        Object recvInto(VirtualFrame frame, PSocket socket, PMemoryView buffer, @SuppressWarnings("unused") Object flags,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLen,
                        @Cached("create(__SETITEM__)") LookupAndCallTernaryNode setItem) {
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            int bufferLen = asSizeNode.executeExact(frame, callLen.executeObject(frame, buffer));
            byte[] targetBuffer = new byte[bufferLen];
            ByteBuffer byteBuffer = PythonUtils.wrapByteBuffer(targetBuffer);
            int length;
            try {
                length = SocketUtils.recv(this, socket, byteBuffer);
            } catch (NotYetConnectedException e) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN, e);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF, e);
            }
            for (int i = 0; i < length; i++) {
                int b = targetBuffer[i];
                if (b < 0) {
                    b += 256;
                }
                setItem.execute(frame, buffer, i, (Object) b);
            }
            return length;
        }

        @Specialization
        Object recvInto(VirtualFrame frame, PSocket socket, PByteArray buffer, @SuppressWarnings("unused") Object flags,
                        @Cached GilNode gil,
                        @Cached ConditionProfile byteStorage,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItem) {
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            SequenceStorage storage = buffer.getSequenceStorage();
            int bufferLen = lenNode.execute(storage);
            if (byteStorage.profile(storage instanceof ByteSequenceStorage)) {
                ByteBuffer byteBuffer = ((ByteSequenceStorage) storage).getBufferView();
                try {
                    gil.release(true);
                    try {
                        return SocketUtils.recv(this, socket, byteBuffer);
                    } finally {
                        gil.acquire();
                    }
                } catch (NotYetConnectedException e) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTCONN, e);
                } catch (IOException e) {
                    throw raiseOSError(frame, OSErrorEnum.EBADF, e);
                }
            } else {
                byte[] targetBuffer = new byte[bufferLen];
                ByteBuffer byteBuffer = PythonUtils.wrapByteBuffer(targetBuffer);
                int length;
                try {
                    gil.release(true);
                    try {
                        length = SocketUtils.recv(this, socket, byteBuffer);
                    } finally {
                        gil.acquire();
                    }
                } catch (NotYetConnectedException e) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTCONN, e);
                } catch (IOException e) {
                    throw raiseOSError(frame, OSErrorEnum.EBADF, e);
                }
                for (int i = 0; i < length; i++) {
                    // we don't allow generalization
                    setItem.execute(frame, storage, i, targetBuffer[i]);
                }
                return length;
            }
        }
    }

    // recvmsg(bufsize[, ancbufsize[, flags]])
    @Builtin(name = "recvmsg", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class RecvMsgNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int ancbufsize, int flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int ancbufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, PNone ancbufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // send(bytes[, flags])
    @Builtin(name = "send", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SendNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object send(VirtualFrame frame, PSocket socket, PBytes bytes, @SuppressWarnings("unused") Object flags,
                        @Cached GilNode gil,
                        @Cached SequenceStorageNodes.ToByteArrayNode toBytes) {
            // TODO: do not ignore flags
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            int written;
            ByteBuffer buffer = PythonUtils.wrapByteBuffer(toBytes.execute(bytes.getSequenceStorage()));
            try {
                gil.release(true);
                try {
                    written = SocketUtils.send(this, socket, buffer);
                } finally {
                    gil.acquire();
                }
            } catch (NotYetConnectedException e) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            } catch (IOException e) {
                throw raise(OSError);
            }
            if (written == 0) {
                throw raiseOSError(frame, OSErrorEnum.EWOULDBLOCK);
            }
            return written;
        }
    }

    // sendall(bytes[, flags])
    @Builtin(name = "sendall", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SendAllNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object sendAll(VirtualFrame frame, PSocket socket, PBytesLike bytes, @SuppressWarnings("unused") Object flags,
                        @Cached GilNode gil,
                        @Cached SequenceStorageNodes.ToByteArrayNode toBytes,
                        @Cached ConditionProfile hasTimeoutProfile) {
            // TODO: do not ignore flags
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            ByteBuffer buffer = PythonUtils.wrapByteBuffer(toBytes.execute(bytes.getSequenceStorage()));
            long timeoutMillis = socket.getTimeout();
            TimeoutHelper timeoutHelper = null;
            if (hasTimeoutProfile.profile(timeoutMillis > 0)) {
                timeoutHelper = new TimeoutHelper(timeoutMillis);
            }
            while (PythonUtils.bufferHasRemaining(buffer)) {
                if (timeoutHelper != null) {
                    timeoutMillis = timeoutHelper.checkAndGetRemainingTimeout(this);
                }
                int written;
                try {
                    gil.release(true);
                    try {
                        written = SocketUtils.send(this, socket, buffer, timeoutMillis);
                    } finally {
                        gil.acquire();
                    }
                } catch (NotYetConnectedException e) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
                } catch (IOException e) {
                    throw raise(OSError);
                }
                if (written == 0) {
                    throw raiseOSError(frame, OSErrorEnum.EWOULDBLOCK);
                }
            }
            return PythonUtils.getBufferPosition(buffer);
        }
    }

    // sendto(bytes, address)
    // sendto(bytes, flags, address)
    @Builtin(name = "sendto", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SendToNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object sendTo(PSocket socket, Object bytes, int flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object sendTo(PSocket socket, Object bytes, PNone flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // sendmsg(buffers[, ancdata[, flags[, address]]])
    @Builtin(name = "sendmsg", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class SendMsgNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object sendMsg(PSocket socket, Object buffers, Object ancdata, int flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "setblocking", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "blocking"})
    @ArgumentClinic(name = "blocking", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    public abstract static class SetBlockingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PNone doBoolean(VirtualFrame frame, PSocket socket, boolean blocking,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), blocking);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            socket.setTimeout(blocking ? -1 : 0);
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
        @Specialization(guards = "isNone(none)")
        Object setTimeout(VirtualFrame frame, PSocket socket, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            socket.setTimeout(-1);
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), true);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isNone(seconds)")
        Object setTimeout(VirtualFrame frame, PSocket socket, Object seconds,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyTimeFromObjectNode timeFromObjectNode) {
            // TODO timeout rounding mode
            long timeout = timeFromObjectNode.execute(frame, seconds, SEC_TO_NS);
            if (timeout < 0) {
                throw raise(ValueError, ErrorMessages.TIMEOUT_VALUE_OUT_OF_RANGE);
            }
            socket.setTimeout(timeout);
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), false);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // shutdown(how)
    @Builtin(name = "shutdown", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class shutdownNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object family(VirtualFrame frame, PSocket socket, int how) {
            if (socket.getSocket() == null) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            }
            try {
                shutdown(socket, how);
            } catch (NotYetConnectedException e) {
                throw raiseOSError(frame, OSErrorEnum.ENOTCONN);
            } catch (IOException e) {
                throw raise(OSError);
            }
            return PNone.NO_VALUE;
        }

        @TruffleBoundary
        private static void shutdown(PSocket socket, int how) throws IOException {
            if (how == 0 || how == 2) {
                socket.getSocket().shutdownInput();
            }
            if (how == 1 || how == 2) {
                socket.getSocket().shutdownOutput();
            }
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
            socket.setFd(-1);
            return fd;
        }
    }

    @Builtin(name = "_setsockopt", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SetSockOptNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object setSockOpt(PSocket socket, Object level, Object optname, Object value, Object optlen) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "setsockopt", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SetSockOptionNode extends PythonBuiltinNode {
        @Specialization
        Object setSockOpt(PSocket socket, @SuppressWarnings("unused") Object level, Object option, Object value) {
            // TODO: Implement these
            socket.setSockOpt(option, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "getsockopt", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSockOptionNode extends PythonBuiltinNode {
        private static final int SO_TYPE = 3;

        @Specialization
        Object getSockOpt(PSocket socket, @SuppressWarnings("unused") Object level, int option) {
            // TODO implement more of these
            if (option == SO_TYPE) {
                return socket.getType();
            }
            return socket.getSockOpt(option);
        }
    }
}
