/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.MAXSIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.StringLiterals.J_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonFileDetector;
import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins.GetTracebackFrameNode;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins.MaterializeTruffleStacktraceNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;

/**
 * Equivalent of {@code PyTraceBack_Print} from CPython. The main entry point is
 * {@link #print(Node, GetTracebackFrameNode, MaterializeTruffleStacktraceNode, PythonModule, Object, Object)}.
 * The class contains also a number of other related utility static methods.
 */
public abstract class PyTraceBackPrint {
    static final int TRACEBACK_LIMIT = 1000;
    static final int TB_RECURSIVE_CUTOFF = 3;

    // ---------------------------------------------------------------------------------------------------
    //
    // utility methods
    //
    // ---------------------------------------------------------------------------------------------------
    @TruffleBoundary
    public static boolean fileWriteObject(Object file, Object data, boolean printRaw) {
        Object value;
        if (printRaw) {
            value = objectStr(data);
        } else {
            value = objectRepr(data);
        }
        if (value == PNone.NONE) {
            return false;
        }
        fileWriteString(file, castToString(value));
        return true;
    }

    public static void fileWriteString(Object file, TruffleString data) {
        fileWriteString(null, null, file, data, PyObjectGetAttr.getUncached(), CallNode.getUncached());
    }

    public static void fileWriteString(Object file, String data) {
        CompilerAsserts.neverPartOfCompilation();
        fileWriteString(file, toTruffleStringUncached(data));
    }

    public static void fileWriteString(VirtualFrame frame, Node inliningTarget, Object file, TruffleString data, PyObjectGetAttr getAttr, CallNode callNode) {
        final Object writeMethod = getAttr.execute(frame, inliningTarget, file, T_WRITE);
        callNode.execute(frame, writeMethod, data);
    }

    public static void fileFlush(Object file) {
        final Object flushMethod = PyObjectGetAttr.getUncached().execute(null, null, file, T_FLUSH);
        CallNode.getUncached().execute(null, flushMethod);
    }

    public static Object objectStr(Object value) {
        return objectStr(null, null, value, PyObjectStrAsObjectNode.getUncached());
    }

    public static Object objectStr(VirtualFrame frame, Node inliningTarget, Object value, PyObjectStrAsObjectNode strAsObjectNode) {
        try {
            return strAsObjectNode.execute(frame, inliningTarget, value);
        } catch (PException pe) {
            return PNone.NONE;
        }
    }

    public static Object objectRepr(Object value) {
        return objectRepr(null, null, value, PyObjectReprAsObjectNode.getUncached());
    }

    public static Object objectRepr(VirtualFrame frame, Node inliningTarget, Object value, PyObjectReprAsObjectNode reprAsObjectNode) {
        return reprAsObjectNode.execute(frame, inliningTarget, value);
    }

    public static TruffleString castToString(Object value) {
        return CastToTruffleStringNode.executeUncached(value);
    }

    public static TruffleString tryCastToString(Object value) {
        try {
            return castToString(value);
        } catch (CannotCastException e) {
            return null;
        }
    }

    public static Object objectLookupAttr(Object object, TruffleString attr) {
        return objectLookupAttr(null, null, object, attr, PyObjectLookupAttr.getUncached());
    }

    public static Object objectLookupAttr(VirtualFrame frame, Node inliningTarget, Object object, TruffleString attr, PyObjectLookupAttr lookupAttr) {
        return lookupAttr.execute(frame, inliningTarget, object, attr);
    }

    public static TruffleString objectLookupAttrAsString(VirtualFrame frame, Node inliningTarget, Object object, TruffleString attr, PyObjectLookupAttr lookupAttr,
                    CastToTruffleStringNode castToStringNode) {
        final Object value = objectLookupAttr(frame, inliningTarget, object, attr, lookupAttr);
        return value != PNone.NO_VALUE ? castToStringNode.execute(inliningTarget, value) : null;
    }

    public static boolean objectHasAttr(Object object, TruffleString attr) {
        return objectLookupAttr(object, attr) != PNone.NO_VALUE;
    }

    public static TruffleString getTypeName(Object type) {
        return TypeNodes.GetTpNameNode.executeUncached(type);
    }

