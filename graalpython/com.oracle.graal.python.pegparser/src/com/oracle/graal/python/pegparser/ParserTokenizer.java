/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

import java.util.ArrayList;
import java.util.EnumSet;

import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;

public class ParserTokenizer {
    private int pos; // position of the mark
    private final ArrayList<Token> tokens;

    private final Tokenizer tokenizer;

    public ParserTokenizer(ErrorCallback errorCallback, String code, InputType type, boolean interactiveTerminal) {
        this.pos = 0;
        this.tokens = new ArrayList<>();
        this.tokenizer = Tokenizer.fromString(errorCallback, code, getTokenizerFlags(type, interactiveTerminal));
    }

    public ParserTokenizer(ErrorCallback errorCallback, byte[] code, InputType type, boolean interactiveTerminal) {
        this.pos = 0;
        this.tokens = new ArrayList<>();
        this.tokenizer = Tokenizer.fromBytes(errorCallback, code, getTokenizerFlags(type, interactiveTerminal));
    }

    private static EnumSet<Tokenizer.Flag> getTokenizerFlags(InputType type, boolean interactiveTerminal) {
        EnumSet<Tokenizer.Flag> flags = EnumSet.of(Tokenizer.Flag.TYPE_COMMENT);
        if (type == InputType.FILE) {
            flags.add(Tokenizer.Flag.EXEC_INPUT);
        } else if (type == InputType.SINGLE && interactiveTerminal) {
            flags.add(Tokenizer.Flag.INTERACTIVE);
        }
        return flags;
    }

    public int mark() {
        return pos;
    }

    public void resetState() {
        pos = 0;
        tokenizer.reportIncompleteSourceIfInteractive = false;
    }

    public void reset(int position) {
        pos = position;
    }

    public Token getToken() {
        Token token = peekToken();
        pos++;
        return token;
    }

    protected Token peekToken() {
        if (pos == tokens.size()) {
            tokens.add(tokenizer.next());
        }
        return tokens.get(pos);
    }

    protected Token peekToken(int position) {
        assert position < tokens.size();
        return tokens.get(position);
    }

    protected int getFill() {
        return tokens.size();
    }

    public String getText(Token token) {
        return tokenizer.getTokenString(token);
    }

    SourceRange extendRangeToCurrentPosition(SourceRange rangeStart) {
        return tokenizer.extendRangeToCurrentPosition(rangeStart);
    }
}
