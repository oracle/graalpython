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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PRawIOBase;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.mapPythonSeekWhenceToPosix;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins.BUFSIZ;
import static com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins.DEFAULT_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSEFD;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READALL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__BLKSIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__FINALIZING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NAME;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EAGAIN;
import static com.oracle.graal.python.nodes.BuiltinNames.J_OPEN;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_MODE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_USE_CLOSEFD;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_BYTE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_INT_FROM_OPENER;
import static com.oracle.graal.python.nodes.ErrorMessages.FILE_NOT_OPEN_FOR_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_MODE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.OPENER_RETURNED_D;
import static com.oracle.graal.python.nodes.ErrorMessages.REENTRANT_CALL_INSIDE_P_REPR;
import static com.oracle.graal.python.nodes.ErrorMessages.UNBOUNDED_READ_RETURNED_MORE_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.UNCLOSED_FILE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_FALSE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_TRUE;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.PosixConstants.AT_FDCWD;
import static com.oracle.graal.python.runtime.PosixConstants.O_APPEND;
import static com.oracle.graal.python.runtime.PosixConstants.O_CREAT;
import static com.oracle.graal.python.runtime.PosixConstants.O_EXCL;
import static com.oracle.graal.python.runtime.PosixConstants.O_RDONLY;
import static com.oracle.graal.python.runtime.PosixConstants.O_RDWR;
import static com.oracle.graal.python.runtime.PosixConstants.O_TRUNC;
import static com.oracle.graal.python.runtime.PosixConstants.O_WRONLY;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.FtruncateNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.LseekNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IONodes.IOMode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFileIO)
public final class FileIOBuiltins extends PythonBuiltins {

    /*
     * We are limited to max primitive array size, Integer.MAX_VALUE, the jdk can offer. CPython
     * defines the max value as system's SSIZE_T_MAX on linux and INT_MAX for MacOS and Windows.
     */
    private static final int MAX_SIZE = SysModuleBuiltins.MAXSIZE;
    public static final int READ_MAX = MAX_SIZE;

