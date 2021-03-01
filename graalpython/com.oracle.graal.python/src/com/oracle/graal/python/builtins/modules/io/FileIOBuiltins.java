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
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.append;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.createStream;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.toByteArray;
import static com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins.BUFSIZ;
import static com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins.DEFAULT_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EAGAIN;
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
import static com.oracle.graal.python.runtime.PosixSupportLibrary.DEFAULT_DIR_FD;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_APPEND;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_CREAT;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_EXCL;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_RDONLY;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_RDWR;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_TRUNC;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.O_WRONLY;
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
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFileIO)
public class FileIOBuiltins extends PythonBuiltins {

    /*
     * We are limited to max primitive array size, Integer.MAX_VALUE, the jdk can offer. CPython
     * defines the max value as system's SSIZE_T_MAX on linux and INT_MAX for MacOS and Windows.
     */
    private static final int MAX_SIZE = SysModuleBuiltins.MAXSIZE;
    public static final int READ_MAX = MAX_SIZE;

    private static final int SMALLCHUNK = BUFSIZ;

    static final String READ = "read";
    static final String READALL = "readall";
    static final String READINTO = "readinto";
    static final String WRITE = "write";
    static final String SEEK = "seek";
    static final String TELL = "tell";
    static final String TRUNCATE = "truncate";

    static final String CLOSE = "close";
    static final String SEEKABLE = "seekable";
    static final String READABLE = "readable";
    static final String WRITABLE = "writable";
    static final String FILENO = "fileno";
    static final String ISATTY = "isatty";
    static final String _DEALLOC_WARN = "_dealloc_warn";

    static final String CLOSED = "closed";
    static final String CLOSEFD = "closefd";
    static final String MODE = "mode";

