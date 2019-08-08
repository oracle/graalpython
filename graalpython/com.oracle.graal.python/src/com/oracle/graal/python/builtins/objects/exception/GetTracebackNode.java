/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Use this node to get the traceback object of an exception object. The traceback may need to be
 * created lazily and this node takes care of it.
 */
public abstract class GetTracebackNode extends Node {

    public abstract PTraceback execute(VirtualFrame frame, PBaseException e);

    @Specialization(guards = "!hasLazyTraceback(e)")
    static PTraceback doExisting(PBaseException e) {
        return e.getTraceback();
    }

    // case 1: not on stack: there is already a PFrame (so the frame of this frame info is no
    // longer on the stack) and the frame has already been materialized
    @Specialization(guards = {"hasLazyTraceback(e)", "isMaterialized(e.getFrameInfo())"})
    static PTraceback doMaterializedFrame(PBaseException e,
                    @Cached PythonObjectFactory factory) {
        Reference frameInfo = e.getFrameInfo();
        assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";

        PFrame escapedFrame = frameInfo.getPyFrame();
        assert escapedFrame != null;

        PTraceback result = factory.createTraceback(escapedFrame, e.getException());
        e.setTraceback(result);
        return result;
    }

    // case 2: on stack: the PFrame is not yet available so the frame must still be on the stack
    @Specialization(guards = {"hasLazyTraceback(e)", "!isMaterialized(e.getFrameInfo())"})
    PTraceback doOnStack(VirtualFrame frame, PBaseException e,
                    @Cached PythonObjectFactory factory,
                    @Cached MaterializeFrameNode materializeNode,
                    @Cached ReadCallerFrameNode readCallerFrame,
                    @Cached("createBinaryProfile()") ConditionProfile isCurFrameProfile) {
        Reference frameInfo = e.getFrameInfo();
        assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";

        PFrame escapedFrame = null;

        // case 2.1: the frame info refers to the current frame
        if (isCurFrameProfile.profile(PArguments.getCurrentFrameInfo(frame) == frameInfo)) {
            // materialize the current frame; marking is not necessary (already done); refreshing
            // values is also not necessary (will be done on access to the locals or when returning
            // from the frame)
            escapedFrame = materializeNode.execute(frame, false);
        } else {
            // case 2.2: the frame info does not refer to the current frame
            for (int i = 0;; i++) {
                escapedFrame = readCallerFrame.executeWith(frame, i);
                if (escapedFrame == null || escapedFrame.getRef() == frameInfo) {
                    break;
                }
            }
        }

        PTraceback result = factory.createTraceback(escapedFrame, e.getException());
        e.setTraceback(result);
        return result;
    }

    protected static boolean hasLazyTraceback(PBaseException e) {
        return e.getTraceback() == null && e.getFrameInfo() != null;
    }

    protected static boolean isMaterialized(PFrame.Reference frameInfo) {
        return frameInfo.getPyFrame() != null;
    }
}
