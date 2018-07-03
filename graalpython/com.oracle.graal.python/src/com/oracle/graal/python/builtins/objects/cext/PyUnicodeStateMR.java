/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PyUnicodeState.class)
public class PyUnicodeStateMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {

        public Object access(PyUnicodeState object, String key) {
            int value;
            if (onlyAscii(object.getDelegate().getValue())) {
                // padding(24), ready(1), ascii(1), compact(1), kind(3), interned(2)
                value = 0b000000000000000000000000_1_1_0_000_00;
            } else {
                value = 0b000000000000000000000000_1_0_0_000_00;
            }
            switch (key) {
                case NativeMemberNames.UNICODE_STATE_INTERNED:
                case NativeMemberNames.UNICODE_STATE_KIND:
                case NativeMemberNames.UNICODE_STATE_COMPACT:
                case NativeMemberNames.UNICODE_STATE_ASCII:
                case NativeMemberNames.UNICODE_STATE_READY:
                    // it's a bit field; so we need to return the whole 32-bit word
                    return value;
            }
            throw UnknownIdentifierException.raise(key);
        }

        @CompilationFinal private CharsetEncoder asciiEncoder;

        public boolean isPureAscii(String v) {
            return asciiEncoder.canEncode(v);
        }

        private boolean onlyAscii(String value) {
            if (asciiEncoder == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asciiEncoder = Charset.forName("US-ASCII").newEncoder();
            }
            return doCheck(value, asciiEncoder);
        }

        @TruffleBoundary
        private static boolean doCheck(String value, CharsetEncoder asciiEncoder) {
            return asciiEncoder.canEncode(value);
        }
    }
}
