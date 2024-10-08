/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.pegparser.tokenizer.Token.Kind.DEDENT;
import static com.oracle.graal.python.pegparser.tokenizer.Token.Kind.ERRORTOKEN;
import static com.oracle.graal.python.pegparser.tokenizer.Token.Kind.INDENT;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.pegparser.PythonStringFactory.PythonStringBuilder;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ConstantValue.Kind;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.Name;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.Flag;

/**
 * From this class is extended the generated parser. It allow access to the tokenizer. The methods
 * defined in this class are mostly equivalents to those defined in CPython's {@code pegen.c}. This
 * allows us to keep the actions and parser generator very similar to CPython for easier updating in
 * the future.
 */
public abstract class AbstractParser {
    static final ExprTy[] EMPTY_EXPR_ARRAY = new ExprTy[0];
    static final KeywordTy[] EMPTY_KEYWORD_ARRAY = new KeywordTy[0];
    static final ArgTy[] EMPTY_ARG_ARRAY = new ArgTy[0];

    /**
     * Corresponds to TARGET_TYPES in CPython
     */
    public enum TargetsType {
        STAR_TARGETS,
        DEL_TARGETS,
        FOR_TARGETS
    }

    public enum Flags {
        /**
         * Corresponds to PyPARSE_BARRY_AS_BDFL, check whether <> should be used instead != .
         */
        BARRY_AS_BDFL,
        /**
         * Corresponds to PyPARSE_TYPE_COMMENTS.
         */
        TYPE_COMMENTS,

        /**
         * Corresponds to fp_interactive and prompt != NULL in struct tok_state.
         */
        INTERACTIVE_TERMINAL,
        ASYNC_HACKS
    }

    private static final String BARRY_AS_BDFL = "with Barry as BDFL, use '<>' instead of '!='";

    private int currentPos; // position of the mark
    private final ArrayList<Token> tokens;
    private final Tokenizer tokenizer;
    final ErrorCallback errorCb;
    protected final NodeFactory factory;
    final PythonStringFactory stringFactory;
    private final InputType startRule;

    private final EnumSet<Flags> flags;
    final int featureVersion;

    protected int level = 0;
    boolean callInvalidRules = false;

    private boolean parsingStarted;

    /**
     * Indicates, whether there was found an error
     */
    protected boolean errorIndicator = false;

    private ExprTy.Name cachedDummyName;

    protected final RuleResultCache<Object> cache = new RuleResultCache<>(this);
    protected final ArrayList<TypeIgnoreTy> comments = new ArrayList<>();

    private final Object[][][] reservedKeywords;
    private final String[] softKeywords;

    protected abstract Object[][][] getReservedKeywords();

    protected abstract String[] getSoftKeywords();

    protected abstract SSTNode runParser(InputType inputType);

    AbstractParser(String source, SourceRange sourceRange, PythonStringFactory stringFactory, ErrorCallback errorCb, InputType startRule, EnumSet<Flags> flags, int featureVersion) {
        this.currentPos = 0;
        this.tokens = new ArrayList<>();
        this.tokenizer = Tokenizer.fromString(errorCb, stringFactory, source, getTokenizerFlags(startRule, flags), sourceRange);
        this.factory = new NodeFactory();
        this.errorCb = errorCb;
        this.stringFactory = stringFactory;
        this.reservedKeywords = getReservedKeywords();
        this.softKeywords = getSoftKeywords();
        this.startRule = startRule;
        this.flags = flags;
        this.featureVersion = featureVersion;
    }

    private static EnumSet<Flag> getTokenizerFlags(InputType type, EnumSet<Flags> parserFlags) {
        EnumSet<Tokenizer.Flag> flags = EnumSet.noneOf(Tokenizer.Flag.class);
        if (type == InputType.FILE) {
            flags.add(Tokenizer.Flag.EXEC_INPUT);
        } else if (type == InputType.SINGLE && parserFlags.contains(Flags.INTERACTIVE_TERMINAL)) {
            flags.add(Tokenizer.Flag.INTERACTIVE);
        }
        if (parserFlags.contains(Flags.TYPE_COMMENTS)) {
            flags.add(Tokenizer.Flag.TYPE_COMMENT);
        }
        if (parserFlags.contains(Flags.ASYNC_HACKS)) {
            flags.add(Tokenizer.Flag.ASYNC_HACKS);
        }
        return flags;
    }

    public SSTNode parse() {
        SSTNode res = runParser(startRule);
        if (res == null) {
            resetParserState();
            runParser(startRule);
            if (errorIndicator) {
                // shouldn't we return at least wrong AST based on a option?
                return null;
            }
            int fill = getFill();
            if (fill == 0) {
                raiseSyntaxError("error at start before reading any input");
            } else if (peekToken(fill - 1).type == Token.Kind.ERRORTOKEN && tokenizer.getDone() == Tokenizer.StatusCode.EOF) {
                if (tokenizer.getParensNestingLevel() > 0) {
                    raiseUnclosedParenthesesError();
                } else {
                    raiseSyntaxError("unexpected EOF while parsing");
                }
            } else {
                if (peekToken(fill - 1).type == INDENT) {
                    raiseIndentationError("unexpected indent");
                } else if (peekToken(fill - 1).type == DEDENT) {
                    raiseIndentationError("unexpected unindent");
                } else {
                    raiseSyntaxErrorKnownLocation(peekToken(fill - 1), "invalid syntax");
                }
            }
        }
        if (startRule == InputType.SINGLE && tokenizer.isBadSingleStatement()) {
            return raiseSyntaxError("multiple statements found while compiling a single statement");
        }

        return res;
    }

    private void resetParserState() {
        errorIndicator = false;
        callInvalidRules = true;
        level = 0;
        cache.clear();
        currentPos = 0;
        tokenizer.reportIncompleteSourceIfInteractive = false;
    }

    /**
     * Get position in the tokenizer.
     *
     * @return the position in tokenizer.
     */
    public int mark() {
        return currentPos;
    }