    private static final int SMALLCHUNK = BUFSIZ;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FileIOBuiltinsFactory.getFactories();
    }

    static final class FDReleaseCallback implements AsyncHandler.AsyncAction {
        private final OwnFD fd;

        public FDReleaseCallback(OwnFD fd) {
            this.fd = fd;
        }

        @Override
        public void execute(PythonContext context) {
            if (fd.isReleased()) {
                return;
            }
            try {
                fd.doRelease();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    static void internalClose(VirtualFrame frame, PFileIO self,
                    PosixModuleBuiltins.CloseNode posixClose) {
        int fd = self.getFD();
        if (fd >= 0) {
            self.setClosed();
            posixClose.execute(frame, fd);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class FileIOInit extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener);

        private static void errorCleanup(VirtualFrame frame, PFileIO self, boolean fdIsOwn,
                        PosixModuleBuiltins.CloseNode posixClose) {
            if (!fdIsOwn) {
                self.setClosed();
            } else {
                internalClose(frame, self, posixClose);
            }
        }

        private static int open(VirtualFrame frame, TruffleString name, int flags, int mode,
                        PythonContext ctxt,
                        Node inliningTarget,
                        PosixSupportLibrary posixLib,
                        GilNode gil,
                        InlinedBranchProfile errorProfile,
                        PRaiseNode.Lazy raiseNode,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Object path = posixLib.createPathFromString(ctxt.getPosixSupport(), name);
            if (path == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, EMBEDDED_NULL_BYTE);
            }
            while (true) {
                try {
                    gil.release(true);
                    try {
                        return posixLib.openat(ctxt.getPosixSupport(), AT_FDCWD.value, path, flags, mode);
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, name);
                    }
                }
            }
        }

        public static boolean isInvalidMode(IONodes.IOMode mode) {
            return mode.isInvalid || mode.text || mode.universal;
        }

        public static boolean isBadMode(IONodes.IOMode mode) {
            return mode.isBad || mode.xrwa != 1;
        }

        private static int processMode(PFileIO self, IONodes.IOMode mode) {
            int flags = 0;
            if (mode.creating) { // x
                self.setCreated();
                self.setWritable();
                flags |= O_EXCL.value | O_CREAT.value;
            } else if (mode.reading) { // r
                self.setReadable();
            } else if (mode.writing) { // w
                self.setWritable();
                flags |= O_CREAT.value | O_TRUNC.value;
            } else if (mode.appending) { // a
                self.setWritable();
                self.setAppending();
                flags |= O_APPEND.value | O_CREAT.value;
            }

            if (mode.updating) { // +
                self.setWritable();
                self.setReadable();
            }

            if (self.isReadable() && self.isWritable()) {
                flags |= O_RDWR.value;
            } else {
                flags |= self.isReadable() ? O_RDONLY.value : O_WRONLY.value;
            }

            return flags;
        }

        public static void internalInit(PFileIO self, TruffleString name, int fd, IOMode mode) {
            self.setCloseFD(false);
            self.setFD(fd, null);
            processMode(self, mode);
            self.setBlksize(DEFAULT_BUFFER_SIZE);
            WriteAttributeToObjectNode.getUncached().execute(self, T_NAME, name);
        }

        @Specialization(guards = {"!isBadMode(mode)", "!isInvalidMode(mode)"})
        static void doInit(VirtualFrame frame, Node inliningTarget, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached(inline = false) CallNode callOpener,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached(inline = false) IONodes.CastOpenNameNode castOpenNameNode,
                        @Cached(inline = false) PosixModuleBuiltins.CloseNode posixClose,
                        @Cached(inline = false) SetAttributeNode.Dynamic setAttr,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached InlinedBranchProfile exceptionProfile,
                        @Cached InlinedBranchProfile exceptionProfile1,
                        @Cached InlinedBranchProfile exceptionProfile2,
                        @Cached InlinedBranchProfile exceptionProfile3,
                        @Cached InlinedConditionProfile errorProfile,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) TruffleString.FromLongNode fromLongNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (self.getFD() >= 0) {
                if (self.isCloseFD()) {
                    /* Have to close the existing file first. */
                    internalClose(frame, self, posixClose);
                }
            }
            self.setClosed();

            int fd = -1;
            TruffleString name = null;
            Object o = castOpenNameNode.execute(frame, nameobj);
            if (o instanceof TruffleString) {
                name = (TruffleString) o;
            } else {
                fd = (int) o;
            }

            int flags = processMode(self, mode);
            auditNode.audit(inliningTarget, J_OPEN, nameobj, mode.mode, flags);

            try {
                boolean fdIsOwn = false;
                PythonContext ctxt = PythonContext.get(inliningTarget);
                if (fd >= 0) {
                    self.setCloseFD(closefd);
                    self.setFD(fd, ctxt);
                } else {
                    self.setCloseFD(true);
                    if (errorProfile.profile(inliningTarget, !closefd)) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, CANNOT_USE_CLOSEFD);
                    }

                    if (opener instanceof PNone) {
                        self.setFD(open(frame, name, flags, 0666, ctxt, inliningTarget, posixLib, gil, exceptionProfile, raiseNode, constructAndRaiseNode), ctxt);
                    } else {
                        Object fdobj = callOpener.execute(frame, opener, nameobj, flags);
                        if (!indexCheckNode.execute(inliningTarget, fdobj)) {
                            throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_INT_FROM_OPENER);
                        }

                        self.setFD(asSizeNode.executeExact(frame, inliningTarget, fdobj), ctxt);
                        if (self.getFD() < 0) {
                            /*
                             * The opener returned a negative but didn't set an exception. See issue
                             * #27066
                             */
                            throw raiseNode.get(inliningTarget).raise(ValueError, OPENER_RETURNED_D, self.getFD());
                        }
                    }
                    try {
                        posixLib.setInheritable(ctxt.getPosixSupport(), self.getFD(), false);
                    } catch (PosixException e) {
                        exceptionProfile1.enter(inliningTarget);
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                    fdIsOwn = true;
                }
                self.setBlksize(DEFAULT_BUFFER_SIZE);
                try {
                    long[] fstatResult;
                    gil.release(true);
                    try {
                        fstatResult = posixLib.fstat(ctxt.getPosixSupport(), self.getFD());
                    } finally {
                        gil.acquire();
                    }
                    /*
                     * On Unix, open will succeed for directories. In Python, there should be no
                     * file objects referring to directories, so we need a check.
                     */
                    if (errorProfile.profile(inliningTarget, PosixSupportLibrary.isDIR(fstatResult[0]))) {
                        errorCleanup(frame, self, fdIsOwn, posixClose);
                        TruffleString fname = name == null ? fromLongNode.execute(fd, TS_ENCODING, false) : name;
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.EISDIR, fname);
                    }
                    /*
                     * TODO: read fstatResult.st_blksize if (fstatResult[8] > 1)
                     * self.setBlksize(fstatResult[8]); }
                     */
                } catch (PosixException e) {
                    exceptionProfile2.enter(inliningTarget);
                    /*
                     * Tolerate fstat() errors other than EBADF. See Issue #25717, where an
                     * anonymous file on a Virtual Box shared folder filesystem would raise ENOENT.
                     */
                    if (e.getErrorCode() == OSErrorEnum.EBADF.getNumber()) {
                        errorCleanup(frame, self, fdIsOwn, posixClose);
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
                setAttr.execute(frame, self, T_NAME, nameobj);

                if (self.isAppending()) {
                    /*
                     * For consistent behaviour, we explicitly seek to the end of file (otherwise,
                     * it might be done only on the first write()).
                     */
                    try {
                        gil.release(true);
                        try {
                            long res = posixLib.lseek(ctxt.getPosixSupport(), self.getFD(), 0, mapPythonSeekWhenceToPosix(SEEK_END));
                            self.setSeekable(res >= 0 ? 1 : 0);
                        } finally {
                            gil.acquire();
                        }
                    } catch (PosixException e) {
                        exceptionProfile3.enter(inliningTarget);
                        if (self.getSeekable() < 0) {
                            self.setSeekable(0);
                        }
                        if (e.getErrorCode() != OSErrorEnum.ESPIPE.getNumber()) {
                            errorCleanup(frame, self, fdIsOwn, posixClose);
                            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                        }
                    }
                }
            } catch (PException e) {
                /*
                 * IMPORTANT: In case of any error that happens during initialization, we need reset
                 * the file descriptor such that the finalizer won't close it. This is necessary
                 * because the file descriptor value could be reused and then this (broken) instance
                 * would incorrectly close another resource that accidentally got the same file
                 * descriptor value.
                 */
                self.setClosed();
                throw e;
            }
        }

        @Specialization(guards = "isInvalidMode(mode)")
        static void invalidMode(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object nameobj, IONodes.IOMode mode, @SuppressWarnings("unused") boolean closefd,
                        @SuppressWarnings("unused") Object opener,
                        @Shared @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, INVALID_MODE_S, mode.mode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isBadMode(mode)")
        static void badMode(PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener,
                        @Shared @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, BAD_MODE);
        }
    }

    // FileIO(name, mode='r', closefd=True, opener=None)
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "name", "mode", "closefd", "opener"})
    // "mode" should not have `null` character
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "false")
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doInit(VirtualFrame frame, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget,
                        @Cached FileIOInit fileIOInit) {
            fileIOInit.execute(frame, inliningTarget, self, nameobj, mode, closefd, opener);
            return PNone.NONE;
        }

    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()", "size < 0"})
        static Object readall(VirtualFrame frame, PFileIO self, @SuppressWarnings("unused") int size,
                        @Cached ReadallNode readallNode) {
            return readallNode.execute(frame, self);
        }

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()", "size == 0"})
        static Object none(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()", "size >= 0"})
        static Object read(VirtualFrame frame, PFileIO self, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached InlinedBranchProfile readErrorProfile,
                        @Cached InlinedBranchProfile readErrorProfile2,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            try {
                return posixRead.read(self.getFD(), size, inliningTarget, posixLib, readErrorProfile, gil, factory);
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    readErrorProfile2.enter(inliningTarget);
                    return PNone.NONE;
                }
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = {"!self.isClosed()", "!self.isReadable()"})
        static Object notReadable(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "reading");
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_READALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadallNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!self.isClosed()")
        static Object readall(VirtualFrame frame, PFileIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached InlinedBranchProfile readErrorProfile,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile multipleReadsProfile,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int bufsize = SMALLCHUNK;
            boolean mayBeQuick = false;
            try {
                PosixSupport posixSupport = PosixSupport.get(inliningTarget);
                long pos = posixLib.lseek(posixSupport, self.getFD(), 0L, mapPythonSeekWhenceToPosix(SEEK_CUR));
                long[] status = posixLib.fstat(posixSupport, self.getFD());
                long end = status[6]; // TODO: st_size
                if (end > 0 && end >= pos && pos >= 0 && end - pos < MAX_SIZE) {
                    /*
                     * This is probably a real file, so we try to allocate a buffer one byte larger
                     * than the rest of the file. If the calculation is right then we should get EOF
                     * without having to enlarge the buffer.
                     */
                    bufsize = (int) (end - pos + 1); // cast guaranteed since we check against
                                                     // (MAX_SIZE: MAX_INT)
                    mayBeQuick = true;
                }
            } catch (PosixException e) {
                // ignore
            }

            int bytesRead = 0;
            PBytes b;
            try {
                b = posixRead.read(self.getFD(), bufsize, inliningTarget, posixLib, readErrorProfile, gil, factory);
                bytesRead = b.getSequenceStorage().length();
                if (bytesRead == 0 || (mayBeQuick && bytesRead == bufsize - 1)) {
                    return b;
                }
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }

            multipleReadsProfile.enter(inliningTarget);
            byte[] buffer = getBytes.execute(inliningTarget, b.getSequenceStorage());
            ByteArrayOutputStream result = createOutputStream();
            append(result, buffer, bytesRead);

            while (true) {
                if (bytesRead >= bufsize) {
                    // see CPython's function 'fileio.c: new_buffersize'
                    bufsize = bytesRead + Math.max(SMALLCHUNK, bytesRead + 256);
                    if (bufsize <= 0) {
                        throw raiseNode.get(inliningTarget).raise(OverflowError, UNBOUNDED_READ_RETURNED_MORE_BYTES);
                    }
                }

                int n;
                try {
                    b = posixRead.read(self.getFD(), bufsize - bytesRead, inliningTarget, posixLib, readErrorProfile, gil, factory);
                    /*
                     * PosixModuleBuiltins#ReadNode creates PBytes with exact size;
                     */
                    buffer = getBytes.execute(inliningTarget, b.getSequenceStorage());
                    n = b.getSequenceStorage().length();
                    if (n == 0) {
                        break;
                    }
                } catch (PosixException e) {
                    if (e.getErrorCode() == EAGAIN.getNumber()) {
                        if (bytesRead > 0) {
                            break;
                        }
                        return PNone.NONE;
                    }
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }

                append(result, buffer, n);
                bytesRead += n;
            }

            return factory.createBytes(toByteArray(result));
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadintoNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()"})
        static Object readinto(VirtualFrame frame, PFileIO self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached InlinedBranchProfile readErrorProfile,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                int size = bufferLib.getBufferLength(buffer);
                if (size == 0) {
                    return 0;
                }
                try {
                    PBytes data = posixRead.read(self.getFD(), size, inliningTarget, posixLib, readErrorProfile, gil, factory);
                    int n = bufferLib.getBufferLength(data);
                    bufferLib.readIntoBuffer(data, 0, buffer, 0, n, bufferLib);
                    return n;
                } catch (PosixException e) {
                    if (e.getErrorCode() == EAGAIN.getNumber()) {
                        return PNone.NONE;
                    }
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!self.isClosed()", "!self.isReadable()"})
        static Object notReadable(PFileIO self, Object buffer,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "reading");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        static Object closedError(PFileIO self, Object buffer,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.ReadintoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()"})
        static Object write(VirtualFrame frame, PFileIO self, Object data,
                        @Bind("this") Node inliningTarget,
                        @Cached GetBytesToWriteNode getBytesToWriteNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            byte[] bytes = getBytesToWriteNode.execute(frame, inliningTarget, self, data);
            try {
                return PosixModuleBuiltins.WriteNode.write(self.getFD(), bytes, bytes.length, inliningTarget, posixLib, errorProfile, gil);
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                errorProfile.enter(inliningTarget);
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = {"!self.isClosed()", "!self.isWritable()"})
        static Object notWritable(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object buf,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "writing");
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object buf,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetBytesToWriteNode extends Node {
        abstract byte[] execute(VirtualFrame frame, Node inliningTarget, PFileIO self, Object data);

        @Specialization(guards = "!self.isUTF8Write()")
        static byte[] doBytes(VirtualFrame frame, @SuppressWarnings("unused") PFileIO self, Object data,
                        @Cached(inline = false) BytesNodes.ToBytesNode toBytes) {
            return toBytes.execute(frame, data);
        }

        @Specialization(guards = "self.isUTF8Write()")
        static byte[] doUtf8(Node inliningTarget, @SuppressWarnings("unused") PFileIO self, Object data,
                        @Cached(inline = false) CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encode,
                        @Cached CastToTruffleStringNode castStr) {
            return encode.execute(castStr.execute(inliningTarget, data), T_UTF8, T_STRICT);
        }
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "pos", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Long)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!self.isClosed()")
        Object seek(VirtualFrame frame, PFileIO self, long pos, int whence,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                gil.release(true);
                try {
                    return internalSeek(self, pos, whence, getPosixSupport(), posixLib);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object pos, @SuppressWarnings("unused") int whence,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }

        protected static long internalSeek(PFileIO self, long pos, int whence,
                        Object posixSupport,
                        PosixSupportLibrary posixLib) throws PosixException {
            try {
                long res = posixLib.lseek(posixSupport, self.getFD(), pos, mapPythonSeekWhenceToPosix(whence));
                if (self.getSeekable() < 0) {
                    self.setSeekable(1);
                }
                return res;
            } finally {
                if (self.getSeekable() < 0) {
                    self.setSeekable(0);
                }
            }
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class TellNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tell(VirtualFrame frame, PFileIO self,
                        @Cached SeekNode seekNode) {
            return seekNode.execute(frame, self, 0, SEEK_CUR);
        }

        static long internalTell(PFileIO self,
                        Object posixSupport,
                        PosixSupportLibrary posixLib) throws PosixException {
            return SeekNode.internalSeek(self, 0, SEEK_CUR, posixSupport, posixLib);
        }
    }

    static void deallocWarn(VirtualFrame frame, PFileIO self,
                    WarningsModuleBuiltins.WarnNode warn,
                    PythonLanguage language,
                    PythonContext context) {
        if (self.getFD() >= 0 && self.isCloseFD()) {
            PythonThreadState threadState = context.getThreadState(language);
            PException exc = threadState.getCurrentException();
            warn.resourceWarning(frame, self, 1, UNCLOSED_FILE, self);
            /* Spurious errors can appear at shutdown */
            /* (mq) we aren't doing WriteUnraisable as WarnNode will take care of it */
            threadState.setCurrentException(exc);
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", ""})
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()", "!isPNone(posobj)"})
        static Object num(VirtualFrame frame, PFileIO self, Object posobj,
                        @Shared("ft") @Cached FtruncateNode posixTruncate) {
            posixTruncate.execute(frame, self.getFD(), posobj);
            return posobj;
        }

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()"})
        static Object none(VirtualFrame frame, PFileIO self, @SuppressWarnings("unused") PNone posobj,
                        @Shared("ft") @Cached FtruncateNode posixTruncate,
                        @Cached LseekNode posixSeek) {
            Object pos = posixSeek.execute(frame, self.getFD(), 0, SEEK_CUR);
            posixTruncate.execute(frame, self.getFD(), pos);
            return pos;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!self.isClosed()", "!self.isWritable()"})
        static Object notWritable(PFileIO self, Object posobj,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "writing");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        static Object closedError(PFileIO self, Object posobj,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isCloseFD()")
        static Object simple(VirtualFrame frame, PFileIO self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callClose) {
            try {
                callClose.execute(frame, inliningTarget, PythonContext.get(inliningTarget).lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                self.setClosed();
                throw e;
            }
            self.setClosed();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "!self.isFinalizing()"})
        static Object common(VirtualFrame frame, PFileIO self,
                        @Bind("this") Node inliningTarget,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose,
                        @Shared @Cached PyErrChainExceptions chainExceptions) {
            try {
                callSuperClose.execute(frame, inliningTarget, PythonContext.get(inliningTarget).lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                try {
                    internalClose(frame, self, posixClose);
                } catch (PException ee) {
                    throw chainExceptions.execute(inliningTarget, ee, e);
                }
                throw e;
            }
            internalClose(frame, self, posixClose);
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "self.isFinalizing()"})
        static Object slow(VirtualFrame frame, PFileIO self,
                        @Bind("this") Node inliningTarget,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose,
                        @Shared @Cached PyErrChainExceptions chainExceptions) {
            PException rawIOException = null;
            PythonContext context = PythonContext.get(inliningTarget);
            try {
                callSuperClose.execute(frame, inliningTarget, context.lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                rawIOException = e;
            }
            try {
                deallocWarn(frame, self, warnNode, PythonLanguage.get(inliningTarget), context);
            } catch (PException e) {
                // ignore
            }
            try {
                internalClose(frame, self, posixClose);
            } catch (PException ee) {
                if (rawIOException != null) {
                    throw chainExceptions.execute(inliningTarget, ee, rawIOException);
                } else {
                    throw ee;
                }
            }
            if (rawIOException != null) {
                throw rawIOException;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryBuiltinNode {
        protected static boolean isUnknown(PFileIO self) {
            return self.getSeekable() < 0;
        }

        @Specialization(guards = {"!self.isClosed()", "isUnknown(self)"})
        Object unknown(PFileIO self,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            try {
                posixLib.lseek(getPosixSupport(), self.getFD(), 0, mapPythonSeekWhenceToPosix(SEEK_CUR));
                self.setSeekable(1);
                return true;
            } catch (PosixException e) {
                self.setSeekable(0);
                // pass through as CPython clears the exception.
            }
            return false;
        }

        @Specialization(guards = {"!self.isClosed()", "!isUnknown(self)"})
        static Object known(PFileIO self) {
            return self.getSeekable() == 1;
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object readable(PFileIO self) {
            return self.isReadable();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object writable(PFileIO self) {
            return self.isWritable();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object fileno(PFileIO self) {
            return self.getFD();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        boolean isatty(@SuppressWarnings("unused") PFileIO self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                return posixLib.isatty(getPosixSupport(), self.getFD());
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = "self.isClosed()")
        static boolean closedError(@SuppressWarnings("unused") PFileIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J__DEALLOC_WARN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object deallocWarn(VirtualFrame frame, PFileIO self,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            FileIOBuiltins.deallocWarn(frame, self, warnNode, getLanguage(), getContext());
            return PNone.NONE;
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.getFD() < 0;
        }
    }

    @Builtin(name = J_CLOSEFD, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CloseFDNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.isCloseFD();
        }
    }

    @Builtin(name = J_MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryBuiltinNode {

        public static final TruffleString T_XB = tsLiteral("xb");
        public static final TruffleString T_XBP = tsLiteral("xb+");
        public static final TruffleString T_AB = tsLiteral("ab");
        public static final TruffleString T_ABP = tsLiteral("ab+");
        public static final TruffleString T_RB = tsLiteral("rb");
        public static final TruffleString T_RBP = tsLiteral("rb+");
        public static final TruffleString T_WB = tsLiteral("wb");

        static TruffleString modeString(PFileIO self) {
            if (self.isCreated()) {
                return self.isReadable() ? T_XBP : T_XB;
            }
            if (self.isAppending()) {
                return self.isReadable() ? T_ABP : T_AB;
            } else if (self.isReadable()) {
                return self.isWritable() ? T_RBP : T_RB;
            }
            return T_WB;
        }

        @Specialization
        static TruffleString doit(PFileIO self) {
            return modeString(self);
        }
    }

    @Builtin(name = J__BLKSIZE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class BlksizeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.getBlksize();
        }

        @Specialization(guards = "!isNoValue(v)")
        static Object doit(VirtualFrame frame, PFileIO self, Object v,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            self.setBlksize(asSizeNode.executeExact(frame, inliningTarget, v));
            return PNone.NONE;
        }
    }

    @Builtin(name = J__FINALIZING, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.isFinalizing();
        }

        @Specialization(guards = "!isNoValue(v)")
        static Object doit(VirtualFrame frame, PFileIO self, Object v,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setFinalizing(isTrueNode.execute(frame, inliningTarget, v));
            return PNone.NONE;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        private static final TruffleString T_CLOSED = tsLiteral("<_io.FileIO [closed]>");

        @Specialization(guards = "self.isClosed()")
        static TruffleString doit(@SuppressWarnings("unused") PFileIO self) {
            return T_CLOSED;
        }

        @Specialization(guards = "!self.isClosed()")
        static TruffleString doit(VirtualFrame frame, PFileIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupName,
                        @Cached("create(Repr)") LookupAndCallUnaryNode repr,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString mode = ModeNode.modeString(self);
            TruffleString closefd = self.isCloseFD() ? T_TRUE : T_FALSE;
            Object nameobj = lookupName.execute(frame, inliningTarget, self, T_NAME);
            if (nameobj instanceof PNone) {
                return simpleTruffleStringFormatNode.format("<_io.FileIO fd=%d mode='%s' closefd=%s>", self.getFD(), mode, closefd);
            }
            if (!PythonContext.get(inliningTarget).reprEnter(self)) {
                throw raiseNode.get(inliningTarget).raise(RuntimeError, REENTRANT_CALL_INSIDE_P_REPR, self);
            }
            try {
                TruffleString name = castToTruffleStringNode.execute(inliningTarget, repr.executeObject(frame, nameobj));
                return simpleTruffleStringFormatNode.format("<_io.FileIO name=%s mode='%s' closefd=%s>", name, mode, closefd);
            } finally {
                PythonContext.get(inliningTarget).reprLeave(self);
            }
        }
    }
}
