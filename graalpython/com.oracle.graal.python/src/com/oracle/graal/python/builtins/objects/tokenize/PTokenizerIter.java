/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.tokenize;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.EnumSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.tokenizer.CodePoints;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Token.Kind;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.Flag;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public final class PTokenizerIter extends PythonBuiltinObject {

    private final RaisePythonExceptionErrorCallback errorCallback;
    final Tokenizer tokenizer;
    private int lastLineNo = -1;
    private TruffleString lastLine;
    private boolean done;
    private Source cachedSource;

    @TruffleBoundary
    public PTokenizerIter(Object cls, Shape instanceShape, Supplier<int[]> inputSupplier, boolean extraTokens) {
        super(cls, instanceShape);
        errorCallback = new RaisePythonExceptionErrorCallback(this::getSource, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(null)));
        EnumSet<Flag> flags = EnumSet.of(Flag.EXEC_INPUT);
        if (extraTokens) {
            flags.add(Flag.EXTRA_TOKENS);
        }
        tokenizer = Tokenizer.fromReadline(errorCallback, flags, inputSupplier);
    }

    boolean isDone() {
        return done;
    }

    @TruffleBoundary
    Token getNextToken() {
        Token token = tokenizer.next();
        errorCallback.triggerAndClearDeprecationWarnings();
        if (token.type == Kind.ERRORTOKEN) {
            reportTokenizerError();
        }
        if (token.type == Kind.ENDMARKER) {
            done = true;
        }
        return token;
    }

    @TruffleBoundary
    CodePoints getTokenCodePoints(Token token) {
        return tokenizer.getTokenCodePoints(token);
    }

    TruffleString getLine(Token token, TruffleString.FromIntArrayUTF32Node fromIntArrayUTF32Node, TruffleString.SwitchEncodingNode switchEncodingNode) {
        if (tokenizer.getCurrentLineNumber() == lastLineNo) {
            return lastLine;
        }
        CodePoints line = tokenizer.getTokenLine(token.type);
        if (line.isEmpty()) {
            lastLine = TS_ENCODING.getEmpty();
        } else {
            lastLine = switchEncodingNode.execute(fromIntArrayUTF32Node.execute(line.getBuffer(), line.getOffset(), line.getLength()), TS_ENCODING);
        }
        lastLineNo = tokenizer.getCurrentLineNumber();
        return lastLine;
    }

    private void reportTokenizerError() {
        ErrorCallback.ErrorType errorType = ErrorCallback.ErrorType.Syntax;
        String msg;
        int colOffset = Math.max(0, tokenizer.getCodePointsInputLength() - tokenizer.getLineStartIndex() - 1);
        switch (tokenizer.getDone()) {
            case BAD_TOKEN:
                msg = "invalid token";
                break;
            case EOF:
                msg = "unexpected EOF in multi-line statement";
                break;
            case DEDENT_INVALID:
                errorType = ErrorCallback.ErrorType.Indentation;
                msg = "unindent does not match any outer indentation level";
                break;
            case TABS_SPACES_INCONSISTENT:
                errorType = ErrorCallback.ErrorType.Tab;
                msg = "inconsistent use of tabs and spaces in indentation";
                break;
            case TOO_DEEP_INDENTATION:
                errorType = ErrorCallback.ErrorType.Indentation;
                msg = "too many levels of indentation";
                break;
            case LINE_CONTINUATION_ERROR:
                msg = "unexpected character after line continuation character";
                break;
            default:
                msg = "unknown tokenization error";
                break;
        }
        throw errorCallback.raiseSyntaxError(errorType, new SourceRange(tokenizer.getCurrentLineNumber(), colOffset, -1, -1), msg);
    }

    private Source getSource() {
        if (cachedSource == null) {
            String src = tokenizer.getCodePointsInput().toJavaString();
            cachedSource = Source.newBuilder(PythonLanguage.ID, src, "<string>").build();
        }
        return cachedSource;
    }

}
