/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Parse an integer from a string.
 */
@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class PyLongFromUnicodeObject extends Node {
    public final Object execute(Node inliningTarget, Object string, int base) {
        return execute(inliningTarget, string, base, null, 0);
    }

    public abstract Object execute(Node inliningTarget, Object string, int base, byte[] originalBytes, int originalBytesLen);

    public final Object executeCached(Object string, int base) {
        return execute(null, string, base);
    }

    public static Object executeUncached(Object string, int base) {
        return PyLongFromUnicodeObjectNodeGen.getUncached().execute(null, string, base);
    }

    @Specialization
    static Object doString(Node inliningTarget, Object stringObj, int base, byte[] originalBytes, int originalBytesLen,
                    @Cached CastToTruffleStringNode cast,
                    @Cached TruffleString.ParseLongNode parseLongNode,
                    @Cached InlinedConditionProfile intOrLongResult,
                    @Cached GenericIntParserNode genericParser) {
        TruffleString string = cast.castKnownString(inliningTarget, stringObj);
        /*
         * For other bases, we would have to pre-process the possible 0? prefix. That applies even
         * to decimal literals with base 0 as they have to reject leading zeros.
         */
        if (base == 10) {
            try {
                long result = parseLongNode.execute(string, 10);
                if (intOrLongResult.profile(inliningTarget, PInt.isIntRange(result))) {
                    return (int) result;
                }
                return result;
            } catch (TruffleString.NumberFormatException e) {
                // Fall through to the generic parser
            }
        }
        return parseGeneric(string, base, originalBytes, originalBytesLen, genericParser);
    }

    @InliningCutoff
    private static Object parseGeneric(TruffleString string, int base, byte[] originalBytes, int originalBytesLen, GenericIntParserNode fromString) {
        return fromString.execute(string, base, originalBytes, originalBytesLen);
    }

    @GenerateInline(false) // Slow path
    @GenerateUncached
    abstract static class GenericIntParserNode extends Node {
        public abstract Object execute(TruffleString number, int base, byte[] originalBytes, int originalBytesLen);

        @Specialization
        static Object doGeneric(TruffleString numberTs, int base, byte[] originalBytes, int originalBytesLen,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached InlinedBranchProfile invalidBase,
                        @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Cached InlinedBranchProfile invalidValueProfile,
                        @Cached PRaiseNode raiseNode,
                        @Cached StringNodes.StringReprNode stringReprNode,
                        @Cached BytesNodes.BytesReprNode bytesReprNode) {
            String number = toJavaStringNode.execute(numberTs);
            if ((base != 0 && base < 2) || base > 36) {
                invalidBase.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.INT_BASE_MUST_BE_2_AND_36_OR_0);
            }
            notSimpleDecimalLiteralProfile.enter(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            Object value = stringToIntInternal(inliningTarget, number, base, context);
            if (value == null) {
                invalidValueProfile.enter(inliningTarget);
                Object repr;
                if (originalBytes == null) {
                    repr = stringReprNode.execute(numberTs);
                } else {
                    repr = bytesReprNode.execute(inliningTarget, PFactory.createBytes(context.getLanguage(inliningTarget), originalBytes, originalBytesLen));
                }
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_LITERAL_FOR_INT_WITH_BASE, base, repr);
            }
            return value;
        }

        @TruffleBoundary
        private static Object stringToIntInternal(Node inliningTarget, String num, int base, PythonContext context) {
            try {
                BigInteger bi = asciiToBigInteger(inliningTarget, num, base, context);
                if (bi == null) {
                    return null;
                }
                if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    return PFactory.createInt(context.getLanguage(), bi);
                } else {
                    return bi.intValue();
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @TruffleBoundary
        private static BigInteger asciiToBigInteger(Node inliningTarget, String str, int possibleBase, PythonContext context) throws NumberFormatException {
            int base = possibleBase;
            int b = 0;
            int e = str.length();

            while (b < e && Character.isWhitespace(str.charAt(b))) {
                b++;
            }

            while (e > b && Character.isWhitespace(str.charAt(e - 1))) {
                e--;
            }

            boolean acceptUnderscore = false;
            boolean raiseIfNotZero = false;
            char sign = 0;
            if (b < e) {
                sign = str.charAt(b);
                if (sign == '-' || sign == '+') {
                    b++;
                }

                if (b < e && str.charAt(b) == '0') {
                    char next = b + 1 < e ? Character.toUpperCase(str.charAt(b + 1)) : '?';
                    if (base == 0) {
                        if (next == 'X') {
                            base = 16;
                        } else if (next == 'O') {
                            base = 8;
                        } else if (next == 'B') {
                            base = 2;
                        } else {
                            raiseIfNotZero = true;
                        }
                    }
                    if (base == 16 && next == 'X' || base == 8 && next == 'O' || base == 2 && next == 'B') {
                        b += 2;
                        acceptUnderscore = true;
                    }
                }
            }

            if (base == 0) {
                base = 10;
            }

            // reject invalid characters without going to BigInteger
            for (int i = b; i < e; i++) {
                char c = str.charAt(i);
                if (c == '_') {
                    if (!acceptUnderscore || i == e - 1) {
                        throw new NumberFormatException("Illegal underscore in int literal");
                    } else {
                        acceptUnderscore = false;
                    }
                } else {
                    acceptUnderscore = true;
                    if (Character.digit(c, base) == -1) {
                        // invalid char
                        return null;
                    }
                }
            }

            String s = str;
            if (b > 0 || e < str.length()) {
                s = str.substring(b, e);
            }
            s = s.replace("_", "");

            checkMaxDigits(inliningTarget, context, s.length(), base);

            BigInteger bi;
            if (sign == '-') {
                bi = new BigInteger("-" + s, base);
            } else {
                bi = new BigInteger(s, base);
            }

            if (raiseIfNotZero && !bi.equals(BigInteger.ZERO)) {
                throw new NumberFormatException("Obsolete octal int literal");
            }
            return bi;
        }

        private static void checkMaxDigits(Node inliningTarget, PythonContext context, int digits, int base) {
            if (digits > SysModuleBuiltins.INT_MAX_STR_DIGITS_THRESHOLD && Integer.bitCount(base) != 1) {
                int maxDigits = context.getIntMaxStrDigits();
                if (maxDigits > 0 && digits > maxDigits) {
                    throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION_D, maxDigits,
                                    digits);
                }
            }
        }
    }

    @NeverDefault
    public static PyLongFromUnicodeObject create() {
        return PyLongFromUnicodeObjectNodeGen.create();
    }
}
