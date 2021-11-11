/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.UntypedSSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * From this class is extended the generated parser. It allow access to the
 * tokenizer.  The methods defined in this class are mostly equivalents to those
 * defined in CPython's {@code pegen.c}. This allows us to keep the actions and
 * parser generator very similar to CPython for easier updating in the future.
 */
public abstract class AbstractParser {
    private final ParserTokenizer tokenizer;
    protected final NodeFactory factory;

    protected int level = 0;
    protected boolean callInvalidRules = false;
    private VarLookupSSTNode cachedDummyName;

    protected final RuleResultCache<Object> cache = new RuleResultCache(this);
    protected final Map<Integer, String> comments = new LinkedHashMap<>();

    private final Object[][][] reservedKeywords;
    private final String[] softKeywords;

    protected abstract Object[][][] getReservedKeywords();
    protected abstract String[] getSoftKeywords();

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory) {
        this.tokenizer = tokenizer;
        this.factory = factory;
        this.reservedKeywords = getReservedKeywords();
        this.softKeywords = getSoftKeywords();
    }

    /**
     * Get position in the tokenizer.
     * @return the position in tokenizer.
     */
    public int mark() {
        return tokenizer.mark();
    }

    /**
     * Reset position in the tokenizer
     * @param position where the tokenizer should set the current position
     */
    public void reset(int position) {
        tokenizer.reset(position);
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the
     * expected token, then the current position in tokenizer is changed to the next token.
     * @param tokenKind - the token kind that is expected on the current position
     * @return The expected token or null if the token on the current position is not
     * the expected one.
     */
    public Token expect(int tokenKind) {
        Token token = tokenizer.peekToken();
        if (token.type == tokenKind) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the
     * expected token, then the current position in tokenizer is changed to the next token.
     * @param text - the token on the current position has to have this text
     * @return The expected token or null if the token on the current position is not
     * the expected one.
     */
    public Token expect(String text) {
        Token token = tokenizer.peekToken();
        if (text.equals(tokenizer.getText(token))) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has
     * does not advance the tokenizer, in contrast to {@link expect(int)}.
     */
    protected boolean lookahead(boolean match, int kind) {
        int pos = mark();
        Token token = expect(kind);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has
     * does not advance the tokenizer, in contrast to {@link expect(String)}.
     */
    protected boolean lookahead(boolean match, String text) {
        int pos = mark();
        Token token = expect(text);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Shortcut to Tokenizer.getText(Token)
     * @param token
     * @return
     */
    public String getText(Token token) {
        return tokenizer.getText(token);
    }


    public Token getToken(int pos) {
        if (pos > tokenizer.mark()) {
            throw new RuntimeException("getToken(pos) can be used only for position that is already scanned!");
        }
        int helpPos = mark();
        Token token =  tokenizer.peekToken();
        
        tokenizer.reset(pos);
        return token;
    }

    /**
     * equivalent to PyPegen_fill_token in that it modifies the token
     */
    public Token getAndInitializeToken() {
        int pos = mark();
        Token token = getToken(pos);
        while (token.type == Token.Kind.TYPE_IGNORE) {
            String tag = getText(token);
            comments.put(token.startLine, tag);
            token = getToken(pos);
        }

        // TODO: handle reaching end in single_input mode
        
        return initializeToken(token);
    }

    public Token getLastNonWhitespaceToken() {
        Token t = null;
        for (int i = mark() - 1; i >= 0; i--) {
            t = tokenizer.peekToken(i);
            if (t.type != Token.Kind.ENDMARKER && (t.type < Token.Kind.NEWLINE || t.type > Token.Kind.DEDENT)) {
                break;
            }
        }
        return t;
    }

    public SSTNode name_token() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {            
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    protected SSTNode expect_SOFT_KEYWORD(String keyword) {
        Token t = tokenizer.peekToken();
        if (t.type == Token.Kind.NAME && getText(t).equals(keyword)) {
            tokenizer.getToken();
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        }
        return null;
    }

    /**
     * IMPORTANT! _PyPegen_string_token returns (through void*) a Token*. We are
     * trying to be type safe, so we create a container.
     */
    public SSTNode string_token() {
        Token t = expect(Token.Kind.STRING);
        if (t == null) {
            return null;
        }
        int pos = mark();
        assert tokenizer.peekToken(pos) == t;
        return factory.createUntyped(pos);
    }

    public SSTNode number_token() {
        Token t = expect(Token.Kind.NUMBER);
        if (t != null) {            
            return factory.createNumber(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    public Token expect_forced_token(int kind, String msg) {
        Token t = getAndInitializeToken();
        if (t.type != kind) {
            // TODO: raise error
            return null;
        }
        return t;
    }

    public SSTNode name_from_token(Token t) {
        if (t == null) {
            return null;
        }
        String id = getText(t);
        return factory.createVariable(id, t.startOffset, t.endOffset);
    }

    public SSTNode soft_keyword_token() {
        Token t = expect(Token.Kind.NAME);
        if (t == null) {
            return null;
        }
        String txt = getText(t);
        for (String s : softKeywords) {
            if (s.equals(txt)) {
                return name_from_token(t);
            }
        }
        return null;
    }

    public SSTNode dummyName(Object... args) {
        if (cachedDummyName != null) {
            return cachedDummyName;
        }
        cachedDummyName = factory.createVariable("", 0, 0);
        return cachedDummyName;
    }

    public Object[] insertInFront(Object element, Object[] seq) {
        Object[] result = new Object[seq.length + 1];
        System.arraycopy(seq, 0, result, 1, seq.length);
        result[0] = element;
        return result;
    }

    public Object[] appendToEnd(Object[] seq, Object element) {
        Object[] result = Arrays.copyOf(seq, seq.length + 1);
        result[seq.length] = element;
        return result;
    }

    public Object[] singletonSequence(Object element) {
        return new Object[]{element};
    }

    public SSTNode concatenateStrings(SSTNode[] tokens) {
        // TODO: finish
        StringBuilder sb = null;
        String result = null;
        int start = -1;
        int end = -1;
        for (SSTNode n : tokens) {
            Token t = tokenizer.peekToken(((UntypedSSTNode)n).getTokenPosition());
            String s = getText(t);
            if (start < 0) {
                start = t.startOffset;
            }
            end = t.endOffset;
            if (result != null) {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(result);
            }
            result = s;
        }
        if (sb != null) {
            sb.append(result);
            return factory.createString(sb.toString(), start, end);
        } else {
            return factory.createString(result, start, end);
        }
    }

    /**
     * equivalent to initialize_token
     */
    private Token initializeToken(Token token) {
        if (token.type == Token.Kind.NAME) {
            String txt = getText(token);
            int l = txt.length();
            Object[][] kwlist;
            if (l < reservedKeywords.length && (kwlist = reservedKeywords[l]) != null) {
                for (Object[] kwAssoc : kwlist) {
                    if (txt.equals(kwAssoc[0])) {
                        token.type = (int)kwAssoc[1];
                    }
                }
            }
        }
        return token;
    }

    // debug methods
    private void indent(StringBuffer sb) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    void debugMessage(String text) {
        debugMessage(text, true);
    }

    void debugMessage(String text, boolean indent) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(text);
        System.out.print(sb.toString());
    }

    void debugMessageln(String text, Object... args) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(String.format(text, args));
        System.out.println(sb.toString());
    }
}