    public static Object getObjectClass(Object object) {
        CompilerAsserts.neverPartOfCompilation();
        return GetClassNode.executeUncached(object);
    }

    public static Object getExceptionTraceback(Object e) {
        return ExceptionNodes.GetTracebackNode.executeUncached(e);
    }

    public static void setExceptionTraceback(Object e, Object tb) {
        ExceptionNodes.SetTracebackNode.executeUncached(e, tb);
    }

    private static long longAsLongAndOverflow(Object object, long overflowValue) {
        try {
            return PyLongAsLongAndOverflowNodeGen.getUncached().execute(null, null, object);
        } catch (OverflowException e) {
            if (object instanceof PInt) {
                return ((PInt) object).isZeroOrNegative() ? 0 : overflowValue;
            }
            return overflowValue;
        }
    }

    public static TruffleString classNameNoDot(TruffleString name) {
        int len = name.codePointLengthUncached(TS_ENCODING);
        final int i = name.lastIndexOfCodePointUncached('.', len, 0, TS_ENCODING);
        return (i > 0) ? name.substringUncached(i + 1, len - i - 1, TS_ENCODING, true) : name;
    }

    private static PCode getCode(PythonLanguage language, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, PTraceback tb) {
        final PFrame pFrame = getTbFrameNode.execute(null, tb);
        return PFactory.createCode(language, pFrame.getTarget());
    }

    protected static PTraceback getNextTb(Node inliningTarget, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, PTraceback traceback) {
        materializeStNode.execute(inliningTarget, traceback);
        return traceback.getNext();
    }

    @TruffleBoundary
    private static void printLineRepeated(Object out, int count) {
        int cnt = count;
        cnt -= TB_RECURSIVE_CUTOFF;
        final StringBuilder sb = new StringBuilder("  [Previous line repeated ");
        append(sb, cnt, (cnt > 1) ? " more times]\n" : " more time]\n");
        fileWriteString(out, sbToString(sb));
    }

    private static void displayLine(Object out, TruffleString fileName, int lineNo, TruffleString name, int indent, TruffleString margin) {
        if (fileName == null || name == null) {
            return;
        }

        boolean withIndentOrMargin = indent > 0 || !margin.isEmpty();
        final StringBuilder sb = withIndentOrMargin ? new StringBuilder() : newStringBuilder("  File \"");
        if (withIndentOrMargin) {
            append(sb, getIndent(indent), margin, "  File \"");
        }
        append(sb, fileName, "\", line ", lineNo, ", in ", name, J_NEWLINE);
        fileWriteString(out, sbToString(sb));
        // ignore errors since we can't report them, can we?
        displaySourceLine(out, fileName, lineNo, 4, indent, margin);
    }

    protected static TruffleString getIndent(int indent) {
        return T_SPACE.repeatUncached(indent, TS_ENCODING);
    }

    protected static CharSequence getSourceLine(TruffleString fileName, int lineNo) {
        final PythonContext context = PythonContext.get(null);
        TruffleFile file = null;
        try {
            file = context.getEnv().getInternalTruffleFile(fileName.toJavaStringUncached());
        } catch (Exception e) {
            return null;
        }
        String line = null;
        try {
            Charset encoding;
            try {
                encoding = PythonFileDetector.findEncodingStrict(file);
            } catch (PythonFileDetector.InvalidEncodingException e) {
                encoding = StandardCharsets.UTF_8;
            }
            try (BufferedReader reader = file.newBufferedReader(encoding)) {
                int i = 1;
                while (i <= lineNo) {
                    if (i == lineNo) {
                        line = reader.readLine();
                    } else {
                        reader.readLine();
                    }
                    i++;
                }
            }
        } catch (IOException ioe) {
            line = null;
        }
        return line;
    }

