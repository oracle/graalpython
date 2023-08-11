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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.BuiltinNames.T_DECODE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CODECS;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_S_RETURNED_P_INSTEAD_OF_STR;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyUnicode_Decode}.
 */
@ImportStatic(PyUnicodeAsEncodedString.class)
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyUnicodeDecode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Node inliningTarget, Object object, Object encoding, Object errors);

    @Specialization(guards = "frame != null")
    static Object doFast(VirtualFrame frame, Node inliningTarget, Object object, Object encoding, Object errors,
                    @Cached(inline = false) CodecsModuleBuiltins.DecodeNode decodeNode,
                    @Cached PRaiseNode.Lazy raiseNode) {
        final Object unicode = decodeNode.execute(frame, object, encoding, errors);
        if (!PGuards.isString(unicode)) {
            throw raiseNode.get(inliningTarget).raise(TypeError, DECODER_S_RETURNED_P_INSTEAD_OF_STR, encoding, unicode);
        }
        return unicode;
    }

    @Specialization(replaces = "doFast")
    static Object doWithCall(Node inliningTarget, Object object, Object encoding, Object errors,
                    @Cached PyObjectCallMethodObjArgs callNode) {
        return callNode.execute(null, inliningTarget, PythonContext.get(inliningTarget).getCore().lookupBuiltinModule(T__CODECS), T_DECODE, object, encoding, errors);
    }
}
