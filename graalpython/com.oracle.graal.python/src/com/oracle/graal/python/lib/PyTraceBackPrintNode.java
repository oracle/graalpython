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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.MAXSIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TRACEBACKLIMIT;
import static com.oracle.graal.python.nodes.StringLiterals.J_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonFileDetector;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of {@code PyTraceBack_Print} from CPython. the node contains also a number of utility
 * static methods
 */
@GenerateInline(false)
public abstract class PyTraceBackPrintNode extends PNodeWithContext {
    static final int TRACEBACK_LIMIT = 1000;
    static final int TB_RECURSIVE_CUTOFF = 3;

    // ---------------------------------------------------------------------------------------------------
    //
    // utility methods
    //
    // ---------------------------------------------------------------------------------------------------
    public static boolean fileWriteObject(VirtualFrame frame, Object file, Object data, boolean printRaw) {
        Object value;
        if (printRaw) {
            value = objectStr(frame, data);
        } else {
            value = objectRepr(frame, data);
        }
        if (value == null) {
            return false;
        }
        fileWriteString(frame, file, castToString(value));
        return true;
    }

    public static void fileWriteString(VirtualFrame frame, Object file, TruffleString data) {
        fileWriteString(frame, null, file, data, PyObjectGetAttr.getUncached(), CallNode.getUncached());
    }

    public static void fileWriteString(VirtualFrame frame, Object file, String data) {
        fileWriteString(frame, file, toTruffleStringUncached(data));
    }

    public static void fileWriteString(VirtualFrame frame, Node inliningTarget, Object file, TruffleString data, PyObjectGetAttr getAttr, CallNode callNode) {
        final Object writeMethod = getAttr.execute(frame, inliningTarget, file, T_WRITE);
        callNode.execute(frame, writeMethod, data);
    }

    public static void fileFlush(VirtualFrame frame, Object file) {
        final Object flushMethod = PyObjectGetAttr.getUncached().execute(frame, null, file, T_FLUSH);
        CallNode.getUncached().execute(frame, flushMethod);
    }

    public static Object objectStr(VirtualFrame frame, Object value) {
        return objectStr(frame, null, value, PyObjectStrAsObjectNode.getUncached());
    }

    public static Object objectStr(VirtualFrame frame, Node inliningTarget, Object value, PyObjectStrAsObjectNode strAsObjectNode) {
        try {
            return strAsObjectNode.execute(frame, inliningTarget, value);
        } catch (PException pe) {
            return null;
        }
    }

    public static Object objectRepr(VirtualFrame frame, Object value) {
        return objectRepr(frame, null, value, PyObjectReprAsObjectNode.getUncached());
    }

    public static Object objectRepr(VirtualFrame frame, Node inliningTarget, Object value, PyObjectReprAsObjectNode reprAsObjectNode) {
        try {
            return reprAsObjectNode.execute(frame, inliningTarget, value);
        } catch (PException pe) {
            return null;
        }
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

    public static Object objectLookupAttr(VirtualFrame frame, Object object, TruffleString attr) {
        return objectLookupAttr(frame, null, object, attr, PyObjectLookupAttr.getUncached());
    }

    public static Object objectLookupAttr(VirtualFrame frame, Node inliningTarget, Object object, TruffleString attr, PyObjectLookupAttr lookupAttr) {
        return lookupAttr.execute(frame, inliningTarget, object, attr);
    }

    public static TruffleString objectLookupAttrAsString(VirtualFrame frame, Node inliningTarget, Object object, TruffleString attr, PyObjectLookupAttr lookupAttr,
                    CastToTruffleStringNode castToStringNode) {
        final Object value = objectLookupAttr(frame, inliningTarget, object, attr, lookupAttr);
        return value != PNone.NO_VALUE ? castToStringNode.execute(inliningTarget, value) : null;
    }

    public static boolean objectHasAttr(VirtualFrame frame, Object object, TruffleString attr) {
        return objectLookupAttr(frame, object, attr) != PNone.NO_VALUE;
    }

    public static TruffleString getTypeName(Object type) {
        return TypeNodes.GetNameNode.executeUncached(type);
    }

    public static Object getObjectClass(Object object) {
        return GetClassNode.executeUncached(object);
    }

    public static Object getExceptionTraceback(Object e) {
        return ExceptionNodes.GetTracebackNode.executeUncached(e);
    }

    public static void setExceptionTraceback(Object e, Object tb) {
        ExceptionNodes.SetTracebackNode.executeUncached(e, tb);
    }

    public static boolean checkLong(Object object) {
        return PyLongCheckNode.executeUncached(object);
    }

    public static int longAsInt(MaterializedFrame frame, Object object) {
        return PyLongAsIntNodeGen.getUncached().execute(frame, null, object);
    }

    public static long longAsLongAndOverflow(VirtualFrame frame, Object object, long overflowValue) {
        try {
            return PyLongAsLongAndOverflowNodeGen.getUncached().execute(frame, null, object);
        } catch (OverflowException e) {
            if (object instanceof PInt) {
                return ((PInt) object).isZeroOrNegative() ? 0 : overflowValue;
            }
            return overflowValue;
        }
    }

    public static Object objectReadAttr(Object object, TruffleString attribute) {
        return ReadAttributeFromObjectNode.getUncached().execute(object, attribute);
    }

    public static TruffleString classNameNoDot(TruffleString name) {
        int len = name.codePointLengthUncached(TS_ENCODING);
        final int i = name.lastIndexOfCodePointUncached('.', len, 0, TS_ENCODING);
        return (i > 0) ? name.substringUncached(i + 1, len - i - 1, TS_ENCODING, true) : name;
    }

    public PCode getCode(VirtualFrame frame, PythonObjectFactory factory, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, PTraceback tb) {
        final PFrame pFrame = getTbFrameNode.execute(frame, tb);
        return factory.createCode(pFrame.getTarget());
    }

    protected PTraceback getNextTb(Node inliningTarget, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, PTraceback traceback) {
        materializeStNode.execute(inliningTarget, traceback);
        return traceback.getNext();
    }

    private static void printLineRepeated(VirtualFrame frame, Object out, int count) {
        int cnt = count;
        cnt -= TB_RECURSIVE_CUTOFF;
        final StringBuilder sb = newStringBuilder("  [Previous line repeated ");
        append(sb, cnt, (cnt > 1) ? " more times]\n" : " more time]\n");
        fileWriteString(frame, out, sbToString(sb));
    }

    private void displayLine(VirtualFrame frame, Object out, TruffleString fileName, int lineNo, TruffleString name) {
        if (fileName == null || name == null) {
            return;
        }

        final StringBuilder sb = newStringBuilder("  File \"");
        append(sb, fileName, "\", line ", lineNo, ", in ", name, J_NEWLINE);
        fileWriteString(frame, out, sbToString(sb));
        // ignore errors since we can't report them, can we?
        displaySourceLine(frame, out, fileName, lineNo, 4);
    }

    protected static TruffleString getIndent(int indent) {
        return T_SPACE.repeatUncached(indent, TS_ENCODING);
    }

    @CompilerDirectives.TruffleBoundary
    protected CharSequence getSourceLine(TruffleString fileName, int lineNo) {
        final PythonContext context = getContext();
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
            BufferedReader reader = file.newBufferedReader(encoding);
            int i = 1;
            while (i <= lineNo) {
                if (i == lineNo) {
                    line = reader.readLine();
                } else {
                    reader.readLine();
                }
                i++;
            }
        } catch (IOException ioe) {
            line = null;
        }
        return line;
    }