    /**
     * Reset position in the tokenizer
     *
     * @param position where the tokenizer should set the current position
     */
    public void reset(int position) {
        currentPos = position;
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the expected token,
     * then the current position in tokenizer is changed to the next token.
     *
     * @param tokenKind - the token kind that is expected on the current position
     * @return The expected token or null if the token on the current position is not the expected
     *         one.
     */
    public Token expect(int tokenKind) {
        Token token = getAndInitializeToken();
        if (token.type == tokenKind) {
            currentPos++;
            return token;
        }
        return null;
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the expected token,
     * then the current position in tokenizer is changed to the next token.
     *
     * @param text - the token on the current position has to have this text
     * @return The expected token or null if the token on the current position is not the expected
     *         one.
     */
    public Token expect(String text) {
        Token token = getAndInitializeToken();
        if (text.equals(getText(token))) {
            currentPos++;
            return token;
        }
        return null;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has does not advance
     * the tokenizer, in contrast to {@link #expect(int)}.
     */
    protected boolean lookahead(boolean match, int kind) {
        int pos = mark();
        Token token = expect(kind);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has does not advance
     * the tokenizer, in contrast to {@link #expect(String)}.
     */
    protected boolean lookahead(boolean match, String text) {
        int pos = mark();
        Token token = expect(text);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Shortcut to Tokenizer.getText(Token)
     */
    public String getText(Token token) {
        if (token == null) {
            return null;
        }
        return tokenizer.getTokenString(token);
    }

    /**
     * equivalent to _PyPegen_fill_token in that it modifies the token, and does not advance
     */
    public Token getAndInitializeToken() {
        if (currentPos < getFill()) {
            return peekToken(currentPos);
        }
        Token token = tokenizer.next();
        while (token.type == Token.Kind.TYPE_IGNORE) {
            String tag = getText(token);
            comments.add(factory.createTypeIgnore(token.sourceRange.startLine, tag, token.sourceRange));
            token = tokenizer.next();
        }

        if (startRule == InputType.SINGLE && token.type == Token.Kind.ENDMARKER && parsingStarted) {
            token.type = Token.Kind.NEWLINE;
            parsingStarted = false;
            if (tokenizer.getCurrentIndentIndex() > 0) {
                tokenizer.setPendingIndents(-tokenizer.getCurrentIndentIndex());
                tokenizer.setCurrentIndentIndex(0);
            }
        } else {
            parsingStarted = true;
        }
        tokens.add(token);
        return initializeToken(token);
    }

    /**
     * _PyPegen_get_last_nonwhitespace_token
     */
    public Token getLastNonWhitespaceToken() {
        Token t = null;
        for (int i = mark() - 1; i >= 0; i--) {
            t = peekToken(i);
            if (t.type != Token.Kind.ENDMARKER && (t.type < Token.Kind.NEWLINE || t.type > DEDENT)) {
                break;
            }
        }
        return t;
    }

    /**
     * _PyPegen_name_token
     */
    public ExprTy.Name name_token() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {
            return factory.createVariable(getText(t), t.sourceRange);
        } else {
            return null;
        }
    }

    /**
     * _PyPegen_seq_count_dots
     */
    public int countDots(Token[] tokenArray) {
        int cnt = 0;
        for (Token t : tokenArray) {
            if (t.type == Token.Kind.ELLIPSIS) {
                cnt += 3;
            } else {
                assert t.type == Token.Kind.DOT;
                cnt += 1;
            }
        }
        return cnt;
    }

    /**
     * _PyPegen_expect_soft_keyword
     */
    protected ExprTy.Name expect_SOFT_KEYWORD(String keyword) {
        Token t = getAndInitializeToken();
        if (t.type == Token.Kind.NAME && getText(t).equals(keyword)) {
            currentPos++;
            return factory.createVariable(getText(t), t.sourceRange);
        }
        return null;
    }

    /**
     * IMPORTANT! _PyPegen_string_token returns (through void*) a Token*. We are trying to be type
     * safe, so we create a container.
     */
    public Token string_token() {
        return expect(Token.Kind.STRING);
    }

    /**
     * _PyPegen_number_token
     */
    public ExprTy number_token() {
        Token t = expect(Token.Kind.NUMBER);
        if (t == null) {
            return null;
        }

        String number = getText(t);
        if (number.contains("_")) {
            if (featureVersion < 6) {
                raiseSyntaxError("Underscores in numeric literals are only supported in Python 3.6 and greater");
            }
            number = number.replace("_", "");
        }
        int base = 10;
        int start = 0;
        boolean isFloat = false;
        boolean isComplex = false;

        if (number.startsWith("0")) {
            if (number.startsWith("0x") || number.startsWith("0X")) {
                base = 16;
                start = 2;
            } else if (number.startsWith("0o") || number.startsWith("0O")) {
                base = 8;
                start = 2;
            } else if (number.startsWith("0b") || number.startsWith("0B")) {
                base = 2;
                start = 2;
            }
        }
        if (base == 10) {
            isComplex = number.endsWith("j") || number.endsWith("J");
            if (!isComplex) {
                isFloat = number.contains(".") || number.contains("e") || number.contains("E");
            }
        }

        if (isComplex) {
            double imag = Double.parseDouble(number.substring(0, number.length() - 1));
            return factory.createConstant(ConstantValue.ofComplex(0.0, imag), t.sourceRange);
        }
        if (isFloat) {
            return factory.createConstant(ConstantValue.ofDouble(Double.parseDouble(number)), t.sourceRange);
        }
        final long max = Long.MAX_VALUE;
        final long moltmax = max / base;
        int i = start;
        long result = 0;
        int lastD;
        boolean overunder = false;
        while (i < number.length()) {
            lastD = digitValue(number.charAt(i));

            long next = result;
            if (next > moltmax) {
                overunder = true;
            } else {
                next *= base;
                if (next > (max - lastD)) {
                    overunder = true;
                } else {
                    next += lastD;
                }
            }
            if (overunder) {
                // overflow
                BigInteger bigResult = BigInteger.valueOf(result);
                BigInteger bigBase = BigInteger.valueOf(base);
                while (i < number.length()) {
                    bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(number.charAt(i))));
                    i++;
                }
                return factory.createConstant(ConstantValue.ofBigInteger(bigResult), t.sourceRange);
            }
            result = next;
            i++;
        }
        return factory.createConstant(ConstantValue.ofLong(result), t.sourceRange);
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    /**
     * _PyPegen_expect_forced_token
     */
    public Token expect_forced_token(int kind, String expected) {
        Token t = getAndInitializeToken();
        if (t.type != kind) {
            raiseSyntaxErrorKnownLocation(t, "expected '%s'", expected);
            return null;
        }
        currentPos++;
        return t;
    }

    public ExprTy.Name name_from_token(Token t) {
        if (t == null) {
            return null;
        }
        String id = getText(t);
        return factory.createVariable(id, t.sourceRange);
    }

    /**
     * _PyPegen_soft_keyword_token
     */
    public ExprTy.Name soft_keyword_token() {
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
    public ExprTy.Name dummyName(@SuppressWarnings("unused") Object... args) {
        if (cachedDummyName != null) {
            return cachedDummyName;
        }
        cachedDummyName = factory.createVariable("", SourceRange.ARTIFICIAL_RANGE);
        return cachedDummyName;
    }

    /**
     * _PyPegen_join_names_with_dot
     */
    public SSTNode joinNamesWithDot(ExprTy a, ExprTy b) {
        String id = ((ExprTy.Name) a).id + "." + ((ExprTy.Name) b).id;
        return factory.createVariable(id, a.getSourceRange().withEnd(b.getSourceRange()));
    }

    /**
     * _PyPegen_seq_insert_in_front
     */
    @SuppressWarnings("unchecked")
    public <T> T[] insertInFront(T element, T[] seq, Class<T> clazz) {
        T[] result;
        if (seq == null) {
            result = (T[]) Array.newInstance(clazz, 1);
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            System.arraycopy(seq, 0, result, 1, seq.length);
        }
        result[0] = element;
        return result;
    }

    public ExprTy[] insertInFront(ExprTy element, ExprTy[] seq) {
        return insertInFront(element, seq, ExprTy.class);
    }

    public PatternTy[] insertInFront(PatternTy element, PatternTy[] seq) {
        return insertInFront(element, seq, PatternTy.class);
    }

    /**
     * _PyPegen_seq_append_to_end
     */
    @SuppressWarnings("unchecked")
    public <T> T[] appendToEnd(T[] seq, T element, Class<T> clazz) {
        T[] result;
        if (seq == null) {
            result = (T[]) Array.newInstance(clazz, 1);
            result[0] = element;
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            result[seq.length] = element;
        }
        return result;
    }

    public ExprTy[] appendToEnd(ExprTy[] seq, ExprTy element) {
        return appendToEnd(seq, element, ExprTy.class);
    }

    /**
     * _PyPegen_decode_fstring_part
     */
    private ExprTy.Constant decodeFStringPart(boolean isRaw, ExprTy.Constant constant, Token token) {
        assert constant.value.kind == Kind.RAW;
        int[] codePoints = stringFactory.toCodePoints(constant.value);
        int len;
        if (codePoints.length == 2 && codePoints[0] == codePoints[1] && (codePoints[0] == '{' || codePoints[0] == '}')) {
            len = 1;
        } else {
            len = codePoints.length;
        }
        boolean isRawUpdated = isRaw || StringParser.indexOf(codePoints, 0, codePoints.length, '\\') < 0;
        ConstantValue str = StringParser.decodeString(this, codePoints, isRawUpdated, 0, len, token);
        return factory.createConstant(str, constant.getSourceRange());
    }

    /**
     * unpack_top_level_joined_strs
     */
    private static ExprTy[] unpackTopLevelJoinedStrs(ExprTy[] rawExpressions) {
        int reqSize = 0;
        for (ExprTy expr : rawExpressions) {
            if (expr instanceof ExprTy.JoinedStr joinedStr) {
                reqSize += joinedStr.values.length;
            } else {
                reqSize++;
            }
        }

        ExprTy[] expressions = new ExprTy[reqSize];
        int reqIndex = 0;
        for (ExprTy expr : rawExpressions) {
            if (expr instanceof ExprTy.JoinedStr joinedStr) {
                ExprTy[] values = joinedStr.values;
                System.arraycopy(values, 0, expressions, reqIndex, values.length);
                reqIndex += values.length;
            } else {
                expressions[reqIndex++] = expr;
            }
        }
        return expressions;
    }

    /**
     * _PyPegen_joined_str
     */
    public ExprTy joinedStr(Token a, ExprTy[] rawExpressions, Token b) {
        ExprTy[] expr = unpackTopLevelJoinedStrs(rawExpressions);
        int nItems = expr.length;

        String quoteStr = tokenizer.getTokenString(a);
        boolean isRaw = quoteStr.indexOf('r') >= 0 || quoteStr.indexOf('R') >= 0;

        ExprTy[] seq = new ExprTy[nItems];

        int index = 0;
        for (ExprTy item : expr) {
            if (item instanceof ExprTy.Constant constant) {
                item = constant = decodeFStringPart(isRaw, constant, b);
                if (constant.value.kind == Kind.RAW && stringFactory.isEmpty(constant.value)) {
                    continue;
                }
            }
            seq[index++] = item;
        }

        ExprTy[] resizedExprs = Arrays.copyOf(seq, index);
        return factory.createJoinedStr(resizedExprs, a.sourceRange.withEnd(b.sourceRange));
    }

    /**
     * _PyPegen_decoded_constant_from_token
     */
    public ExprTy decodedConstantFromToken(Token tok) {
        ConstantValue cv = StringParser.decodeString(this, tokenizer.getCodePointsInput(), false, tok.startOffset, tok.endOffset - tok.startOffset, tok);
        return factory.createConstant(cv, tok.getSourceRange());
    }

    /**
     * _PyPegen_constant_from_token
     */
    public SSTNode constantFromToken(Token tok) {
        return factory.createConstant(stringFactory.fromCodePoints(tokenizer.getCodePointsInput(), tok.startOffset, tok.endOffset - tok.startOffset), tok.sourceRange);
    }

    /**
     * _PyPegen_constant_from_string
     */
    public ExprTy constantFromString(Token tok) {
        assert tok.startOffset < tokenizer.getCodePointsInputLength();
        assert tok.endOffset <= tokenizer.getCodePointsInputLength();
        int[] codePoints = tokenizer.getCodePointsInput();
        String kind = codePoints[tok.startOffset] == 'u' ? "u" : null;
        ConstantValue cv = StringParser.parseString(this, codePoints, tok);
        return factory.createConstant(cv, kind, tok.getSourceRange());
    }

    /**
     * _PyPegen_formatted_value
     */
    public ExprTy formattedValue(ExprTy expression, Token debug, ResultTokenWithMetadata conversion,
                    ResultTokenWithMetadata format, Token closingBrace, SourceRange sourceRange) {
        int conversionVal = -1;
        if (conversion != null) {
            assert conversion.result() instanceof Name;
            String conversionKind = ((ExprTy.Name) conversion.result()).id;
            char first = conversionKind.length() == 1 ? conversionKind.charAt(0) : 0;
            if (first != 's' && first != 'r' && first != 'a') {
                raiseSyntaxErrorKnownLocation(conversion.result(), "f-string: invalid conversion character '%s': expected 's', 'r', or 'a'", conversionKind);
            }
            conversionVal = first;
        } else if (debug != null && format == null) {
            /* If no conversion is specified, use !r for debug expressions */
            conversionVal = 'r';
        }
        ExprTy.FormattedValue formattedValue = factory.createFormattedValue(expression, conversionVal, format != null ? format.result : null, sourceRange);
        if (debug != null) {
            int debugEndLine;
            int debugEndColumn;
            ConstantValue debugMetadata;
            if (conversion != null) {
                debugEndLine = conversion.result.getSourceRange().startLine;
                debugEndColumn = conversion.result.getSourceRange().startColumn;
                debugMetadata = conversion.metadata;
            } else if (format != null) {
                debugEndLine = format.result.getSourceRange().startLine;
                debugEndColumn = format.result.getSourceRange().startColumn + 1;
                debugMetadata = format.metadata;
            } else {
                debugEndLine = sourceRange.endLine;
                debugEndColumn = sourceRange.endColumn;
                debugMetadata = closingBrace.metadata;
            }
            ExprTy.Constant debugText = factory.createConstant(debugMetadata,
                            new SourceRange(sourceRange.startLine, sourceRange.startColumn + 1, debugEndLine, debugEndColumn - 1));
            return factory.createJoinedStr(new ExprTy[]{debugText, formattedValue},
                            new SourceRange(sourceRange.startLine, sourceRange.startColumn, debugEndLine, debugEndColumn));
        }
        return formattedValue;
    }

    /**
     * _PyPegen_concatenate_strings
     */
    public ExprTy concatenateStrings(ExprTy[] strings, SourceRange sourceRange) {
        boolean fStringFound = false;
        boolean unicodeStringFound = false;
        boolean bytesFound = false;

        int nFlattenedElements = 0;
        for (ExprTy elem : strings) {
            if (elem instanceof ExprTy.Constant constant) {
                if (constant.value.kind == Kind.BYTES) {
                    bytesFound = true;
                } else {
                    unicodeStringFound = true;
                }
                nFlattenedElements++;
            } else if (elem instanceof ExprTy.JoinedStr joinedStr) {
                nFlattenedElements += joinedStr.values.length;
                fStringFound = true;
            } else {
                nFlattenedElements++;
                fStringFound = true;
            }
        }

        if ((unicodeStringFound || fStringFound) && bytesFound) {
            return (ExprTy) raiseSyntaxError("cannot mix bytes and nonbytes literals");
        }

        if (bytesFound) {
            Object kind = ((ExprTy.Constant) strings[0]).kind;
            int totalLen = 0;
            for (ExprTy elem : strings) {
                totalLen += ((ExprTy.Constant) elem).value.getBytes().length;
            }
            byte[] dest = new byte[totalLen];
            int offset = 0;
            for (ExprTy elem : strings) {
                byte[] src = ((ExprTy.Constant) elem).value.getBytes();
                System.arraycopy(src, 0, dest, offset, src.length);
                offset += src.length;
            }
            return factory.createConstant(ConstantValue.ofBytes(dest), kind, sourceRange);
        }

        if (!fStringFound && strings.length == 1) {
            return strings[0];
        }

        ExprTy[] flattened = new ExprTy[nFlattenedElements];
        int curPos = 0;
        for (ExprTy elem : strings) {
            if (elem instanceof ExprTy.JoinedStr joined) {
                for (ExprTy subvalue : joined.values) {
                    flattened[curPos++] = subvalue;
                }
            } else {
                flattened[curPos++] = elem;
            }
        }

        /* calculate folded element count */
        int nElements = 0;
        boolean prevIsConstant = false;
        for (ExprTy elem : flattened) {
            /*
             * The concatenation of a FormattedValue and an empty Constant should lead to the
             * FormattedValue itself. Thus, we will not take any empty constants into account, just
             * as in `_PyPegen_joined_str`
             */
            if (fStringFound && elem instanceof ExprTy.Constant constant &&
                            constant.value.kind == Kind.RAW && stringFactory.isEmpty(constant.value)) {
                continue;
            }

            if (!prevIsConstant || !(elem instanceof ExprTy.Constant)) {
                nElements++;
            }
            prevIsConstant = elem instanceof ExprTy.Constant;
        }

        ExprTy[] values = new ExprTy[nElements];

        /* build folded list */
        PythonStringBuilder writer;
        curPos = 0;
        for (int i = 0; i < flattened.length; i++) {
            ExprTy elem = flattened[i];

            /*
             * if the current elem and the following are constants, fold them and all consequent
             * constants
             */
            if (elem instanceof ExprTy.Constant elemConst) {
                if (i + 1 < flattened.length && flattened[i + 1] instanceof ExprTy.Constant) {
                    ExprTy.Constant firstElemConst = elemConst;

                    /*
                     * When a string is getting concatenated, the kind of the string is determined
                     * by the first string in the concatenation sequence.
                     */
                    Object kind = elemConst.kind;

                    writer = stringFactory.createBuilder(0);
                    ExprTy.Constant lastElemConst = elemConst;
                    int j = i;
                    for (; j < flattened.length; j++) {
                        ExprTy currentElem = flattened[j];
                        if (currentElem instanceof ExprTy.Constant currentElemConst) {
                            writer.appendConstantValue(currentElemConst.value);
                            lastElemConst = currentElemConst;
                        } else {
                            break;
                        }
                    }
                    i = j - 1;

                    elem = elemConst = factory.createConstant(writer.build(), kind, firstElemConst.getSourceRange().withEnd(lastElemConst.getSourceRange()));
                }

                /* Drop all empty constant strings */
                if (fStringFound && elemConst.value.kind == Kind.RAW && stringFactory.isEmpty(elemConst.value)) {
                    continue;
                }
            }
            values[curPos++] = elem;
        }

        if (!fStringFound) {
            assert nElements == 1;
            ExprTy elem = values[0];
            assert elem instanceof ExprTy.Constant;
            return elem;
        }

        assert curPos == nElements;
        return factory.createJoinedStr(values, sourceRange);
    }

    /**
     * _PyPegen_check_barry_as_flufl
     */
    public boolean checkBarryAsFlufl(Token token) {
        if (flags.contains(Flags.BARRY_AS_BDFL) && !getText(token).equals("<>")) {
            errorCb.onError(token.sourceRange, BARRY_AS_BDFL);
            return true;
        }
        if (!flags.contains(Flags.BARRY_AS_BDFL) && !getText(token).equals("!=")) {
            // no explicit error message here, the parser will just fail to match the input
            // producing the generic 'invalid syntax' error
            return true;
        }
        return false;
    }

    /**
     * _PyPegen_check_legacy_stmt
     */
    public boolean checkLegacyStmt(ExprTy name) {
        if (!(name instanceof ExprTy.Name)) {
            return false;
        }
        String[] candidates = {"print", "exec"};
        for (String candidate : candidates) {
            if (candidate.equals(((ExprTy.Name) name).id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * _PyPegen_check_fstring_conversion
     */
    ResultTokenWithMetadata checkFstringConversion(Token convToken, ExprTy conv) {
        if (convToken.sourceRange.startLine != conv.getSourceRange().startLine ||
                        convToken.sourceRange.endColumn != conv.getSourceRange().startColumn) {
            raiseSyntaxErrorKnownRange(convToken, conv, "f-string: conversion type must come right after the exclamanation mark");
        }
        return new ResultTokenWithMetadata(conv, convToken.metadata);
    }

    /**
     * _PyPegen_setup_full_format_spec
     */
    ResultTokenWithMetadata setupFullFormatSpec(Token colon, ExprTy[] spec, SourceRange sourceRange) {
        // This is needed to keep compatibility with 3.11, where an empty format spec
        // is parsed as an *empty* JoinedStr node, instead of having an empty constant
        // in it.
        ExprTy[] fixedSpec;
        if (spec.length == 1 && spec[0] instanceof ExprTy.Constant constant && constant.value.kind == Kind.RAW && stringFactory.isEmpty(constant.value)) {
            fixedSpec = new ExprTy[0];
        } else {
            fixedSpec = spec;
        }
        ExprTy res;
        if (fixedSpec.length == 0 || (fixedSpec.length == 1 && fixedSpec[0] instanceof ExprTy.Constant)) {
            res = factory.createJoinedStr(fixedSpec, sourceRange);
        } else {
            res = concatenateStrings(fixedSpec, sourceRange);
        }
        return new ResultTokenWithMetadata(res, colon.metadata);
    }

    /**
     * _PyPegen_get_expr_name
     */
    public String getExprName(ExprTy e) {
        if (e instanceof ExprTy.Attribute || e instanceof ExprTy.Subscript || e instanceof ExprTy.Starred || e instanceof ExprTy.Name || e instanceof ExprTy.Tuple || e instanceof ExprTy.List ||
                        e instanceof ExprTy.Lambda) {
            return e.getClass().getSimpleName().toLowerCase();
        }
        if (e instanceof ExprTy.Call) {
            return "function call";
        }
        if (e instanceof ExprTy.BoolOp || e instanceof ExprTy.BinOp || e instanceof ExprTy.UnaryOp) {
            return "expression";
        }
        if (e instanceof ExprTy.GeneratorExp) {
            return "generator expression";
        }
        if (e instanceof ExprTy.Yield || e instanceof ExprTy.YieldFrom) {
            return "yield expression";
        }
        if (e instanceof ExprTy.Await) {
            return "await expression";
        }
        if (e instanceof ExprTy.ListComp) {
            return "list comprehension";
        }
        if (e instanceof ExprTy.SetComp) {
            return "set comprehension";
        }
        if (e instanceof ExprTy.DictComp) {
            return "dict comprehension";
        }
        if (e instanceof ExprTy.Dict) {
            return "dict literal";
        }
        if (e instanceof ExprTy.Set) {
            return "set display";
        }
        if (e instanceof ExprTy.JoinedStr || e instanceof ExprTy.FormattedValue) {
            return "f-string expression";
        }
        if (e instanceof ExprTy.Constant) {
            ExprTy.Constant constant = (ExprTy.Constant) e;
            switch (constant.value.kind) {
                case NONE:
                    return "None";
                case BOOLEAN:
                    return constant.value.getBoolean() ? "True" : "False";
                case ELLIPSIS:
                    return "ellipsis";
            }
            return "literal";
        }
        if (e instanceof ExprTy.Compare) {
            return "comparision";
        }
        if (e instanceof ExprTy.IfExp) {
            return "conditional expression";
        }
        if (e instanceof ExprTy.NamedExpr) {
            return "named expression";
        }
        assert false : "unexpected expression " + e.getClass() + " in assignment";
        return null;
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
                        token.type = (int) kwAssoc[1];
                        break;
                    }
                }
            }
        }
        if (token.type == ERRORTOKEN) {
            tokenizerError(token);
        }
        return token;
    }

    /**
     * _PyPegen_new_type_comment
     */
    protected String newTypeComment(Object token) {
        return getText((Token) token);
    }

    /**
     * _PyPegen_join_sequences
     *
     */
    protected <T> T[] join(T[] a, T[] b) {
        if (a == null && b != null) {
            return b;
        }
        if (a != null && b == null) {
            return a;
        }

        if (a != null) {
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
     * includes an attempt with a symbol and a scope stream synchronized to the token stream, but it
     * doesn't really work with the pegen generator.
     */
    protected ExprTy setExprContext(ExprTy node, ExprContextTy context) {
        return node.accept(new CopyWithContextVisitor(context));
    }

    // debug methods
    private void indent(StringBuffer sb) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    void debugMessageln(String text, Object... args) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(String.format(text, args));
        System.out.println(sb);
    }

    // Helper classes that are not really meaningful parts of the AST, just containers to move the
    // data where we need it.

    public static final class CmpopExprPair {
        final CmpOpTy op;
        final ExprTy expr;

        CmpopExprPair(CmpOpTy op, ExprTy expr) {
            this.op = op;
            this.expr = expr;
        }
    }

    public static final class KeyValuePair {
        final ExprTy key;
        final ExprTy value;

        KeyValuePair(ExprTy key, ExprTy value) {
            this.key = key;
            this.value = value;
        }

    }

    static ExprTy[] extractKeys(KeyValuePair[] l) {
        int len = l == null ? 0 : l.length;
        ExprTy[] keys = new ExprTy[len];
        for (int i = 0; i < len; i++) {
            keys[i] = l[i].key;
        }
        return keys;
    }

    static ExprTy[] extractValues(KeyValuePair[] l) {
        int len = l == null ? 0 : l.length;
        ExprTy[] values = new ExprTy[len];
        for (int i = 0; i < len; i++) {
            values[i] = l[i].value;
        }
        return values;
    }

    public static final class KeyPatternPair {
        final ExprTy key;
        final PatternTy pattern;

        KeyPatternPair(ExprTy key, PatternTy pattern) {
            this.key = key;
            this.pattern = pattern;
        }
    }

    static ExprTy[] extractKeys(KeyPatternPair[] l) {
        int len = l == null ? 0 : l.length;
        ExprTy[] keys = new ExprTy[len];
        for (int i = 0; i < len; i++) {
            keys[i] = l[i].key;
        }
        return keys;
    }

    static PatternTy[] extractPatterns(KeyPatternPair[] l) {
        int len = l == null ? 0 : l.length;
        PatternTy[] values = new PatternTy[len];
        for (int i = 0; i < len; i++) {
            values[i] = l[i].pattern;
        }
        return values;
    }

    public static final class NameDefaultPair {
        final ArgTy name;
        final ExprTy def;

        NameDefaultPair(ArgTy name, ExprTy def) {
            this.name = name;
            this.def = def;
        }
    }

    public static final class SlashWithDefault {
        final ArgTy[] plainNames;
        final NameDefaultPair[] namesWithDefaults;

        SlashWithDefault(ArgTy[] plainNames, NameDefaultPair[] namesWithDefaults) {
            this.plainNames = plainNames;
            this.namesWithDefaults = namesWithDefaults;
        }
    }

    public static final class StarEtc {
        final ArgTy varArg;
        final NameDefaultPair[] kwOnlyArgs;
        final ArgTy kwArg;

        StarEtc(ArgTy varArg, NameDefaultPair[] kwOnlyArgs, ArgTy kwArg) {
            this.varArg = varArg;
            this.kwOnlyArgs = kwOnlyArgs;
            this.kwArg = kwArg;
        }
    }

    public static final class KeywordOrStarred {
        final SSTNode element;
        final boolean isKeyword;

        KeywordOrStarred(SSTNode element, boolean isKeyword) {
            this.element = element;
            this.isKeyword = isKeyword;
        }
    }

    /**
     * _PyPegen_seq_extract_starred_exprs
     */
    static ExprTy[] extractStarredExpressions(KeywordOrStarred[] kwds) {
        List<ExprTy> list = new ArrayList<>();
        for (KeywordOrStarred n : kwds) {
            if (!n.isKeyword) {
                ExprTy element = (ExprTy) n.element;
                list.add(element);
            }
        }
        return list.toArray(new ExprTy[0]);
    }

    /**
     * _PyPegen_seq_delete_starred_exprs
     */
    static KeywordTy[] deleteStarredExpressions(KeywordOrStarred[] kwds) {
        List<KeywordTy> list = new ArrayList<>();
        for (KeywordOrStarred n : kwds) {
            if (n.isKeyword) {
                KeywordTy element = (KeywordTy) n.element;
                list.add(element);
            }
        }
        return list.toArray(new KeywordTy[0]);
    }

    /**
     * _PyPegen_map_names_to_ids
     */
    static String[] extractNames(ExprTy[] seq) {
        List<String> list = new ArrayList<>();
        for (ExprTy e : seq) {
            String id = ((ExprTy.Name) e).id;
            list.add(id);
        }
        return list.toArray(new String[0]);
    }

    /**
     * _PyPegen_collect_call_seqs
     */
    final ExprTy collectCallSequences(ExprTy[] a, KeywordOrStarred[] b, SourceRange sourceRange) {
        if (b == null) {
            return factory.createCall(dummyName(), a, EMPTY_KEYWORD_ARRAY, sourceRange);
        } else {
            ExprTy[] starred = extractStarredExpressions(b);
            ExprTy[] args;
            if (starred.length > 0) {
                args = Arrays.copyOf(a, a.length + starred.length);
                System.arraycopy(starred, 0, args, a.length, starred.length);
            } else {
                args = a;
            }
            return factory.createCall(dummyName(), args, deleteStarredExpressions(b), sourceRange);
        }
    }

    private ExprTy visitContainer(ExprTy[] elements, TargetsType type) {
        if (elements == null) {
            return null;
        }
        ExprTy child;
        for (ExprTy expr : elements) {
            child = getInvalidTarget(expr, type);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private ExprTy getInvalidTarget(ExprTy expr, TargetsType type) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof ExprTy.List) {
            return visitContainer(((ExprTy.List) expr).elements, type);
        }
        if (expr instanceof ExprTy.Tuple) {
            return visitContainer(((ExprTy.Tuple) expr).elements, type);
        }
        if (expr instanceof ExprTy.Starred) {
            if (type == TargetsType.DEL_TARGETS) {
                return expr;
            }
            return getInvalidTarget(((ExprTy.Starred) expr).value, type);
        }
        if (expr instanceof ExprTy.Compare) {
            if (type == TargetsType.FOR_TARGETS) {
                ExprTy.Compare compare = (ExprTy.Compare) expr;
                if (compare.ops[0] == CmpOpTy.In) {
                    return getInvalidTarget(compare.left, type);
                }
                return null;
            }
            return expr;
        }
        if (expr instanceof ExprTy.Name || expr instanceof ExprTy.Subscript || expr instanceof ExprTy.Attribute) {
            return null;
        }
        return expr;
    }

    /**
     * _PyPegen_nonparen_genexp_in_call
     */
    SSTNode nonparenGenexpInCall(ExprTy args, ComprehensionTy[] comprehensions) {
        assert args instanceof ExprTy.Call;
        ExprTy.Call call = (ExprTy.Call) args;
        int len = call.args.length;
        if (len <= 1) {
            return null;
        }
        ComprehensionTy lastComprehension = comprehensions[comprehensions.length - 1];
        return raiseSyntaxErrorKnownRange(call.args[len - 1], getLastComprehensionItem(lastComprehension),
                        "Generator expression must be parenthesized");
    }

    /**
     * RAISE_SYNTAX_ERROR_INVALID_TARGET
     */
    SSTNode raiseSyntaxErrorInvalidTarget(TargetsType type, ExprTy expr) {
        ExprTy invalidTarget = getInvalidTarget(expr, type);
        if (invalidTarget != null) {
            String message = (type == TargetsType.STAR_TARGETS || type == TargetsType.FOR_TARGETS)
                            ? "cannot assign to %s"
                            : "cannot delete %s";
            raiseSyntaxErrorKnownLocation(invalidTarget, message, getExprName(invalidTarget));
        }
        return raiseSyntaxError("invalid syntax");
    }

    /**
     * RAISE_SYNTAX_ERROR
     */
    SSTNode raiseSyntaxError(String msg, Object... arguments) {
        Token errorToken = peekToken();
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, errorToken.sourceRange, msg, arguments);
    }

    /**
     * RAISE_SYNTAX_ERROR_ON_NEXT_TOKEN
     */
    SSTNode raiseSyntaxErrorOnNextToken(String msg) {
        Token errorToken = peekToken();
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, errorToken.sourceRange, msg);
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION the first param is a token, where error begins
     */
    public SSTNode raiseSyntaxErrorKnownLocation(Token errorToken, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, errorToken.sourceRange, msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION
     */
    SSTNode raiseErrorKnownLocation(ErrorCallback.ErrorType typeError, SourceRange where, String msg, Object... argument) {
        errorIndicator = true;
        errorCb.onError(typeError, where, msg, argument);
        return null;
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION the first param is node, where error begins
     */
    SSTNode raiseSyntaxErrorKnownLocation(SSTNode where, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, where.getSourceRange(), msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION
     */
    SSTNode raiseErrorKnownLocation(ErrorCallback.ErrorType errorType, SSTNode where, String msg, Object... arguments) {
        return raiseErrorKnownLocation(errorType, where.getSourceRange(), msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_RANGE
     */
    SSTNode raiseSyntaxErrorKnownRange(Token startToken, SSTNode endNode, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, startToken.sourceRange.withEnd(endNode.getSourceRange()), msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_RANGE
     */
    SSTNode raiseSyntaxErrorKnownRange(Token startToken, Token endToken, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, startToken.sourceRange.withEnd(endToken.sourceRange), msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_RANGE
     */
    SSTNode raiseSyntaxErrorKnownRange(SSTNode startNode, SSTNode endNode, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, startNode.getSourceRange().withEnd(endNode.getSourceRange()), msg, arguments);
    }

    /**
     * RAISE_ERROR_KNOWN_RANGE
     */
    SSTNode raiseSyntaxErrorKnownRange(SSTNode startNode, Token endToken, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, startNode.getSourceRange().withEnd(endToken.sourceRange), msg, arguments);
    }

    /**
     * RAISE_SYNTAX_ERROR_STARTING_FROM
     */
    SSTNode raiseSyntaxErrorStartingFrom(Token where, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, tokenizer.extendRangeToCurrentPosition(where.sourceRange), msg, arguments);
    }

    /**
     * RAISE_SYNTAX_ERROR_STARTING_FROM
     */
    SSTNode raiseSyntaxErrorStartingFrom(SSTNode where, String msg, Object... arguments) {
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, tokenizer.extendRangeToCurrentPosition(where.getSourceRange()), msg, arguments);
    }

    /**
     * _PyPegen_arguments_parsing_error
     */
    SSTNode raiseArgumentsParsingError(ExprTy e) {
        for (KeywordTy keyword : ((ExprTy.Call) e).keywords) {
            if (keyword.arg == null) {
                return raiseSyntaxError("positional argument follows keyword argument unpacking");
            }
        }
        return raiseSyntaxError("positional argument follows keyword argument");
    }

    /**
     * RAISE_INDENTATION_ERROR
     */
    SSTNode raiseIndentationError(String msg, Object... arguments) {
        Token errorToken = peekToken();
        return raiseErrorKnownLocation(ErrorCallback.ErrorType.Indentation, errorToken.sourceRange, msg, arguments);
    }

    /**
     * raise_unclosed_parentheses_error
     */
    void raiseUnclosedParenthesesError() {
        int nestingLevel = tokenizer.getParensNestingLevel();
        assert nestingLevel > 0;
        int errorLineno = tokenizer.getParensLineNumberStack()[nestingLevel - 1];
        int errorCol = tokenizer.getParensColumnsStack()[nestingLevel - 1];
        // TODO unknown source offsets
        raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax,
                        new SourceRange(errorLineno, errorCol, errorLineno, -1),
                        "'%c' was never closed", tokenizer.getParensStack()[nestingLevel - 1]);
    }

    /**
     * tokenizer_error
     */
    void tokenizerError(Token token) {
        if (token.type == ERRORTOKEN && tokenizer.getDone() == Tokenizer.StatusCode.SYNTAX_ERROR) {
            raiseErrorKnownLocation(ErrorCallback.ErrorType.Syntax, token.getSourceRange(), (String) token.extraData);
        }
        ErrorCallback.ErrorType errorType = ErrorCallback.ErrorType.Syntax;
        String msg;
        int colOffset = -1;
        switch (tokenizer.getDone()) {
            case BAD_TOKEN:
                msg = "invalid token";
                break;
            case EOF:
                if (tokenizer.getParensNestingLevel() > 0) {
                    raiseUnclosedParenthesesError();
                } else {
                    raiseSyntaxError("unexpected EOF while parsing");
                }
                return;
            case DEDENT_INVALID:
                raiseIndentationError("unindent does not match any outer indentation level");
                return;
            case TABS_SPACES_INCONSISTENT:
                errorType = ErrorCallback.ErrorType.Tab;
                msg = "inconsistent use of tabs and spaces in indentation";
                break;
            case TOO_DEEP_INDENTATION:
                errorType = ErrorCallback.ErrorType.Indentation;
                msg = "too many levels of indentation";
                break;
            case LINE_CONTINUATION_ERROR:
                msg = "unexpected character after line continuation character";
                colOffset = tokenizer.getNextCharIndex() - tokenizer.getLineStartIndex();
                break;
            default:
                msg = "unknown parsing error";
                break;
        }
        // TODO unknown source offsets
        raiseErrorKnownLocation(errorType, new SourceRange(tokenizer.getCurrentLineNumber(),
                        colOffset >= 0 ? colOffset : 0, tokenizer.getCurrentLineNumber(), -1), msg);
    }

    // Equivalent of _PyPegen_interactive_exit
    SSTNode interactiveExit() {
        // This causes the corresponding rule to always fail. CPython also sets a variable to E_EOF
        // which is later checked in PyRun_InteractiveOneObjectEx to end the REPL. Our REPL handles
        // it differently - it won't call the parser with an empty string at all. We still need to
        // fail here in case someone calls compile('', 'single'), but we don't need the error code.
        return null;
    }

    <T> T lastItem(T[] seq) {
        return seq[seq.length - 1];
    }

    ExprTy getLastComprehensionItem(ComprehensionTy comprehension) {
        if (comprehension.ifs == null || comprehension.ifs.length == 0) {
            return comprehension.iter;
        }
        return lastItem(comprehension.ifs);
    }

    ExprTy ensureReal(ExprTy e) {
        if (!(e instanceof ExprTy.Constant) || ((ExprTy.Constant) e).value.kind == Kind.COMPLEX) {
            raiseSyntaxErrorKnownLocation(e, "real number required in complex literal");
        }
        return e;
    }

    ExprTy ensureImaginary(ExprTy e) {
        if (!(e instanceof ExprTy.Constant) || ((ExprTy.Constant) e).value.kind != Kind.COMPLEX) {
            raiseSyntaxErrorKnownLocation(e, "imaginary number required in complex literal");
        }
        return e;
    }

    ModTy makeModule(StmtTy[] statements, SourceRange sourceRange) {
        return factory.createModule(statements, comments.toArray(TypeIgnoreTy[]::new), sourceRange);
    }

    /**
     * CHECK Simple check whether the node is not null.
     */
    <T> T check(T node) {
        if (node == null) {
            errorIndicator = true;
        }
        return node;
    }

    <T> T checkVersion(int version, String msg, T node) {
        checkVersion(version, msg);
        return node;
    }

    <T> T checkVersion(int version, String msg, Supplier<T> node) {
        checkVersion(version, msg);
        return node.get();
    }

    private void checkVersion(int version, String msg) {
        if (featureVersion < version) {
            raiseSyntaxError("%s only supported in Python 3.%d and greater", msg, version);
        }
    }

    private int getFill() {
        return tokens.size();
    }

    private Token peekToken() {
        if (currentPos == tokens.size()) {
            Token t = tokenizer.next();
            if (t.type != Token.Kind.TYPE_IGNORE) {
                tokens.add(t);
            }
            return t;
        }
        return tokens.get(currentPos);
    }

    protected final Token peekToken(int position) {
        assert position < tokens.size();
        return tokens.get(position);
    }

    public record ResultTokenWithMetadata(ExprTy result, ConstantValue metadata) {
    }

}
