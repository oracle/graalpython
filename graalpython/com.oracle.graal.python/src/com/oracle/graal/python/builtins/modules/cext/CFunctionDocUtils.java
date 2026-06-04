/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TEXT_SIGNATURE__;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

public final class CFunctionDocUtils {

    private static final String SIGNATURE_END_MARKER = ")\n--\n\n";
    private static final int SIGNATURE_END_MARKER_LENGTH = SIGNATURE_END_MARKER.length();

    private CFunctionDocUtils() {
    }

    @TruffleBoundary
    public static void writeDocAndTextSignature(PBuiltinFunction function, TruffleString name, Object docObj) {
        Object doc = PNone.NONE;
        Object textSignature = PNone.NONE;
        if (docObj instanceof TruffleString) {
            TruffleString docTruffleString = (TruffleString) docObj;
            String docString = docTruffleString.toJavaStringUncached();
            int start = findSignature(name.toJavaStringUncached(), docString);
            int end = start >= 0 ? skipSignature(docString, start) : -1;
            if (end < 0) {
                doc = docString.isEmpty() ? PNone.NONE : docTruffleString;
            } else {
                int textSignatureEnd = end - SIGNATURE_END_MARKER_LENGTH + 1;
                doc = end == docString.length() ? PNone.NONE : toTruffleStringUncached(docString.substring(end));
                textSignature = toTruffleStringUncached(docString.substring(start, textSignatureEnd));
            }
        }
        WriteAttributeToPythonObjectNode.executeUncached(function, T___DOC__, doc);
        WriteAttributeToPythonObjectNode.executeUncached(function, T___TEXT_SIGNATURE__, textSignature);
    }

    /*
     * Matches CPython's find_signature: the internal doc must start with the callable name followed
     * by the first '(' of the signature.
     */
    private static int findSignature(String name, String doc) {
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }
        int length = name.length();
        if (!doc.startsWith(name) || doc.length() <= length || doc.charAt(length) != '(') {
            return -1;
        }
        return length;
    }

    /*
     * Matches CPython's skip_signature: a blank line before the marker invalidates the signature.
     */
    private static int skipSignature(String doc, int start) {
        for (int i = start; i < doc.length(); i++) {
            if (doc.startsWith(SIGNATURE_END_MARKER, i)) {
                return i + SIGNATURE_END_MARKER_LENGTH;
            }
            if (doc.charAt(i) == '\n' && i + 1 < doc.length() && doc.charAt(i + 1) == '\n') {
                return -1;
            }
        }
        return -1;
    }
}
