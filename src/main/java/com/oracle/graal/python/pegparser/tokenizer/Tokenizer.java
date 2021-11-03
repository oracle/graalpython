/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.tokenizer;

import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.DecodingState;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public class Tokenizer {
    private static final int EOF = -1;
    private static final int ALTTABSIZE = 1;

    private static boolean isPotentialIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_' || c >= 128;
    }

    private static boolean isPotentialIdentifierChar(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c >= 128;
    }

    private static final int TABSIZE = 8;

    private static final String typeCommentPrefix = "# type: ";

    public static enum StatusCode {
        OK,
        EOF,
        INTERRUPTED,
        BAD_TOKEN,
        SYNTAX_ERROR,
        OUT_OF_MEMORY,
        DONE,
        EXECUTION_ERROR,
        TABS_SPACES_INCONSISTENT,
        NODE_OVERFLOW,
        TOO_DEEP_INDENTATION,
        DEDENT_INVALID,
        UNICODE_DECODE_ERROR,
        LINE_CONTINUATION_ERROR,
        BAD_SINGLE_STATEMENT,
        INTERACTIVE_STOP;
    }

    public static enum DecodingState {
        STATE_INIT,
        STATE_SEEK_CODING,
        STATE_NORMAL;
    }

    private final ByteArrayInputStream originalInputStream;
    private BufferedReader buffer;

    private final boolean interactive;
    private StatusCode done = StatusCode.OK;
    private int tabSize = TABSIZE;
    private int currentIndentIndex = 0;
    private int[] indentationStack = new int[100];
    private int[] altIndentationStack = new int[100];
    private boolean atBeginningOfLine = false;
    private int pendingIndents = 0;
    private String prompt;
    private String nextPrompt;
    private int currentLineNumber = 1; // we count lines from one
    private int parensNestingLevel = 0;
    private DecodingState state = DecodingState.STATE_INIT;
    private boolean decodingError = false;
    private Charset currentTokenEncoding;
    private Charset fileEncoding;
    private boolean inContinuationLine = false;
    private String filename;
    private boolean lookForTypeComments;
    private boolean insideAsyncDef = false;
    private int indetationOfAsyncDef = 0;
    private boolean asyncDefFollowedByNewline = false;

    public Tokenizer(String code) {
        this(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)), false, false);
    }

    public Tokenizer(byte[] code) {
        this(new ByteArrayInputStream(code), false, true);
    }

    /**
     * Constructor mimics CPython's tokenizer.c decode_str
     */
    private Tokenizer(ByteArrayInputStream stream, boolean interactive, boolean mustCheckBOM) {
        this.originalInputStream = stream;
        if (mustCheckBOM) {
            this.fileEncoding = checkBOM(stream);
        }
        byte[] line1 = null;
        byte[] line2 = null;
        ByteArrayOutputStream sb = new ByteArrayOutputStream();
        stream.mark(0);
        int b;
        while ((b = stream.read()) != '\n' && b != EOF) {
            sb.write(b);
        }
        line1 = sb.toByteArray();
        sb.reset();
        while ((b = stream.read()) != '\n' && b != EOF) {
            sb.write(b);
        }
        line2 = sb.toByteArray();
        stream.reset();

        fileEncoding = null;
        checkCodingSpec(line1);
        if (fileEncoding == null && state != DecodingState.STATE_NORMAL) {
            checkCodingSpec(line2);
        }
        if (fileEncoding == null) {
            fileEncoding = StandardCharsets.UTF_8;
        }
        if (buffer == null) {
            buffer = new BufferedReader(new InputStreamReader(originalInputStream, fileEncoding));
        }
        this.interactive = interactive;
    }

    private static String getNormalName(String s) {
        if (s.startsWith("utf-8")) {
            return "utf-8";
        } else if (s.startsWith("latin-1") ||
                   s.startsWith("iso-8859-1") ||
                   s.startsWith("iso-latin-1")) {
            return "iso-8859-1";
        } else {
            return s;
        }
    }

    private static final byte[] CODINGS_BYTES = new byte[]{'c', 'o', 'd', 'i', 'n', 'g'};

    /**
     * Return the coding spec in {@code s} or {@code null} if none is found
     */
    private String getCodingSpec(byte[] s) {
        int i = 0;
        for (; i < s.length - 6; i++) {
            byte cp = s[i];
            if (cp == '#') {
                break;
            }
            if (cp != ' ' && cp != '\t' && cp != '\014') {
                return null;
            }
        }
        for (; i < s.length - 6; i++) {
            if (Arrays.equals(s, i, s.length, CODINGS_BYTES, 0, 6)) {
                int t = i + 6;
                byte cp = s[t];
                if (cp != ':' && cp != '=') {
                    continue;
                }
                do {
                    t++;
                    cp = s[t];
                } while (cp == ' ' || cp == '\t');
                int begin = t;
                while (Character.isLetterOrDigit(cp) || cp == '-' || cp == '_' || cp == '.') {
                    t++;
                    cp = s[t];
                }
                if (begin < t) {
                    String r = new String(Arrays.copyOfRange(s, begin, t), StandardCharsets.UTF_8);
                    return getNormalName(r);
                }
            }
        }
        return null;
    }

    /**
     * Check and set file encoding. Return {@code true} on success; false if
     * there was an error.
     */
    private boolean checkCodingSpec(byte[] line) {
        if (inContinuationLine) {
            state = DecodingState.STATE_NORMAL;
            return true;
        }
        String spec = getCodingSpec(line);
        if (spec == null) {
            for (int i = 0; i < line.length; i++) {
                int cp = line[i];
                if (cp == '#' || cp == '\n' || cp == '\r') {
                    break;
                }
                if (cp != ' ' && cp != '\t' && cp != '\014') {
                    /* Stop checking coding spec after a line containing
                     * anything except a comment. */
                    state = DecodingState.STATE_NORMAL;
                    break;
                }
            }
            return true;
        }
        state = DecodingState.STATE_NORMAL;
        if (this.fileEncoding == null) {
            fileEncoding = Charset.forName(spec);
            if (fileEncoding != StandardCharsets.UTF_8) {
                buffer = new BufferedReader(new InputStreamReader(originalInputStream, fileEncoding));
            }
        } else {
            if (fileEncoding != Charset.forName(spec)) {
                return false;
            }
        }
        return true;
    }

    private static Charset checkBOM(ByteArrayInputStream is) {
        is.mark(0);
        if (is.read() == 0xEF &&
            is.read() == 0xBB &&
            is.read() == 0xBF) {
            return StandardCharsets.UTF_8;
        } else {
            is.reset();
            return null;
        }
    }

    

    int nextChar() {
        if (nextCharIndex < text.length()) {
            int c = text.charAt(nextCharIndex);
            // new line
            if (c == '\n') {
                currentLineNumber++;
                atBeginningOfLine = true;
                startCurrentLineOffset = nextCharIndex;
            }
            nextCharIndex++;
            return c;
        } else {
            if (nextCharIndex == text.length()) {
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
                    nextCharIndex++;
                    return '\n';
                }
            }
            if (!isEOF) {
                // the first EOF is on the new line
                currentLineNumber++;
                startCurrentLineOffset = nextCharIndex;
            }
            isEOF = true;
            return EOF;
        }
    }

    private static final int STATE_NEXTLINE = 0;
    private static final int STATE_INIT = 1;
    private static final int STATE_FRACTION = 2;
    private static final int STATE_AFTER_FRACTION = 3;
    private static final int STATE_STRING = 4;

    public Token next() {
        int c = 0;
        int state = STATE_NEXTLINE;
        boolean blankline = false;
        while (true) {
            switch (state) {
                case STATE_NEXTLINE:
                    // Get indentation level
                    if (nextCharIndex - 1 == startCurrentLineOffset) { // we're at the beginning of a line
                        int col = 0;
                        int altcol = 0;
                        OUTER: while (true) {
                            c = nextChar();
                            switch (c) {
                            case ' ':
                                col++;
                                altcol++;
                                break;
                            case '\t':
                                col = (col / TABSIZE + 1) * TABSIZE;
                                altcol = (altcol / ALTTABSIZE + 1) * ALTTABSIZE;
                                break;
                            case '\014':
                                col = altcol = 0;
                                break;
                            default:
                                break OUTER;
                            }
                        }
                        oneBack();
                        if (c == '#' || c == '\n' || c == '\\') {
                            /* Lines with only whitespace and/or comments
                               and/or a line continuation character
                               shouldn't affect the indentation and are
                               not passed to the parser as NEWLINE tokens,
                               except *totally* empty lines in interactive
                               mode, which signal the end of a command group. */
                            if (col == 0 && c == '\n' && interactiveMode) {
                                blankline = false;
                            } else if (interactiveMode && currentLineNumber == 1) {
                                /* In interactive mode, if the first line contains
                                   only spaces and/or a comment, let it through. */
                                blankline = false;
                                col = altcol = 0;
                            } else {
                                blankline = true;
                            }
                        }
                        if (!blankline && 
                    }
                case STATE_INIT:
                    // skip spaces
                    do {
                        c = nextChar();
                    } while (c == ' ' || c == '\t' || c == '\014');

                    startOffset = nextCharIndex - 1;

                    // skip comment
                    if (c == '#') {
                        do {
                            c = nextChar();
                        } while (c != EOF && c != '\n');

                        // TODO: look for type comments
                    }

                    // check EOF
                    if (c == EOF) {
                        // TODO return EOF Token
                        return new Token(Token.Kind.ENDMARKER, startOffset, startOffset, currentLineNumber, 0, currentLineNumber, 0);
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
                        boolean nonascii = false;
                        while (isIdentifierChar(c)) {
                            if (c >= 128) {
                                nonascii = true;
                            }
                            c = nextChar();
                        }

                        oneBack();

                        Token tok = createToken(Token.Kind.NAME);
                        String tokenString = getTokenString(tok);
                        String errMsg = null;
                        if (nonascii && ((errMsg = verifyIdentifier(tokenString)) != null)) {
                            return createToken(Token.Kind.ERRORTOKEN, errMsg);
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
                    // period
                    if (c == '.') {
                        c = nextChar();
                        if (Character.isDigit(c)) {
                            state = STATE_FRACTION;
                            break;
                        } else if (c == '.') {
                            c = nextChar();
                            if (c == '.') {
                                return createToken(Token.Kind.ELLIPSIS);
                            }
                            oneBack();
                        }
                        oneBack();
                        return createToken(Token.Kind.DOT);
                    }
                    // Number
                    if (Character.isDigit(c)) {
                        if (c == '0') {
                            // it can be hex, octal or binary
                            c = nextChar();
                            switch (c) {
                                case 'x':
                                case 'X':
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
                                    break;
                                case 'o':
                                case 'O':
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
                                    }   break;
                                case 'b':
                                case 'B':
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
                                    }   break;
                                default:
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
                                    break;
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

    private boolean isHexDegit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void oneBack() {
        if (nextCharIndex > 0 && !isEOF) {
            nextCharIndex--;
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
        return new Token(kind, startOffset, nextCharIndex, currentLineNumber, startOffset - startCurrentLineOffset, currentLineNumber, nextCharIndex - startCurrentLineOffset);
    }

    private Token createToken(Token.Kind kind, Object extraData) {
        return new Token(kind, startOffset, nextCharIndex, currentLineNumber, startOffset - startCurrentLineOffset, currentLineNumber, nextCharIndex - startCurrentLineOffset, extraData);
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

    /**
     * Verify that the string is a valid identifier.
     * @return {@code null} if valid, else an error message
     */
    private String verifyIdentifier(String tokenString) {
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
            return String.format("invalid character '%s' (U+%x)", printString, codePoint);
        }
        return null;
    }
}
