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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.lib.PyUnicodeAsEncodedString.ENC_UTF8;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_S_RETURNED_P_INSTEAD_OF_STR;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of CPython's {@code PyUnicode_Decode}.
 */
@ImportStatic(PyUnicodeAsEncodedString.class)
public abstract class PyUnicodeDecode extends PNodeWithRaise {
    public abstract Object execute(VirtualFrame frame, Object object, Object encoding, Object errors);

    @Specialization(guards = "isCommon(encoding)")
    Object doCommon(VirtualFrame frame, Object object, String encoding, String errors,
                    @Cached CodecsModuleBuiltins.CodecsDecodeNode decodeNode) {
        return decodeNode.execute(frame, object, encoding, errors, false);
    }

    @Specialization(guards = "!isCommon(encoding)")
    Object doRegistry(VirtualFrame frame, Object object, String encoding, String errors,
                    @Cached CodecsModuleBuiltins.DecodeNode decodeNode) {
        final Object unicode = decodeNode.execute(frame, object, encoding, errors);
        if (!PGuards.isString(unicode)) {
            throw raise(TypeError, DECODER_S_RETURNED_P_INSTEAD_OF_STR, encoding, unicode);
        }
        return unicode;
    }

    @Specialization(guards = "isNoValue(encoding)")
    Object doNoEncoding(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone encoding, Object errors,
                    @Cached CodecsModuleBuiltins.CodecsDecodeNode decodeNode) {
        return decodeNode.execute(frame, object, ENC_UTF8, errors, false);
    }
}
