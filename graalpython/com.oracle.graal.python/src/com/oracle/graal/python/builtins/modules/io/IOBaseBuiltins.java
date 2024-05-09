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

import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITELINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__CHECKCLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__CHECKREADABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__CHECKSEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__CHECKWRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T___IOBASE_CLOSED;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES_NOT_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_FILENO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_FILENO;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PIOBase)
public final class IOBaseBuiltins extends PythonBuiltins {

    // taken from usr/include/stdio.h
    public static final int BUFSIZ = 8192;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean closed(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup) {
            return isClosed(inliningTarget, self, frame, lookup);
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean seekable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean readable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean writable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @GenerateCached(false)
    @GenerateUncached(false)
    @GenerateInline
    abstract static class CheckClosedHelperNode extends Node {
        abstract PNone execute(VirtualFrame frame, Node inliningTarget, PythonObject self);

        @Specialization
        static PNone doIt(VirtualFrame frame, Node inliningTarget, PythonObject self,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode.Lazy lazyRaiseNode) {
            if (isTrueNode.execute(frame, inliningTarget, getAttr.execute(frame, inliningTarget, self, T_CLOSED))) {
                throw lazyRaiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J__CHECKCLOSED, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doCheckClosed(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckClosedHelperNode helper) {
            return helper.execute(frame, inliningTarget, self);
        }
    }

    @GenerateCached(false)
    @GenerateUncached(false)
    @GenerateInline
    abstract static class CheckBoolMethodHelperNode extends Node {
        final boolean checkSeekable(VirtualFrame frame, Node inliningTarget, Object self) {
            return this.execute(frame, inliningTarget, self, T_SEEKABLE, ErrorMessages.FILE_OR_STREAM_IS_NOT_SEEKABLE);
        }

        final boolean checkWriteable(VirtualFrame frame, Node inliningTarget, Object self) {
            return this.execute(frame, inliningTarget, self, T_WRITABLE, ErrorMessages.FILE_OR_STREAM_IS_NOT_WRITABLE);
        }

        final boolean checkReadable(VirtualFrame frame, Node inliningTarget, Object self) {
            return this.execute(frame, inliningTarget, self, T_READABLE, ErrorMessages.FILE_OR_STREAM_IS_NOT_READABLE);
        }

        abstract boolean execute(VirtualFrame frame, Node inliningTarget, Object self, TruffleString method, TruffleString errorMessage);

        @Specialization
        static boolean doIt(VirtualFrame frame, Node inliningTarget, Object self, TruffleString method, TruffleString errorMessage,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached(inline = false) IsNode isNode,
                        @Cached PRaiseNode.Lazy lazyRaiseNode) {
            CompilerAsserts.partialEvaluationConstant(method);
            CompilerAsserts.partialEvaluationConstant(errorMessage);
            Object v = callMethod.execute(frame, inliningTarget, self, method);
            if (isNode.isTrue(v)) {
                return true;
            }
            throw unsupported(lazyRaiseNode.get(inliningTarget), errorMessage);
        }
    }

    @Builtin(name = J__CHECKSEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckSeekableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckSeekable(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckBoolMethodHelperNode helper) {
            return helper.checkSeekable(frame, inliningTarget, self);
        }
    }

    @Builtin(name = J__CHECKREADABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckReadableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckReadable(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckBoolMethodHelperNode helper) {
            return helper.checkReadable(frame, inliningTarget, self);
        }
    }

    @Builtin(name = J__CHECKWRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckWritableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckWritable(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckBoolMethodHelperNode helper) {
            return helper.checkWriteable(frame, inliningTarget, self);
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone close(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectSetAttr setAttributeNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PyErrChainExceptions chainExceptions) {
            if (!isClosed(inliningTarget, self, frame, lookup)) {
                try {
                    callMethod.execute(frame, inliningTarget, self, T_FLUSH);
                } catch (PException e) {
                    errorProfile.enter(inliningTarget);
                    try {
                        setAttributeNode.execute(frame, inliningTarget, self, T___IOBASE_CLOSED, true);
                    } catch (PException e1) {
                        throw chainExceptions.execute(inliningTarget, e1, e);
                    }
                    throw e;
                }
                setAttributeNode.execute(frame, inliningTarget, self, T___IOBASE_CLOSED, true);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone flush(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (isClosed(inliningTarget, self, frame, lookup)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonBuiltinNode {
        @Specialization
        static Object seek(@SuppressWarnings("unused") PythonObject self, @SuppressWarnings("unused") Object args,
                        @Cached PRaiseNode raiseNode) {
            throw unsupported(raiseNode, T_SEEK);
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBuiltinNode {
        @Specialization
        static Object truncate(@SuppressWarnings("unused") PythonObject self,
                        @Cached PRaiseNode raiseNode) {
            throw unsupported(raiseNode, T_TRUNCATE);
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tell(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self, T_SEEK, 0, 1);
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PythonObject enter(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckClosedHelperNode checkClosedNode) {
            checkClosedNode.execute(frame, inliningTarget, self);
            return self;
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization
        static Object exit(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self, T_CLOSE);
        }
    }

    @Builtin(name = J_FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object fileno(@SuppressWarnings("unused") PythonObject self,
                        @Cached PRaiseNode raiseNode) {
            throw unsupported(raiseNode, T_FILENO);
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isatty(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckClosedHelperNode checkClosedNode) {
            checkClosedNode.execute(frame, inliningTarget, self);
            return false;
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PythonObject iter(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckClosedHelperNode checkClosedNode) {
            checkClosedNode.execute(frame, inliningTarget, self);
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PythonObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object line = callMethod.execute(frame, inliningTarget, self, T_READLINE);
            if (sizeNode.execute(frame, inliningTarget, line) <= 0) {
                throw raiseNode.get(inliningTarget).raiseStopIteration();
            }
            return line;
        }
    }

    @Builtin(name = J_WRITELINES, minNumOfPositionalArgs = 2, parameterNames = {"$self", "lines"})
    @GenerateNodeFactory
    abstract static class WriteLinesNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object writeLines(VirtualFrame frame, PythonObject self, Object lines,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckClosedHelperNode checkClosedNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectGetIter getIter) {
            checkClosedNode.execute(frame, inliningTarget, self);
            Object iter = getIter.execute(frame, inliningTarget, lines);
            while (true) {
                Object line;
                try {
                    line = getNextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                }
                callMethod.execute(frame, inliningTarget, self, T_WRITE, line);
                // TODO _PyIO_trap_eintr [GR-23297]
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOBaseBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        /**
         * implementation of cpython/Modules/_io/iobase.c:_io__IOBase_readline_impl
         */
        @Specialization
        static PBytes readline(VirtualFrame frame, Object self, int limit,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupPeek,
                        @Cached CallNode callPeek,
                        @Cached PyObjectCallMethodObjArgs callRead,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile hasPeek,
                        @Cached InlinedConditionProfile isBytes,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            /* For backwards compatibility, a (slowish) readline(). */
            Object peek = lookupPeek.execute(frame, inliningTarget, self, T_PEEK);
            ByteArrayOutputStream buffer = createOutputStream();
            while (limit < 0 || buffer.size() < limit) {
                int nreadahead = 1;
                if (hasPeek.profile(inliningTarget, peek != PNone.NO_VALUE)) {
                    Object readahead = callPeek.execute(frame, peek, 1);
                    // TODO _PyIO_trap_eintr [GR-23297]
                    if (isBytes.profile(inliningTarget, !(readahead instanceof PBytes))) {
                        throw raiseNode.get(inliningTarget).raise(OSError, S_SHOULD_RETURN_BYTES_NOT_P, "peek()", readahead);
                    }
                    byte[] buf = bufferLib.getInternalOrCopiedByteArray(readahead);
                    int bufLen = bufferLib.getBufferLength(readahead);
                    if (bufLen > 0) {
                        int n = 0;
                        while ((limit < 0 || n < limit) && n < bufLen) {
                            if (buf[n++] == '\n') {
                                break;
                            }
                        }
                        nreadahead = n;
                    }
                }

                Object b = callRead.execute(frame, inliningTarget, self, T_READ, nreadahead);
                if (isBytes.profile(inliningTarget, !(b instanceof PBytes))) {
                    throw raiseNode.get(inliningTarget).raise(OSError, S_SHOULD_RETURN_BYTES_NOT_P, "read()", b);
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(b);
                int bytesLen = bufferLib.getBufferLength(b);
                if (bytesLen == 0) {
                    break;
                }

                append(buffer, bytes, bytesLen);
                if (bytes[bytesLen - 1] == '\n') {
                    break;
                }
            }

            return factory.createBytes(toByteArray(buffer));
        }
    }

    @Builtin(name = J_READLINES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "hint"})
    @ArgumentClinic(name = "hint", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOBaseBuiltinsClinicProviders.ReadlinesNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object withHint(VirtualFrame frame, Object self, int hintIn,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNextNode next,
                        @Cached InlinedConditionProfile isNegativeHintProfile,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PythonObjectFactory factory) {
            int hint = isNegativeHintProfile.profile(inliningTarget, hintIn <= 0) ? Integer.MAX_VALUE : hintIn;
            int length = 0;
            Object iterator = getIter.execute(frame, inliningTarget, self);
            ArrayBuilder<Object> list = new ArrayBuilder<>();
            while (true) {
                try {
                    Object line = next.execute(frame, iterator);
                    list.add(line);
                    int lineLength = sizeNode.execute(frame, inliningTarget, line);
                    if (lineLength > hint - length) {
                        break;
                    }
                    length += lineLength;
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                }
            }
            return factory.createList(list.toArray(new Object[0]));
        }
    }

    /**
     * Equivalent of {@code iobase_is_closed}.
     *
     * @param self the IOBase instance
     * @return true if the {@link IONodes#T___IOBASE_CLOSED} attribute exists
     */
    private static boolean isClosed(Node inliningTarget, PythonObject self, VirtualFrame frame, PyObjectLookupAttr lookup) {
        return !PGuards.isNoValue(lookup.execute(frame, inliningTarget, self, T___IOBASE_CLOSED));
    }

    /**
     * Equivalent of {@code iobase_unsupported}.
     */
    private static PException unsupported(PRaiseNode raiseNode, TruffleString message) {
        throw raiseNode.raise(IOUnsupportedOperation, message);
    }
}
