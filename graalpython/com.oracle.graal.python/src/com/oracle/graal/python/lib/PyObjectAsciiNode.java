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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.unicodeNonAsciiEscape;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringIterator;

/**
 * Equivalent of CPython's PyObject_ASCII.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class PyObjectAsciiNode extends PNodeWithContext {
    public static TruffleString executeUncached(Object object) {
        return PyObjectAsciiNodeGen.getUncached().execute(null, null, object);
    }

    public final TruffleString executeCached(Frame frame, Object object) {
        return execute(frame, this, object);
    }

    public abstract TruffleString execute(Frame frame, Node inliningTarget, Object object);

    @Specialization
    static TruffleString ascii(VirtualFrame frame, Node inliningTarget, Object obj,
                    @Cached PyObjectReprAsTruffleStringNode reprNode,
                    @Cached(inline = false) TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                    @Cached(inline = false) TruffleStringIterator.NextNode nextNode,
                    @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                    @Cached(inline = false) TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode) {
        // TODO GR-37220: rewrite using TruffleStringBuilder?
        TruffleString repr = reprNode.execute(frame, inliningTarget, obj);
        byte[] bytes = new byte[codePointLengthNode.execute(repr, TS_ENCODING) * 10];
        TruffleStringIterator it = createCodePointIteratorNode.execute(repr, TS_ENCODING);
        int j = 0;
        while (it.hasNext()) {
            int ch = nextNode.execute(it);
            j = unicodeNonAsciiEscape(ch, j, bytes);
        }
        return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, 0, j, Encoding.US_ASCII, true), TS_ENCODING);
    }

    @NeverDefault
    public static PyObjectAsciiNode create() {
        return PyObjectAsciiNodeGen.create();
    }

    public static PyObjectAsciiNode getUncached() {
        return PyObjectAsciiNodeGen.getUncached();
    }
}
