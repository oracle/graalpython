/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.parser.antlr;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import com.oracle.graal.python.runtime.PythonParser.PIncompleteSourceException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * An error listener that immediately bails out of the parse (does not recover) and throws a runtime
 * exception with a descriptive error message.
 */
public class DescriptiveBailErrorListener extends BaseErrorListener {

    @Override
    @TruffleBoundary
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                    int line, int charPositionInLine,
                    String msg, RecognitionException e) {

        String entireMessage = String.format("source: %s, line: %s, index: %s, error message: %s %s",
                        recognizer.getInputStream().getSourceName(), line, charPositionInLine, msg,
                        e == null ? "<null>" : e.getMessage());

        if (e != null) {
            PIncompleteSourceException handleRecognitionException = handleRecognitionException(e.getExpectedTokens(), entireMessage, e, line);
            if (handleRecognitionException != null) {
                throw handleRecognitionException;
            }
        } else if (recognizer instanceof Python3Parser) {
            PIncompleteSourceException handleRecognitionException = handleRecognitionException(((Python3Parser) recognizer).getExpectedTokens(), entireMessage, null, line);
            if (handleRecognitionException != null) {
                throw handleRecognitionException;
            }
        } else if (recognizer instanceof Python3NewParser) {
            PIncompleteSourceException handleRecognitionException = handleRecognitionException(((Python3NewParser) recognizer).getExpectedTokens(), entireMessage, null, line);
            if (handleRecognitionException != null) {
                throw handleRecognitionException;
            }
        }
        if (offendingSymbol instanceof Token) {
            throw new RuntimeException(entireMessage, new EmptyRecognitionException(entireMessage, recognizer, (Token) offendingSymbol));
        }
        throw new RuntimeException(entireMessage, e);
    }

    private static PIncompleteSourceException handleRecognitionException(IntervalSet et, String message, Throwable cause, int line) {
        if (et.contains(Python3Parser.INDENT) || et.contains(Python3Parser.FINALLY) || et.contains(Python3Parser.EXCEPT) || et.contains(Python3Parser.NEWLINE) ||
                        et.contains(Python3NewParser.INDENT) || et.contains(Python3NewParser.FINALLY) || et.contains(Python3NewParser.EXCEPT) || et.contains(Python3Parser.NEWLINE) && et.size() == 1) {
            return new PIncompleteSourceException(message, cause, line);
        }
        return null;
    }

    private static class EmptyRecognitionException extends RecognitionException {
        private static final long serialVersionUID = 1L;
        private Token offendingToken;

        public EmptyRecognitionException(String message, Recognizer<?, ?> recognizer, Token offendingToken) {
            super(message, recognizer, offendingToken.getInputStream(), null);
            this.offendingToken = offendingToken;
        }

        @Override
        public Token getOffendingToken() {
            return offendingToken;
        }
    }
}
