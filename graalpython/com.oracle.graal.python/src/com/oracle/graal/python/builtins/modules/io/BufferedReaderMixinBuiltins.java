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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.safeDowncast;
import static com.oracle.graal.python.builtins.modules.io.BufferedReaderNodes.ReadNode.bufferedreaderReadFast;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEG_OR_NEG_1;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = {PBufferedReader, PBufferedRandom})
public class BufferedReaderMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedReaderMixinBuiltinsFactory.getFactories();
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, READABLE);
        }
    }

    /*
     * Generic read function: read from the stream until enough bytes are read, or until an EOF
     * occurs or until read() would block.
     */

    @Builtin(name = READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isValidSize(int size) {
            return size >= -1;
        }

        @Specialization(guards = {"self.isOK()", "isValidSize(size)"})
        Object read(VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(READ)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.ReadNode readNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] res = readNode.execute(frame, self, size);
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!isValidSize(size)"})
        Object initError(VirtualFrame frame, PBuffered self, int size) {
            throw raise(ValueError, MUST_BE_NON_NEG_OR_NEG_1);
        }
    }

    @Builtin(name = READ1, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class Read1Node extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.Read1NodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(READ)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.RawReadNode rawReadNode) {
            checkIsClosedNode.execute(frame, self);
            int n = size;
            if (n < 0) {
                n = self.getBufferSize();
            }

            if (n == 0) {
                return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }
            /*- Return up to n bytes.  If at least one byte is buffered, we
               only return buffered bytes.  Otherwise, we do one raw read. */

            int have = safeDowncast(self);
            if (have > 0) {
                n = have < n ? have : n;
                byte[] b = bufferedreaderReadFast(self, n);
                return factory().createBytes(b);
            }
            self.resetRead(); // _bufferedreader_reset_buf
            byte[] fill = rawReadNode.execute(frame, self, n);
            return factory().createBytes(fill);
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends PythonBinaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        int doit(VirtualFrame frame, PBuffered self, Object buffer,
                        @Cached("create(READLINE)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.ReadintoNode readintoNode,
                        @Cached("createReadIntoArg()") BytesNodes.GetByteLengthIfWritableNode getLen) {
            checkIsClosedNode.execute(frame, self);
            int bufLen = getLen.execute(frame, buffer);
            return readintoNode.execute(frame, self, buffer, bufLen, isReadinto1Mode());
        }

        protected boolean isReadinto1Mode() {
            return false;
        }
    }

    @Builtin(name = READINTO1, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReadIntoNode {
        @Override
        protected boolean isReadinto1Mode() {
            return true;
        }
    }

    @Builtin(name = READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self, int size,
                        @Cached("create(READLINE)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.ReadlineNode readlineNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] res = readlineNode.execute(frame, self, size);
            return factory().createBytes(res);
        }
    }

    @Builtin(name = PEEK, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0", useDefaultForNone = true)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class PeekNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderMixinBuiltinsClinicProviders.PeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.isOK()")
        Object doit(VirtualFrame frame, PBuffered self, @SuppressWarnings("unused") int size,
                        @Cached("create(PEEK)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedReaderNodes.PeekUnlockedNode peekUnlockedNode,
                        @Cached BufferedIONodes.FlushAndRewindUnlockedNode flushAndRewindUnlockedNode) {
            checkIsClosedNode.execute(frame, self);
            if (self.isWritable()) {
                flushAndRewindUnlockedNode.execute(frame, self);
            }
            return factory().createBytes(peekUnlockedNode.execute(frame, self));
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @ImportStatic(AbstractBufferedIOBuiltins.class)
    @GenerateNodeFactory
    abstract static class IternextNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        PBytes doit(VirtualFrame frame, PBuffered self,
                        @Cached("create(READLINE)") BufferedIONodes.CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.ReadlineNode readlineNode) {
            checkIsClosedNode.execute(frame, self);
            byte[] line = readlineNode.execute(frame, self, -1);
            if (line.length == 0) {
                throw raise(StopIteration);
            }
            return factory().createBytes(line);
        }
    }
}
