/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.tokenizer;


public class Tokenizer {

    private final String text;
    private static final int EOF = -1;
    private boolean isEOF = false;
    private int current = 0;

    private int startOffset;
    private int lineNumber = 1; // we count lines from one
    private int startCurrentLineOffset;
    private boolean newLineBeforeEOF = false; // is triggerd new line before EOF?
    private static final String EMPTY_STRING = "";

    public Tokenizer(String code) {
        this.text = code;
    }

    public String getTokenString(Token token) {
        if (text.length() <= token.startOffset ||  text.length() < token.endOffset) {
            return EMPTY_STRING;
        }
        return text.substring(token.startOffset, token.endOffset);
    }

    int nextChar() {
        if (current < text.length()) {
            int c = text.charAt(current);
            // new line
            if (c == '\n') {
                lineNumber++;
                startCurrentLineOffset = current;
            }
            current++;
            return c;
        } else {
            if (current == text.length()) {
                int index = text.length() - 1;
                int c = -1;
                while(index >= 0) {
                    c = text.charAt(index);
                    if (!Character.isWhitespace(c) || c == '\n') {
                        break;
                    }
                    index++;
                }
                if (c != '\n') {
                    // report new line before eof
                    current++;
                    return '\n';
                }
            }
            if (!isEOF) {
                // the first EOF is on the new line
                lineNumber++;
                startCurrentLineOffset = current;
            }
            isEOF = true;
            return EOF;
        }
    }

    private static final int STATE_INIT = 0;
    private static final int STATE_FRACTION = 1;
    private static final int STATE_AFTER_FRACTION = 2;
    private static final int STATE_STRING = 3;

