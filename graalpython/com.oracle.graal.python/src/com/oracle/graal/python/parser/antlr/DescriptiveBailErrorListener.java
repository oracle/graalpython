/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.antlr.v4.runtime.CommonTokenStream;
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
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;

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
        // If we already constructed the error, keep it, it should be the most specific one
        if (e instanceof EmptyRecognitionException) {
            throw e;
        }
        if (e instanceof Python3Parser.PythonRecognitionException && offendingSymbol instanceof Token) {
            // These are errors constructed in ANTLR grammar (Pyhton3.g4). They are specific and we
            // should keep them in every parser mode.
            throw new EmptyRecognitionException(ErrorType.Generic, e.getMessage(), recognizer, (Token) offendingSymbol);
        }
        String entireMessage = e == null || e.getMessage() == null ? "invalid syntax" : e.getMessage();

        IntervalSet expectedTokens = null;
        if (e != null) {
            expectedTokens = e.getExpectedTokens();
        } else if (recognizer instanceof Python3Parser) {
            expectedTokens = ((Python3Parser) recognizer).getExpectedTokens();
        }

        if (isInteractive(recognizer)) {
            PIncompleteSourceException incompleteSourceException = null;
            if (expectedTokens != null) {
                incompleteSourceException = handleRecognitionException(expectedTokens, entireMessage, e, line, recognizer, offendingSymbol);
            }
            if (incompleteSourceException == null) {
                incompleteSourceException = handleInteractiveException(recognizer, offendingSymbol);
            }
            if (incompleteSourceException != null) {
                throw incompleteSourceException;
            }
        }

        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            ErrorType errorType = ErrorType.Generic;
            switch (token.getType()) {
                case Python3Parser.INDENT_ERROR:
                    entireMessage = "unindent does not match any outer indentation level";
                    errorType = ErrorType.Indentation;
                    break;
                case Python3Parser.TAB_ERROR:
                    entireMessage = "inconsistent use of tabs and spaces in indentation";
                    errorType = ErrorType.Tab;
                    break;
                case Python3Parser.INDENT:
                    entireMessage = "unexpected indent";
                    errorType = ErrorType.Indentation;
                    break;
                case Python3Parser.DEDENT:
                    entireMessage = "unexpected unindent";
                    errorType = ErrorType.Indentation;
                    break;
                case Python3Parser.LONG_QUOTES1:
                case Python3Parser.LONG_QUOTES2:
                    entireMessage = "EOF while scanning triple-quoted string literal";
                    break;
                case Python3Parser.LINE_JOINING_EOF_ERROR:
                    entireMessage = "unexpected EOF while parsing";
                    break;
                case Python3Parser.UNKNOWN_CHAR:
                    String text = token.getText();
                    if (text.startsWith("\'") || text.startsWith("\"")) {
                        entireMessage = "EOL while scanning string literal";
                    } else if (text.equals("\\")) {
                        entireMessage = "unexpected character after line continuation character";
                    }
                    break;
                default:
                    if (expectedTokens != null && expectedTokens.contains(Python3Parser.INDENT)) {
                        entireMessage = "expected an indented block";
                        errorType = ErrorType.Indentation;
                    }
            }
            throw new EmptyRecognitionException(errorType, entireMessage, recognizer, token);
        }
        throw new RuntimeException(entireMessage, e);
    }

    private static PIncompleteSourceException handleRecognitionException(IntervalSet et, String message, Throwable cause, int line, Recognizer<?, ?> recognizer, Object offendingSymbol) {
        Token token = (Token) offendingSymbol;
        int tokenId = token.getType();
        if (et.contains(Python3Parser.INDENT) && et.size() == 1) {
            if (tokenId != Python3Parser.NEWLINE && tokenId != Python3Parser.EOF && tokenId != Python3Parser.DEDENT) {
                // this can be indentation error and not incomplete source
                return null;
            }
            if (tokenId == Python3Parser.NEWLINE || tokenId == Python3Parser.DEDENT) {
                TokenStream ts = ((Python3Parser) recognizer).getTokenStream();

                Token tMinus1 = ts.LT(-1);
                Token tMinus2 = ts.LT(-2);

                if (tMinus1.getType() == Python3Parser.NEWLINE && tMinus2.getType() != Python3Parser.NEWLINE) {
                    String text = ts.getTokenSource().getInputStream().getText(Interval.of(tMinus2.getStopIndex(), tMinus1.getStartIndex()));
                    if (text.charAt(text.length() - 1) == text.charAt(text.length() - 2)) {
                        // cases like: 'if True:\n\n' -> indentation error
                        return null;
                    }
                }
                return new PIncompleteSourceException(message, cause, line);
            }
        }

        if (tokenId == Python3Parser.NEWLINE && (et.contains(Python3Parser.FINALLY) || et.contains(Python3Parser.EXCEPT))) {
            return new PIncompleteSourceException("", null, -1);
        }

        if ((tokenId != Python3Parser.INDENT_ERROR && tokenId != Python3Parser.TAB_ERROR && tokenId != Python3Parser.INDENT && tokenId != Python3Parser.DEDENT) &&
                        (et.contains(Python3Parser.INDENT) || et.contains(Python3Parser.NEWLINE) && et.size() == 1)) {
            return new PIncompleteSourceException(message, cause, line);
        }
        return null;
    }

    private static PIncompleteSourceException handleInteractiveException(Recognizer<?, ?> recognizer, Object offendingSymbol) {
        if (isBackslash(offendingSymbol)) {
            return new PIncompleteSourceException("", null, -1);
        }
        Token token = (Token) offendingSymbol;
        int tokenId = token.getType();
        if (tokenId == Python3Parser.LONG_QUOTES1 || tokenId == Python3Parser.LONG_QUOTES2) {
            return new PIncompleteSourceException("", null, -1);
        }
        if (tokenId == Python3Parser.UNKNOWN_CHAR) {
            String text = token.getText();
            if (text.startsWith("\'") || text.startsWith("\"")) {
                return new PIncompleteSourceException("", null, -1);
            }
        }
        if (isOpened(((Python3Parser) recognizer).getTokenStream())) {
            // The assumption is:
            // Look ahead and if there are other tokens (except new lines and EOF),
            // then it's a syntax error and not error of unfinished code.
            IntStream intStream = recognizer.getInputStream();
            int index = 2;
            int id = intStream.LA(index);
            if (((Token) offendingSymbol).getType() == Python3Parser.NEWLINE && id == Python3Parser.EOF) {
                return new PIncompleteSourceException("", null, -1);
            }
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
        private final Token offendingToken;
        private final ErrorType errorType;
        private final CommonTokenStream inputStream;

        public EmptyRecognitionException(ErrorType errorType, String message, Recognizer<?, ?> recognizer, Token offendingToken) {
            super(message, recognizer, offendingToken.getInputStream(), null);
            this.errorType = errorType;
            this.offendingToken = offendingToken;
            if (recognizer.getInputStream() instanceof CommonTokenStream) {
                this.inputStream = (CommonTokenStream) recognizer.getInputStream();
            } else {
                this.inputStream = null;
            }
        }

        public String getPreviousToken() {
            if (inputStream == null) {
                return null;
            }
            int previousTokenIndex = offendingToken.getTokenIndex() - 1;
            if (previousTokenIndex >= 0) {
                return inputStream.get(offendingToken.getTokenIndex() - 1).getText();
            }
            return null;
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
