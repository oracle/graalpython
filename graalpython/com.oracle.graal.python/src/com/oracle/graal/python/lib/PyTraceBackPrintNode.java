/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.TRACEBACKLIMIT;
import static com.oracle.graal.python.util.PythonUtils.NEW_LINE;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonFileDetector;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
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
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of {@code PyTraceBack_Print} from CPython. the node contains also a number of utility
 * static methods
 */
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

    public static void fileWriteString(VirtualFrame frame, Object file, String data) {
        fileWriteString(frame, file, data, PyObjectGetAttr.getUncached(), CallNode.getUncached());
    }

    public static void fileWriteString(VirtualFrame frame, Object file, String data, PyObjectGetAttr getAttr, CallNode callNode) {
        final Object writeMethod = getAttr.execute(frame, file, WRITE);
        callNode.execute(frame, writeMethod, data);
    }

    public static void fileFlush(VirtualFrame frame, Object file) {
        final Object flushMethod = PyObjectGetAttr.getUncached().execute(frame, file, FLUSH);
        CallNode.getUncached().execute(frame, flushMethod);
    }

    public static Object objectStr(VirtualFrame frame, Object value) {
        return objectStr(frame, value, PyObjectStrAsObjectNode.getUncached());
    }

    public static Object objectStr(VirtualFrame frame, Object value, PyObjectStrAsObjectNode strAsObjectNode) {
        try {
            return strAsObjectNode.execute(frame, value);
        } catch (PException pe) {
            return null;
        }
    }

    public static Object objectRepr(VirtualFrame frame, Object value) {
        return objectRepr(frame, value, PyObjectReprAsObjectNode.getUncached());
    }

    public static Object objectRepr(VirtualFrame frame, Object value, PyObjectReprAsObjectNode reprAsObjectNode) {
        try {
            return reprAsObjectNode.execute(frame, value);
        } catch (PException pe) {
            return null;
        }
    }

    public static String castToString(Object value) {
        return castToString(value, CastToJavaStringNode.getUncached());
    }

    public static String castToString(Object value, CastToJavaStringNode castToJavaStringNode) {
        return castToJavaStringNode.execute(value);
    }

    public static String tryCastToString(Object value) {
        try {
            return castToString(value);
        } catch (CannotCastException e) {
            return null;
        }
    }

    public static Object objectLookupAttr(VirtualFrame frame, Object object, String attr) {
        return objectLookupAttr(frame, object, attr, PyObjectLookupAttr.getUncached());
    }

    public static Object objectLookupAttr(VirtualFrame frame, Object object, String attr, PyObjectLookupAttr lookupAttr) {
        return lookupAttr.execute(frame, object, attr);
    }

    public static String objectLookupAttrAsString(VirtualFrame frame, Object object, String attr, PyObjectLookupAttr lookupAttr, CastToJavaStringNode castToJavaStringNode) {
        final Object value = objectLookupAttr(frame, object, attr, lookupAttr);
        return value != PNone.NO_VALUE ? castToString(value, castToJavaStringNode) : null;
    }

    public static String objectLookupAttrAsString(VirtualFrame frame, Object object, String attr) {
        return objectLookupAttrAsString(frame, object, attr, PyObjectLookupAttr.getUncached(), CastToJavaStringNode.getUncached());
    }

    public static boolean objectHasAttr(VirtualFrame frame, Object object, String attr) {
        return objectLookupAttr(frame, object, attr) != PNone.NO_VALUE;
    }

    public static String getTypeName(Object type) {
        return TypeNodes.GetNameNode.getUncached().execute(type);
    }

    public static Object getObjectClass(Object object) {
        return GetClassNode.getUncached().execute(object);
    }

    public static Object getExceptionTraceback(PBaseException e) {
        return GetExceptionTracebackNode.getUncached().execute(e);
    }

    public static boolean checkLong(Object object) {
        return PyLongCheckNodeGen.getUncached().execute(object);
    }

    public static int longAsInt(VirtualFrame frame, Object object) {
        return PyLongAsIntNodeGen.getUncached().execute(frame, object);
    }

    public static long longAsLongAndOverflow(VirtualFrame frame, Object object, long overflowValue) {
        try {
            return PyLongAsLongAndOverflowNodeGen.getUncached().execute(frame, object);
        } catch (OverflowException e) {
            if (object instanceof PInt) {
                return ((PInt) object).isZeroOrNegative() ? 0 : overflowValue;
            }
            return overflowValue;
        }
    }

    public static Object objectReadAttr(Object object, String attribute) {
        return ReadAttributeFromObjectNode.getUncached().execute(object, attribute);
    }

    public static String classNameNoDot(String name) {
        final int i = PythonUtils.lastIndexOf(name, '.');
        return (i > 0) ? PythonUtils.substring(name, i + 1) : name;
    }

    public PCode getCode(VirtualFrame frame, PythonObjectFactory factory, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, PTraceback tb) {
        final PFrame pFrame = getTbFrameNode.execute(frame, tb);
        return factory.createCode(pFrame.getTarget());
    }

    protected PTraceback getNextTb(TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, PTraceback traceback) {
        materializeStNode.execute(traceback);
        return traceback.getNext();
    }

    private void printLineRepeated(VirtualFrame frame, Object out, int count) {
        int cnt = count;
        cnt -= TB_RECURSIVE_CUTOFF;
        final StringBuilder sb = PythonUtils.newStringBuilder("  [Previous line repeated ");
        PythonUtils.append(sb, cnt, (cnt > 1) ? " more times]\n" : " more time]\n");
        fileWriteString(frame, out, PythonUtils.sbToString(sb));
    }

    private void displayLine(VirtualFrame frame, Object out, String fileName, int lineNo, String name) {
        if (fileName == null || name == null) {
            return;
        }

        final StringBuilder sb = PythonUtils.newStringBuilder("  File \"");
        PythonUtils.append(sb, fileName, "\", line ", lineNo, ", in ", name, NEW_LINE);
        fileWriteString(frame, out, PythonUtils.sbToString(sb));
        // ignore errors since we can't report them, can we?
        displaySourceLine(frame, out, fileName, lineNo, 4);
    }

    protected static String getIndent(int indent) {
        StringBuilder sb = PythonUtils.newStringBuilder();
        for (int i = 0; i < indent; i++) {
            PythonUtils.append(sb, ' ');
        }
        return PythonUtils.sbToString(sb);
    }

    @CompilerDirectives.TruffleBoundary
    protected CharSequence getSourceLine(String fileName, int lineNo) {
        final PythonContext context = getContext();
        TruffleFile file = context.getEnv().getInternalTruffleFile(fileName);
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

    private void displaySourceLine(VirtualFrame frame, Object out, String fileName, int lineNo, int indent) {
        final CharSequence line = getSourceLine(fileName, lineNo);
        if (line != null) {
            fileWriteString(frame, out, getIndent(indent));
            fileWriteString(frame, out, PythonUtils.trimLeft(line));
            fileWriteString(frame, out, NEW_LINE);
        }
    }

    private void printInternal(VirtualFrame frame, PythonObjectFactory factory, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                    TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode, Object out, PTraceback traceback, long limit) {
        int depth = 0;
        String lastFile = null;
        int lastLine = -1;
        String lastName = null;
        int cnt = 0;
        PTraceback tb1 = traceback;
        PTraceback tb = traceback;
        while (tb1 != null) {
            depth++;
            tb1 = getNextTb(materializeStNode, tb1);
        }
        while (tb != null && depth > limit) {
            depth--;
            tb = getNextTb(materializeStNode, tb);
        }
        while (tb != null) {
            final PCode code = getCode(frame, factory, getTbFrameNode, tb);
            if (lastFile == null ||
                            !code.getFilename().equals(lastName) ||
                            lastLine == -1 || tb.getLineno() != lastLine ||
                            lastName == null || !code.getName().equals(lastName)) {
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
            tb = getNextTb(materializeStNode, tb);
        }
        if (cnt > TB_RECURSIVE_CUTOFF) {
            printLineRepeated(frame, out, cnt);
        }
    }

    public abstract void execute(VirtualFrame frame, PythonModule sys, Object out, Object tb);

    @Specialization
    public void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, PTraceback tb,
                    @Cached TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                    @Cached TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                    @Cached PythonObjectFactory factory) {
        long limit = TRACEBACK_LIMIT;
        final Object limitv = objectReadAttr(sys, TRACEBACKLIMIT);
        if (checkLong(limitv)) {
            limit = longAsLongAndOverflow(frame, limitv, MAXSIZE);
            if (limit <= 0) {
                return;
            }
        }
        fileWriteString(frame, out, "Traceback (most recent call last):\n");
        printInternal(frame, factory, getTbFrameNode, materializeStNode, out, tb, limit);
    }

    @Specialization(guards = "!isPTraceback(tb)")
    @SuppressWarnings("unused")
    public void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, Object tb,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raiseBadInternalCall();
    }

    public static PyTraceBackPrintNode create() {
        return PyTraceBackPrintNodeGen.create();
    }
}
