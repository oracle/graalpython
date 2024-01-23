/* Copyright (c) 2021, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2021 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.pegparser.tokenizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;
import org.graalvm.shadowed.com.ibm.icu.lang.UProperty;

import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.ErrorCallback.WarningType;

/**
 * This class is intentionally kept very close to CPython's tokenizer.c and tokenizer.h files. The
 * last time it was updated to the versions on the v3.10.0 tag in the CPython source code
 * repository. Where the names are not the exact same, there are javadoc comments that tell the
 * names in the CPython source code.
 */
public class Tokenizer {
    private static final int EOF = -1;
    private static final int ALTTABSIZE = 1;
    private static final int MAXINDENT = 100;
    private static final int MAXLEVEL = 200;
    private static final int UTF8_BOM = 0xFEFF;

    /**
     * is_potential_identifier_start
     */
    private static boolean isPotentialIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c >= 128;
    }

    /**
     * is_potential_identifier_char
     */
    private static boolean isPotentialIdentifierChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c >= 128;
    }

    private static final int TABSIZE = 8;

    public enum Flag {
        EXEC_INPUT,
        INTERACTIVE,
        TYPE_COMMENT,
        ASYNC_HACKS
    }

    /**
     * type_comment_prefix
     *
     * Spaces in this constant are treated as "zero or more spaces or tabs" when tokenizing.
     */
    private static final byte[] TYPE_COMMENT_PREFIX = "# type: ".getBytes(StandardCharsets.US_ASCII);
    private static final int[] IGNORE_BYTES = charsToCodePoints("ignore".toCharArray());

    public enum StatusCode {
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
        INTERACTIVE_STOP
    }

    private final ErrorCallback errorCallback;

    // tok_new initialization is taken care of here
    private final boolean execInput;

    /** {@code tok_state->buf, tok_state->inp, tok_state->str, tok_state->input} */
    private final int[] codePointsInput;
    /** {@code tok_state->cur} */
    private int nextCharIndex = 0;
    /** combines {@code tok_state->fp_interactive} and {@code tok_state->prompt != NULL} */
    private final boolean interactive;
    /** {@code tok_state->start} */
    private int tokenStart = 0;
    /** {@code tok_state->done} */
    private StatusCode done = StatusCode.OK;
    /** {@code tok_state->tabsize} */
    private final int tabSize = TABSIZE;
    /** {@code tok_state->indent} */
    private int currentIndentIndex = 0;
    /** {@code tok_state->indstack} */
    private final int[] indentationStack = new int[MAXINDENT];
    /** {@code tok_state->atbol} */
    private boolean atBeginningOfLine = true;
    /** {@code tok_state->pendin} */
    private int pendingIndents = 0;
    /** {@code tok_state->lineno, we count lines from one} */
    private int currentLineNumber = 1;
    /** {@code tok_state->first_lineno} */
    private int firstLineNumber = 0;
    /** {@code tok_state->level} */
    private int parensNestingLevel = 0;
    /** {@code tok_state->parenstack} */
    private final int[] parensStack = new int[MAXLEVEL];
    /** {@code tok_state->parenlinenostack} */
    private final int[] parensLineNumberStack = new int[MAXLEVEL];
    /** {@code tok_state->parencolstack} */
    private final int[] parensColumnsStack = new int[MAXLEVEL];
    /** {@code tok_state->altindstack} */
    private final int[] altIndentationStack = new int[MAXINDENT];
    /** {@code tok_state->cont_line} */
    // TODO
    @SuppressWarnings("unused") private boolean inContinuationLine = false;
    /** {@code tok_state->line_start} */
    private int lineStartIndex = 0;
    /** {@code tok_state->multi_line_start} */
    private int multiLineStartIndex = 0;
    /** {@code tok_state->type_comments} */
    private final boolean lookForTypeComments;
    /** {@code tok_state->async_hacks} */
    private final boolean asyncHacks;
    /** {@code tok_state->async_def} */
    private boolean insideAsyncDef = false;
    /** {@code tok_state->async_def_indent} */
    private int indentationOfAsyncDef = 0;
    /** {@code tok_state->async_def_nl} */
    private boolean asyncDefFollowedByNewline = false;
    private boolean readNewline = false;
    /** {@code tok_state->interactive_underflow} */
    public boolean reportIncompleteSourceIfInteractive = true;
    private final int srcStartLine;
    private final int srcStartColumn;
    // error_ret

    private Tokenizer(ErrorCallback errorCallback, int[] codePointsInput, EnumSet<Flag> flags, SourceRange inputSourceRange) {
        this.errorCallback = errorCallback;
        this.codePointsInput = codePointsInput;
        this.execInput = flags.contains(Flag.EXEC_INPUT);
        this.interactive = flags.contains(Flag.INTERACTIVE);
        this.lookForTypeComments = flags.contains(Flag.TYPE_COMMENT);
        this.asyncHacks = flags.contains(Flag.ASYNC_HACKS);
        if (inputSourceRange != null) {
            srcStartLine = inputSourceRange.startLine - 1;    // lines use 1-base indexing
            srcStartColumn = inputSourceRange.startColumn - 1;    // account for extra '(' in the
                                                                  // string
        } else {
            srcStartLine = 0;
            srcStartColumn = 0;
        }
    }

    /**
     * Copy constructor used to look ahead if there is a 'def' after 'async'.
     */
    private Tokenizer(Tokenizer t) {
        errorCallback = t.errorCallback;
        execInput = t.execInput;
        codePointsInput = t.codePointsInput;
        nextCharIndex = t.nextCharIndex;
        interactive = t.interactive;
        tokenStart = t.tokenStart;
        done = t.done;
        currentIndentIndex = t.currentIndentIndex;
        System.arraycopy(t.indentationStack, 0, indentationStack, 0, indentationStack.length);
        atBeginningOfLine = t.atBeginningOfLine;
        pendingIndents = t.pendingIndents;
        currentLineNumber = t.currentLineNumber;
        firstLineNumber = t.firstLineNumber;
        parensNestingLevel = t.parensNestingLevel;
        System.arraycopy(t.parensStack, 0, parensStack, 0, parensStack.length);
        System.arraycopy(t.parensLineNumberStack, 0, parensLineNumberStack, 0, parensLineNumberStack.length);
        System.arraycopy(t.parensColumnsStack, 0, parensColumnsStack, 0, parensColumnsStack.length);
        System.arraycopy(t.altIndentationStack, 0, altIndentationStack, 0, altIndentationStack.length);
        inContinuationLine = t.inContinuationLine;
        lineStartIndex = t.lineStartIndex;
        multiLineStartIndex = t.multiLineStartIndex;
        lookForTypeComments = t.lookForTypeComments;
        asyncHacks = t.asyncHacks;
        insideAsyncDef = t.insideAsyncDef;
        indentationOfAsyncDef = t.indentationOfAsyncDef;
        asyncDefFollowedByNewline = t.asyncDefFollowedByNewline;
        readNewline = t.readNewline;
        reportIncompleteSourceIfInteractive = t.reportIncompleteSourceIfInteractive;
        srcStartLine = t.srcStartLine;
        srcStartColumn = t.srcStartColumn;
    }

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
    private static String getCodingSpec(byte[] byteInput, int lineStart) {
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
            byte cp = byteInput[i];
            if (cp == '\n') {
                return null;
            }
            if (Arrays.equals(byteInput, i, i + 6, CODINGS_BYTES, 0, 6)) {
                int t = i + 6;
                cp = byteInput[t];
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
     * Check and return file encoding or {@code null} if none was found. This returns the default
     * encoding if anything but a comment is found in the line, since that means there can be no
     * further coding comments in this source.
     */
    private static Charset checkCodingSpec(byte[] byteInput, int lineStart) {
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

    private static final byte[] BOM_BYTES = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

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
    // translate_newlines

    private static int getSourceStart(byte[] byteInput) {
        return checkBOM(byteInput) ? 3 : 0;
    }

    private static Charset detectEncoding(int sourceStart, byte[] code) {
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
        Charset fileEncoding = checkCodingSpec(code, offset);
        if (fileEncoding == null) {
            // we didn't find the encoding in the first line, so we need to
            // check the second line too
            while (offset < code.length && code[offset] != '\n') {
                offset++;
            }
            offset++; // skip over newline
            if (offset < code.length) {
                fileEncoding = checkCodingSpec(code, offset);
            }
        }
        if (fileEncoding == null) {
            fileEncoding = StandardCharsets.UTF_8;
        }
        return fileEncoding;
    }

    /**
     * Equivalent of {@code PyTokenizer_FromString} and {@code decode_str}. The encoding of the
     * input is automatically detected using BOM and/or coding spec comment.
     */
    public static Tokenizer fromBytes(ErrorCallback errorCallback, byte[] code, EnumSet<Flag> flags) {
        // we do not translate newlines or add a missing final newline. we deal
        // with those in the call to get the next character
        int sourceStart = getSourceStart(code);
        Charset fileEncoding = detectEncoding(sourceStart, code);
        int[] codePointsInput = charsToCodePoints(fileEncoding.decode(ByteBuffer.wrap(code, sourceStart, code.length)).array());
        return new Tokenizer(errorCallback, codePointsInput, flags, null);
    }

    private static int[] charsToCodePoints(char[] chars) {
        int cpIndex = 0;
        boolean hasUTF8Bom = chars.length > 0 && Character.codePointAt(chars, 0) == UTF8_BOM;
        for (int charIndex = 0; charIndex < chars.length; cpIndex++) {
            int cp = Character.codePointAt(chars, charIndex);
            charIndex += Character.charCount(cp);
        }
        if (hasUTF8Bom) {
            cpIndex--;
        }
        int[] codePoints = new int[cpIndex];
        cpIndex = 0;
        for (int charIndex = hasUTF8Bom ? Character.charCount(UTF8_BOM) : 0; charIndex < chars.length; cpIndex++) {
            int cp = Character.codePointAt(chars, charIndex);
            codePoints[cpIndex] = cp;
            charIndex += Character.charCount(cp);
        }
        return codePoints;
    }

    /**
     * Equivalent of {@code PyTokenizer_FromUTF8}. No charset decoding is performed, BOM or coding
     * spec comment are ignored,
     */
    public static Tokenizer fromString(ErrorCallback errorCallback, String code, EnumSet<Flag> flags, SourceRange inputSourceRange) {
        if (code.length() > 0 && code.charAt(0) == '\\') {
            System.out.println("Creating tokenizer for *" + code + "*");
        }
        return new Tokenizer(errorCallback, charsToCodePoints(code.toCharArray()), flags, inputSourceRange);
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
     */
    int nextChar() {
        if (readNewline) {
            readNewline = false;
            if (nextCharIndex < codePointsInput.length) {
                // cpython does not increment the line number when the last line is empty
                // (early exit from tok_underflow_file/tok_underflow_string)
                currentLineNumber++;
            }
            lineStartIndex = nextCharIndex;
        }
        if (nextCharIndex < codePointsInput.length) {
            int c = codePointsInput[nextCharIndex];
            if (c == '\r') {
                if (nextCharIndex + 1 < codePointsInput.length && codePointsInput[nextCharIndex + 1] == '\n') {
                    nextCharIndex++;
                }
                c = '\n';
            }
            nextCharIndex++;
            if (c == '\n') {
                readNewline = true;
            }
            return c;
        } else {
            if (nextCharIndex == codePointsInput.length && execInput) {
                // check if we need to report a missing newline before eof
                if (codePointsInput.length == 0 || codePointsInput[nextCharIndex - 1] != '\n') {
                    nextCharIndex++;
                    readNewline = true;
                    return '\n';
                }
            }
            if (interactive) {
                if (reportIncompleteSourceIfInteractive) {
                    errorCallback.reportIncompleteSource(currentLineNumber);
                } else {
                    done = StatusCode.INTERACTIVE_STOP;
                }
                return EOF;
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
            if (nextCharIndex < codePointsInput.length && codePointsInput[nextCharIndex] == '\n') {
                if (nextCharIndex > 0 && codePointsInput[nextCharIndex - 1] == '\r') {
                    nextCharIndex--;
                }
            }
            readNewline = false;
        }
    }

    /**
     * syntaxerror_known_range, _syntaxerror_range
     */
    @SuppressWarnings("unused")     // TODO use column offsets
    Token syntaxError(int colOffset, int endColOffset, String message) {
        done = StatusCode.SYNTAX_ERROR;
        return createToken(Token.Kind.ERRORTOKEN, message);
    }

    /**
     * syntaxerror
     */
    Token syntaxError(String message) {
        return syntaxError(-1, -1, message);
    }

    /**
     * indenterror
     */
    Token indentError() {
        done = StatusCode.TABS_SPACES_INCONSISTENT;
        return createToken(Token.Kind.ERRORTOKEN);
    }

    private void parserWarn(String warning) {
        errorCallback.onWarning(WarningType.Syntax, getCurrentTokenRange(false), warning);
    }

    /**
     * lookahead
     */
    private boolean lookahead(int... test) {
        int end = nextCharIndex + test.length;
        if (end + 1 < codePointsInput.length) {
            return Arrays.equals(codePointsInput, nextCharIndex, end, test, 0, test.length) &&
                            !isPotentialIdentifierChar(codePointsInput[end]);
        } else {
            return false;
        }
    }

    /**
     * verify_end_of_number
     *
     * In contrast to CPython, we return {@code null} if fine, and a Token if there was an error.
     * The caller should return the token further up in that case.
     */
    private Token verifyEndOfNumber(int c, String kind) {
        /*
         * Emit a deprecation warning only if the numeric literal is immediately followed by one of
         * keywords which can occur after a numeric literal in valid code: "and", "else", "for",
         * "if", "in", "is" and "or". It allows to gradually deprecate existing valid code without
         * adding warning before error in most cases of invalid numeric literal (which would be
         * confusing and break existing tests). Raise a syntax error with slightly better message
         * than plain "invalid syntax" if the numeric literal is immediately followed by other
         * keyword or identifier.
         */
        boolean r = false;
        if (c == 'a') {
            r = lookahead('n', 'd');
        } else if (c == 'e') {
            r = lookahead('l', 's', 'e');
        } else if (c == 'f') {
            r = lookahead('o', 'r');
        } else if (c == 'i') {
            int c2 = nextChar();
            if (c2 == 'f' || c2 == 'n' || c2 == 's') {
                r = true;
            }
            oneBack();
        } else if (c == 'o') {
            r = lookahead('r');
        } else if (c == 'n') {
            r = lookahead('o', 't');
        }
        if (r) {
            oneBack();
            parserWarn(String.format("invalid %s literal", kind));
            nextChar();
        } else { /* In future releases, only error will remain. */
            if (c < 128 && isPotentialIdentifierChar(c)) {
                oneBack();
                return syntaxError(String.format("invalid %s literal", kind));
            }
        }
        return null;
    }

    /**
     * verify_identifier Verify that the string is a valid identifier.
     *
     * @return {@code null} if valid, else an error message
     */
    private static String verifyIdentifier(String tokenString) {
        // inlined the logic from _PyUnicode_ScanIdentifier
        int len = tokenString.codePointCount(0, tokenString.length());
        int invalid = len;
        int cp = tokenString.codePointAt(0);
        if (cp != '_' && !UCharacter.hasBinaryProperty(cp, UProperty.XID_START)) {
            invalid = 0;
        }
        for (int i = 1; i < invalid;) {
            cp = tokenString.codePointAt(i);
            if (!UCharacter.hasBinaryProperty(cp, UProperty.XID_CONTINUE)) {
                invalid = i;
                break;
            }
            i += Character.charCount(cp);
        }
        if (invalid < len) {
            int codePoint = tokenString.codePointAt(invalid);
            String printString = new String(new int[]{codePoint}, 0, 1);
            return String.format("invalid character '%s' (U+%x)", printString, codePoint);
        }
        return null;
    }

    /**
     * tok_decimal_tail
     *
     * if this returns {@code 0}, the caller must return a {@code
     * syntaxError("invalid decimal literal")}.
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
                return 0;
            }
        }
        return c;
    }

    private int continuationLine() {
        int c = nextChar();
        if (c != '\n') {
            done = StatusCode.LINE_CONTINUATION_ERROR;
            return -1;
        }
        c = nextChar();
        if (c == EOF) {
            done = StatusCode.EOF;
            return -1;
        } else {
            oneBack();
        }
        return c;
    }

    private static final int LABEL_NEXTLINE = 0;
    private static final int LABEL_AGAIN = 1;
    private static final int LABEL_LETTER_QUOTE = 2;
    private static final int LABEL_FRACTION = 3;
    private static final int LABEL_EXPONENT = 4;
    private static final int LABEL_IMAGINARY = 5;

    /**
     * tok_get, PyTokenizer_Get
     */
    @SuppressWarnings("fallthrough")
    public Token next() {
        int c = 0;
        boolean blankline = false;
        boolean nonascii = false;

        int target = LABEL_NEXTLINE;

        GOTO_LOOP: while (true) {
            switch (target) {
                case LABEL_NEXTLINE:
                    blankline = false;

                    if (atBeginningOfLine) {
                        int col = 0;
                        int altcol = 0;
                        atBeginningOfLine = false;
                        int contLineCol = 0;
                        OUTER: while (true) {
                            c = nextChar();
                            switch (c) {
                                case ' ':
                                    col++;
                                    altcol++;
                                    break;
                                case '\t':
                                    col = (col / tabSize + 1) * tabSize;
                                    altcol = (altcol / ALTTABSIZE + 1) * ALTTABSIZE;
                                    break;
                                case '\014':
                                    col = altcol = 0;
                                    break;
                                case '\\':
                                    // Indentation cannot be split over multiple physical lines
                                    // using backslashes. This means that if we found a backslash
                                    // preceded by whitespace, **the first one we find** determines
                                    // the level of indentation of whatever comes next.
                                    contLineCol = contLineCol != 0 ? contLineCol : col;
                                    if ((c = continuationLine()) == -1) {
                                        return createToken(Token.Kind.ERRORTOKEN);
                                    }
                                    break;
                                default:
                                    break OUTER;
                            }
                        }
                        oneBack();
                        if (c == '#' || c == '\n') {
                            /*
                             * Lines with only whitespace and/or comments shouldn't affect the
                             * indentation and are not passed to the parser as NEWLINE tokens,
                             * except *totally* empty lines in interactive mode, which signal the
                             * end of a command group.
                             */
                            if (col == 0 && c == '\n' && interactive) {
                                blankline = false; /* Let it through */
                            } else if (interactive && currentLineNumber == 1) {
                                /*
                                 * In interactive mode, if the first line contains only spaces
                                 * and/or a comment, let it through.
                                 */
                                blankline = false;
                                col = altcol = 0;
                            } else {
                                blankline = true; /* Ignore completely */
                            }
                            /*
                             * We can't jump back right here since we still may need to skip to the
                             * end of a comment
                             */
                        }
                        if (!blankline && parensNestingLevel == 0) {
                            col = contLineCol != 0 ? contLineCol : col;
                            altcol = contLineCol != 0 ? contLineCol : altcol;
                            if (col == indentationStack[currentIndentIndex]) {
                                /* No change */
                                if (altcol != altIndentationStack[currentIndentIndex]) {
                                    return indentError();
                                }
                            } else if (col > indentationStack[currentIndentIndex]) {
                                /* Indent -- always one */
                                if (currentIndentIndex + 1 >= MAXINDENT) {
                                    done = StatusCode.TOO_DEEP_INDENTATION;
                                    return createToken(Token.Kind.ERRORTOKEN);
                                }
                                if (altcol <= altIndentationStack[currentIndentIndex]) {
                                    return indentError();
                                }
                                pendingIndents++;
                                indentationStack[++currentIndentIndex] = col;
                                altIndentationStack[currentIndentIndex] = altcol;
                            } else {
                                assert col < indentationStack[currentIndentIndex];
                                /* Dedent -- any number, must be consistent */
                                while (currentIndentIndex > 0 && col < indentationStack[currentIndentIndex]) {
                                    pendingIndents--;
                                    currentIndentIndex--;
                                }
                                if (col != indentationStack[currentIndentIndex]) {
                                    done = StatusCode.DEDENT_INVALID;
                                    return createToken(Token.Kind.ERRORTOKEN);
                                }
                                if (altcol != altIndentationStack[currentIndentIndex]) {
                                    return indentError();
                                }
                            }
                        }
                    }

                    tokenStart = nextCharIndex;

                    /* Return pending indents/dedents */
                    if (pendingIndents != 0) {
                        if (pendingIndents < 0) {
                            pendingIndents++;
                            return createToken(Token.Kind.DEDENT);
                        } else {
                            pendingIndents--;
                            return createToken(Token.Kind.INDENT);
                        }
                    }

                    /* Peek ahead at the next character */
                    c = nextChar();
                    oneBack();
                    /* Check if we are closing an async function */
                    if (insideAsyncDef && !blankline
                    /*
                     * Due to some implementation artifacts of type comments, a TYPE_COMMENT at the
                     * start of a function won't set an indentation level and it will produce a
                     * NEWLINE after it. To avoid spuriously ending an async function due to this,
                     * wait until we have some non-newline char in front of us.
                     */
                                    && c != '\n' && parensNestingLevel == 0
                                    /*
                                     * There was a NEWLINE after ASYNC DEF, so we're past the
                                     * signature.
                                     */
                                    && asyncDefFollowedByNewline
                                    /*
                                     * Current indentation level is less than where the async
                                     * function was defined
                                     */
                                    && indentationOfAsyncDef >= currentIndentIndex) {
                        insideAsyncDef = false;
                        indentationOfAsyncDef = 0;
                        asyncDefFollowedByNewline = false;
                    }

                case LABEL_AGAIN:
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

                        if (lookForTypeComments) {
                            int prefixIdx = 0;
                            // int chIdx = nextCharIndex;
                            int chIdx = tokenStart;
                            while (chIdx < codePointsInput.length && prefixIdx < TYPE_COMMENT_PREFIX.length) {
                                if (TYPE_COMMENT_PREFIX[prefixIdx] == ' ') {
                                    while (chIdx < codePointsInput.length &&
                                                    (codePointsInput[chIdx] == ' ' || codePointsInput[chIdx] == '\t')) {
                                        chIdx++;
                                    }
                                } else if (TYPE_COMMENT_PREFIX[prefixIdx] == codePointsInput[chIdx]) {
                                    chIdx++;
                                } else {
                                    break;
                                }
                                prefixIdx++;
                            }

                            /* This is a type comment if we matched all of type_comment_prefix. */
                            if (prefixIdx == TYPE_COMMENT_PREFIX.length) {
                                boolean isTypeIgnore;
                                int ignoreEnd = chIdx + 6;
                                int endChar = ignoreEnd < codePointsInput.length ? codePointsInput[ignoreEnd] : -1;
                                oneBack(); /* don't eat the newline or EOF */

                                int typeStart = chIdx;

                                /*
                                 * A TYPE_IGNORE is "type: ignore" followed by the end of the token
                                 * or anything ASCII and non-alphanumeric.
                                 */
                                isTypeIgnore = (nextCharIndex >= ignoreEnd &&
                                                Arrays.equals(codePointsInput, chIdx, ignoreEnd, IGNORE_BYTES, 0, 6) &&
                                                !(nextCharIndex > ignoreEnd &&
                                                                (endChar >= 128 || Character.isLetterOrDigit(endChar))));

                                if (isTypeIgnore) {
                                    /*
                                     * If this type ignore is the only thing on the line, consume
                                     * the newline also.
                                     */
                                    if (blankline) {
                                        nextChar();
                                        atBeginningOfLine = true;
                                    }
                                    tokenStart = ignoreEnd;
                                    return createToken(Token.Kind.TYPE_IGNORE);
                                } else {
                                    tokenStart = typeStart; /* after type_comment_prefix */
                                    return createToken(Token.Kind.TYPE_COMMENT);
                                }
                            }
                        }
                    }

                    if (done == StatusCode.INTERACTIVE_STOP) {
                        return createToken(Token.Kind.ENDMARKER);
                    }

                    // check EOF
                    if (c == EOF) {
                        tokenStart = nextCharIndex;
                        if (parensNestingLevel > 0) {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                        if (done == StatusCode.EOF) {
                            return createToken(Token.Kind.ENDMARKER);
                        } else {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                    }

                    // identifier
                    nonascii = false;
                    if (isPotentialIdentifierStart(c)) {
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
                                target = LABEL_LETTER_QUOTE;
                                continue GOTO_LOOP;
                            }
                        }
                        while (isPotentialIdentifierChar(c)) {
                            if (c >= 128) {
                                nonascii = true;
                            }
                            c = nextChar();
                        }
                        oneBack();

                        String tokenString = new String(codePointsInput, tokenStart, nextCharIndex - tokenStart);
                        String errMsg = null;
                        if (nonascii && ((errMsg = verifyIdentifier(tokenString)) != null)) {
                            return createToken(Token.Kind.ERRORTOKEN, errMsg);
                        }
                        if (!asyncHacks || insideAsyncDef) {
                            if (tokenString.equals("async")) {
                                return createToken(Token.Kind.ASYNC);
                            }
                            if (tokenString.equals("await")) {
                                return createToken(Token.Kind.AWAIT);
                            }
                        } else if (tokenString.equals("async")) {
                            Token t = new Tokenizer(this).next();
                            if (t.type == Token.Kind.NAME && getTokenString(t).equals("def")) {
                                insideAsyncDef = true;
                                indentationOfAsyncDef = currentIndentIndex;
                                return createToken(Token.Kind.ASYNC);
                            }
                        }
                        return createToken(Token.Kind.NAME);
                    }

                    // newline
                    if (c == '\n') {
                        atBeginningOfLine = true;
                        if (blankline || parensNestingLevel > 0) {
                            target = LABEL_NEXTLINE;
                            continue GOTO_LOOP;
                        }
                        if (insideAsyncDef) {
                            /*
                             * We're somewhere inside an 'async def' function, and we've encountered
                             * a NEWLINE after its signature.
                             */
                            asyncDefFollowedByNewline = true;
                        }
                        return createToken(Token.Kind.NEWLINE);
                    }

                    // period or number starting with period?
                    if (c == '.') {
                        c = nextChar();
                        if (Character.isDigit(c)) {
                            target = LABEL_FRACTION;
                            continue GOTO_LOOP;
                        } else if (c == '.') {
                            c = nextChar();
                            if (c == '.') {
                                return createToken(Token.Kind.ELLIPSIS);
                            } else {
                                oneBack();
                            }
                            oneBack();
                        } else {
                            oneBack();
                        }
                        return createToken(Token.Kind.DOT);
                    }

                    // Number
                    if (Character.isDigit(c)) {
                        if (c == '0') {
                            // it can be hex, octal or binary
                            c = nextChar();
                            if (c == 'x' || c == 'X') {
                                // Hex
                                c = nextChar();
                                do {
                                    if (c == '_') {
                                        c = nextChar();
                                    }
                                    if (!isHexDigit(c)) {
                                        oneBack();
                                        return syntaxError("invalid hexadecimal literal");
                                    }
                                    do {
                                        c = nextChar();
                                    } while (isHexDigit(c));
                                } while (c == '_');
                                Token syntaxError = verifyEndOfNumber(c, "hexadecimal");
                                if (syntaxError != null) {
                                    return syntaxError;
                                }
                            } else if (c == 'o' || c == 'O') {
                                // octal
                                c = nextChar();
                                do {
                                    if (c == '_') {
                                        c = nextChar();
                                    }
                                    if (c < '0' || c >= '8') {
                                        oneBack();
                                        if (Character.isDigit(c)) {
                                            return syntaxError(String.format("invalid digit '%c' in octal literal", (char) c));
                                        } else {
                                            oneBack();
                                            return syntaxError("invalid octal literal");
                                        }
                                    }
                                    do {
                                        c = nextChar();
                                    } while ('0' <= c && c < '8');
                                } while (c == '_');
                                if (Character.isDigit(c)) {
                                    return syntaxError(String.format("invalid digit '%c' in octal literal", (char) c));
                                }
                                Token syntaxError = verifyEndOfNumber(c, "octal");
                                if (syntaxError != null) {
                                    return syntaxError;
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
                                        if (Character.isDigit(c)) {
                                            return syntaxError(String.format("invalid digit '%c' in binary literal", (char) c));
                                        } else {
                                            return syntaxError("invalid binary literal");
                                        }
                                    }
                                    do {
                                        c = nextChar();
                                    } while (c == '0' || c == '1');
                                } while (c == '_');
                                if (Character.isDigit(c)) {
                                    return syntaxError(String.format("invalid digit '%c' in binary literal", (char) c));
                                }
                                Token syntaxError = verifyEndOfNumber(c, "octal");
                                if (syntaxError != null) {
                                    return syntaxError;
                                }
                            } else {
                                boolean nonzero = false;
                                /* maybe old-style octal; c is first char of it */
                                /* in any case, allow '0' as a literal */
                                while (true) {
                                    if (c == '_') {
                                        c = nextChar();
                                        if (!Character.isDigit(c)) {
                                            oneBack();
                                            return syntaxError("invalid decimal literal");
                                        }
                                    }
                                    if (c != '0') {
                                        break;
                                    }
                                    c = nextChar();
                                }
                                int zerosEnd = nextCharIndex;
                                if (Character.isDigit(c)) {
                                    nonzero = true;
                                    c = readDecimalTail();
                                    if (c == 0) {
                                        return syntaxError("invalid decimal literal");
                                    }
                                }
                                if (c == '.') {
                                    c = nextChar();
                                    target = LABEL_FRACTION;
                                    continue GOTO_LOOP;
                                } else if (c == 'e' || c == 'E') {
                                    target = LABEL_EXPONENT;
                                    continue GOTO_LOOP;
                                } else if (c == 'j' || c == 'J') {
                                    target = LABEL_IMAGINARY;
                                    continue GOTO_LOOP;
                                } else if (nonzero) {
                                    /* Old-style octal: now disallowed. */
                                    oneBack();
                                    nextCharIndex = zerosEnd;
                                    return syntaxError(tokenStart + 1 - lineStartIndex, zerosEnd - lineStartIndex,
                                                    "leading zeros in decimal integer " +
                                                                    "literals are not permitted; " +
                                                                    "use an 0o prefix for octal integers");
                                }
                                Token syntaxError = verifyEndOfNumber(c, "decimal");
                                if (syntaxError != null) {
                                    return syntaxError;
                                }
                            }
                        } else {
                            // Decimal
                            c = readDecimalTail();
                            if (c == 0) {
                                return syntaxError("invalid decimal literal");
                            }
                            if (c == '.') {
                                c = nextChar();
                                target = LABEL_FRACTION;
                                continue GOTO_LOOP;
                            } else if (c == 'e' || c == 'E') {
                                target = LABEL_EXPONENT;
                                continue GOTO_LOOP;
                            } else if (c == 'j' || c == 'J') {
                                target = LABEL_IMAGINARY;
                                continue GOTO_LOOP;
                            } else {
                                Token syntaxError = verifyEndOfNumber(c, "decimal");
                                if (syntaxError != null) {
                                    return syntaxError;
                                }
                            }
                        }
                        oneBack();
                        return createToken(Token.Kind.NUMBER);
                    }
                    target = LABEL_LETTER_QUOTE;
                    continue GOTO_LOOP;
                case LABEL_FRACTION:
                    // fraction
                    if (Character.isDigit(c)) {
                        c = readDecimalTail();
                        if (c == 0) {
                            return syntaxError("invalid decimal literal");
                        }
                    }
                    if (c == 'e' || c == 'E') {
                        target = LABEL_EXPONENT;
                        continue GOTO_LOOP;
                    }
                    if (c == 'j' || c == 'J') {
                        target = LABEL_IMAGINARY;
                        continue GOTO_LOOP;
                    } else {
                        Token syntaxError = verifyEndOfNumber(c, "decimal");
                        if (syntaxError != null) {
                            return syntaxError;
                        }
                    }
                    oneBack();
                    return createToken(Token.Kind.NUMBER);
                case LABEL_EXPONENT:
                // exponent part
                {
                    int e = c;
                    c = nextChar();
                    if (c == '+' || c == '-') {
                        c = nextChar();
                        if (!Character.isDigit(c)) {
                            oneBack();
                            return syntaxError("invalid decimal literal");
                        }
                    } else if (!Character.isDigit(c)) {
                        oneBack();
                        Token syntaxError = verifyEndOfNumber(e, "decimal");
                        if (syntaxError != null) {
                            return syntaxError;
                        }
                        oneBack();
                        return createToken(Token.Kind.NUMBER);
                    }
                    c = readDecimalTail();
                    if (c == 0) {
                        return syntaxError("invalid decimal literal");
                    }
                }
                    if (c == 'j' || c == 'J') {
                        target = LABEL_IMAGINARY;
                        continue GOTO_LOOP;
                    } else {
                        Token syntaxError = verifyEndOfNumber(c, "decimal");
                        if (syntaxError != null) {
                            return syntaxError;
                        }
                    }
                    oneBack();
                    return createToken(Token.Kind.NUMBER);
                case LABEL_IMAGINARY: {
                    c = nextChar();
                    Token syntaxError = verifyEndOfNumber(c, "decimal");
                    if (syntaxError != null) {
                        return syntaxError;
                    }
                }
                    oneBack();
                    return createToken(Token.Kind.NUMBER);
                case LABEL_LETTER_QUOTE:
                    // String
                    if (c == '\'' || c == '"') {
                        int quote = c;
                        int quote_size = 1;
                        int end_quote_size = 0;

                        /*
                         * Nodes of type STRING, especially multi line strings must be handled
                         * differently in order to get both the starting line number and the column
                         * offset right. (cf. issue 16806)
                         */
                        firstLineNumber = currentLineNumber;
                        multiLineStartIndex = lineStartIndex;

                        /* Find the quote size and start of string */
                        c = nextChar();
                        if (c == quote) {
                            c = nextChar();
                            if (c == quote) {
                                quote_size = 3;
                            } else {
                                end_quote_size = 1; /* empty string found */
                            }
                        }
                        if (c != quote) {
                            oneBack();
                        }

                        /* Get rest of string */
                        while (end_quote_size != quote_size) {
                            c = nextChar();
                            if (c == EOF || (quote_size == 1 && c == '\n')) {
                                // shift the tok_state's location into
                                // the start of string, and report the error
                                // from the initial quote character
                                nextCharIndex = tokenStart;
                                nextCharIndex++;
                                lineStartIndex = multiLineStartIndex;
                                int start = currentLineNumber;
                                currentLineNumber = firstLineNumber;
                                if (quote_size == 3) {
                                    return syntaxError(String.format("unterminated triple-quoted string literal" +
                                                    " (detected at line %d)", start));
                                } else {
                                    return syntaxError(String.format("unterminated string literal" +
                                                    " (detected at line %d)", start));
                                }
                            }
                            if (c == quote) {
                                end_quote_size += 1;
                            } else {
                                end_quote_size = 0;
                                if (c == '\\') {
                                    nextChar(); /* skip escaped char */
                                }
                            }
                        }

                        return createToken(Token.Kind.STRING);
                    }

                    /* Line continuation */
                    if (c == '\\') {
                        if ((c = continuationLine()) == -1) {
                            return createToken(Token.Kind.ERRORTOKEN);
                        }
                        inContinuationLine = true;
                        target = LABEL_AGAIN;
                        continue GOTO_LOOP; /* Read next line */
                    }

                /* Check for two-character token */
                {
                    int c2 = nextChar();
                    int kind2 = Token.twoChars(c, c2);
                    if (kind2 != Token.Kind.OP) {
                        int c3 = nextChar();
                        int kind3 = Token.threeChars(c, c2, c3);
                        if (kind3 != Token.Kind.OP) {
                            return createToken(kind3);
                        } else {
                            oneBack();
                        }
                        return createToken(kind2);
                    }
                    oneBack();
                }

                    /* Keep track of parentheses nesting level */
                    switch (c) {
                        case '(':
                        case '[':
                        case '{':
                            if (parensNestingLevel >= MAXLEVEL) {
                                return syntaxError("too many nested parentheses");
                            }
                            parensStack[parensNestingLevel] = c;
                            parensLineNumberStack[parensNestingLevel] = currentLineNumber;
                            parensColumnsStack[parensNestingLevel] = (tokenStart - lineStartIndex);
                            parensNestingLevel++;
                            break;
                        case ')':
                        case ']':
                        case '}':
                            if (parensNestingLevel == 0) {
                                return syntaxError(String.format("unmatched '%c'", (char) c));
                            }
                            parensNestingLevel--;
                            int opening = parensStack[parensNestingLevel];
                            if (!((opening == '(' && c == ')') ||
                                            (opening == '[' && c == ']') ||
                                            (opening == '{' && c == '}'))) {
                                if (parensLineNumberStack[parensNestingLevel] != currentLineNumber) {
                                    return syntaxError(String.format("closing parenthesis '%c' does not match " +
                                                    "opening parenthesis '%c' on line %d",
                                                    (char) c, (char) opening, parensLineNumberStack[parensNestingLevel]));
                                } else {
                                    return syntaxError(String.format("closing parenthesis '%c' does not match " +
                                                    "opening parenthesis '%c'",
                                                    (char) c, (char) opening));
                                }
                            }
                            break;
                    }

                    /* Punctuation character */
                    return createToken(Token.oneChar(c));
            }
        }
    }

    /**
     * PyTokenizer_FindEncodingFilename
     *
     * Public API to expose how the tokenizer decides on the encoding of a file.
     *
     * @param channel - the data stream to read from
     * @throws IOException if a read error occurs in the {@code channel}
     * @return the {@link Charset} the Tokenizer will use for this data.
     */
    public static Charset findEncodingForFilename(SeekableByteChannel channel) throws IOException {
        ByteBuffer buf;
        int bytesRead;
        byte[] ary = new byte[0];
        int bufferSize = 0;
        int newlines = 0;
        int totalBytesRead = 0;
        do {
            int i = bufferSize;
            bufferSize += 4096;
            buf = ByteBuffer.allocate(bufferSize);
            buf.put(ary);
            bytesRead = channel.read(buf);
            if (bytesRead > 0) {
                totalBytesRead += bytesRead;
                ary = buf.array();
                while (i < totalBytesRead) {
                    if (ary[i++] == '\n') {
                        newlines++;
                        if (newlines == 2) {
                            break;
                        }
                    }
                }
            }
        } while (bytesRead > 0 && newlines < 2);
        return detectEncoding(getSourceStart(ary), ary);
    }

    // isxdigit
    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private Token createToken(int kind) {
        return createToken(kind, null);
    }

    private Token createToken(int kind, Object extraData) {
        if (kind == Token.Kind.ENDMARKER) {
            return new Token(kind, parensNestingLevel, tokenStart, nextCharIndex, new SourceRange(currentLineNumber, -1, currentLineNumber, -1), extraData);
        }
        return new Token(kind, parensNestingLevel, tokenStart, nextCharIndex, getCurrentTokenRange(kind == Token.Kind.STRING), extraData);
    }

    private SourceRange getCurrentTokenRange(boolean multiLineString) {
        int lineStart = multiLineString ? multiLineStartIndex : lineStartIndex;
        int lineno = multiLineString ? firstLineNumber : currentLineNumber;
        int endLineno = currentLineNumber;
        int colOffset = (tokenStart >= lineStart) ? (tokenStart - lineStart) : -1;
        int endColOffset = (nextCharIndex >= lineStartIndex) ? (nextCharIndex - lineStartIndex) : -1;
        if (lineno == 1) {
            colOffset += srcStartColumn;
        }
        if (endLineno == 1) {
            endColOffset += srcStartColumn;
        }
        lineno += srcStartLine;
        endLineno += srcStartLine;
        return new SourceRange(lineno, colOffset, endLineno, endColOffset);
    }

    public String getTokenString(Token tok) {
        String s;
        if (tok.startOffset >= codePointsInput.length) {
            return "";
        } else if (tok.endOffset >= codePointsInput.length) {
            s = new String(codePointsInput, tok.startOffset, codePointsInput.length - tok.startOffset);
        } else {
            s = new String(codePointsInput, tok.startOffset, tok.endOffset - tok.startOffset);
        }
        if (s.indexOf('\r') >= 0) {
            s = s.replaceAll("\r\n", "\n");
            s = s.replace('\r', '\n');
        }
        return s;
    }

    public String toString(Token token) {
        StringBuilder sb = new StringBuilder();
        sb.append("Token ");
        sb.append(token.typeName());
        sb.append(" [").append(token.startOffset).append(", ").append(token.endOffset).append("]");
        sb.append(" (").append(token.sourceRange.startLine).append(", ").append(token.sourceRange.startColumn);
        sb.append(") (").append(token.sourceRange.endLine).append(", ").append(token.sourceRange.endColumn).append(") '");
        sb.append(getTokenString(token)).append("'");
        return sb.toString();
    }

    public SourceRange extendRangeToCurrentPosition(SourceRange rangeStart) {
        return rangeStart.withEnd(currentLineNumber, nextCharIndex - lineStartIndex);
    }

    /**
     * bad_single_statement
     */
    public boolean isBadSingleStatement() {
        int cur = nextCharIndex;
        if (cur >= codePointsInput.length) {
            return false;
        }
        int c = codePointsInput[cur];
        while (true) {
            while (c == ' ' || c == '\t' || c == '\n' || c == '\014') {
                cur++;
                if (cur >= codePointsInput.length) {
                    return false;
                }
                c = codePointsInput[cur];
            }
            if (c != '#') {
                return true;
            }
            while (c != '\n') {
                cur++;
                if (cur >= codePointsInput.length) {
                    return false;
                }
                c = codePointsInput[cur];
            }
        }
    }

    public StatusCode getDone() {
        return done;
    }

    public int getParensNestingLevel() {
        return parensNestingLevel;
    }

    public int[] getParensStack() {
        return parensStack;
    }

    public int[] getParensLineNumberStack() {
        return parensLineNumberStack;
    }

    public int[] getParensColumnsStack() {
        return parensColumnsStack;
    }

    public int getNextCharIndex() {
        return nextCharIndex;
    }

    public int getLineStartIndex() {
        return lineStartIndex;
    }

    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    public int getCurrentIndentIndex() {
        return currentIndentIndex;
    }

    public void setCurrentIndentIndex(int currentIndentIndex) {
        this.currentIndentIndex = currentIndentIndex;
    }

    public void setPendingIndents(int pendingIndents) {
        this.pendingIndents = pendingIndents;
    }
}
