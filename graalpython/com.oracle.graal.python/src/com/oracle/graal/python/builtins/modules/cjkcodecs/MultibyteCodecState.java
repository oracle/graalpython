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

import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.ERROR_IGNORE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.ERROR_REPLACE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_FLUSH;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOFEW;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOSMALL;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class MultibyteCodecState {

    protected CoderResult coderResult;

    protected static class Encoder extends MultibyteCodecState {

        private final CharsetEncoder encoder;

        Encoder(Charset charset, CodingErrorAction errorAction) {
            this.encoder = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
        }

        @TruffleBoundary
        public int encode(MultibyteEncodeBuffer buf, int flags) {
            boolean endOfInput = (flags & MBENC_FLUSH) != 0;
            while (true) {
                coderResult = encoder.encode(buf.inputBuffer, buf.outputBuffer, endOfInput);
                if (endOfInput && coderResult.isUnderflow()) {
                    coderResult = encoder.flush(buf.outputBuffer);
                }

                if (coderResult.isUnderflow()) {
                    buf.outputBuffer.flip();
                    return MBERR_TOOFEW;
                }

                if (coderResult.isOverflow()) {
                    // buf.expandOutputBuffer(-1, raiseNode);
                    return MBERR_TOOSMALL;
                } else if (coderResult.isError()) {
                    return coderResult.length();
                }
            }
        }

        @TruffleBoundary
        public int encreset(MultibyteEncodeBuffer buf) {
            int r = encode(buf, MBENC_FLUSH);
            if (r != 0) {
                return r;
            }
            encoder.reset();
            return 0;
        }

    }

    protected static class Decoder extends MultibyteCodecState {

        private final CharsetDecoder decoder;

        Decoder(Charset charset, CodingErrorAction errorAction) {
            this.decoder = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
        }

        @TruffleBoundary
        protected int decode(MultibyteDecodeBuffer buf, Node raisingNode) {
            while (true) {
                coderResult = decoder.decode(buf.inputBuffer, buf.writer, true);
                if (coderResult.isUnderflow()) {
                    coderResult = decoder.flush(buf.writer);
                    buf.writer.flip();
                    return MBERR_TOOFEW;
                }

                if (coderResult.isOverflow()) {
                    buf.grow(raisingNode);
                    return MBERR_TOOSMALL;
                } else if (coderResult.isError()) {
                    return coderResult.length();
                }
            }
        }

        @TruffleBoundary
        protected void decreset() {
            decoder.reset();
        }

    }

    @TruffleBoundary
    protected static Encoder encoder(Charset charset, TruffleString error) {
        CodingErrorAction errorAction = CodingErrorAction.REPORT;
        errorAction = (error == ERROR_IGNORE) ? CodingErrorAction.IGNORE : errorAction;
        errorAction = (error == ERROR_REPLACE) ? CodingErrorAction.REPLACE : errorAction;
        return new Encoder(charset, errorAction);
    }

    @TruffleBoundary
    protected static Decoder decoder(Charset charset, TruffleString error) {
        CodingErrorAction errorAction = CodingErrorAction.REPORT;
        errorAction = (error == ERROR_IGNORE) ? CodingErrorAction.IGNORE : errorAction;
        errorAction = (error == ERROR_REPLACE) ? CodingErrorAction.REPLACE : errorAction;
        return new Decoder(charset, errorAction);
    }
}
