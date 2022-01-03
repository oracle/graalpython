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

import com.oracle.graal.python.pegparser.sst.CollectionSSTNode;
import com.oracle.graal.python.pegparser.sst.GetAttributeSSTNode;
import com.oracle.graal.python.pegparser.sst.KeyValueSSTNode;
import com.oracle.graal.python.pegparser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StarSSTNode;
import com.oracle.graal.python.pegparser.sst.SubscriptSSTNode;
import com.oracle.graal.python.pegparser.sst.UntypedSSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * From this class is extended the generated parser. It allow access to the
 * tokenizer.  The methods defined in this class are mostly equivalents to those
 * defined in CPython's {@code pegen.c}. This allows us to keep the actions and
 * parser generator very similar to CPython for easier updating in the future.
 */
abstract class AbstractParser {
    private final ParserTokenizer tokenizer;
    private final ParserErrorCallback errorCb;
    private final FExprParser fexprParser;
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

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, ParserErrorCallback errorCb) {
        this.tokenizer = tokenizer;
        this.factory = factory;
        this.fexprParser = fexprParser;
        this.errorCb = errorCb;
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
        Token token = getAndInitializeToken();
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

    /**
     * equivalent to _PyPegen_fill_token in that it modifies the token, and does not advance
     */
    public Token getAndInitializeToken() {
        int pos = mark();
        Token token = tokenizer.getToken();
        while (token.type == Token.Kind.TYPE_IGNORE) {
            String tag = getText(token);
            comments.put(token.startLine, tag);
            pos++;
            token = tokenizer.getToken();
        }
        reset(pos);

        // TODO: handle reaching end in single_input mode
        return initializeToken(token);
    }

    /**
     * _PyPegen_get_last_nonnwhitespace_token
     */
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

    /**
     * _PyPegen_name_token
     */
    public VarLookupSSTNode name_token() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    /**
     * _PyPegen_expect_soft_keyword
     */
    protected VarLookupSSTNode expect_SOFT_KEYWORD(String keyword) {
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
        int pos = mark();
        Token t = expect(Token.Kind.STRING);
        if (t == null) {
            return null;
        }
        assert tokenizer.peekToken(pos) == t : ("token at " + pos + " is not equal to " + t);
        return factory.createUntyped(pos);
    }

    /**
     * _PyPegen_number_token
     */
    public NumberLiteralSSTNode number_token() {
        Token t = expect(Token.Kind.NUMBER);
        if (t != null) {
            return factory.createNumber(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    /**
     * _PyPegen_expect_forced_token
     */
    public Token expect_forced_token(int kind, String msg) {
        Token t = getAndInitializeToken();
        if (t.type != kind) {
            // TODO: raise error
            return null;
        }
        tokenizer.getToken(); // advance
        return t;
    }

    public VarLookupSSTNode name_from_token(Token t) {
        if (t == null) {
            return null;
        }
        String id = getText(t);
        return factory.createVariable(id, t.startOffset, t.endOffset);
    }

    /**
     * _PyPegen_soft_keyword_token
     */
    public VarLookupSSTNode soft_keyword_token() {
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

    /**
     * _PyPegen_dummy_name
     */
    public SSTNode dummyName(Object... args) {
        if (cachedDummyName != null) {
            return cachedDummyName;
        }
        cachedDummyName = factory.createVariable("", 0, 0);
        return cachedDummyName;
    }

    /**
     * _PyPegen_join_names_with_dot
     */
    public SSTNode joinNamesWithDot(VarLookupSSTNode a, VarLookupSSTNode b) {
        String id = a.getName() + "." + b.getName();
        return factory.createVariable(id, a.getStartOffset(), b.getEndOffset());
    }

    /**
     * _PyPegen_seq_insert_in_front
     */
    @SuppressWarnings("unchecked")
    public <T> T[] insertInFront(T element, T[] seq) {
        T[] result;
        if (seq == null) {
            result = (T[])Array.newInstance(element.getClass(), 1);
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            System.arraycopy(seq, 0, result, 1, seq.length);
        }
        result[0] = element;
        return result;
    }

    /**
     * _PyPegen_seq_append_to_end
     */
    @SuppressWarnings("unchecked")
    public <T> T[] appendToEnd(T[] seq, T element) {
        T[] result;
        if (seq == null) {
            result = (T[])Array.newInstance(element.getClass(), 1);
            result[0] = element;
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            System.arraycopy(seq, 0, result, 1, seq.length);
            result[seq.length] = element;
        }
        return result;
    }

    /**
     * _PyPegen_singleton_seq
     */
    @SuppressWarnings("unchecked")
    public <T> T[] singletonSequence(T element) {
        T[] result = (T[])Array.newInstance(element.getClass(), 1);
        result[0] = element;
        return result;
    }

    /**
     * _PyPegen_concatenate_strings
     */
    public SSTNode concatenateStrings(SSTNode[] tokens) {
        int n = tokens.length;
        String[] values = new String[n];
        Token t = tokenizer.peekToken(((UntypedSSTNode)tokens[0]).getTokenPosition());
        int startOffset = t.startOffset;
        values[0] = getText(t);
        for (int i = 1; i < n; i++) {
            t = tokenizer.peekToken(((UntypedSSTNode)tokens[i]).getTokenPosition());
            values[i] = getText(t);
        }
        int endOffset = t.endOffset;
        return factory.createString(values, startOffset, endOffset, fexprParser, errorCb);
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
                        break;
                    }
                }
            }
        }
        return token;
    }

    /**
     * _PyPegen_new_type_comment
     */
    protected SSTNode newTypeComment(Token token) {
        // FIXME: this is creating an SSTNode, the Python parser just creates a String from the text
        return token != null ? factory.createTypeComment(getText(token), token.startOffset, token.endOffset) : null;
    }

    private int getKeywordArgsCount(SSTNode[] argsOrKeywordArgs) {
        int count = 0;
        if (argsOrKeywordArgs != null && argsOrKeywordArgs.length > 0) {
            for (SSTNode argsOrKeywprdArg : argsOrKeywordArgs) {
                if (argsOrKeywprdArg instanceof KeyValueSSTNode) {
                    count++;
                }
            }
        }
        return count;
    }

    protected SSTNode[] extractArgs(SSTNode[] argsOrKeywordArgs) {
        int keywords = getKeywordArgsCount(argsOrKeywordArgs);
        if (keywords == 0) {
            return argsOrKeywordArgs;
        }
        SSTNode[] result = new SSTNode[argsOrKeywordArgs.length - keywords];
        int i = 0;
        for (SSTNode node : argsOrKeywordArgs) {
            if (!(node instanceof KeyValueSSTNode)) {
                result[i++] = node;
            }
        }
        return result;
    }

    protected SSTNode[] extractKeywordArgs(SSTNode[] argsOrKeywordArgs) {
        int keywords = getKeywordArgsCount(argsOrKeywordArgs);
        if (keywords == 0) {
            return null;
        }
        SSTNode[] result = new SSTNode[keywords];
        int i = 0;
        for (SSTNode node : argsOrKeywordArgs) {
            if (node instanceof KeyValueSSTNode) {
                result[i++] = node;
            }
        }
        return result;
    }

    /**
     * _PyPegen_join_sequences
     *
     */
    protected <T> T[] join(T[] a , T[]b) {
        if (a == null && b != null) {
            return b;
        }
        if (a != null && b == null) {
            return a;
        }

        if (a != null && b != null) {
            T[] result = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
        return null;
    }

    /**
     * _PyPegen_set_expr_context
     *
     * TODO: (tfel) We should try to avoid having to walk the parse tree so often. The git history
     * includes an attempt with a symbol and a scope stream synchronized to the token stream, but
     * it doesn't really work with the pegen generator.
     */
    protected SSTNode setExprContext(SSTNode node, ExprContext context) {
        if (node instanceof VarLookupSSTNode) {
            return factory.createVariable(((VarLookupSSTNode) node).getName(), node.getStartOffset(), node.getEndOffset(), context);
        } else if (node instanceof CollectionSSTNode) {
            CollectionSSTNode.Type type = ((CollectionSSTNode) node).getType();
            if (type == CollectionSSTNode.Type.Tuple || type == CollectionSSTNode.Type.List) {
                SSTNode[] values = ((CollectionSSTNode) node).getValues();
                for (int i = 0; i < values.length; i++) {
                    values[i] = setExprContext(values[i], context);
                }
                int start = node.getStartOffset();
                int end = node.getEndOffset();
                if (type == CollectionSSTNode.Type.Tuple) {
                    return factory.createTuple(values, start, end);
                } else {
                    return factory.createList(values, start, end);
                }
            }
        } else if (node instanceof SubscriptSSTNode) {
            return factory.createSubscript(setExprContext(((SubscriptSSTNode) node).getReceiver(), context),
                            setExprContext(((SubscriptSSTNode) node).getSubscript(), context),
                            node.getStartOffset(), node.getEndOffset());
        } else if (node instanceof GetAttributeSSTNode) {
            return factory.createGetAttribute(setExprContext(((GetAttributeSSTNode) node).getReceiver(), context),
                            ((GetAttributeSSTNode) node).getName(),
                            node.getStartOffset(), node.getEndOffset());
        } else if (node instanceof StarSSTNode) {
            // todo
            throw new RuntimeException("missing");
        }
        return node;
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
