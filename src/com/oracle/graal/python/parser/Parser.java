/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.parser.tokenizer.Token;

/**
 * From this class is extended the generated parser. It allow access to the tokenizer. 
 */
public class Parser {

    private final ParserTokenizer tokenizer;
    protected final NodeFactory factory;
    
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
    public Token expect(Token.Kind tokenKind) {
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

}
