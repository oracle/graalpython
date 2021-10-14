/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.parser.tokenizer.Token;

public class Parser {

    private final ParserTokenizer tokenizer;
    
    public Parser(ParserTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public int mark() {
        return tokenizer.mark();
    }

    public void reset(int position) {
        tokenizer.reset(position);
    }

    public Token expect(Token.Kind tokenKind) {
        Token token = tokenizer.peekToken();
        if (token.type == tokenKind) {
            return tokenizer.getToken();
        }
        return null;
    }

    public Token expect(String text) {
        Token token = tokenizer.peekToken();
        if (text.equals(tokenizer.getText(token))) {
            return tokenizer.getToken();
        }
        return null;
    }

}