    private void displaySourceLine(VirtualFrame frame, Object out, TruffleString fileName, int lineNo, int indent) {
        final CharSequence line = getSourceLine(fileName, lineNo);
        if (line != null) {
            fileWriteString(frame, out, getIndent(indent));
            fileWriteString(frame, out, trimLeft(line));
            fileWriteString(frame, out, J_NEWLINE);
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

    private void printInternal(VirtualFrame frame, Node inliningTarget, PythonObjectFactory factory, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                    TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, Object out, PTraceback traceback, long limit,
                    TruffleString.EqualNode equalNode) {
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
        while (tb != null) {
            final PCode code = getCode(frame, factory, getTbFrameNode, tb);
            if (lastFile == null ||
                            !equalNode.execute(code.getFilename(), lastFile, TS_ENCODING) ||
                            lastLine == -1 || tb.getLineno() != lastLine ||
                            lastName == null || !equalNode.execute(code.getName(), lastName, TS_ENCODING)) {
                if (cnt > TB_RECURSIVE_CUTOFF) {
                    printLineRepeated(frame, out, cnt);
                }
                lastFile = code.getFilename();
                lastLine = tb.getLineno();
                lastName = code.getName();
                cnt = 0;
            }
            cnt++;
            if (cnt <= TB_RECURSIVE_CUTOFF) {
                displayLine(frame, out, code.getFilename(), tb.getLineno(), code.getName());
            }
            tb = getNextTb(inliningTarget, materializeStNode, tb);
        }
        if (cnt > TB_RECURSIVE_CUTOFF) {
            printLineRepeated(frame, out, cnt);
        }
    }

    public abstract void execute(VirtualFrame frame, PythonModule sys, Object out, Object tb);

    @Specialization
    public void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, PTraceback tb,
                    @Bind("this") Node inliningTarget,
                    @Cached TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                    @Cached TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                    @Cached PythonObjectFactory factory,
                    @Cached TruffleString.EqualNode equalNode) {
        long limit = TRACEBACK_LIMIT;
        final Object limitv = objectReadAttr(sys, T_TRACEBACKLIMIT);
        if (checkLong(limitv)) {
            limit = longAsLongAndOverflow(frame, limitv, MAXSIZE);
            if (limit <= 0) {
                return;
            }
        }
        fileWriteString(frame, out, "Traceback (most recent call last):\n");
        printInternal(frame, inliningTarget, factory, getTbFrameNode, materializeStNode, out, tb, limit, equalNode);
    }

    @Specialization(guards = "!isPTraceback(tb)")
    @SuppressWarnings("unused")
    public void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, Object tb,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raiseBadInternalCall();
    }

    @NeverDefault
    public static PyTraceBackPrintNode create() {
        return PyTraceBackPrintNodeGen.create();
    }
}
