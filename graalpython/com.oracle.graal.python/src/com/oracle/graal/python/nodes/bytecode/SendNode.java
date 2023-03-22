/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.generator.CommonGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline(false) // used in BCI root node
public abstract class SendNode extends PNodeWithContext {
    private static final TruffleString T_SEND = tsLiteral("send");

    // Returns true when the generator finished
    public abstract boolean execute(VirtualFrame virtualFrame, int stackTop, Object iter, Object arg);

    @Specialization
    static boolean doGenerator(VirtualFrame virtualFrame, int stackTop, PGenerator generator, Object arg,
                    @Bind("this") Node inliningTarget,
                    @Cached CommonGeneratorBuiltins.SendNode sendNode,
                    @Shared("profile") @Cached IsBuiltinObjectProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = sendNode.execute(virtualFrame, generator, arg);
            virtualFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, stackTop);
            return true;
        }
    }

    @Specialization(guards = "iterCheck.execute(inliningTarget, iter)", limit = "1")
    static boolean doIterator(VirtualFrame virtualFrame, int stackTop, Object iter, @SuppressWarnings("unused") PNone arg,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached PyIterCheckNode iterCheck,
                    @Cached GetNextNode getNextNode,
                    @Shared("profile") @Cached IsBuiltinObjectProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = getNextNode.execute(virtualFrame, iter);
            virtualFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, stackTop);
            return true;
        }
    }

    @Fallback
    static boolean doOther(VirtualFrame virtualFrame, int stackTop, Object obj, Object arg,
                    @Bind("this") Node inliningTarget,
                    @Cached PyObjectCallMethodObjArgs callMethodNode,
                    @Shared("profile") @Cached IsBuiltinObjectProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = callMethodNode.execute(virtualFrame, inliningTarget, obj, T_SEND, arg);
            virtualFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(virtualFrame, e, inliningTarget, stopIterationProfile, getValue, stackTop);
            return true;
        }
    }

    private static void handleException(VirtualFrame frame, PException e, Node inliningTarget, IsBuiltinObjectProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue,
                    int stackTop) {
        e.expectStopIteration(inliningTarget, stopIterationProfile);
        Object value = getValue.execute((PBaseException) e.getUnreifiedException());
        frame.setObject(stackTop, null);
        frame.setObject(stackTop - 1, value);
    }

    public static SendNode create() {
        return SendNodeGen.create();
    }
}
