/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.STRICT;
import static com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.mapPythonSeekWhenceToPosix;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins.BUFSIZ;
import static com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins.DEFAULT_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSEFD;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READALL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._BLKSIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.IONodes._FINALIZING;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EAGAIN;
import static com.oracle.graal.python.nodes.BuiltinNames.OPEN;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_MODE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_USE_CLOSEFD;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_BYTE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_INT_FROM_OPENER;
import static com.oracle.graal.python.nodes.ErrorMessages.FILE_NOT_OPEN_FOR_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_MODE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.OPENER_RETURNED_D;
import static com.oracle.graal.python.nodes.ErrorMessages.UNBOUNDED_READ_RETURNED_MORE_BYTES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode.chainExceptions;
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
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
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

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

    public abstract static class FileIOInit extends PNodeWithRaise {

        @Child private PConstructAndRaiseNode constructAndRaiseNode;

        public abstract void execute(VirtualFrame frame, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener);

        private static void errorCleanup(VirtualFrame frame, PFileIO self, boolean fdIsOwn,
                        PosixModuleBuiltins.CloseNode posixClose) {
            if (!fdIsOwn) {
                self.setClosed();
            } else {
                internalClose(frame, self, posixClose);
            }
        }

        private int open(VirtualFrame frame, String name, int flags, int mode,
                        PythonContext ctxt,
                        PosixSupportLibrary posixLib,
                        GilNode gil,
                        BranchProfile errorProfile) {
            Object path = posixLib.createPathFromString(ctxt.getPosixSupport(), name);
            if (path == null) {
                throw raise(ValueError, EMBEDDED_NULL_BYTE);
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
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(this);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e, name);
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

        public static void internalInit(PFileIO self, String name, int fd, String mode) {
            self.setCloseFD(false);
            self.setFD(fd, null);
            processMode(self, IONodes.IOMode.create(mode));
            self.setBlksize(DEFAULT_BUFFER_SIZE);
            WriteAttributeToObjectNode.getUncached().execute(self, NAME, name);
        }

        protected final Object getPosixSupport() {
            return getContext().getPosixSupport();
        }

        @Specialization(guards = {"!isBadMode(mode)", "!isInvalidMode(mode)"})
        void doInit(VirtualFrame frame, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached CallNode callOpener,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached IONodes.CastOpenNameNode castOpenNameNode,
                        @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached SetAttributeNode.Dynamic setAttr,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile errorProfile,
                        @Cached GilNode gil) {
            if (self.getFD() >= 0) {
                if (self.isCloseFD()) {
                    /* Have to close the existing file first. */
                    internalClose(frame, self, posixClose);
                }
            }
            self.setClosed();

            int fd = -1;
            String name = null;
            Object o = castOpenNameNode.execute(frame, nameobj);
            if (o instanceof String) {
                name = (String) o;
            } else {
                fd = (int) o;
            }

            int flags = processMode(self, mode);
            auditNode.audit(OPEN, nameobj, mode.mode, flags);

            try {
                boolean fdIsOwn = false;
                PythonContext ctxt = getContext();
                if (fd >= 0) {
                    self.setCloseFD(closefd);
                    self.setFD(fd, ctxt);
                } else {
                    self.setCloseFD(true);
                    if (errorProfile.profile(!closefd)) {
                        throw raise(ValueError, CANNOT_USE_CLOSEFD);
                    }

                    if (opener instanceof PNone) {
                        self.setFD(open(frame, name, flags, 0666, ctxt, posixLib, gil, exceptionProfile), ctxt);
                    } else {
                        Object fdobj = callOpener.execute(frame, opener, nameobj, flags);
                        if (!indexCheckNode.execute(fdobj)) {
                            throw raise(TypeError, EXPECTED_INT_FROM_OPENER);
                        }

                        self.setFD(asSizeNode.executeExact(frame, fdobj), ctxt);
                        if (self.getFD() < 0) {
                            /*
                             * The opener returned a negative but didn't set an exception. See issue
                             * #27066
                             */
                            throw raise(ValueError, OPENER_RETURNED_D, self.getFD());
                        }
                    }
                    try {
                        posixLib.setInheritable(ctxt.getPosixSupport(), self.getFD(), false);
                    } catch (PosixException e) {
                        exceptionProfile.enter();
                        throw raiseOSErrorFromPosixException(frame, e);
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
                    if (errorProfile.profile(PosixSupportLibrary.isDIR(fstatResult[0]))) {
                        errorCleanup(frame, self, fdIsOwn, posixClose);
                        name = name == null ? Integer.toString(fd) : name;
                        throw raiseOSError(frame, OSErrorEnum.EISDIR, name);
                    }
                    /*
                     * TODO: read fstatResult.st_blksize if (fstatResult[8] > 1)
                     * self.setBlksize(fstatResult[8]); }
                     */
                } catch (PosixException e) {
                    exceptionProfile.enter();
                    /*
                     * Tolerate fstat() errors other than EBADF. See Issue #25717, where an
                     * anonymous file on a Virtual Box shared folder filesystem would raise ENOENT.
                     */
                    if (e.getErrorCode() == OSErrorEnum.EBADF.getNumber()) {
                        errorCleanup(frame, self, fdIsOwn, posixClose);
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
                setAttr.execute(frame, self, NAME, nameobj);

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
                        exceptionProfile.enter();
                        if (self.getSeekable() < 0) {
                            self.setSeekable(0);
                        }
                        if (e.getErrorCode() != OSErrorEnum.ESPIPE.getNumber()) {
                            errorCleanup(frame, self, fdIsOwn, posixClose);
                            throw raiseOSErrorFromPosixException(frame, e);
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
        void invalidMode(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object nameobj, IONodes.IOMode mode, @SuppressWarnings("unused") boolean closefd,
                        @SuppressWarnings("unused") Object opener) {
            throw raise(ValueError, INVALID_MODE_S, mode.mode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isBadMode(mode)")
        void badMode(PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener) {
            throw raise(ValueError, BAD_MODE);
        }

        protected PConstructAndRaiseNode getConstructAndRaiseNode() {
            if (constructAndRaiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructAndRaiseNode = insert(PConstructAndRaiseNode.create());
            }
            return constructAndRaiseNode;
        }

        private PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e) {
            return getConstructAndRaiseNode().raiseOSError(frame, e.getErrorCode(), e.getMessage(), null, null);
        }

        private PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
            return getConstructAndRaiseNode().raiseOSError(frame, oserror, filename);
        }

        private PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e, Object filename1) {
            return getConstructAndRaiseNode().raiseOSError(frame, e.getErrorCode(), e.getMessage(), filename1, null);
        }
    }

    // FileIO(name, mode='r', closefd=True, opener=None)
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "name", "mode", "closefd", "opener"})
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
        protected static PNone doInit(VirtualFrame frame, PFileIO self, Object nameobj, IONodes.IOMode mode, boolean closefd, Object opener,
                        @Cached FileIOInit fileIOInit) {
            fileIOInit.execute(frame, self, nameobj, mode, closefd, opener);
            return PNone.NONE;
        }

    }

    @Builtin(name = READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
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
        Object none(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size) {
            return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()", "size >= 0"})
        Object read(VirtualFrame frame, PFileIO self, int size,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached BranchProfile readErrorProfile,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached BranchProfile exceptionProfile,
                        @Cached GilNode gil) {
            try {
                return posixRead.read(self.getFD(), size, posixLib, readErrorProfile, gil);
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                exceptionProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = {"!self.isClosed()", "!self.isReadable()"})
        Object notReadable(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "reading");
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") int size) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = READALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadallNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!self.isClosed()")
        Object readall(VirtualFrame frame, PFileIO self,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached BranchProfile readErrorProfile,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached BranchProfile multipleReadsProfile,
                        @Cached BranchProfile exceptionProfile,
                        @Cached GilNode gil) {
            int bufsize = SMALLCHUNK;
            boolean mayBeQuick = false;
            try {
                long pos = posixLib.lseek(getPosixSupport(), self.getFD(), 0L, mapPythonSeekWhenceToPosix(SEEK_CUR));
                long[] status = posixLib.fstat(getPosixSupport(), self.getFD());
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
                b = posixRead.read(self.getFD(), bufsize, posixLib, readErrorProfile, gil);
                bytesRead = b.getSequenceStorage().length();
                if (bytesRead == 0 || (mayBeQuick && bytesRead == bufsize - 1)) {
                    return b;
                }
            } catch (PosixException e) {
                exceptionProfile.enter();
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                throw raiseOSErrorFromPosixException(frame, e);
            }

            multipleReadsProfile.enter();
            byte[] buffer = getBytes.execute(b.getSequenceStorage());
            ByteArrayOutputStream result = createOutputStream();
            append(result, buffer, bytesRead);

            while (true) {
                if (bytesRead >= bufsize) {
                    // see CPython's function 'fileio.c: new_buffersize'
                    bufsize = bytesRead + Math.max(SMALLCHUNK, bytesRead + 256);
                    if (bufsize <= 0) {
                        throw raise(OverflowError, UNBOUNDED_READ_RETURNED_MORE_BYTES);
                    }
                }

                int n;
                try {
                    b = posixRead.read(self.getFD(), bufsize - bytesRead, posixLib, readErrorProfile, gil);
                    /*
                     * PosixModuleBuiltins#ReadNode creates PBytes with exact size;
                     */
                    buffer = getBytes.execute(b.getSequenceStorage());
                    n = buffer.length;
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
                    exceptionProfile.enter();
                    throw raiseOSErrorFromPosixException(frame, e);
                }

                append(result, buffer, n);
                bytesRead += n;
            }

            return factory().createBytes(toByteArray(result));
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadintoNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()"})
        Object readinto(VirtualFrame frame, PFileIO self, Object buffer,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached BranchProfile readErrorProfile,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached BranchProfile exceptionProfile,
                        @Cached GilNode gil) {
            try {
                int size = bufferLib.getBufferLength(buffer);
                if (size == 0) {
                    return 0;
                }
                try {
                    PBytes data = posixRead.read(self.getFD(), size, posixLib, readErrorProfile, gil);
                    int n = bufferLib.getBufferLength(data);
                    bufferLib.readIntoBuffer(data, 0, buffer, 0, n, bufferLib);
                    return n;
                } catch (PosixException e) {
                    if (e.getErrorCode() == EAGAIN.getNumber()) {
                        return PNone.NONE;
                    }
                    exceptionProfile.enter();
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!self.isClosed()", "!self.isReadable()"})
        Object notReadable(PFileIO self, Object buffer) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "reading");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self, Object buffer) {
            throw raise(ValueError, IO_CLOSED);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.ReadintoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()", "!self.isUTF8Write()"})
        Object write(VirtualFrame frame, PFileIO self, Object data,
                        @Shared("p") @Cached PosixModuleBuiltins.WriteNode posixWrite,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("e") @Cached BranchProfile errorProfile,
                        @Shared("g") @Cached GilNode gil) {
            try {
                return posixWrite.write(self.getFD(), toBytes.execute(frame, data), toBytes.execute(frame, data).length, posixLib, errorProfile, gil);
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()", "self.isUTF8Write()"})
        Object utf8write(VirtualFrame frame, PFileIO self, Object data,
                        @Cached CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encode,
                        @Shared("p") @Cached PosixModuleBuiltins.WriteNode posixWrite,
                        @Cached CastToJavaStringNode castStr,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("e") @Cached BranchProfile errorProfile,
                        @Shared("g") @Cached GilNode gil) {
            byte[] bytes = encode.execute(castStr.execute(data), "utf-8", STRICT);
            try {
                return posixWrite.write(self.getFD(), bytes, bytes.length, posixLib, errorProfile, gil);
            } catch (PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = {"!self.isClosed()", "!self.isWritable()"})
        Object notWritable(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object buf) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "writing");
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object buf) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "pos", "whence"})
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached BranchProfile exceptionProfile) {
            try {
                gil.release(true);
                try {
                    return internalSeek(self, pos, whence, getPosixSupport(), posixLib);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                exceptionProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self, @SuppressWarnings("unused") Object pos, @SuppressWarnings("unused") int whence) {
            throw raise(ValueError, IO_CLOSED);
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

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
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
            warn.resourceWarning(frame, self, 1, "unclosed file %r", self);
            /* Spurious errors can appear at shutdown */
            /* (mq) we aren't doing WriteUnraisable as WarnNode will take care of it */
            threadState.setCurrentException(exc);
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", ""})
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
        Object notWritable(PFileIO self, Object posobj) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "writing");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self, Object posobj) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isCloseFD()")
        Object simple(VirtualFrame frame, PFileIO self,
                        @Cached PyObjectCallMethodObjArgs callClose) {
            try {
                callClose.execute(frame, getContext().lookupType(PRawIOBase), CLOSE, self);
            } catch (PException e) {
                self.setClosed();
                throw e;
            }
            self.setClosed();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "!self.isFinalizing()"})
        Object common(VirtualFrame frame, PFileIO self,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose) {
            try {
                callSuperClose.execute(frame, getContext().lookupType(PRawIOBase), CLOSE, self);
            } catch (PException e) {
                try {
                    internalClose(frame, self, posixClose);
                } catch (PException ee) {
                    chainExceptions(ee.getEscapedException(), e);
                    throw ee.getExceptionForReraise();
                }
                throw e;
            }
            internalClose(frame, self, posixClose);
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "self.isFinalizing()"})
        Object slow(VirtualFrame frame, PFileIO self,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose) {
            PException rawIOException = null;
            try {
                callSuperClose.execute(frame, getContext().lookupType(PRawIOBase), CLOSE, self);
            } catch (PException e) {
                rawIOException = e;
            }
            try {
                deallocWarn(frame, self, warnNode, getLanguage(), getContext());
            } catch (PException e) {
                // ignore
            }
            try {
                internalClose(frame, self, posixClose);
            } catch (PException ee) {
                if (rawIOException != null) {
                    chainExceptions(ee.getEscapedException(), rawIOException);
                    throw ee.getExceptionForReraise();
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

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1)
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
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object readable(PFileIO self) {
            return self.isReadable();
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object writable(PFileIO self) {
            return self.isWritable();
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object fileno(PFileIO self) {
            return self.getFD();
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
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
        boolean closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = _DEALLOC_WARN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object deallocWarn(VirtualFrame frame, PFileIO self,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            FileIOBuiltins.deallocWarn(frame, self, warnNode, getLanguage(), getContext());
            return PNone.NONE;
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.getFD() < 0;
        }
    }

    @Builtin(name = CLOSEFD, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CloseFDNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.isCloseFD();
        }
    }

    @Builtin(name = MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryBuiltinNode {

        static String modeString(PFileIO self) {
            if (self.isCreated()) {
                return self.isReadable() ? "xb+" : "xb";
            }
            if (self.isAppending()) {
                return self.isReadable() ? "ab+" : "ab";
            } else if (self.isReadable()) {
                return self.isWritable() ? "rb+" : "rb";
            }
            return "wb";
        }

        @Specialization
        static Object doit(PFileIO self) {
            return modeString(self);
        }
    }

    @Builtin(name = _BLKSIZE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class BlksizeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.getBlksize();
        }

        @Specialization(guards = "!isNoValue(v)")
        static Object doit(VirtualFrame frame, PFileIO self, Object v,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            self.setBlksize(asSizeNode.executeExact(frame, v));
            return PNone.NONE;
        }
    }

    @Builtin(name = _FINALIZING, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.isFinalizing();
        }

        @Specialization(guards = "!isNoValue(v)")
        static Object doit(VirtualFrame frame, PFileIO self, Object v,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setFinalizing(isTrueNode.execute(frame, v));
            return PNone.NONE;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isClosed()")
        static Object doit(@SuppressWarnings("unused") PFileIO self) {
            return "<_io.FileIO [closed]>";
        }

        @Specialization(guards = "!self.isClosed()")
        Object doit(VirtualFrame frame, PFileIO self,
                        @Cached PyObjectLookupAttr lookupName,
                        @Cached("create(Repr)") LookupAndCallUnaryNode repr) {
            String mode = ModeNode.modeString(self);
            String closefd = self.isCloseFD() ? "True" : "False";
            Object nameobj = lookupName.execute(frame, self, "name");
            if (nameobj instanceof PNone) {
                return PythonUtils.format("<_io.FileIO fd=%d mode='%s' closefd=%s>", self.getFD(), mode, closefd);
            } else {
                if (!getContext().reprEnter(self)) {
                    throw raise(RuntimeError, "reentrant call inside %p.__repr__", self);
                } else {
                    try {
                        Object name = repr.executeObject(frame, nameobj);
                        return PythonUtils.format("<_io.FileIO name=%s mode='%s' closefd=%s>", name, mode, closefd);
                    } finally {
                        getContext().reprLeave(self);
                    }
                }
            }
        }
    }
}
