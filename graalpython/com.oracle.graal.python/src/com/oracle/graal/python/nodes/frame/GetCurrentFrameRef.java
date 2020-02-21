/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * This node retrieves the frame reference ({@link PFrame.Reference}) of the current Python frame
 * regardless if a frame is available or not.
 */
@GenerateUncached
public abstract class GetCurrentFrameRef extends Node {

    private static final ConditionProfile[] DISABLED = new ConditionProfile[]{ConditionProfile.getUncached()};

    public abstract Reference execute(Frame frame);

    @Specialization(guards = "frame != null")
    static Reference doWithFrame(Frame frame) {
        return PArguments.getCurrentFrameInfo(frame);
    }

    @Specialization(guards = "frame == null")
    Reference doWithoutFrame(@SuppressWarnings("unused") Frame frame,
                    @Cached(value = "getFlag()", uncached = "getFlagUncached()", dimensions = 1) ConditionProfile[] flag,
                    @CachedContext(PythonLanguage.class) PythonContext context) {

        PFrame.Reference ref = context.peekTopFrameInfo();
        if (flag[0] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // executed the first time, don't pollute the profile, we'll mark the caller to
            // pass the info on the next call
            flag[0] = ConditionProfile.createBinaryProfile();
            if (ref == null) {
                ref = PArguments.getCurrentFrameInfo(ReadCallerFrameNode.getCurrentFrame(this, FrameInstance.FrameAccess.READ_ONLY));
            }
        }
        if (flag[0].profile(ref == null)) {
            ref = PArguments.getCurrentFrameInfo(ReadCallerFrameNode.getCurrentFrame(this, FrameInstance.FrameAccess.READ_ONLY));
        }

        return ref;
    }

    @Specialization(replaces = {"doWithFrame", "doWithoutFrame"})
    Reference doGeneric(Frame frame,
                    @Cached(value = "getFlag()", uncached = "getFlagUncached()", dimensions = 1) ConditionProfile[] flag,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
        PFrame.Reference ref;
        if (frame == null) {
            ref = contextRef.get().peekTopFrameInfo();
            if (ref == null) {
                return PArguments.getCurrentFrameInfo(ReadCallerFrameNode.getCurrentFrame(this, FrameInstance.FrameAccess.READ_ONLY));
            }
            return ref;
        }
        return PArguments.getCurrentFrameInfo(frame);
    }

    static ConditionProfile[] getFlag() {
        return new ConditionProfile[1];
    }

    static ConditionProfile[] getFlagUncached() {
        return DISABLED;
    }
}
