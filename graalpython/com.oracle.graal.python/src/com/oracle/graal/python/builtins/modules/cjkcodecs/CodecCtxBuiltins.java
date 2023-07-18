/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteIncrementalDecoder;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteIncrementalEncoder;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteStreamReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteStreamWriter;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_DELETE;
import static com.oracle.graal.python.nodes.ErrorMessages.ERRORS_MUST_BE_A_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {
                MultibyteIncrementalDecoder,
                MultibyteIncrementalEncoder,
                MultibyteStreamReader,
                MultibyteStreamWriter
})
public final class CodecCtxBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecCtxBuiltinsFactory.getFactories();
    }

    @Builtin(name = "errors", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "how to treat errors")
    @GenerateNodeFactory
    protected abstract static class CodecCtxNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(ignored)")
        static Object codecctxErrorsGet(MultibyteStatefulCodecContext self, @SuppressWarnings("unused") PNone ignored) {
            return self.errors;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object codecctxErrorsSet(MultibyteStatefulCodecContext self, Object value,
                        @Cached TruffleString.EqualNode isEqual,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode) {

            if (value == PNone.NONE) {
                throw raise(AttributeError, CANNOT_DELETE);
            }
            if (!unicodeCheckNode.execute(value)) {
                throw raise(TypeError, ERRORS_MUST_BE_A_STRING);
            }

            TruffleString str = castToStringNode.execute(value);
            self.errors = internalErrorCallback(str, isEqual);
            return PNone.NONE;
        }
    }

}
