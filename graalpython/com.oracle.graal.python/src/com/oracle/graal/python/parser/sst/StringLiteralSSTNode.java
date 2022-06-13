/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_MIX_MESSAGE;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.parser.PythonSSTNodeFactory.FStringExprParser;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.source.Source;

public abstract class StringLiteralSSTNode extends SSTNode {

    private StringLiteralSSTNode(int start, int end) {
        super(start, end);
    }

    public static final class RawStringLiteralSSTNode extends StringLiteralSSTNode {

        protected String value;

        public RawStringLiteralSSTNode(String value, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        public String getValue() {
            return value;
        }

    }

    public static final class BytesLiteralSSTNode extends StringLiteralSSTNode {

        protected byte[] value;

        protected BytesLiteralSSTNode(byte[] value, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.value = value;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public enum FormatStringConversionType {
        STR_CONVERTION,
        REPR_CONVERSION,
        ASCII_CONVERSION,
        NO_CONVERSION
    }

    public static final class FormatExpressionSSTNode extends StringLiteralSSTNode {

        protected final String expressionCode;
        protected final SSTNode expression;
        protected final SSTNode specifier;
        protected final FormatStringConversionType conversionType;

        protected FormatExpressionSSTNode(String expressionCode, SSTNode expression, SSTNode specifier, FormatStringConversionType conversionType, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.expressionCode = expressionCode;
            this.expression = expression;
            this.specifier = specifier;
            this.conversionType = conversionType;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        public String getExpressionCode() {
            return expressionCode;
        }

        public SSTNode getExpression() {
            return expression;
        }

        public SSTNode getSpecifier() {
            return specifier;
        }

        public FormatStringConversionType getConversionType() {
            return conversionType;
        }

    }

    public static final class FormatStringLiteralSSTNode extends StringLiteralSSTNode {
        protected final SSTNode[] parts;

        protected FormatStringLiteralSSTNode(SSTNode[] parts, int startIndex, int endIndex) {
            super(startIndex, endIndex);
            this.parts = parts;
        }

        @Override
        public <T> T accept(SSTreeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        public SSTNode[] getParts() {
            return parts;
        }

    }

    private static class BytesBuilder {
        List<byte[]> bytes = new ArrayList<>();
        int len = 0;

        void append(byte[] b) {
            len += b.length;
            bytes.add(b);
        }

        byte[] build() {
            byte[] output = new byte[len];
            int offset = 0;
            for (byte[] bs : bytes) {
                PythonUtils.arraycopy(bs, 0, output, offset, bs.length);
                offset += bs.length;
            }
            return output;
        }
    }

    public static StringLiteralSSTNode create(String[] values, int startOffset, int endOffset, Source source, ParserErrorCallback errors, PythonSSTNodeFactory nodeFactory,
                    FStringExprParser exprParser) {
        StringBuilder sb = null;
        BytesBuilder bb = null;
        boolean isFormatString = false;
        ArrayList<SSTNode> formatStringParts = null;
        int startPartOffsetInValues = 0;
        int endPartOffsetInValues = 0;
        int sbStartOffset = 0;
        int sbEndOffset = 0;
        CharSequence code = source.getCharacters().subSequence(startOffset, endOffset);
        for (String text : values) {
            boolean isRaw = false;
            boolean isBytes = false;
            boolean isFormat = false;

            while (Character.isWhitespace(code.charAt(startPartOffsetInValues))) {
                startPartOffsetInValues++;
            }
            int strStartIndex = 1;
            int strEndIndex = text.length() - 1;
            endPartOffsetInValues = startPartOffsetInValues + text.length();
            for (int i = 0; i < 3; i++) {
                char chr = Character.toLowerCase(text.charAt(i));

                if (chr == 'r') {
                    isRaw = true;
                } else if (chr == 'u') {
                    // unicode case (default)
                } else if (chr == 'b') {
                    isBytes = true;
                } else if (chr == 'f') {
                    isFormat = true;
                } else if (chr == '\'' || chr == '"') {
                    strStartIndex = i + 1;
                    break;
                }
            }

            if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
                strStartIndex += 2;
                strEndIndex -= 2;
            }

            text = text.substring(strStartIndex, strEndIndex);
            if (isBytes) {
                if (sb != null || isFormatString) {
                    throw errors.raiseInvalidSyntax(source, source.createSection(startOffset, endOffset - startOffset), CANNOT_MIX_MESSAGE);
                }
                if (bb == null) {
                    bb = new BytesBuilder();
                }
                if (isRaw) {
                    bb.append(text.getBytes());
                } else {
                    bb.append(BytesUtils.fromString(errors, text));
                }
            } else {
                if (bb != null) {
                    throw errors.raiseInvalidSyntax(source, source.createSection(startOffset, endOffset - startOffset), CANNOT_MIX_MESSAGE);
                }
                if (!isRaw && !isFormat) {
                    text = StringUtils.unescapeString(startOffset + startPartOffsetInValues, startOffset + endPartOffsetInValues, source, errors, text);
                }
                if (isFormat) {
                    isFormatString = true;
                    if (formatStringParts == null) {
                        formatStringParts = new ArrayList<>();
                    }
                    if (sb != null && sb.length() > 0) {
                        String part = sb.toString();
                        if (!isRaw) {
                            part = StringUtils.unescapeString(startOffset + sbStartOffset, startOffset + sbEndOffset, source, errors, part);
                        }
                        formatStringParts.add(new RawStringLiteralSSTNode(part, startOffset + sbStartOffset, startOffset + sbEndOffset));
                        sb = null;
                    }

                    FormatStringParser.parse(formatStringParts, text, isRaw, startOffset + startPartOffsetInValues, startOffset + startPartOffsetInValues + strStartIndex, source, exprParser, errors,
                                    nodeFactory);
                } else {
                    if (sb == null) {
                        sb = new StringBuilder();
                        sbStartOffset = startPartOffsetInValues;
                    }
                    sb.append(text);
                    sbEndOffset = endPartOffsetInValues;
                }
            }
            startPartOffsetInValues = endPartOffsetInValues;
        }

        if (bb != null) {
            return new BytesLiteralSSTNode(bb.build(), startOffset, endOffset);
        } else if (isFormatString) {
            if (sb != null && sb.length() > 0) {
                String part = sb.toString();
                formatStringParts.add(new RawStringLiteralSSTNode(part, startOffset + sbStartOffset, startOffset + endPartOffsetInValues));
            }
            SSTNode[] formatParts = formatStringParts.toArray(new SSTNode[formatStringParts.size()]);
            return new FormatStringLiteralSSTNode(formatParts, startOffset, endOffset);
        }
        return new RawStringLiteralSSTNode(sb == null ? "" : sb.toString(), startOffset, endOffset);
    }

}