    static final String BLKSIZE = "_blksize";
    static final String FINALIZING = "_finalizing";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FileIOBuiltinsFactory.getFactories();
    }

    static class FDReleaseCallback implements AsyncHandler.AsyncAction {
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
            posixClose.call(frame, fd);
        }
    }

    // FileIO(name, mode='r', closefd=True, opener=None)
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "name", "mode", "closefd", "opener"})
    // "mode" should not have `null` character
    @ArgumentClinic(name = "mode", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"r\"", useDefaultForNone = true)
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        void errorCleanup(VirtualFrame frame, PFileIO self, boolean fdIsOwn,
                        PosixModuleBuiltins.CloseNode posixClose) {
            if (!fdIsOwn) {
                self.setClosed();
            } else {
                internalClose(frame, self, posixClose);
            }
        }

        int open(VirtualFrame frame, String name, int flags, int mode,
                        PosixSupportLibrary posixLib,
                        BranchProfile errorProfile) {
            Object path = posixLib.createPathFromString(getPosixSupport(), name);
            if (path == null) {
                throw raise(ValueError, EMBEDDED_NULL_BYTE);
            }
            while (true) {
                try {
                    return posixLib.openat(getPosixSupport(), DEFAULT_DIR_FD, path, flags, mode);
                } catch (PosixSupportLibrary.PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e, name);
                    }
                }
            }
        }

        void badMode() {
            throw raise(ValueError, BAD_MODE);
        }

        @Specialization(limit = "2")
        public PNone doInit(VirtualFrame frame, PFileIO self, Object nameobj, String mode, boolean closefd, Object opener,
                        @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary("opener") PythonObjectLibrary libOpener,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @CachedLibrary("nameobj") PythonObjectLibrary asInt,
                        @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached BytesNodes.DecodeUTF8FSPathNode fspath,
                        @Cached SetAttributeNode.Dynamic setAttr,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile errorProfile) {
            if (self.getFD() >= 0) {
                if (self.isCloseFD()) {
                    /* Have to close the existing file first. */
                    internalClose(frame, self, posixClose);
                }
            }
            self.setClosed();

            int fd = -1;
            String name = null;
            if (asInt.canBePInt(nameobj)) {
                fd = asInt.asSize(nameobj);
                if (errorProfile.profile(fd < 0)) {
                    throw raise(ValueError, OPENER_RETURNED_D);
                }
            } else {
                name = fspath.execute(frame, nameobj);
            }

            int flags = 0;
            boolean rwa = false, plus = false;
            for (char s : mode.toCharArray()) {
                switch (s) {
                    case 'x':
                        if (rwa) {
                            badMode();
                        }
                        rwa = true;
                        self.setCreated();
                        self.setWritable();
                        flags |= O_EXCL | O_CREAT;
                        break;
                    case 'r':
                        if (rwa) {
                            badMode();
                        }
                        rwa = true;
                        self.setReadable();
                        break;
                    case 'w':
                        if (rwa) {
                            badMode();
                        }
                        rwa = true;
                        self.setWritable();
                        flags |= O_CREAT | O_TRUNC;
                        break;
                    case 'a':
                        if (rwa) {
                            badMode();
                        }
                        rwa = true;
                        self.setWritable();
                        self.setAppending();
                        flags |= O_APPEND | O_CREAT;
                        break;
                    case 'b':
                        break;
                    case '+':
                        if (plus) {
                            badMode();
                        }
                        self.setWritable();
                        self.setReadable();
                        plus = true;
                        break;
                    default:
                        throw raise(ValueError, INVALID_MODE_S, mode);
                }
            }
            if (!rwa) {
                badMode();
            }

            if (self.isReadable() && self.isWritable()) {
                flags |= O_RDWR;
            } else {
                flags |= self.isReadable() ? O_RDONLY : O_WRONLY;
            }

            auditNode.audit("open", nameobj, mode, flags);

            boolean fdIsOwn = false;
            if (fd >= 0) {
                self.setCloseFD(closefd);
                self.setFD(fd, ctxt);
            } else {
                self.setCloseFD(true);
                if (errorProfile.profile(!closefd)) {
                    throw raise(ValueError, CANNOT_USE_CLOSEFD);
                }

                if (opener instanceof PNone) {
                    self.setFD(open(frame, name, flags, 0666, posixLib, exceptionProfile), ctxt);
                } else {
                    Object fdobj = libOpener.callObject(opener, frame, nameobj, flags);
                    if (!lib.canBePInt(fdobj)) {
                        throw raise(TypeError, EXPECTED_INT_FROM_OPENER);
                    }

                    self.setFD(lib.asSize(fdobj), ctxt);
                    if (self.getFD() < 0) {
                        /*
                         * The opener returned a negative but didn't set an exception. See issue
                         * #27066
                         */
                        throw raise(ValueError, OPENER_RETURNED_D, self.getFD());
                    }
                }
                try {
                    posixLib.setInheritable(getPosixSupport(), self.getFD(), false);
                } catch (PosixSupportLibrary.PosixException e) {
                    exceptionProfile.enter();
                    throw raiseOSErrorFromPosixException(frame, e);
                }
                fdIsOwn = true;
            }
            self.setBlksize(DEFAULT_BUFFER_SIZE);
            try {
                long[] fstatResult = posixLib.fstat(getPosixSupport(), self.getFD());
                /*
                 * On Unix, open will succeed for directories. In Python, there should be no file
                 * objects referring to directories, so we need a check.
                 */
                if (errorProfile.profile(PosixSupportLibrary.isDIR(fstatResult[0]))) {
                    errorCleanup(frame, self, fdIsOwn, posixClose);
                    name = name == null ? Integer.toString(fd) : name;
                    throw raiseOSError(frame, OSErrorEnum.EISDIR, name);
                }
                /*
                 * // TODO: read fstatResult.st_blksize if (fstatResult[8] > 1)
                 * self.setBlksize(fstatResult[8]); }
                 */
            } catch (PosixSupportLibrary.PosixException e) {
                exceptionProfile.enter();
                /*
                 * Tolerate fstat() errors other than EBADF. See Issue #25717, where an anonymous
                 * file on a Virtual Box shared folder filesystem would raise ENOENT.
                 */
                if (e.getErrorCode() == OSErrorEnum.EBADF.getNumber()) {
                    errorCleanup(frame, self, fdIsOwn, posixClose);
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            }
            setAttr.execute(frame, self, "name", nameobj);

            if (self.isAppending()) {
                /*
                 * For consistent behaviour, we explicitly seek to the end of file (otherwise, it
                 * might be done only on the first write()).
                 */
                try {
                    long res = posixLib.lseek(getPosixSupport(), self.getFD(), 0, SEEK_END);
                    self.setSeekable(res >= 0 ? 1 : 0);
                } catch (PosixSupportLibrary.PosixException e) {
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
        Object readall(VirtualFrame frame, PFileIO self, @SuppressWarnings("unused") int size,
                        @Cached ReadallNode readallNode) {
            return readallNode.call(frame, self);
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
                        @Cached BranchProfile exceptionProfile) {
            try {
                return posixRead.read(frame, self.getFD(), size, posixLib, readErrorProfile);
            } catch (PosixSupportLibrary.PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                exceptionProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!self.isClosed()", "!self.isReadable()"})
        Object notReadable(PFileIO self, int size) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "reading");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self, int size) {
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
                        @Cached BranchProfile exceptionProfile) {
            int bufsize = SMALLCHUNK;
            try {
                long pos = posixLib.lseek(getPosixSupport(), self.getFD(), 0L, SEEK_CUR);
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
                }
            } catch (PosixSupportLibrary.PosixException e) {
                // ignore
            }

            ByteArrayOutputStream result = createStream();
            byte[] buffer;
            int bytesRead = 0;
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
                    PBytes b = posixRead.read(frame, self.getFD(), bufsize - bytesRead, posixLib, readErrorProfile);
                    /*
                     * PosixModuleBuiltins#ReadNode creates PBytes with exact size;
                     */
                    buffer = getBytes.execute(b.getSequenceStorage());
                    n = buffer.length;
                    if (n == 0) {
                        break;
                    }
                } catch (PosixSupportLibrary.PosixException e) {
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

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadintoNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isReadable()"})
        Object readinto(VirtualFrame frame, PFileIO self, Object buffer,
                        @Cached PosixModuleBuiltins.ReadNode posixRead,
                        @Cached BranchProfile readErrorProfile,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.BytesMemcpyNode memcpyNode,
                        @Cached("createReadIntoArg()") BytesNodes.GetByteLengthIfWritableNode getLen,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached BranchProfile exceptionProfile) {
            int size = getLen.execute(frame, buffer);
            if (size == 0) {
                return 0;
            }
            try {
                PBytes data = posixRead.read(frame, self.getFD(), size, posixLib, readErrorProfile);
                byte[] buf = getBytes.execute(data.getSequenceStorage());
                int n = buf.length;
                memcpyNode.execute(frame, buffer, 0, buf, 0, n);
                return n;
            } catch (PosixSupportLibrary.PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                exceptionProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
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
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()"})
        Object write(VirtualFrame frame, PFileIO self, Object data,
                        @Cached PosixModuleBuiltins.WriteNode posixWrite,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            try {
                return posixWrite.write(frame, self.getFD(), toBytes.execute(data), posixLib, errorProfile);
            } catch (PosixSupportLibrary.PosixException e) {
                if (e.getErrorCode() == EAGAIN.getNumber()) {
                    return PNone.NONE;
                }
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!self.isClosed()", "!self.isWritable()"})
        Object notWritable(PFileIO self, Object buf) {
            throw raise(IOUnsupportedOperation, FILE_NOT_OPEN_FOR_S, "writing");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self, Object buf) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$pos", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FileIOBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!self.isClosed()", limit = "2")
        Object seek(VirtualFrame frame, PFileIO self, Object posobj, int whence,
                        @CachedLibrary("posobj") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached BranchProfile exceptionProfile) {
            long pos = lib.asJavaLong(posobj, frame);
            try {
                long res = posixLib.lseek(getPosixSupport(), self.getFD(), pos, whence);
                if (self.getSeekable() < 0) {
                    self.setSeekable(1);
                }
                return res;
            } catch (PosixSupportLibrary.PosixException e) {
                exceptionProfile.enter();
                if (self.getSeekable() < 0) {
                    self.setSeekable(0);
                }
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self, Object pos, int whence) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object tell(VirtualFrame frame, PFileIO self,
                        @Cached SeekNode seekNode) {
            return seekNode.call(frame, self, 0, SEEK_CUR);
        }
    }

    static void deallocWarn(VirtualFrame frame, PFileIO self,
                    WarningsModuleBuiltins.WarnNode warn,
                    PythonContext context) {
        if (self.getFD() >= 0 && self.isCloseFD()) {
            PException exc = context.getCurrentException();
            warn.resourceWarning(frame, self, 1, "unclosed file %r", self);
            /* Spurious errors can appear at shutdown */
            /* (mq) we aren't doing WriteUnraisable as WarnNode will take care of it */
            context.setCurrentException(exc);
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", ""})
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()", "!isPNone(posobj)"})
        Object num(VirtualFrame frame, PFileIO self, Object posobj,
                        @Shared("ft") @Cached PosixModuleBuiltins.FtruncateNode posixTruncate) {
            return posixTruncate.call(frame, self.getFD(), posobj);
        }

        @Specialization(guards = {"!self.isClosed()", "self.isWritable()"})
        Object none(VirtualFrame frame, PFileIO self, @SuppressWarnings("unused") PNone posobj,
                        @Shared("ft") @Cached PosixModuleBuiltins.FtruncateNode posixTruncate,
                        @Cached PosixModuleBuiltins.LseekNode posixSeek) {
            return posixTruncate.call(frame, self.getFD(), posixSeek.call(frame, self.getFD(), 0, SEEK_CUR));
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
                        @Shared("l") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            lib.lookupAndCallRegularMethod(getContext().getCore().lookupType(PRawIOBase), frame, CLOSE, self);
            self.setClosed();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "!self.isFinalizing()"})
        Object common(VirtualFrame frame, PFileIO self,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Shared("l") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            lib.lookupAndCallRegularMethod(getContext().getCore().lookupType(PRawIOBase), frame, CLOSE, self);
            internalClose(frame, self, posixClose);
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "self.isFinalizing()"})
        Object slow(VirtualFrame frame, PFileIO self,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Shared("l") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            lib.lookupAndCallRegularMethod(getContext().getCore().lookupType(PRawIOBase), frame, CLOSE, self);
            if (self.isFinalizing()) {
                deallocWarn(frame, self, warnNode, getContext());
            }
            internalClose(frame, self, posixClose);
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
                posixLib.lseek(getPosixSupport(), self.getFD(), 0, SEEK_CUR);
                self.setSeekable(1);
                return true;
            } catch (PosixSupportLibrary.PosixException e) {
                self.setSeekable(0);
                // pass through as CPython clears the exception.
            }
            return false;
        }

        @Specialization(guards = {"!self.isClosed()", "!isUnknown(self)"})
        Object known(PFileIO self) {
            return self.getSeekable() == 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        Object readable(PFileIO self) {
            return self.isReadable();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        Object writable(PFileIO self) {
            return self.isWritable();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isClosed()")
        Object closedError(PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        Object fileno(PFileIO self) {
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
        Object isatty(@SuppressWarnings("unused") PFileIO self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.isatty(getPosixSupport(), self.getFD());
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PFileIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = _DEALLOC_WARN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object deallocWarn(VirtualFrame frame, PFileIO self,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            FileIOBuiltins.deallocWarn(frame, self, warnNode, getContext());
            return PNone.NONE;
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(PFileIO self) {
            return self.getFD() < 0;
        }
    }

    @Builtin(name = CLOSEFD, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CloseFDNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(PFileIO self) {
            return self.isCloseFD();
        }
    }

    @Builtin(name = MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryBuiltinNode {

        public static String modeString(PFileIO self) {
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
        Object doit(PFileIO self) {
            return modeString(self);
        }
    }

    @Builtin(name = BLKSIZE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class BlksizeNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.getBlksize();
        }

        @Specialization(guards = "!isNoValue(v)", limit = "1")
        Object doit(PFileIO self, Object v,
                        @CachedLibrary("v") PythonObjectLibrary toInt) {
            self.setBlksize(toInt.asSize(v));
            return PNone.NONE;
        }
    }

    @Builtin(name = FINALIZING, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        Object doit(PFileIO self, @SuppressWarnings("unused") PNone v) {
            return self.isFinalizing();
        }

        @Specialization(guards = "!isNoValue(v)", limit = "1")
        Object doit(PFileIO self, Object v,
                        @CachedLibrary("v") PythonObjectLibrary isTrue) {
            self.setFinalizing(isTrue.isTrue(v));
            return PNone.NONE;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isClosed()")
        Object doit(@SuppressWarnings("unused") PFileIO self) {
            return "<_io.FileIO [closed]>";
        }

        @Specialization(guards = "!self.isClosed()")
        Object doit(VirtualFrame frame, PFileIO self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libSelf,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr) {
            String mode = ModeNode.modeString(self);
            String closefd = self.isCloseFD() ? "True" : "False";
            Object nameobj = libSelf.lookupAttribute(self, frame, "name");
            if (nameobj instanceof PNone) {
                return PythonUtils.format("<_io.FileIO fd=%d mode='%s' closefd=%s>", self.getFD(), mode, closefd);
            } else {
                if (!getContext().reprEnter(self)) {
                    throw raise(RuntimeError, "reentrant call inside %p.__repr__", libSelf.getLazyPythonClass(self));
                } else {
                    Object name = repr.executeObject(frame, nameobj);
                    getContext().reprLeave(self);
                    return PythonUtils.format("<_io.FileIO name=%s mode='%s' closefd=%s>", name, mode, closefd);
                }
            }
        }
    }
}
