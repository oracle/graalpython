/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.PosixConstants.F_RDLCK;
import static com.oracle.graal.python.runtime.PosixConstants.F_UNLCK;
import static com.oracle.graal.python.runtime.PosixConstants.F_WRLCK;
import static com.oracle.graal.python.runtime.PosixConstants.LOCK_EX;
import static com.oracle.graal.python.runtime.PosixConstants.LOCK_NB;
import static com.oracle.graal.python.runtime.PosixConstants.LOCK_SH;
import static com.oracle.graal.python.runtime.PosixConstants.LOCK_UN;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.FcntlModuleBuiltinsClinicProviders.FlockNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.FileDescriptorConversionNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixConstants.IntConstant;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "fcntl")
public final class FcntlModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FcntlModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        for (IntConstant c : PosixConstants.flockOperation) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }
        for (IntConstant c : PosixConstants.flockType) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }
        super.initialize(core);
    }

    @Builtin(name = "flock", parameterNames = {"fd", "operation"})
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @ArgumentClinic(name = "operation", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class FlockNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FlockNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        synchronized PNone flock(VirtualFrame frame, int fd, int operation,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posix,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "fcntl.flock", fd, operation);
            try {
                posix.flock(getPosixSupport(), fd, operation);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "lockf", minNumOfPositionalArgs = 2, parameterNames = {"fd", "cmd", "len", "start", "whence"})
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @ArgumentClinic(name = "cmd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "whence", conversion = ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class LockfNode extends PythonClinicBuiltinNode {
        @Specialization
        PNone lockf(VirtualFrame frame, int fd, int code, Object lenObj, Object startObj, int whence,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posix,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "fcntl.lockf", fd, code, lenObj != PNone.NO_VALUE ? lenObj : PNone.NONE, startObj != PNone.NO_VALUE ? startObj : PNone.NONE, whence);
            int lockType;
            if (code == LOCK_UN.value) {
                lockType = F_UNLCK.getValueIfDefined();
            } else if ((code & LOCK_SH.value) != 0) {
                lockType = F_RDLCK.getValueIfDefined();
            } else if ((code & LOCK_EX.value) != 0) {
                lockType = F_WRLCK.getValueIfDefined();
            } else {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.UNRECOGNIZED_LOCKF_ARGUMENT);
            }
            long start = 0;
            if (startObj != PNone.NO_VALUE) {
                start = asLongNode.execute(frame, inliningTarget, startObj);
            }
            long len = 0;
            if (lenObj != PNone.NO_VALUE) {
                len = asLongNode.execute(frame, inliningTarget, lenObj);
            }
            try {
                posix.fcntlLock(getPosixSupport(), fd, (code & LOCK_NB.value) == 0, lockType, whence, start, len);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FcntlModuleBuiltinsClinicProviders.LockfNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "ioctl", minNumOfPositionalArgs = 2, parameterNames = {"fd", "request", "arg", "mutate_flag"})
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @ArgumentClinic(name = "request", conversion = ClinicConversion.Long)
    @ArgumentClinic(name = "mutate_flag", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class IoctlNode extends PythonClinicBuiltinNode {
        private static final int IOCTL_BUFSZ = 1024;

        @Specialization
        Object ioctl(VirtualFrame frame, int fd, long request, Object arg, boolean mutateArg,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached CastToTruffleStringNode castToString,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached GilNode gilNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit(inliningTarget, "fcnt.ioctl", fd, request, arg);

            int intArg = 0;
            if (arg != PNone.NO_VALUE) {
                Object buffer = null;
                // Buffer argument
                if (acquireLib.hasBuffer(arg)) {
                    boolean writable = false;
                    try {
                        buffer = acquireLib.acquireWritable(arg, frame, indirectCallData);
                        writable = true;
                    } catch (PException e) {
                        try {
                            buffer = acquireLib.acquireReadonly(arg, frame, indirectCallData);
                        } catch (PException e1) {
                            // ignore
                        }
                    }
                    if (buffer != null) {
                        try {
                            int len = bufferLib.getBufferLength(buffer);
                            boolean writeBack = false;
                            boolean releaseGil = true;
                            byte[] ioctlArg = null;
                            if (writable && mutateArg) {
                                writeBack = true;
                                if (bufferLib.hasInternalByteArray(buffer)) {
                                    byte[] internalArray = bufferLib.getInternalByteArray(buffer);
                                    if (internalArray.length > len && internalArray[len] == 0) {
                                        writeBack = false;
                                        releaseGil = false; // Could resize concurrently
                                        ioctlArg = internalArray;
                                    }
                                }
                            } else {
                                if (len > IOCTL_BUFSZ) {
                                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.IOCTL_STRING_ARG_TOO_LONG);
                                }
                            }
                            if (ioctlArg == null) {
                                ioctlArg = new byte[len + 1];
                                bufferLib.readIntoByteArray(buffer, 0, ioctlArg, 0, len);
                            }
                            try {
                                int ret = callIoctlBytes(frame, inliningTarget, fd, request, ioctlArg, releaseGil, posixLib, gilNode, constructAndRaiseNode);
                                if (writable && mutateArg) {
                                    return ret;
                                } else {
                                    return factory.createBytes(ioctlArg, len);
                                }
                            } finally {
                                if (writeBack) {
                                    bufferLib.writeFromByteArray(buffer, 0, ioctlArg, 0, len);
                                }
                            }
                        } finally {
                            bufferLib.release(buffer, frame, indirectCallData);
                        }
                    }
                }
                // string arg
                TruffleString stringArg = null;
                try {
                    stringArg = castToString.execute(inliningTarget, arg);
                } catch (CannotCastException e) {
                    // ignore
                }
                if (stringArg != null) {
                    TruffleString.Encoding utf8 = TruffleString.Encoding.UTF_8;
                    stringArg = switchEncodingNode.execute(stringArg, utf8);
                    int len = stringArg.byteLength(utf8);
                    if (len > IOCTL_BUFSZ) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.IOCTL_STRING_ARG_TOO_LONG);
                    }
                    byte[] ioctlArg = new byte[len + 1];
                    copyToByteArrayNode.execute(stringArg, 0, ioctlArg, 0, len, utf8);
                    callIoctlBytes(frame, inliningTarget, fd, request, ioctlArg, true, posixLib, gilNode, constructAndRaiseNode);
                    return factory.createBytes(ioctlArg, len);
                }

                // int arg
                intArg = asIntNode.execute(frame, inliningTarget, arg);
                // fall through
            }

            // default arg or int arg
            try {
                gilNode.release(true);
                try {
                    return posixLib.ioctlInt(getPosixSupport(), fd, request, intArg);
                } finally {
                    gilNode.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        private int callIoctlBytes(VirtualFrame frame, Node inliningTarget, int fd, long request, byte[] ioctlArg, boolean releaseGil, PosixSupportLibrary posixLib, GilNode gilNode,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                if (releaseGil) {
                    gilNode.release(true);
                }
                try {
                    return posixLib.ioctlBytes(getPosixSupport(), fd, request, ioctlArg);
                } finally {
                    if (releaseGil) {
                        gilNode.acquire();
                    }
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FcntlModuleBuiltinsClinicProviders.IoctlNodeClinicProviderGen.INSTANCE;
        }
    }
}
