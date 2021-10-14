/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import java.util.ArrayList;
import com.oracle.graal.python.parser.tokenizer.Token;
import com.oracle.graal.python.parser.tokenizer.Tokenizer;

/**
 *
 * @author petr
 */
public class ParserTokenizer {
    
    private final String code;
    
    private int pos; // position of the mark
    private final ArrayList<Token> tokens;
    
    private final Tokenizer tokenizer;

    public ParserTokenizer(String code) {
        this.code = code;
        this.pos = 0;
        this.tokens = new ArrayList<>();
        this.tokenizer = new Tokenizer(code);
    }
    
    public int mark() {
        return pos;
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
    
    
    public String getText(Token token) {
        // TODO handle this in better way
        return code.substring(token.startOffset, token.endOffset);
    }
    
}
