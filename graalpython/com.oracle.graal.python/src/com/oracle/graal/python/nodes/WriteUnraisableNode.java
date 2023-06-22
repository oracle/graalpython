/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;

import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
public abstract class WriteUnraisableNode extends Node {

    private static final TruffleString T_IGNORED = tsLiteral("Exception ignored ");

    public final void execute(VirtualFrame frame, PBaseException exception, TruffleString message, Object object) {
        executeInternal(frame, exception, message, object);
    }

    public final void execute(PBaseException exception, TruffleString message, Object object) {
        executeInternal(null, exception, message, object);
    }

    protected abstract void executeInternal(Frame frame, PBaseException exception, TruffleString message, Object object);

    @Specialization
    static void writeUnraisable(VirtualFrame frame, PBaseException exception, TruffleString message, Object object,
                    @Bind("this") Node inliningTarget,
                    @Cached PyObjectLookupAttr lookup,
                    @Cached CallNode callNode,
                    @Cached GetClassNode getClassNode,
                    @Cached PythonObjectFactory factory,
                    @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                    @Cached TruffleString.ConcatNode concatNode,
                    @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        PythonContext context = PythonContext.get(getClassNode);
        try {
            PythonModule sysModule = context.lookupBuiltinModule(T_SYS);
            Object unraisablehook = lookup.execute(frame, sysModule, BuiltinNames.T_UNRAISABLEHOOK);
            Object exceptionType = getClassNode.execute(exception);
            Object traceback = getTracebackNode.execute(inliningTarget, exception);
            Object messageObj = PNone.NONE;
            if (message != null) {
                messageObj = formatMessage(message, concatNode);
            }
            Object hookArguments = factory.createStructSeq(SysModuleBuiltins.UNRAISABLEHOOK_ARGS_DESC, exceptionType, exception, traceback, messageObj, object != null ? object : PNone.NONE);
            callNode.execute(frame, unraisablehook, hookArguments);
        } catch (PException e) {
            ignoreException(context, message, concatNode, copyToByteArrayNode);
        }
    }

    @TruffleBoundary
    private static void ignoreException(PythonContext context, TruffleString message, TruffleString.ConcatNode concatNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        try {
            if (message != null) {
                TruffleString formatedMsg = formatMessage(message, concatNode);
                byte[] data = new byte[formatedMsg.byteLength(TS_ENCODING)];
                copyToByteArrayNode.execute(formatedMsg, 0, data, 0, data.length, TS_ENCODING);
                context.getEnv().err().write(data);
            } else {
                context.getEnv().err().write(ignoredMsg());
            }
        } catch (IOException ioException) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static TruffleString formatMessage(TruffleString message, TruffleString.ConcatNode concatNode) {
        return concatNode.execute(T_IGNORED, message, TS_ENCODING, true);
    }

    @TruffleBoundary
    private static byte[] ignoredMsg() {
        return "Exception ignored in sys.unraisablehook".getBytes();
    }

    public static WriteUnraisableNode getUncached() {
        return WriteUnraisableNodeGen.getUncached();
    }
}
