/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.parser;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;

import com.oracle.graal.python.parser.antlr.DescriptiveBailErrorListener;
import com.oracle.graal.python.runtime.PythonParser.ErrorType;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class PythonErrorStrategy extends DefaultErrorStrategy {
    private static final String LINE_PADDING = "  ";

    @Override
    public void recover(Parser recognizer, RecognitionException e) {
        super.recover(recognizer, e);
    }

    static SourceSection getPosition(Source source, Exception e) {
        RecognitionException r;
        if (e instanceof RecognitionException) {
            r = (RecognitionException) e;
        } else {
            Throwable cause = e.getCause();
            if (cause instanceof RecognitionException) {
                r = (RecognitionException) cause;
            } else {
                return source.createUnavailableSection();
            }
        }
        Token token = r.getOffendingToken();
        return source.createSection(token.getStartIndex(), Math.max(0, token.getStopIndex() - token.getStartIndex()));
    }

    static ErrorType getErrorType(Exception e) {
        if (e instanceof DescriptiveBailErrorListener.EmptyRecognitionException) {
            return ((DescriptiveBailErrorListener.EmptyRecognitionException) e).getErrorType();
        }
        return ErrorType.Generic;
    }

    private static String getTokeLineText(Parser recognizer, Token token) {
        TokenStream tokenStream = recognizer.getTokenStream();
        int index = token.getTokenIndex();
        // search for line start
        int tokenIndex = index;
        int start = 0;
        while (tokenIndex >= 0) {
            Token t = tokenStream.get(tokenIndex);
            if (t.getText().equals("\n")) {
                break;
            }
            start = t.getStartIndex();
            tokenIndex--;
        }

        // search for line stop
        tokenIndex = index;
        int stop = -1;
        while (tokenIndex < tokenStream.size()) {
            Token t = tokenStream.get(tokenIndex);
            stop = t.getStopIndex();
            if (t.getText().equals("\n")) {
                break;
            }
            tokenIndex++;
        }
        return token.getInputStream().getText(Interval.of(start, stop));
    }

    private static void handlePythonSyntaxError(Parser recognizer, RecognitionException e) {
        Token offendingToken = e.getOffendingToken();
        String lineText = getTokeLineText(recognizer, offendingToken);
        String errorMarker = new String(new char[offendingToken.getCharPositionInLine()]).replace('\0', ' ') + "^";
        String pythonSyntaxErrorMessage = "\n" + LINE_PADDING + lineText + "\n" + LINE_PADDING + errorMarker;
        recognizer.notifyErrorListeners(offendingToken, pythonSyntaxErrorMessage, e);
    }

    @Override
    protected void reportInputMismatch(Parser recognizer, InputMismatchException e) {
        handlePythonSyntaxError(recognizer, e);
    }

    @Override
    protected void reportNoViableAlternative(Parser recognizer, NoViableAltException e) {
        handlePythonSyntaxError(recognizer, e);
    }

    @Override
    public void reportError(Parser recognizer, RecognitionException e) {
        handlePythonSyntaxError(recognizer, e);
    }

}