    public Token next() {

        int c = 0;
        int state = STATE_INIT;

        while (true) {
            switch (state) {
                case STATE_INIT:
                    // skip spaces
                    do {
                        c = nextChar();
                    } while (c == ' ' || c == '\t' || c == '\014');

                    startOffset = current - 1;

                    // TODO Skip comments
                    // check EOF
                    if (c == EOF) {
                        // TODO return EOF Token
                        return new Token(Token.Kind.ENDMARKER, startOffset, startOffset, lineNumber, 0, lineNumber, 0);
                    }

                    // identifier
                    if (isIdentifierStart(c)) {
                        // check combinations of b"", r"", u"" and f""
                        boolean sawb = false;
                        boolean sawr = false;
                        boolean sawu = false;
                        boolean sawf = false;

                        while (true) {
                            if (!(sawb || sawu || sawf) && (c == 'b' || c == 'B')) {
                                sawb = true;
                            } else if (!(sawb || sawu || sawr || sawf) && (c == 'u' || c == 'U')) {
                                sawu = true;
                            } else if (!(sawr || sawu) && (c == 'r' || c == 'R')) {
                                sawr = true;
                            } else if (!(sawf || sawb || sawu) && (c == 'f' || c == 'F')) {
                                sawf = true;
                            } else {
                                break;
                            }
                            c = nextChar();
                            if (c == '"' || c == '\'') {
                                state = STATE_STRING;
                                break;
                            }
                        }
                        if (state != STATE_INIT) {
                            break;
                        }
                        while (isIdentifierChar(c)) {
                            c = nextChar();
                        }

                        oneBack();

                        Token tok = createToken(Token.Kind.NAME);
                        String tokenString = getTokenString(tok);
                        if (!verifyIdentifier(tok, tokenString)) {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }

                        if (tokenString.equals("async")) {
                            return createToken(Token.Kind.ASYNC);
                        }
                        if (tokenString.equals("await")) {
                            return createToken(Token.Kind.AWAIT);
                        }

                        return tok;
                    }

                    // TODO new line
                    if (c == '\n') {
                        return createToken(Token.Kind.NEWLINE);
                    }
                    // TODO period
                    if (c == '.') {
                        c = nextChar();
                        if (Character.isDigit(c)) {
                            state = STATE_FRACTION;
                            break;
                        }
                    }
                    // TODO Number
                    if (Character.isDigit(c)) {
                        if (c == '0') {
                            // it can be hex, octal or binary
                            c = nextChar();
                            if (c == 'x' || c == 'X') {
                                c = nextChar();
                                do {
                                    if (c == '_') {
                                        c = nextChar();
                                    }
                                    if (!isHexDegit(c)) {
                                        oneBack();
                                        // TODO handle syntax error: "invalid hexadecimal literal"
                                        return createToken(Token.Kind.ERRORTOKEN);
                                    }
                                    do {
                                        c = nextChar();
                                    } while (isHexDegit(c));
                                } while (c == '_');

                            } else if (c == 'o' || c == 'O') {
                                // octal
                                c = nextChar();
                                do {
                                    if (c == '_') {
                                        c = nextChar();
                                    }
                                    if (c < '0' || c >= '8') {
                                        oneBack();
                                        if (Character.isDigit(0)) {
                                            // TODO handle syntax error: "invalid digit '%c' in octal literal"
                                            return createToken(Token.Kind.ERRORTOKEN);
                                        } else {
                                            // TODO handle syntax error: "invalid octal literal"
                                            return createToken(Token.Kind.ERRORTOKEN);
                                        }
                                    }
                                    do {
                                        c = nextChar();
                                    } while ('0' <= c && c < '8');
                                } while (c == '_');
                                if (Character.isDigit(c)) {
                                    // TODO handle syntax error: "invalid digit '%c' in octal literal"
                                    return createToken(Token.Kind.ERRORTOKEN);
                                }
                            } else if (c == 'b' || c == 'B') {
                                // binary
                                c = nextChar();
                                do {
                                    if (c == '_') {
                                        c = nextChar();
                                    }
                                    if (c != '0' && c != '1') {
                                        oneBack();
                                        if (Character.isDigit(0)) {
                                            // TODO handle syntax error: "invalid digit '%c' in binary literal"
                                            return createToken(Token.Kind.ERRORTOKEN);
                                        } else {
                                            // TODO handle syntax error: "invalid binary literal"
                                            return createToken(Token.Kind.ERRORTOKEN);
                                        }
                                    }
                                    do {
                                        c = nextChar();
                                    } while (c == '0' || c == '1');
                                } while (c == '_');
                                if (Character.isDigit(c)) {
                                    // TODO handle syntax error: "invalid digit '%c' in binary literal"
                                    return createToken(Token.Kind.ERRORTOKEN);
                                }
                            } else {
                                boolean nonzero = false;
                                while (true) {
                                    if (c == '_') {
                                        c = nextChar();
                                        if (!Character.isDigit(c)) {
                                            oneBack();
                                            // TODO handle syntax error: "invalid decimal literal"
                                            return createToken(Token.Kind.ERRORTOKEN);
                                        }
                                    }
                                    if (c != '0') {
                                        break;
                                    }
                                    c = nextChar();
                                }
                                // TODO finish this branch
                            }
                        } else {
                            // Decimal
                            if ((c = readDecimalTail()) == 0) {
                                return createToken(Token.Kind.ERRORTOKEN);
                            }

                            if (c == '.') {
                                c = nextChar();
                                state = STATE_FRACTION;
                                break;
                            }
                            state = STATE_AFTER_FRACTION;
                            break;
                        }
                        oneBack();
                        return createToken(Token.Kind.NUMBER);
                    }

                    // TODO String
                    if (c == '\'' || c == '"') {
                        state = STATE_STRING;
                        break;
                    }
                    // TODO Line continuation
                    // TODO two char token
                    int c2 = nextChar();
                    Token.Kind kind2 = Token.twoChars(c, c2);
                    if (kind2 != Token.Kind.OP) {
                        int c3 = nextChar();
                        Token.Kind kind3 = Token.threeChars(c, c2, c3);
                        if (kind3 != Token.Kind.OP) {
                            return createToken(kind3);
                        } else {
                            oneBack();
                        }
                        return createToken(kind2);
                    }
                    oneBack();

                    // TODO check parenthesis and nesting level
                    // one character token
                    Token.Kind kind = Token.oneChar(c);
                    if (kind != Token.Kind.OP) {
                        return createToken(kind);
                    }
                    break;
                case STATE_FRACTION:
                    // fraction
                    if (Character.isDigit(c)) {
                        if ((c = readDecimalTail()) == 0) {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }

                    }
                    state = STATE_AFTER_FRACTION;
                    break;
                case STATE_AFTER_FRACTION:
                    if (c == 'e' || c == 'E') {
                        // exponent part
                        int e = c;
                        c = nextChar();
                        if (c == '+' || c == '-') {
                            c = nextChar();
                            if (!Character.isDigit(c)) {
                                oneBack();
                                // TODO handle syntax error: "invalid decimal literal"
                                return createToken(Token.Kind.ERRORTOKEN);
                            }
                        } else if (!Character.isDigit(c)) {
                            oneBack();
                            oneBack();
                            return createToken(Token.Kind.NUMBER);
                        }
                        if ((c = readDecimalTail()) == 0) {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                    }
                    if (c == 'j' || c == 'J') {
                        // imaginary
                        c = nextChar();
                    }
                    oneBack();
                    return createToken(Token.Kind.NUMBER);
                case STATE_STRING:
                    int quote = c;
                    int quoteSize = 1; // 1 or 3
                    int endQuateSize = 0;
                    c = nextChar();
                    if (c == quote) {
                        c = nextChar();
                        if (c == quote) {
                            quoteSize = 3;
                        } else {
                            endQuateSize = 1; // empty string
                        }
                    }
                    if (c != quote) {
                        oneBack();
                    }
                    // rest of string
                    while (endQuateSize != quoteSize) {
                        c = nextChar();
                        if (c == EOF) {
                            // TODO handle EOF and EOL
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                        if (quoteSize == 1 && c == '\n') {
                            // TODO handle EOL
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                        if (c == quote) {
                            endQuateSize++;
                        } else {
                            endQuateSize = 0;
                            if (c == '\\') {
                                nextChar(); // just skip escaped char
                            }
                        }
                    }
                    return createToken(Token.Kind.STRING);
            }
        }
    }

    private boolean isIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_' || c >= 128;
    }

    private boolean isIdentifierChar(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c >= 128;
    }

    private boolean isHexDegit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void oneBack() {
        if (current > 0 && !isEOF) {
            current--;
        }
    }

    private int readDecimalTail() {
        int c;

        while (true) {
            do {
                c = nextChar();
            } while (Character.isDigit(c));
            if (c != '_') {
                break;
            }
            c = nextChar();
            if (!Character.isDigit(c)) {
                oneBack();
                // TODO syntax error : invalid decimal literal
                return 0;
            }
        }
        return c;
    }

    private Token createToken(Token.Kind kind) {
        return new Token(kind, startOffset, current, lineNumber, startOffset - startCurrentLineOffset, lineNumber, current - startCurrentLineOffset);
    }

    public String toString(Token token) {
        StringBuilder sb = new StringBuilder();
        sb.append("Token ");
        sb.append(token.type.name());
        sb.append(" [").append(token.startOffset).append(", ").append(token.endOffset).append("]");
        sb.append(" (").append(token.startLine).append(", ").append(token.startColumn);
        sb.append(") (").append(token.endLine).append(", ").append(token.endColumn).append(") '");
        sb.append(getTokenString(token)).append("'");
        return sb.toString();
    }

    private boolean verifyIdentifier(Token tok, String tokenString) {
        // inlined the logic from _PyUnicode_ScanIdentifier
        int invalid = tokenString.length();
        if (!Character.isJavaIdentifierStart(tokenString.codePointAt(0))) {
            invalid = 0;
        }
        for (int i = 1; i < invalid; i++) {
            if (!Character.isJavaIdentifierPart(tokenString.codePointAt(i))) {
                invalid = i;
                break;
            }
        }
        if (invalid < tokenString.length()) {
            int codePoint = tokenString.codePointAt(invalid);
            String printString = new String(new int[] { codePoint }, 0, 1);
            // TODO: communicate syntax error
            // syntaxerror(tok, "invalid character '%s' (U+%x)", printString, codePoint);
            return false;
        }
        return true;
    }
}
