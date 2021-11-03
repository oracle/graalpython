/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.tokenizer;


public class Token {

    public static enum Kind {
        ENDMARKER,
        NAME,
        NUMBER,
        STRING,
        NEWLINE,
        INDENT,
        DEDENT,
        LPAR,
        RPAR,
        LSQB,
        RSQB,
        COLON,
        COMMA,
        SEMI,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        VBAR,
        AMPER,
        LESS,
        GREATER,
        EQUAL,
        DOT,
        PERCENT,
        LBRACE,
        RBRACE,
        EQEQUAL,
        NOTEQUAL,
        LESSEQUAL,
        GREATEREQUAL,
        TILDE,
        CIRCUMFLEX,
        LEFTSHIFT,
        RIGHTSHIFT,
        DOUBLESTAR,
        PLUSEQUAL,
        MINEQUAL,
        STAREQUAL,
        SLASHEQUAL,
        PERCENTEQUAL,
        AMPEREQUAL,
        VBAREQUAL,
        CIRCUMFLEXEQUAL,
        LEFTSHIFTEQUAL,
        RIGHTSHIFTEQUAL,
        DOUBLESTAREQUAL,
        DOUBLESLASH,
        DOUBLESLASHEQUAL,
        AT,
        ATEQUAL,
        RARROW,
        ELLIPSIS,
        COLONEQUAL,
        OP,
        AWAIT,
        ASYNC,
        TYPE_IGNORE,
        TYPE_COMMENT,
        ERRORTOKEN,
        UNKNOWN
    };

    public final int startOffset;
    public final int endOffset;
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;
    public final Kind type;
    public final String text;
    public final Object extraData;

    public Token(Kind type, int startOffset, int endOffset, int startLine, int startColumn, int endLine, int endColumn, String text) {
        this(type, startOffset, endOffset, startLine, startColumn, endLine, endColumn, text, null);
    }

    public Token(Kind type,
                 int startOffset, int endOffset,
                 int startLine, int startColumn,
                 int endLine, int endColumn,
                 String text,
                 Object extraData) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.type = type;
        this.text = text;
        this.extraData = extraData;
    }

    static Kind oneChar(int c) {
        switch (c) {
            case '%':
                return Kind.PERCENT;
            case '&':
                return Kind.AMPER;
            case '(':
                return Kind.LPAR;
            case ')':
                return Kind.RPAR;
            case '*':
                return Kind.STAR;
            case '+':
                return Kind.PLUS;
            case ',':
                return Kind.COMMA;
            case '-':
                return Kind.MINUS;
            case '.':
                return Kind.DOT;
            case '/':
                return Kind.SLASH;
            case ':':
                return Kind.COLON;
            case ';':
                return Kind.SEMI;
            case '<':
                return Kind.LESS;
            case '=':
                return Kind.EQUAL;
            case '>':
                return Kind.GREATER;
            case '@':
                return Kind.AT;
            case '[':
                return Kind.LSQB;
            case ']':
                return Kind.RSQB;
            case '^':
                return Kind.CIRCUMFLEX;
            case '{':
                return Kind.LBRACE;
            case '|':
                return Kind.VBAR;
            case '}':
                return Kind.RBRACE;
            case '~':
                return Kind.TILDE;
        }
        return Kind.OP;
    }

    static Kind twoChars(int c1, int c2) {
        switch (c1) {
            case '!':
                switch (c2) {
                    case '=':
                        return Kind.NOTEQUAL;
                }
                break;
            case '%':
                switch (c2) {
                    case '=':
                        return Kind.PERCENTEQUAL;
                }
                break;
            case '&':
                switch (c2) {
                    case '=':
                        return Kind.AMPEREQUAL;
                }
                break;
            case '*':
                switch (c2) {
                    case '*':
                        return Kind.DOUBLESTAR;
                    case '=':
                        return Kind.STAREQUAL;
                }
                break;
            case '+':
                switch (c2) {
                    case '=':
                        return Kind.PLUSEQUAL;
                }
                break;
            case '-':
                switch (c2) {
                    case '=':
                        return Kind.MINEQUAL;
                    case '>':
                        return Kind.RARROW;
                }
                break;
            case '/':
                switch (c2) {
                    case '/':
                        return Kind.DOUBLESLASH;
                    case '=':
                        return Kind.SLASHEQUAL;
                }
                break;
            case ':':
                switch (c2) {
                    case '=':
                        return Kind.COLONEQUAL;
                }
                break;
            case '<':
                switch (c2) {
                    case '<':
                        return Kind.LEFTSHIFT;
                    case '=':
                        return Kind.LESSEQUAL;
                    case '>':
                        return Kind.NOTEQUAL;
                }
                break;
            case '=':
                switch (c2) {
                    case '=':
                        return Kind.EQEQUAL;
                }
                break;
            case '>':
                switch (c2) {
                    case '=':
                        return Kind.GREATEREQUAL;
                    case '>':
                        return Kind.RIGHTSHIFT;
                }
                break;
            case '@':
                switch (c2) {
                    case '=':
                        return Kind.ATEQUAL;
                }
                break;
            case '^':
                switch (c2) {
                    case '=':
                        return Kind.CIRCUMFLEXEQUAL;
                }
                break;
            case '|':
                switch (c2) {
                    case '=':
                        return Kind.VBAREQUAL;
                }
                break;
        }
        return Kind.OP;
    }

    static Kind threeChars(int c1, int c2, int c3) {
        switch (c1) {
            case '*':
                switch (c2) {
                    case '*':
                        switch (c3) {
                            case '=':
                                return Kind.DOUBLESTAREQUAL;
                        }
                        break;
                }
                break;
            case '.':
                switch (c2) {
                    case '.':
                        switch (c3) {
                            case '.':
                                return Kind.ELLIPSIS;
                        }
                        break;
                }
                break;
            case '/':
                switch (c2) {
                    case '/':
                        switch (c3) {
                            case '=':
                                return Kind.DOUBLESLASHEQUAL;
                        }
                        break;
                }
                break;
            case '<':
                switch (c2) {
                    case '<':
                        switch (c3) {
                            case '=':
                                return Kind.LEFTSHIFTEQUAL;
                        }
                        break;
                }
                break;
            case '>':
                switch (c2) {
                    case '>':
                        switch (c3) {
                            case '=':
                                return Kind.RIGHTSHIFTEQUAL;
                        }
                        break;
                }
                break;
        }
        return Kind.OP;
    }

}
