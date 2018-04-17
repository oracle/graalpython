/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
            ((PRootNode) rootNode).setWithCallerFrame();
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
