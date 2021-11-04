/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.tokenizer;

import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.DecodingState;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.StatusCode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
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

    // tok_new initialization is taken care of here
    private final boolean execInput;

    /** {@code tok_state->buf, tok_state->inp, tok_state->str, tok_state->input} */
    private final int[] codePointsInput;
    /** {@code tok_state->cur} */
    private int nextCharIndex = 0;
    /** {@code tok_state->fp_interactive} */
    private final boolean interactive;
    /** {@code tok_state->start} */
    private int tokenStart = 0;
    /** {@code tok_state->done} */
    private StatusCode done = StatusCode.OK;
    /** {@code tok_state->tabsize} */
    private int tabSize = TABSIZE;
    /** {@code tok_state->indent} */
    private int currentIndentIndex = 0;
    /** {@code tok_state->indstack} */
    private int[] indentationStack = new int[100];
    /** {@code tok_state->atbol} */
    private boolean atBeginningOfLine = false;
    /** {@code tok_state->pendin} */
    private int pendingIndents = 0;
    /** {@code tok_state->lineno, we count lines from one} */
    private int currentLineNumber = 1;
    /** {@code tok_state->first_lineno} */
    private int firstLineNumber = 0;
    /** {@code tok_state->level} */
    private int parensNestingLevel = 0;
    /** {@code tok_state->parenstack} */
    private byte parensStack[200] = new byte[200];
    /** {@code tok_state->parenlinenostack} */
    private int[] parensLineNumberStack = new int[200];
    /** {@code tok_state->parencolstack} */
    private int[] parensColumnsStack = new int[200];
    /** {@code tok_state->filename} */
    private String filename = null;
    /** {@code tok_state->altindstack} */
    private int[] altIndentationStack = new int[100];
    /** {@code tok_state->enc, tok_state->encoding} */
    private Charset fileEncoding = null;
    /** {@code tok_state->cont_line} */
    private boolean inContinuationLine = false;
    /** {@code tok_state->line_start} */
    private int lineStartIndex = 0;
    /** {@code tok_state->multi_line_start} */
    private int multiLineStartIndex = 0;
    /** {@code tok_state->type_comments} */
    private boolean lookForTypeComments = false;
    /** {@code tok_state->async_def} */
    private boolean insideAsyncDef = false;
    /** {@code tok_state->async_def_indent} */
    private int indetationOfAsyncDef = 0;
    /** {@code tok_state->async_def_nl} */
    private boolean asyncDefFollowedByNewline = false;

    // error_ret

    /**
     * get_normal_name
     */
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
     * get_coding_spec
     *
     * Return the coding spec in the current line or {@code null} if none is found
     */
    static String getCodingSpec(byte[] byteInput, int lineStart) {
        int i = lineStart;
        for (; i < byteInput.length - 6; i++) {
            byte cp = byteInput[i];
            if (cp == '\n') {
                return null;
            }
            if (cp == '#') {
                break;
            }
            if (cp != ' ' && cp != '\t' && cp != '\014') {
                return null;
            }
        }
        for (; i < byteInput.length - 6; i++) {
            if (Arrays.equals(byteInput, i, i + 6, CODINGS_BYTES, 0, 6)) {
                int t = i + 6;
                byte cp = byteInput[t];
                if (cp == '\n') {
                    return null;
                }
                if (cp != ':' && cp != '=') {
                    continue;
                }
                do {
                    t++;
                    cp = byteInput[t];
                } while (cp == ' ' || cp == '\t');
                int begin = t;
                while (Character.isLetterOrDigit(cp) || cp == '-' || cp == '_' || cp == '.') {
                    t++;
                    cp = byteInput[t];
                }
                if (begin < t) {
                    String r = new String(Arrays.copyOfRange(byteInput, begin, t), StandardCharsets.UTF_8);
                    return getNormalName(r);
                }
            }
        }
        return null;
    }

    /**
     * check_coding_spec
     *
     * Check and return file encoding or {@code null} if none was found. This
     * returns the default encoding if anything but a comment is found in the
     * line, since that means there can be no further coding comments in this
     * source.
     */
    static Charset checkCodingSpec(byte[] byteInput, int lineStart) {
        String spec = getCodingSpec(byteInput, lineStart);
        if (spec == null) {
            for (int i = lineStart; i < byteInput.length; i++) {
                int cp = byteInput[i];
                if (cp == '#' || cp == '\n' || cp == '\r') {
                    break;
                }
                if (cp != ' ' && cp != '\t' && cp != '\014') {
                    // Stop checking coding spec after a line containing
                    // anything except a comment. We assume UTF-8 in that case.
                    return StandardCharsets.UTF_8;
                }
            }
            return null;
        }
        return Charset.forName(spec);
    }

    private static final byte[] BOM_BYTES = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};

    /**
     * check_bom
     */
    private static boolean checkBOM(byte[] byteInput) {
        return byteInput.length >= 3 && Arrays.equals(byteInput, 0, 3, BOM_BYTES, 0, 3);
    }

    // tok_concatenate_interactive_new_line
    // tok_reserve_buf
    // tok_readline_recode
    // fp_setreadl
    // fp_getc
    // fp_ungetc
    // valid_utf8
    // ensure_utf8
    // buf_getc
    // buf_ungetc
    // buf_setreadl

    /**
     * translate_into_utf8
     */
    static int[] translateIntoCodePoints(byte[] inputBytes, int offset, Charset fileEncoding) {
        CharBuffer buf = fileEncoding.decode(ByteBuffer.wrap(inputBytes, offset, inputBytes.length - offset));
        return buf.codePoints().toArray();
    }

    // translate_newlines

    /**
     * decode_str
     */
    private Tokenizer(byte[] code, boolean execInput, boolean interactive) {
        // we do not translate newlines or add a missing final newline. we deal
        // with those in the call to get the next character
        this.execInput = execInput;

        // check_bom
        int sourceStart = 0;
        boolean hasUTF8BOM = checkBOM(code);
        if (hasUTF8BOM) {
            sourceStart = 3;
        }
        // If we got a BOM, we need to treat the input as UTF8. But we'll still
        // have to check for coding specs written in the comment line of the
        // first two lines. Since the only valid coding specs in the first lines
        // must be a comment all by itself ('#') followed by spaces and then
        // 'coding' spelled out, and in UTF-8 these are definitely single-byte
        // characters, there is no point in decoding immediately. Also, CPython
        // seems to just accept any encoding after encoding the input already as
        // UTF-8 bytes! So it decodes the input as utf-8 (which I guess just
        // checks that it's valid in the end), uses the decoded bytes to search
        // for a coding spec, and then again decodes these bytes using whatever
        // coding spec was written. This seems seriously broken if a file starts
        // with a UTF-8 BOM and then the coding spec says it's cp1251. So I'm
        // going to ignore the first decode if there's also a coding spec
        // comment.

        int offset = sourceStart;
        this.fileEncoding = checkCodingSpec(code, offset);
        if (this.fileEncoding == null) {
            // we didn't find the encoding in the first line, so we need to
            // check the second line too
            while (offset < code.length && code[offset] != '\n') {
                offset++;
            }
            offset++; // skip over newline
            if (offset < code.length) {
                this.fileEncoding = checkCodingSpec(code, offset);
            }
        }

        if (this.fileEncoding == null && hasUTF8BOM) {
            this.fileEncoding = StandardCharsets.UTF_8;
        }

        this.codePointsInput = fileEncoding
            .decode(ByteBuffer.wrap(code, sourceStart, code.length))
            .codePoints()
            .toArray();
        this.interactive = interactive;
    }

    /**
     * PyTokenizer_FromString
     */
    public Tokenizer(byte[] code, boolean execInput) {
        this(code, execInput, false);
    }

    /**
     * PyTokenizer_FromUTF8
     */
    public Tokenizer(String code, boolean execInput) {
        this.codePointsInput = code.codePoints().toArray();
        this.fileEncoding = StandardCharsets.UTF_8;
        this.interactive = false;
    }

    // PyTokenizer_FromFile
    // PyTokenizer_Free
    // tok_readline_raw
    // tok_underflow_string
    // tok_underflow_interactive
    // tok_underflow_file
    // print_escape

    /**
     * tok_nextc, inlining tok_underflow_string, because that's all we need
     *
     * CPython always scans one line ahead, so every tok_underflow_string call
     * will update the current line number, and then they keep returning the
     * next char until they reach the next line. We do it differently, since we
     * always have the entire buffer here.
     */
    int nextChar() {
        if (nextCharIndex < codePointsInput.length) {
            int c = codePointsInput[nextCharIndex];
            nextCharIndex++;
            if (c == '\n') {
                currentLineNumber++;
                atBeginningOfLine = true;
                lineStartIndex = nextCharIndex;
            }
            return c;
        } else {
            if (nextCharIndex == codePointsInput.length && execInput) {
                // check if we need to report a missing newline before eof
                int index = codePointsInput.length - 1;
                int c = -1;
                while (index >= 0) {
                    c = codePointsInput[index];
                    if (!Character.isWhitespace(c) || c == '\n') {
                        break;
                    }
                    index--;
                }
                if (c != '\n') {
                    nextCharIndex++;
                    return '\n';
                }
            }
            if (done != StatusCode.EOF) {
                // the first EOF is on the new line
                currentLineNumber++;
                lineStartIndex = nextCharIndex;
            }
            done = StatusCode.EOF;
            return EOF;
        }
    }

    /**
     * tok_backup
     */
    void oneBack() {
        if (nextCharIndex > 0 && done != StatusCode.EOF) {
            nextCharIndex--;
        }
    }

    // _syntaxerror_range
    // syntaxerror
    // syntaxerror_known_range
    // TODO: indenterror
    // TODO: parser_warn

    // TODO: lookahead
    // TODO: verify_end_of_number

    /**
     * verify_identifier
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

    /**
     * tok_decimal_tail
     */
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

    private static final int STATE_NEXTLINE = 0;
    private static final int STATE_INIT = 1;
    private static final int STATE_FRACTION = 2;
    private static final int STATE_AFTER_FRACTION = 3;
    private static final int STATE_STRING = 4;

    /**
     * tok_get
     */
    public Token next() {
        int c = 0;
        int state = STATE_NEXTLINE;
        boolean blankline = false;
        while (true) {
            switch (state) {
                case STATE_NEXTLINE:
                    // Get indentation level
                    if (nextCharIndex - 1 == lineStartIndex) { // we're at the beginning of a line
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

                    tokenStart = nextCharIndex - 1;

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
                        return new Token(Token.Kind.ENDMARKER, tokenStart, tokenStart, currentLineNumber, 0, currentLineNumber, 0);
                    }

                    // identifier
                    if (isPotentialIdentifierChar(c)) {
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
                        while (isPotentialIdentifierChar(c)) {
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
                    // TODO period
                    if (c == '.') {
                        c = nextChar();
                        if (Character.isDigit(c)) {
                            state = STATE_FRACTION;
                            break;
                        } else if (c == '.') {
                            int c2 = nextChar();
                            if (c2 == '.') {
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

    private Token createToken(Token.Kind kind) {
        return new Token(kind, tokenStart, nextCharIndex, currentLineNumber, tokenStart - lineStartIndex, currentLineNumber, nextCharIndex - lineStartIndex);
    }

    private Token createToken(Token.Kind kind, Object extraData) {
        return new Token(kind, tokenStart, nextCharIndex, currentLineNumber, tokenStart - lineStartIndex, currentLineNumber, nextCharIndex - lineStartIndex, extraData);
    }

    public String toString(Token token) {
        StringBuilder sb = new StringBuilder();
        sb.append("Token ");
        sb.append(token.type.name());
        sb.append(" [").append(token.tokenStart).append(", ").append(token.endOffset).append("]");
        sb.append(" (").append(token.startLine).append(", ").append(token.startColumn);
        sb.append(") (").append(token.endLine).append(", ").append(token.endColumn).append(") '");
        sb.append(getTokenString(token)).append("'");
        return sb.toString();
    }
}