    private static void displaySourceLine(Object out, TruffleString fileName, int lineNo, int indent, int marginIndent, TruffleString margin) {
        final CharSequence line = getSourceLine(fileName, lineNo);
        if (line != null) {
            fileWriteString(out, getIndent(marginIndent));
            fileWriteString(out, margin);
            fileWriteString(out, getIndent(indent));
            fileWriteString(out, trimLeft(line));
            fileWriteString(out, J_NEWLINE);
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static StringBuilder newStringBuilder(String str) {
        return new StringBuilder(str);
    }

    @TruffleBoundary(allowInlining = true)
    private static String sbToString(StringBuilder sb) {
        return sb.toString();
    }

    @TruffleBoundary(allowInlining = true)
    private static StringBuilder append(StringBuilder sb, Object... args) {
        for (Object arg : args) {
            sb.append(arg);
        }
        return sb;
    }

    @TruffleBoundary(allowInlining = true)
    private static String trimLeft(CharSequence sequence) {
        int len = sequence.length();
        int st = 0;
        while ((st < len) && (sequence.charAt(st) <= ' ')) {
            st++;
        }
        return (st > 0 ? sequence.subSequence(st, len) : sequence).toString();
    }

    private static void printIndentedHeader(Object out, String header, int indent, String margin) {
        String sb = " ".repeat(indent) + margin + header + "\n";
        fileWriteString(out, sb);
    }

    private static void printInternal(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                    TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, Object out, PTraceback traceback, long limit,
                    int indent, TruffleString margin) {
        int depth = 0;
        TruffleString lastFile = null;
        int lastLine = -1;
        TruffleString lastName = null;
        int cnt = 0;
        PTraceback tb1 = traceback;
        PTraceback tb = traceback;
        while (tb1 != null) {
            depth++;
            tb1 = getNextTb(inliningTarget, materializeStNode, tb1);
        }
        while (tb != null && depth > limit) {
            depth--;
            tb = getNextTb(inliningTarget, materializeStNode, tb);
        }
        EqualNode tstrEqNode = EqualNode.getUncached();
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        while (tb != null) {
            final PCode code = getCode(language, getTbFrameNode, tb);
            if (lastFile == null || code.getFilename() == null ||
                            !tstrEqNode.execute(code.getFilename(), lastFile, TS_ENCODING) ||
                            lastLine == -1 || tb.getLineno() != lastLine ||
                            lastName == null || !tstrEqNode.execute(code.getName(), lastName, TS_ENCODING)) {
                if (cnt > TB_RECURSIVE_CUTOFF) {
                    printLineRepeated(out, cnt);
                }
                lastFile = code.getFilename();
                lastLine = tb.getLineno();
                lastName = code.getName();
                cnt = 0;
            }
            cnt++;
            if (cnt <= TB_RECURSIVE_CUTOFF) {
                displayLine(out, code.getFilename(), tb.getLineno(), code.getName(), indent, margin);
            }
            tb = getNextTb(inliningTarget, materializeStNode, tb);
        }
        if (cnt > TB_RECURSIVE_CUTOFF) {
            printLineRepeated(out, cnt);
        }
    }

    public static void print(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, PythonModule sys,
                    Object out, Object tbObj, boolean isExceptionGroup, int indent, TruffleString margin) {
        // Although we should be behind TB, we need cached nodes, because they may do stack walking
        // and for that they must be connected to the currently executing root. In practice, it's
        // not strictly necessary, because they will never request the current frame, but in order
        // to be able to keep all the checks in place, we follow this rule here too.
        CompilerAsserts.neverPartOfCompilation();
        assert inliningTarget != null && inliningTarget.isAdoptable();
        assert getTbFrameNode.isAdoptable();

        if (margin == null) {
            margin = tsLiteral("");
        }

        if (tbObj instanceof PTraceback tb) {
            long limit = TRACEBACK_LIMIT;
            final Object limitv = ReadAttributeFromObjectNode.getUncached().execute(sys, BuiltinNames.T_TRACEBACKLIMIT);
            if (PyLongCheckNode.executeUncached(limitv)) {
                limit = longAsLongAndOverflow(limitv, MAXSIZE);
                if (limit <= 0) {
                    return;
                }
            }
            if (isExceptionGroup) {
                printIndentedHeader(out, "Exception Group Traceback (most recent call last):", indent, "+ ");
            } else {
                printIndentedHeader(out, "Traceback (most recent call last):", indent, "");
            }
            printInternal(inliningTarget, getTbFrameNode, materializeStNode, out, tb, limit, indent, margin);
        } else {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }
    }
}
