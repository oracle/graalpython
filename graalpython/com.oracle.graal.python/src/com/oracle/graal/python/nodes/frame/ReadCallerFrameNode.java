/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "read_caller_fame")
public abstract class ReadCallerFrameNode extends PNode {
    private final FrameInstance.FrameAccess frameAccess;
    private final ConditionProfile cachedCallerFrameProfile = ConditionProfile.createBinaryProfile();

    protected ReadCallerFrameNode(FrameInstance.FrameAccess frameAccess) {
        this.frameAccess = frameAccess;
    }

    public static ReadCallerFrameNode create() {
        return create(FrameInstance.FrameAccess.MATERIALIZE);
    }

    public static ReadCallerFrameNode create(FrameInstance.FrameAccess access) {
        return ReadCallerFrameNodeGen.create(access);
    }

    public FrameInstance.FrameAccess getFrameAccess() {
        return frameAccess;
    }

    @Specialization
    Frame read(VirtualFrame frame) {
        Frame callerFrame = PArguments.getCallerFrame(frame);
        if (cachedCallerFrameProfile.profile(callerFrame != null)) {
            return callerFrame;
        } else {
            return getCallerFrame();
        }
    }

    private void rootNodeStartSendingOwnFrame() {
        RootNode rootNode = this.getRootNode();
        if (rootNode instanceof PRootNode) {
            ((PRootNode) rootNode).setNeedsCallerFrame();
        }
    }

    @TruffleBoundary
    private Frame getCallerFrame() {
        rootNodeStartSendingOwnFrame();
        return Truffle.getRuntime().getCallerFrame().getFrame(frameAccess).materialize();
    }

    public Frame executeWith(VirtualFrame frame) {
        return (Frame) this.execute(frame);
    }
}
