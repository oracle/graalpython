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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_CHARACTER;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_OBJ_TYPE_S_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_MODE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.OPENER_RETURNED_D;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class IONodes {

    public static final String DETACH = "detach";
    public static final String FLUSH = "flush";
    public static final String CLOSE = "close";
    public static final String SEEKABLE = "seekable";
    public static final String READABLE = "readable";
    public static final String WRITABLE = "writable";
    public static final String FILENO = "fileno";
    public static final String ISATTY = "isatty";
    public static final String READ = "read";
    public static final String PEEK = "peek";
    public static final String READ1 = "read1";
    public static final String READINTO = "readinto";
    public static final String READINTO1 = "readinto1";
    public static final String READLINE = "readline";
    public static final String READLINES = "readlines";
    public static final String WRITELINES = "writelines";
    public static final String WRITE = "write";
    public static final String SEEK = "seek";
    public static final String TELL = "tell";
    public static final String TRUNCATE = "truncate";
    public static final String RAW = "raw";
    public static final String CLOSED = "closed";

    public static final String NAME = "name";
    public static final String MODE = "mode";
    public static final String GETBUFFER = "getbuffer";
    public static final String GETVALUE = "getvalue";
    public static final String READALL = "readall";
    public static final String CLOSEFD = "closefd";

    public static final String DECODE = "decode";
    public static final String ENCODE = "encode";

    public static final String GETSTATE = "getstate";
    public static final String SETSTATE = "setstate";

    public static final String RESET = "reset";
    public static final String NEWLINES = "newlines";
    public static final String LINE_BUFFERING = "line_buffering";

    public static final String ENCODING = "encoding";
    public static final String ERRORS = "errors";
    public static final String RECONFIGURE = "reconfigure";
    public static final String BUFFER = "buffer";
    public static final String WRITE_THROUGH = "write_through";

    public static final String _DEALLOC_WARN = "_dealloc_warn";
    public static final String _FINALIZING = "_finalizing";
    public static final String _BLKSIZE = "_blksize";
    public static final String __IOBASE_CLOSED = "__IOBase_closed";
    public static final String _CHECKCLOSED = "_checkClosed";
    public static final String _CHECKSEEKABLE = "_checkSeekable";
    public static final String _CHECKREADABLE = "_checkReadable";
    public static final String _CHECKWRITABLE = "_checkWritable";
    public static final String _CHUNK_SIZE = "_CHUNK_SIZE";

    @CompilerDirectives.ValueType
    public static final class IOMode {
        boolean creating;
        boolean reading;
        boolean writing;
        boolean appending;
        boolean updating;

        boolean text;
        boolean binary;
        boolean universal;

        boolean isInvalid;

        int xrwa = 0;
        boolean isBad;
        boolean hasNil;

        final String mode;

        IOMode(String mode) {
            this.mode = mode;
        }

        static IOMode create(String mode) {
            IOMode m = new IOMode(mode);
            return m.decode();
        }

        IOMode decode() {
            /* Decode mode */
            int flags = 0;
            for (char c : PString.toCharArray(mode)) {
                int current;
                switch (c) {
                    case 'x':
                        current = 2;
                        creating = true;
                        break;
                    case 'r':
                        current = 4;
                        reading = true;
                        break;
                    case 'w':
                        current = 8;
                        writing = true;
                        break;
                    case 'a':
                        current = 16;
                        appending = true;
                        break;
                    case '+':
                        current = 32;
                        updating = true;
                        break;
                    case 't':
                        current = 64;
                        text = true;
                        break;
                    case 'b':
                        current = 128;
                        binary = true;
                        break;
                    case 'U':
                        current = 256;
                        universal = true;
                        reading = true;
                        break;
                    case '\0':
                        hasNil = true;
                        return this;
                    default:
                        isInvalid = true;
                        return this;
                }
                /* c must not be duplicated */
                if ((flags & current) > 0) {
                    isBad = true;
                    return this;
                }
                flags |= current;
            }
            xrwa += isSet(creating);
            xrwa += isSet(reading);
            xrwa += isSet(writing);
            xrwa += isSet(appending);
            return this;
        }

        IOMode read() {
            reading = true;
            xrwa = 1;
            return this;
        }

        private static int isSet(boolean b) {
            return b ? 1 : 0;
        }

        public static boolean isInvalidMode(IONodes.IOMode mode) {
            return mode.isInvalid;
        }

        public static boolean isBadMode(IONodes.IOMode mode) {
            return mode.isBad || isXRWA(mode);
        }

        public static boolean isValidUniveral(IONodes.IOMode mode) {
            if (mode.universal) {
                return !mode.creating && !mode.writing && !mode.appending && !mode.updating;
            }
            return true;
        }

        public static boolean isXRWA(IONodes.IOMode mode) {
            return mode.xrwa > 1;
        }

        public static boolean isUnknown(IONodes.IOMode mode) {
            return mode.xrwa == 0 && !mode.updating;
        }

        public static boolean isTB(IONodes.IOMode mode) {
            return mode.text && isBinary(mode);
        }

        public static boolean isBinary(IONodes.IOMode mode) {
            return mode.binary;
        }
    }

    public abstract static class CreateIOModeNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {

        protected final boolean warnUniversal;

        protected CreateIOModeNode(boolean warnUniversal) {
            this.warnUniversal = warnUniversal;
        }

        @Override
        public abstract IOMode execute(VirtualFrame frame, Object mode);

        public static boolean isFast(Object obj) {
            return obj instanceof PNone || obj instanceof IOMode;
        }

        @Specialization
        static IOMode none(@SuppressWarnings("unused") PNone none) {
            return new IOMode("r").read();
        }

        @Specialization
        static IOMode done(IOMode mode) {
            return mode;
        }

        @Specialization
        IOMode string(VirtualFrame frame, String mode,
                        @Cached ConditionProfile errProfile,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            IOMode m = new IOMode(mode).decode();
            if (errProfile.profile(m.hasNil)) {
                throw raise(ValueError, EMBEDDED_NULL_CHARACTER);
            }
            if (errProfile.profile(m.isInvalid)) {
                throw raise(ValueError, INVALID_MODE_S, mode);
            }
            if (errProfile.profile(warnUniversal && m.universal)) {
                warnNode.warnEx(frame, DeprecationWarning, "'U' mode is deprecated", 1);
            }
            return m;
        }

        @Specialization(guards = "!isFast(mode)", replaces = "string")
        IOMode generic(VirtualFrame frame, Object mode,
                        @Cached CastToJavaStringNode toString,
                        @Cached ConditionProfile errProfile,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            try {
                return string(frame, toString.execute(mode), errProfile, warnNode);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
        }

        @ClinicConverterFactory
        public static CreateIOModeNode create(boolean warnUniversal) {
            return IONodesFactory.CreateIOModeNodeGen.create(warnUniversal);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class CastOpenNameNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {

        public static final int MAX = Integer.MAX_VALUE;

        @Override
        public abstract Object execute(VirtualFrame frame, Object name);

        @Specialization(guards = "fd >= 0")
        static int fast(int fd) {
            return fd;
        }

        @Specialization(guards = {"fd >= 0", "fd <= MAX"})
        static int fast(long fd) {
            return (int) fd;
        }

        @Specialization(guards = "!isInteger(nameobj)")
        Object generic(VirtualFrame frame, Object nameobj,
                        @Cached BytesNodes.DecodeUTF8FSPathNode fspath,
                        @Cached ConditionProfile errorProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(nameobj)) {
                int fd = asSizeNode.executeExact(frame, nameobj);
                if (errorProfile.profile(fd < 0)) {
                    err(fd);
                }
                return fd;
            } else {
                return fspath.execute(frame, nameobj);
            }
        }

        @Specialization(guards = "fd < 0")
        int err(int fd) {
            throw raise(ValueError, OPENER_RETURNED_D, fd);
        }

        @Specialization(guards = "fd < 0")
        int err(long fd) {
            throw raise(ValueError, OPENER_RETURNED_D, fd);
        }

        @ClinicConverterFactory
        public static CastOpenNameNode create() {
            return IONodesFactory.CastOpenNameNodeGen.create();
        }
    }

    public abstract static class CreateBufferedIONode extends Node {
        public abstract PBuffered execute(VirtualFrame frame, PFileIO fileIO, int buffering, PythonObjectFactory factory, IONodes.IOMode mode);

        protected static boolean isRandom(IONodes.IOMode mode) {
            return mode.updating;
        }

        protected static boolean isWriting(IONodes.IOMode mode) {
            return mode.creating || mode.writing || mode.appending;
        }

        protected static boolean isReading(IONodes.IOMode mode) {
            return mode.reading;
        }

        @Specialization(guards = "isRandom(mode)")
        static PBuffered createRandom(VirtualFrame frame, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedRandomBuiltins.BufferedRandomInit initBuffered) {
            PBuffered buffer = factory.createBufferedRandom(PBufferedRandom);
            initBuffered.execute(frame, buffer, fileIO, buffering, factory);
            return buffer;
        }

        @Specialization(guards = {"!isRandom(mode)", "isWriting(mode)"})
        static PBuffered createWriter(VirtualFrame frame, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedWriterBuiltins.BufferedWriterInit initBuffered) {
            PBuffered buffer = factory.createBufferedWriter(PBufferedWriter);
            initBuffered.execute(frame, buffer, fileIO, buffering, factory);
            return buffer;
        }

        @Specialization(guards = {"!isRandom(mode)", "!isWriting(mode)", "isReading(mode)"})
        static PBuffered createWriter(VirtualFrame frame, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedReaderBuiltins.BufferedReaderInit initBuffered) {
            PBuffered buffer = factory.createBufferedReader(PBufferedReader);
            initBuffered.execute(frame, buffer, fileIO, buffering, factory);
            return buffer;
        }
    }

    public abstract static class ToStringNode extends PNodeWithRaise {
        public abstract String execute(Object str);

        public static boolean isString(Object s) {
            return s instanceof String;
        }

        @Specialization
        static String string(String s) {
            return s;
        }

        @Specialization(guards = "!isString(s)")
        String str(Object s,
                        @Cached CastToJavaStringNode str) {
            try {
                return str.execute(s);
            } catch (CannotCastException e) {
                throw raise(TypeError, EXPECTED_OBJ_TYPE_S_GOT_P, "str", s);
            }
        }
    }
}
