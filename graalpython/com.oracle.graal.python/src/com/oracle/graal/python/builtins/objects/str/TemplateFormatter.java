/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.nodes.ErrorMessages.EMPTY_ATTR_IN_FORMAT_STR;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_CONVERSION;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_RBRACE_BEFORE_END_OF_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_AFTER_FORMAT_CONVERSION;
import static com.oracle.graal.python.nodes.ErrorMessages.FORMAT_STR_CONTAINS_POS_FIELDS;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_CONVERSION;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_S;
import static com.oracle.graal.python.nodes.ErrorMessages.ONLY_S_AND_S_AMY_FOLLOW_S;
import static com.oracle.graal.python.nodes.ErrorMessages.RECURSION_DEPTH_EXCEEDED;
import static com.oracle.graal.python.nodes.ErrorMessages.REPLACEMENT_INDEX_S_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.SINGLE_RBRACE_ENCOUNTERED_IN_FORMAT_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.SWITCHING_FROM_AUTOMATIC_TO_MANUAL_NUMBERING;
import static com.oracle.graal.python.nodes.ErrorMessages.SWITCHING_FROM_MANUAL_TO_AUTOMATIC_NUMBERING;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_MANY_DECIMAL_DIGITS_IN_FORMAT_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.UNEXPECTED_S_IN_FIELD_NAME;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class TemplateFormatter {

    private static final int ANS_INIT = 1;
    private static final int ANS_AUTO = 2;
    private static final int ANS_MANUAL = 3;

    private static final BigInteger MAXSIZE = BigInteger.valueOf(SysModuleBuiltins.MAXSIZE);

    private final String template;
    private String empty;
    private Object[] args;
    private Object keywords;
    private List<Object[]> parserList = null;
    private int autoNumbering;
    private int autoNumberingState;

    private int lastEnd = -1;

    public TemplateFormatter(TruffleString template) {
        this.template = template.toJavaStringUncached();
        this.empty = "";
    }

    @TruffleBoundary
    public TruffleString build(Node node, Object[] argsArg, Object kwArgs, FormatNode formatNode, PyObjectGetItem getItemNode) {
        this.args = argsArg;
        this.keywords = kwArgs;
        this.autoNumbering = 0;
        this.autoNumberingState = ANS_INIT;
        return buildString(node, 0, template.length(), 2, formatNode, getItemNode);
    }

    private TruffleString buildString(Node node, int start, int end, int level, FormatNode formatNode, PyObjectGetItem getItemNode) {
        if (level == 0) {
            throw PRaiseNode.raiseUncached(node, ValueError, RECURSION_DEPTH_EXCEEDED);
        }
        return doBuildString(node, start, end, level - 1, this.template, formatNode, getItemNode);
    }

    private TruffleString doBuildString(Node node, int start, int end, int level, String s, FormatNode formatNode, PyObjectGetItem getItemNode) {
        StringBuilder out = new StringBuilder();
        int lastLiteral = start;
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            i += 1;
            if (c == '{' || c == '}') {
                boolean atEnd = i == end;
                // Find escaped "{" and "}"
                boolean markupFollows = true;
                if (c == '}') {
                    if (atEnd || s.charAt(i) != '}') {
                        throw PRaiseNode.raiseUncached(node, ValueError, SINGLE_RBRACE_ENCOUNTERED_IN_FORMAT_STRING);
                    }
                    i += 1;
                    markupFollows = false;
                }
                if (c == '{') {
                    if (atEnd) {
                        throw PRaiseNode.raiseUncached(node, ValueError, SINGLE_RBRACE_ENCOUNTERED_IN_FORMAT_STRING);
                    }
                    if (s.charAt(i) == '{') {
                        i += 1;
                        markupFollows = false;
                    }
                }
                // Attach literal data, ending with { or }
                out.append(s, lastLiteral, i - 1);
                if (!markupFollows) {
                    if (this.parserList != null) {
                        int endLiteral = i - 1;
                        assert endLiteral > lastLiteral;
                        TruffleString literal = toTruffleStringUncached(this.template.substring(lastLiteral, endLiteral));
                        this.parserList.add(new Object[]{literal, PNone.NONE, PNone.NONE, PNone.NONE});
                        this.lastEnd = i;
                    }
                    lastLiteral = i;
                    continue;
                }
                int nested = 1;
                int fieldStart = i;
                boolean recursive = false;
                while (i < end) {
                    c = s.charAt(i);
                    if (c == '{') {
                        recursive = true;
                        nested += 1;
                    } else if (c == '}') {
                        nested -= 1;
                        if (nested == 0) {
                            break;
                        }
                    } else if (c == '[') {
                        i += 1;
                        while (i < end && s.charAt(i) != ']') {
                            i += 1;
                        }
                        continue;
                    }
                    i += 1;
                }
                if (nested > 0) {
                    throw PRaiseNode.raiseUncached(node, ValueError, EXPECTED_RBRACE_BEFORE_END_OF_STRING);
                }
                Object rendered = renderField(node, fieldStart, i, recursive, level, formatNode, getItemNode);
                out.append(rendered);
                i += 1;
                lastLiteral = i;
            }
        }
        out.append(s.substring(lastLiteral, end));
        return toTruffleStringUncached(out.toString());
    }

    private static class Field {
        String name;
        Character conversion;
        int idx;

        public Field(String name, Character conversion, int idx) {
            this.name = name;
            this.conversion = conversion;
            this.idx = idx;
        }
    }

    private Field parseField(Node node, int start, int end) {
        String s = template;
        // Find ":" or "!"
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            Character conversion = null;
            if (c == ':' || c == '!') {
                int endName = i;
                if (c == '!') {
                    i += 1;
                    if (i == end) {
                        throw PRaiseNode.raiseUncached(node, ValueError, EXPECTED_CONVERSION);
                    }
                    conversion = s.charAt(i);
                    i += 1;
                    if (i < end) {
                        if (s.charAt(i) != ':') {
                            throw PRaiseNode.raiseUncached(node, ValueError, EXPECTED_S_AFTER_FORMAT_CONVERSION, ':');
                        }
                        i += 1;
                    }
                } else {
                    conversion = null;
                    i += 1;
                }
                return new Field(s.substring(start, endName), conversion, i);
            } else if (c == '[') {
                while (i + 1 < end && s.charAt(i + 1) != ']') {
                    i += 1;
                }
            } else if (c == '{') {
                throw PRaiseNode.raiseUncached(node, ValueError, UNEXPECTED_S_IN_FIELD_NAME, "'{'");
            }
            i += 1;
        }
        return new Field(s.substring(start, end), null, end);
    }

    private Object getArgument(Node node, String name, PyObjectGetItem getItemNode) {
        // First, find the argument.
        int i = 0;
        int end = name.length();
        while (i < end) {
            char c = name.charAt(i);
            if (c == '[' || c == '.') {
                break;
            }
            i += 1;
        }
        boolean isEmpty = i == 0;
        int index;
        String intString = name.substring(0, i);
        if (isEmpty) {
            index = -1;
        } else {
            index = toInt(node, intString);
        }
        boolean useNumeric = isEmpty || index != -1;
        if (this.autoNumberingState == ANS_INIT && useNumeric) {
            if (isEmpty) {
                this.autoNumberingState = ANS_AUTO;
            } else {
                this.autoNumberingState = ANS_MANUAL;
            }
        }
        if (useNumeric) {
            if (this.autoNumberingState == ANS_MANUAL) {
                if (isEmpty) {
                    throw PRaiseNode.raiseUncached(node, ValueError, SWITCHING_FROM_MANUAL_TO_AUTOMATIC_NUMBERING);
                }
            } else if (!isEmpty) {
                throw PRaiseNode.raiseUncached(node, ValueError, SWITCHING_FROM_AUTOMATIC_TO_MANUAL_NUMBERING);
            }
        }
        if (isEmpty) {
            index = this.autoNumbering;
            this.autoNumbering += 1;
        }
        Object arg = null;
        if (index == -1) {
            String kwarg = intString;
            arg = getKeyword(node, kwarg, getItemNode);
        } else if (index > SysModuleBuiltins.MAXSIZE) {
            throw PRaiseNode.raiseUncached(node, ValueError, TOO_MANY_DECIMAL_DIGITS_IN_FORMAT_STRING);
        } else {
            if (this.args == null) {
                throw PRaiseNode.raiseUncached(node, ValueError, FORMAT_STR_CONTAINS_POS_FIELDS);
            }
            if (index >= this.args.length) {
                throw PRaiseNode.raiseUncached(node, IndexError, REPLACEMENT_INDEX_S_OUT_OF_RANGE, index);
            }
            arg = this.args[index];
        }
        return resolveLookups(node, arg, name, i, end, getItemNode);
    }

    private Object resolveLookups(Node node, Object obj, String name, int startArg, int end, PyObjectGetItem getItemNode) {
        // Resolve attribute and item lookups.
        int i = startArg;
        int start = startArg;
        Object result = obj;
        while (i < end) {
            char c = name.charAt(i);
            if (c == '.') {
                i += 1;
                start = i;
                while (i < end) {
                    c = name.charAt(i);
                    if (c == '[' || c == '.') {
                        break;
                    }
                    i += 1;
                }
                if (start == i) {
                    throw PRaiseNode.raiseUncached(node, ValueError, EMPTY_ATTR_IN_FORMAT_STR);
                }
                TruffleString attr = toTruffleStringUncached(name.substring(start, i));
                if (result != null) {
                    result = PyObjectLookupAttr.getUncached().execute(null, result, attr);
                } else {
                    this.parserList.add(new Object[]{true, attr});
                }
            } else if (c == '[') {
                boolean gotBracket = false;
                i += 1;
                start = i;
                while (i < end) {
                    c = name.charAt(i);
                    if (c == ']') {
                        gotBracket = true;
                        break;
                    }
                    i += 1;
                }
                if (!gotBracket) {
                    throw PRaiseNode.raiseUncached(node, ValueError, MISSING_S, "']'");
                }
                String s = name.substring(start, i);
                if (s.isEmpty()) {
                    throw PRaiseNode.raiseUncached(node, ValueError, EMPTY_ATTR_IN_FORMAT_STR);
                }
                int index = toInt(node, s);
                Object item = index != -1 ? index : toTruffleStringUncached(s);
                i += 1; // # Skip "]"
                if (result != null) {
                    result = getItemNode.execute(null, result, item);
                } else {
                    this.parserList.add(new Object[]{false, item});
                }
            } else {
                throw PRaiseNode.raiseUncached(node, ValueError, ONLY_S_AND_S_AMY_FOLLOW_S, "'['", "'.'", "']'");
            }
        }
        return result;
    }

    private static int toInt(Node node, String s) {
        try {
            BigInteger bigInt = new BigInteger(s);
            if (bigInt.signum() >= 0) {
                return bigInt.intValueExact();
            }
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        } catch (ArithmeticException e) {
            throw PRaiseNode.raiseUncached(node, ValueError, TOO_MANY_DECIMAL_DIGITS_IN_FORMAT_STRING);
        }
    }

    private Object renderField(Node node, int start, int end, boolean recursive, int level, FormatNode formatNode, PyObjectGetItem getItemNode) {
        Field filed = parseField(node, start, end);
        String name = filed.name;
        Character conversion = filed.conversion;
        int specStart = filed.idx;

        TruffleString spec = toTruffleStringUncached(this.template.substring(specStart, end));
        if (this.parserList != null) {
            // used from formatterParser()
            if (level == 1) { // ignore recursive calls
                int startm1 = start - 1;
                assert startm1 >= this.lastEnd;
                this.parserList.add(new Object[]{
                                toTruffleStringUncached(this.template.substring(this.lastEnd, startm1)),
                                toTruffleStringUncached(name),
                                spec,
                                conversion != null ? toTruffleStringUncached(Character.toString(conversion)) : PNone.NONE});
                this.lastEnd = end + 1;
            }
            return this.empty;
        }

        Object obj = getArgument(node, name, getItemNode);
        if (conversion != null) {
            obj = convert(node, obj, conversion);
        }
        if (recursive) {
            spec = buildString(node, specStart, end, level, formatNode, getItemNode);
        }
        Object rendered = formatNode.execute(null, obj, spec);
        return rendered;
    }

    public static class FieldNameSplitResult {
        public Object first;
        public List<Object[]> parserList;

        public FieldNameSplitResult(Object first, List<Object[]> parserList) {
            this.first = first;
            this.parserList = parserList;
        }
    }

    @TruffleBoundary
    public FieldNameSplitResult formatterFieldNameSplit(Node node) {
        String name = this.template;
        int i = 0;
        int end = name.length();
        while (i < end) {
            char c = name.charAt(i);
            if (c == '[' || c == '.') {
                break;
            }
            i += 1;
        }
        int index;
        if (i == 0) {
            index = -1;
        } else {
            try {
                index = Integer.parseInt(name.substring(0, i));
            } catch (NumberFormatException e) {
                index = -1;
            }
        }
        Object first;
        if (index >= 0) {
            first = index;
        } else {
            first = toTruffleStringUncached(name.substring(0, i));
        }
        //
        this.parserList = new ArrayList<>();
        this.resolveLookups(node, null, name, i, end, null);
        return new FieldNameSplitResult(first, parserList);
    }

    private static Object convert(Node node, Object obj, char conversion) {
        switch (conversion) {
            case 'r':
                return PyObjectReprAsObjectNode.getUncached().execute(null, obj);
            case 's':
                return PyObjectStrAsTruffleStringNode.getUncached().execute(null, obj);
            case 'a':
                return PyObjectAsciiNode.getUncached().execute(null, obj);
            default:
                throw PRaiseNode.raiseUncached(node, ValueError, INVALID_CONVERSION);
        }
    }

    @TruffleBoundary
    public List<Object[]> formatterParser(Node node) {
        this.parserList = new ArrayList<>();
        this.lastEnd = 0;
        buildString(node, 0, this.template.length(), 2, null, null);
        if (this.lastEnd < this.template.length()) {
            parserList.add(new Object[]{toTruffleStringUncached(this.template.substring(this.lastEnd)), PNone.NONE, PNone.NONE, PNone.NONE});
        }
        return parserList;
    }

    private Object getKeyword(Node node, String key, PyObjectGetItem getItemNode) {
        TruffleString tKey = toTruffleStringUncached(key);
        if (keywords instanceof PKeyword[]) {
            for (PKeyword kwArg : (PKeyword[]) keywords) {
                if (tKey.equalsUncached(kwArg.getName(), TS_ENCODING)) {
                    return kwArg.getValue();
                }
            }
        } else {
            Object result = getItemNode.execute(null, keywords, tKey);
            if (result != null) {
                return result;
            }
        }
        throw PRaiseNode.raiseUncached(node, KeyError, tKey);
    }

}
