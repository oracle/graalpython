/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PBufferedReader)
public class BufferedReaderBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedReaderBuiltinsFactory.getFactories();
    }

    // BufferedReader(raw[, buffer_size=DEFAULT_BUFFER_SIZE])
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$raw", "buffer_size"})
    @ArgumentClinic(name = "buffer_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends BaseInitNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedReaderBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "bufferSize > 0", limit = "1")
        public PNone doInit(VirtualFrame frame, PBuffered self, Object raw, int bufferSize,
                        @Cached IOBaseBuiltins.CheckReadableNode checkReadableNode,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @CachedLibrary("raw") PythonObjectLibrary libRaw) {
            self.setOK(false);
            self.setDetached(false);
            checkReadableNode.call(frame, raw);
            self.setRaw(raw, isFileIO(self, raw, libSelf, libRaw));
            bufferedInit(frame, self, bufferSize);
            self.resetRead();
            self.setOK(true);
            return PNone.NONE;
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()", limit = "1")
        Object doit(VirtualFrame frame, PBuffered self,
                        @CachedLibrary("self.getRaw()") PythonObjectLibrary libRaw) {
            return libRaw.lookupAndCallRegularMethod(self.getRaw(), frame, FLUSH);
        }
    }
}
