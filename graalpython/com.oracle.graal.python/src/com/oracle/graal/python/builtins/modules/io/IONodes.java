/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public class IONodes {

    public static final String J_DETACH = "detach";
    public static final TruffleString T_DETACH = tsLiteral(J_DETACH);

    public static final String J_FLUSH = "flush";
    public static final TruffleString T_FLUSH = tsLiteral(J_FLUSH);

    public static final String J_CLOSE = "close";
    public static final TruffleString T_CLOSE = tsLiteral(J_CLOSE);

    public static final String J_SEEKABLE = "seekable";
    public static final TruffleString T_SEEKABLE = tsLiteral(J_SEEKABLE);

    public static final String J_READABLE = StringLiterals.J_READABLE;
    public static final TruffleString T_READABLE = StringLiterals.T_READABLE;

    public static final String J_WRITABLE = StringLiterals.J_WRITABLE;
    public static final TruffleString T_WRITABLE = StringLiterals.T_WRITABLE;

    public static final String J_FILENO = "fileno";
    public static final TruffleString T_FILENO = tsLiteral(J_FILENO);

    public static final String J_ISATTY = "isatty";
    public static final TruffleString T_ISATTY = tsLiteral(J_ISATTY);

    public static final String J_READ = "read";
    public static final TruffleString T_READ = tsLiteral(J_READ);

    public static final String J_PEEK = "peek";
    public static final TruffleString T_PEEK = tsLiteral(J_PEEK);

    public static final String J_READ1 = "read1";
    public static final TruffleString T_READ1 = tsLiteral(J_READ1);

    public static final String J_READINTO = "readinto";
    public static final TruffleString T_READINTO = tsLiteral(J_READINTO);

    public static final String J_READINTO1 = "readinto1";
    public static final TruffleString T_READINTO1 = tsLiteral(J_READINTO1);

    public static final String J_READLINE = BuiltinNames.J_READLINE;
    public static final TruffleString T_READLINE = BuiltinNames.T_READLINE;

    public static final String J_READLINES = "readlines";

    public static final String J_WRITELINES = "writelines";

    public static final String J_WRITE = "write";
    public static final TruffleString T_WRITE = tsLiteral(J_WRITE);

    public static final String J_SEEK = "seek";
    public static final TruffleString T_SEEK = tsLiteral(J_SEEK);

    public static final String J_TELL = "tell";
    public static final TruffleString T_TELL = tsLiteral(J_TELL);

    public static final String J_TRUNCATE = "truncate";
    public static final TruffleString T_TRUNCATE = tsLiteral(J_TRUNCATE);

    public static final String J_RAW = "raw";

    public static final String J_CLOSED = "closed";
    public static final TruffleString T_CLOSED = tsLiteral(J_CLOSED);

    public static final String J_NAME = "name";
    public static final TruffleString T_NAME = tsLiteral(J_NAME);

    public static final String J_MODE = "mode";
    public static final TruffleString T_MODE = tsLiteral(J_MODE);

    public static final String J_GETBUFFER = "getbuffer";

    public static final String J_GETVALUE = "getvalue";

    public static final String J_READALL = "readall";
    public static final TruffleString T_READALL = tsLiteral(J_READALL);

    public static final String J_CLOSEFD = "closefd";

    public static final String J_DECODE = "decode";
    public static final TruffleString T_DECODE = tsLiteral(J_DECODE);

    public static final String J_ENCODE = "encode";
    public static final TruffleString T_ENCODE = tsLiteral(J_ENCODE);

    public static final String J_GETSTATE = "getstate";
    public static final TruffleString T_GETSTATE = tsLiteral(J_GETSTATE);

    public static final String J_SETSTATE = "setstate";
    public static final TruffleString T_SETSTATE = tsLiteral(J_SETSTATE);

    public static final String J_RESET = "reset";
    public static final TruffleString T_RESET = tsLiteral(J_RESET);

    public static final String J_NEWLINES = "newlines";
    public static final TruffleString T_NEWLINES = tsLiteral(J_NEWLINES);

    public static final String J_LINE_BUFFERING = "line_buffering";

    public static final String J_ENCODING = "encoding";
    public static final TruffleString T_ENCODING = tsLiteral(J_ENCODING);

    public static final String J_BUFFER = "buffer";
    public static final TruffleString T_BUFFER = tsLiteral(J_BUFFER);

    public static final String J_ERRORS = "errors";
    public static final String J_RECONFIGURE = "reconfigure";
    public static final String J_WRITE_THROUGH = "write_through";

    public static final String J__DEALLOC_WARN = "_dealloc_warn";
    public static final TruffleString T__DEALLOC_WARN = tsLiteral(J__DEALLOC_WARN);

    public static final String J__FINALIZING = "_finalizing";
    public static final String J__BLKSIZE = "_blksize";

    public static final String J___IOBASE_CLOSED = "__IOBase_closed";
    public static final TruffleString T___IOBASE_CLOSED = tsLiteral(J___IOBASE_CLOSED);

    public static final String J__CHECKCLOSED = "_checkClosed";
    public static final String J__CHECKSEEKABLE = "_checkSeekable";
    public static final String J__CHECKREADABLE = "_checkReadable";
    public static final String J__CHECKWRITABLE = "_checkWritable";

    public static final String J__CHUNK_SIZE = "_CHUNK_SIZE";
    public static final TruffleString T__CHUNK_SIZE = tsLiteral(J__CHUNK_SIZE);

    public static final TruffleString T_R = tsLiteral("r");
    public static final TruffleString T_W = tsLiteral("w");
    public static final TruffleString T_RB = tsLiteral("rb");
    public static final TruffleString T_WB = tsLiteral("wb");

    @CompilerDirectives.ValueType
    public static final class IOMode {
        public static final IOMode R = new IOMode(T_R, true, false, false, 1);
        public static final IOMode W = new IOMode(T_W, false, true, false, 1);
        public static final IOMode RB = new IOMode(T_RB, true, false, true, 1);
        public static final IOMode WB = new IOMode(T_WB, false, true, true, 1);

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

        final TruffleString mode;

        private IOMode(TruffleString mode, boolean reading, boolean writing, boolean binary, int xrwa) {
            this.mode = mode;
            this.reading = reading;
            this.writing = writing;
            this.binary = binary;
            this.xrwa = xrwa;
        }

        IOMode(TruffleString mode, TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode, TruffleStringIterator.NextNode nextNode) {
            this.mode = mode;
            /* Decode mode */
            int flags = 0;
            TruffleStringIterator it = createCodePointIteratorNode.execute(mode, TS_ENCODING);
            while (it.hasNext()) {
                int c = nextNode.execute(it);
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
                        return;
                    default:
                        isInvalid = true;
                        return;
                }
                /* c must not be duplicated */
                if ((flags & current) > 0) {
                    isBad = true;
                    return;
                }
                flags |= current;
            }
            xrwa += isSet(creating);
            xrwa += isSet(reading);
            xrwa += isSet(writing);
            xrwa += isSet(appending);
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

    public abstract static class CreateIOModeNode extends ArgumentCastNode {

        protected final boolean warnUniversal;

        protected CreateIOModeNode(boolean warnUniversal) {
            this.warnUniversal = warnUniversal;
        }

        @Override
        public abstract IOMode execute(VirtualFrame frame, Object mode);

        @Specialization
        static IOMode none(@SuppressWarnings("unused") PNone none) {
            return IOMode.R;
        }

        @Specialization
        static IOMode done(IOMode mode) {
            return mode;
        }

        @Fallback
        @SuppressWarnings("truffle-static-method")
        IOMode generic(VirtualFrame frame, Object modeObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode toString,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached InlinedBranchProfile errProfile1,
                        @Cached InlinedBranchProfile errProfile2,
                        @Cached InlinedBranchProfile errProfile3,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString mode;
            try {
                mode = toString.execute(inliningTarget, modeObj);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            IOMode m = new IOMode(mode, createCodePointIteratorNode, nextNode);
            if (m.hasNil) {
                errProfile1.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(ValueError, EMBEDDED_NULL_CHARACTER);
            }
            if (m.isInvalid) {
                errProfile2.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(ValueError, INVALID_MODE_S, mode);
            }
            if (warnUniversal && m.universal) {
                errProfile3.enter(inliningTarget);
                warnNode.warnEx(frame, DeprecationWarning, ErrorMessages.U_MODE_DEPRACATED, 1);
            }
            return m;
        }

        @ClinicConverterFactory
        @NeverDefault
        public static CreateIOModeNode create(boolean warnUniversal) {
            return IONodesFactory.CreateIOModeNodeGen.create(warnUniversal);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class CastOpenNameNode extends ArgumentCastNode {

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
        static Object generic(VirtualFrame frame, Object nameobj,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.DecodeUTF8FSPathNode fspath,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (indexCheckNode.execute(inliningTarget, nameobj)) {
                int fd = asSizeNode.executeExact(frame, inliningTarget, nameobj);
                if (fd < 0) {
                    err(fd, raiseNode.get(inliningTarget));
                }
                return fd;
            } else {
                return fspath.execute(frame, nameobj);
            }
        }

        @Specialization(guards = "fd < 0")
        static int err(int fd,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, OPENER_RETURNED_D, fd);
        }

        @Specialization(guards = "fd < 0")
        static int err(long fd,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, OPENER_RETURNED_D, fd);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static CastOpenNameNode create() {
            return IONodesFactory.CastOpenNameNodeGen.create();
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class CreateBufferedIONode extends Node {
        public abstract PBuffered execute(VirtualFrame frame, Node inliningTarget, PFileIO fileIO, int buffering, PythonObjectFactory factory, IONodes.IOMode mode);

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
        static PBuffered createRandom(VirtualFrame frame, Node inliningTarget, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedRandomBuiltins.BufferedRandomInit initBuffered) {
            PBuffered buffer = factory.createBufferedRandom(PBufferedRandom);
            initBuffered.execute(frame, inliningTarget, buffer, fileIO, buffering, factory);
            return buffer;
        }

        @Specialization(guards = {"!isRandom(mode)", "isWriting(mode)"})
        static PBuffered createWriter(VirtualFrame frame, Node inliningTarget, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedWriterBuiltins.BufferedWriterInit initBuffered) {
            PBuffered buffer = factory.createBufferedWriter(PBufferedWriter);
            initBuffered.execute(frame, inliningTarget, buffer, fileIO, buffering, factory);
            return buffer;
        }

        @Specialization(guards = {"!isRandom(mode)", "!isWriting(mode)", "isReading(mode)"})
        static PBuffered createWriter(VirtualFrame frame, Node inliningTarget, PFileIO fileIO, int buffering, PythonObjectFactory factory, @SuppressWarnings("unused") IONodes.IOMode mode,
                        @Cached BufferedReaderBuiltins.BufferedReaderInit initBuffered) {
            PBuffered buffer = factory.createBufferedReader(PBufferedReader);
            initBuffered.execute(frame, inliningTarget, buffer, fileIO, buffering, factory);
            return buffer;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToTruffleStringNode extends Node {
        public abstract TruffleString execute(Node inliningTarget, Object str);

        public static boolean isString(Object s) {
            return s instanceof TruffleString;
        }

        @Specialization
        static TruffleString string(TruffleString s) {
            return s;
        }

        @Specialization(guards = "!isString(s)")
        static TruffleString str(Node inliningTarget, Object s,
                        @Cached CastToTruffleStringNode str,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return str.execute(inliningTarget, s);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_OBJ_TYPE_S_GOT_P, "str", s);
            }
        }
    }
}
