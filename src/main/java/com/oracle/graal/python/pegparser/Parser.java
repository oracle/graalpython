/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * From this class is extended the generated parser. It allow access to the tokenizer.
 */
public abstract class Parser {
    private final ParserTokenizer tokenizer;
    protected final NodeFactory factory;

    protected int level = 0;
    protected boolean callInvalidRules = false;
    protected final RuleResultCache<Object> cache = new RuleResultCache(this);
    protected final Set<String> softKeywords = new HashSet<>();
    protected final List<Map<String, Integer>> reservedKeywords = new ArrayList<>();

    public Parser(ParserTokenizer tokenizer, NodeFactory factory) {
        this.tokenizer = tokenizer;
        this.factory = factory;
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

    protected Token expect_SOFT_KEYWORD() {
        Token t = expect(Token.Kind.SOFT_KEYWORD);
        if (t != null) {
            String text = getText(t);
            if (softKeywords.contains(text)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public Token getToken(int pos) {
        Token token = super.getToken(pos);
        if (token.type == Token.Kind.NAME) {
            int len = token.endOffset - token.startColumn;
            if (len < reserved_keywords.length) {
                Map<String, Integer> keywords = reserved_keywords[len];
                if (keywords != null && keywords.containsKey(getText(token))) {
                    //TODO we should here change the kind to the keyword.
                }
            }
        }
        return token;
    }

}
