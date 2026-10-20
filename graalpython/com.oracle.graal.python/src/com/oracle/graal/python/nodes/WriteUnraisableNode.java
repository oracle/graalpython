/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsFactory.UnraisableHookNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false)       // footprint reduction 84 -> 68
public abstract class WriteUnraisableNode extends Node {
    private static final TruffleString T_HOOK_FAILED = tsLiteral("Exception ignored in sys.unraisablehook");

    public final void execute(VirtualFrame frame, Object exception, TruffleString message, Object object) {
        executeInternal(frame, exception, message, object);
    }

    public final void execute(Object exception, TruffleString message, Object object) {
        executeInternal(null, exception, message, object);
    }

    protected abstract void executeInternal(Frame frame, Object exception, TruffleString message, Object object);

    @Specialization
    static void writeUnraisable(VirtualFrame frame, Object exception, TruffleString message, Object object,
                    @Bind Node inliningTarget,
                    @Cached PyObjectLookupAttr lookup,
                    @Cached CallNode callNode,
                    @Cached GetClassNode getClassNode,
                    @Bind PythonLanguage language,
                    @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                    @Cached ExceptionNodes.SetTracebackNode setTracebackNode,
                    @Cached ReadFrameNode readFrameNode) {
        PythonContext context = PythonContext.get(inliningTarget);
        PythonModule sysModule = context.getSysModule();
        Object unraisablehook = PNone.NONE;
        try {
            unraisablehook = lookup.execute(frame, inliningTarget, sysModule, BuiltinNames.T_UNRAISABLEHOOK);
            Object exceptionType = getClassNode.execute(inliningTarget, exception);
            Object traceback = getTracebackNode.execute(inliningTarget, exception);
            if (traceback == PNone.NONE) {
                PFrame currentFrame = readFrameNode.getCurrentPythonFrame(frame, CallerFlags.NEEDS_LASTI);
                if (currentFrame != null) {
                    currentFrame.getRef().markAsEscaped();
                    traceback = PFactory.createTracebackWithLasti(language, currentFrame, currentFrame.getLine(), currentFrame.getLasti(), null);
                    setTracebackNode.execute(inliningTarget, exception, traceback);
                }
            }
            Object hookArguments = PFactory.createStructSeq(language, SysModuleBuiltins.UNRAISABLEHOOK_ARGS_DESC, exceptionType, exception, traceback,
                            message != null ? message : PNone.NONE, object != null ? object : PNone.NONE);
            if (unraisablehook == PNone.NONE || unraisablehook == PNone.NO_VALUE) {
                callNode.execute(frame, PythonUtils.createMethod(language, null, UnraisableHookNodeFactory.getInstance(), null, 0), sysModule, hookArguments);
            } else {
                callNode.execute(frame, unraisablehook, hookArguments);
            }
        } catch (PException e) {
            Object hookException = e.getEscapedException();
            Object hookExceptionType = getClassNode.execute(inliningTarget, hookException);
            Object hookTraceback = getTracebackNode.execute(inliningTarget, hookException);
            try {
                Object hookArguments = PFactory.createStructSeq(language, SysModuleBuiltins.UNRAISABLEHOOK_ARGS_DESC, hookExceptionType, hookException, hookTraceback,
                                T_HOOK_FAILED, unraisablehook);
                callNode.execute(frame, PythonUtils.createMethod(language, null, UnraisableHookNodeFactory.getInstance(), null, 0), sysModule, hookArguments);
            } catch (PException ignored) {
                // Reporting an unraisable exception must not raise another exception.
            }
        }
    }

    public static WriteUnraisableNode getUncached() {
        return WriteUnraisableNodeGen.getUncached();
    }
}
