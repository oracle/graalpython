/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;

public abstract class FrameSlotGuards {

    private FrameSlotGuards() {
        // no instances
    }

    public static boolean isNotIllegal(Frame frame, int frameSlot) {
        return frame.getFrameDescriptor().getSlotKind(frameSlot) != FrameSlotKind.Illegal;
    }

    public static boolean isBooleanKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Boolean);
    }

    public static boolean isIntegerKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Int);
    }

    public static boolean isLongKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Long);
    }

    public static boolean isDoubleKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Double);
    }

    public static boolean isIntOrObjectKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Int) || isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Object);
    }

    public static boolean isLongOrObjectKind(Frame frame, int frameSlot) {
        return isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Long) || isKind(frame.getFrameDescriptor(), frameSlot, FrameSlotKind.Object);
    }

    public static void ensureObjectKind(Frame frame, int frameSlot) {
        frame.getFrameDescriptor().setSlotKind(frameSlot, FrameSlotKind.Object);
    }

    public static boolean isNotIllegal(FrameDescriptor descriptor, int frameSlot) {
        return descriptor.getSlotKind(frameSlot) != FrameSlotKind.Illegal;
    }

    public static boolean isBooleanKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Boolean);
    }

    public static boolean isIntegerKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Int);
    }

    public static boolean isLongKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Long);
    }

    public static boolean isDoubleKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Double);
    }

    public static boolean isIntOrObjectKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Int) || isKind(descriptor, frameSlot, FrameSlotKind.Object);
    }

    public static boolean isLongOrObjectKind(FrameDescriptor descriptor, int frameSlot) {
        return isKind(descriptor, frameSlot, FrameSlotKind.Long) || isKind(descriptor, frameSlot, FrameSlotKind.Object);
    }

    public static void ensureObjectKind(FrameDescriptor descriptor, int frameSlot) {
        descriptor.setSlotKind(frameSlot, FrameSlotKind.Object);
    }

    private static boolean isKind(FrameDescriptor descriptor, int frameSlot, FrameSlotKind kind) {
        return descriptor.getSlotKind(frameSlot) == kind || initialSetKind(descriptor, frameSlot, kind);
    }

    private static boolean initialSetKind(FrameDescriptor descriptor, int frameSlot, FrameSlotKind kind) {
        if (descriptor.getSlotKind(frameSlot) == FrameSlotKind.Illegal) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            descriptor.setSlotKind(frameSlot, kind);
            return true;
        }
        return false;
    }
}
