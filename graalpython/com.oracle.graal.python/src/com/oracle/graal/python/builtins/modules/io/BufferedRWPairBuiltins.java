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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRWPair;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READINTO1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_S_OBJECT_IS_BEING_GARBAGE_COLLECTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PBufferedRWPair)
public final class BufferedRWPairBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedRWPairBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 3, parameterNames = {"$self", "reader", "writer", "buffer_size"})
    @ArgumentClinic(name = "buffer_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedRWPairBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doInit(VirtualFrame frame, PRWPair self, Object reader, Object writer, int bufferSize,
                        @Bind("this") Node inliningTarget,
                        @Cached IOBaseBuiltins.CheckBoolMethodHelperNode checkReadableNode,
                        @Cached IOBaseBuiltins.CheckBoolMethodHelperNode checkWritableNode,
                        @Cached BufferedReaderBuiltins.BufferedReaderInit initReaderNode,
                        @Cached BufferedWriterBuiltins.BufferedWriterInit initWriterNode,
                        @Cached PythonObjectFactory factory) {
            checkReadableNode.checkReadable(frame, inliningTarget, reader);
            checkWritableNode.checkWriteable(frame, inliningTarget, writer);
            self.setReader(factory.createBufferedReader(PBufferedReader));
            initReaderNode.execute(frame, inliningTarget, self.getReader(), reader, bufferSize, factory);
            self.setWriter(factory.createBufferedWriter(PBufferedWriter));
            initWriterNode.execute(frame, inliningTarget, self.getWriter(), writer, bufferSize, factory);
            return PNone.NONE;
        }
    }

    abstract static class ReaderInitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getReader() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class WriterInitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getWriter() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        Object error(VirtualFrame frame, PRWPair self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class ReaderInitCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getReader() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        static Object error(VirtualFrame frame, PRWPair self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class WriterInitCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {

        protected static boolean isInit(PRWPair self) {
            return self.getWriter() != null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isInit(self)")
        static Object error(VirtualFrame frame, PRWPair self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object read(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_READ, args);
        }
    }

    @Builtin(name = J_PEEK, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PeekNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object peek(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_PEEK, args);
        }
    }

    @Builtin(name = J_READ1, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class Read1Node extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object read1(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_READ1, args);
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object readInto(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_READINTO, args);
        }
    }

    @Builtin(name = J_READINTO1, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReaderInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object readInto1(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_READINTO1, args);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends WriterInitCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object write(VirtualFrame frame, PRWPair self, Object args,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getWriter(), T_WRITE, args);
        }
    }

    @Builtin(name = J_FLUSH, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends WriterInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getWriter(), T_FLUSH);
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends ReaderInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getReader(), T_READABLE);
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends WriterInitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "isInit(self)")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getWriter(), T_WRITABLE);
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object close(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodReader,
                        @Cached PyObjectCallMethodObjArgs callMethodWriter,
                        @Cached InlinedConditionProfile gotException,
                        @Cached InlinedBranchProfile hasException,
                        @Cached PyErrChainExceptions chainExceptions) {
            PException writeEx = null;
            if (self.getWriter() != null) {
                try {
                    callMethodWriter.execute(frame, inliningTarget, self.getWriter(), T_CLOSE);
                } catch (PException e) {
                    hasException.enter(inliningTarget);
                    writeEx = e;
                }
            } else {
                writeEx = getRaiseNode().raise(ValueError, IO_UNINIT);
            }

            PException readEx;
            if (self.getReader() != null) {
                try {
                    Object res = callMethodReader.execute(frame, inliningTarget, self.getReader(), T_CLOSE);
                    if (gotException.profile(inliningTarget, writeEx != null)) {
                        throw writeEx;
                    }
                    return res;
                } catch (PException e) {
                    readEx = e;
                }
            } else {
                readEx = getRaiseNode().raise(ValueError, IO_UNINIT);
            }

            hasException.enter(inliningTarget);
            return chainedError(writeEx, readEx, inliningTarget, gotException, chainExceptions);
        }

        static Object chainedError(PException first, PException second, Node inliningTarget, InlinedConditionProfile gotFirst, PyErrChainExceptions chainExceptions) {
            if (gotFirst.profile(inliningTarget, first != null)) {
                throw chainExceptions.execute(inliningTarget, second, first);
            } else {
                throw second;
            }
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodWriter,
                        @Cached PyObjectCallMethodObjArgs callMethodReader,
                        @Cached IsNode isNode,
                        @Cached InlinedConditionProfile isSameProfile) {
            Object res = callMethodWriter.execute(frame, inliningTarget, self.getWriter(), T_ISATTY);
            if (isSameProfile.profile(inliningTarget, isNode.isTrue(res))) {
                /* either True or exception */
                return res;
            }
            return callMethodReader.execute(frame, inliningTarget, self.getReader(), T_ISATTY);
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getWriter() != null")
        static Object doit(VirtualFrame frame, PRWPair self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getWriter(), T_CLOSED);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object self) {
            throw raise(RuntimeError, THE_S_OBJECT_IS_BEING_GARBAGE_COLLECTED, "BufferedRWPair");
        }
    }
}
