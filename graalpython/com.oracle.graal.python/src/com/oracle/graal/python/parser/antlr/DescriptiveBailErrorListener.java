/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;

import com.oracle.graal.python.parser.antlr.Python3Parser.Single_inputContext;
import com.oracle.graal.python.runtime.PythonParser.ErrorType;
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

        String entireMessage = e == null || e.getMessage() == null ? "invalid syntax" : e.getMessage();

        if (isInteractive(recognizer)) {
            PIncompleteSourceException handleRecognitionException = null;
            if (e != null) {
                handleRecognitionException = handleRecognitionException(e.getExpectedTokens(), entireMessage, e, line);
            } else if (recognizer instanceof Python3Parser) {
                handleRecognitionException = handleRecognitionException(((Python3Parser) recognizer).getExpectedTokens(), entireMessage, null, line);
            }
            if (handleRecognitionException == null) {
                handleRecognitionException = handleInteractiveException(recognizer, offendingSymbol);
            }
            if (handleRecognitionException != null) {
                throw handleRecognitionException;
            }
        }
        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            ErrorType errorType = ErrorType.Generic;
            if (token.getType() == Python3Parser.INDENT_ERROR) {
                entireMessage = "unindent does not match any outer indentation level";
                errorType = ErrorType.Indentation;
            }
            throw new EmptyRecognitionException(errorType, entireMessage, recognizer, token);
        }
        throw new RuntimeException(entireMessage, e);
    }

    private static PIncompleteSourceException handleRecognitionException(IntervalSet et, String message, Throwable cause, int line) {
        if (et.contains(Python3Parser.INDENT) || et.contains(Python3Parser.FINALLY) || et.contains(Python3Parser.EXCEPT) || et.contains(Python3Parser.NEWLINE) && et.size() == 1) {
            return new PIncompleteSourceException(message, cause, line);
        }
        return null;
    }

    private static PIncompleteSourceException handleInteractiveException(Recognizer<?, ?> recognizer, Object offendingSymbol) {
        if (isOpened(((Python3Parser) recognizer).getTokenStream()) || isBackslash(offendingSymbol)) {
            return new PIncompleteSourceException("", null, -1);
        }
        return null;
    }

    private static ParserRuleContext getRootCtx(ParserRuleContext ctx) {
        ParserRuleContext c = ctx;
        while (c.getParent() != null) {
            c = c.getParent();
        }
        return c;
    }

    private static boolean isInteractive(Recognizer<?, ?> recognizer) {
        if (!(recognizer instanceof Python3Parser)) {
            return false;
        }
        ParserRuleContext ctx = getRootCtx(((Python3Parser) recognizer).getContext());
        if (ctx instanceof Single_inputContext) {
            return ((Single_inputContext) ctx).interactive;
        }
        return false;
    }

    /**
     * @return true if there are an open '(', '[' or '{'.
     */
    private static boolean isOpened(TokenStream input) {
        final Python3Lexer lexer = (Python3Lexer) input.getTokenSource();
        return lexer.isOpened();
    }

    private static final int BACKSLASH = '\\';

    private static boolean isBackslash(Object offendingSymbol) {
        if (offendingSymbol instanceof Token) {
            CharStream cs = ((Token) offendingSymbol).getInputStream();
            if (cs.size() > 2 && cs.LA(-2) == BACKSLASH) {
                return true;
            }
        }
        return false;
    }

    public static class EmptyRecognitionException extends RecognitionException {
        private static final long serialVersionUID = 1L;
        private Token offendingToken;
        private ErrorType errorType;

        public EmptyRecognitionException(ErrorType errorType, String message, Recognizer<?, ?> recognizer, Token offendingToken) {
            super(message, recognizer, offendingToken.getInputStream(), null);
            this.errorType = errorType;
            this.offendingToken = offendingToken;
        }

        @Override
        public Token getOffendingToken() {
            return offendingToken;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
