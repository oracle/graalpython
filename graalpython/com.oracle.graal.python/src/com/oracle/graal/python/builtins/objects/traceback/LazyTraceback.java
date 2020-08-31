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
package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

/**
 * A lazy representation of an exception traceback that can be evaluated to a python object by
 * {@link GetTracebackNode}.
 *
 * @see GetTracebackNode
 */
public class LazyTraceback {
    private final PFrame.Reference frameInfo;
    private final PFrame frame;
    private final PException exception;
    private final LazyTraceback nextChain;
    private PTraceback traceback;
    private boolean materialized;

    public LazyTraceback(PFrame.Reference frameInfo, PException exception, LazyTraceback nextChain) {
        this.frame = null;
        this.frameInfo = frameInfo;
        this.exception = exception;
        this.nextChain = nextChain;
        this.materialized = false;
    }

    public LazyTraceback(PFrame frame, PException exception, LazyTraceback nextChain) {
        this.frame = frame;
        this.frameInfo = null;
        this.exception = exception;
        this.nextChain = nextChain;
        this.materialized = false;
    }

    public LazyTraceback(PTraceback traceback) {
        this.traceback = traceback;
        this.frameInfo = null;
        this.frame = null;
        this.nextChain = null;
        this.exception = null;
        this.materialized = true;
    }

    public PFrame.Reference getFrameInfo() {
        return frameInfo;
    }

    public PFrame getFrame() {
        return frame;
    }

    public PException getException() {
        return exception;
    }

    public LazyTraceback getNextChain() {
        return nextChain;
    }

    public PTraceback getTraceback() {
        return traceback;
    }

    public void setTraceback(PTraceback traceback) {
        this.traceback = traceback;
        this.materialized = true;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public static boolean elementWantedForTraceback(TruffleStackTraceElement element) {
        Frame frame = element.getFrame();
        // only include frames of non-builtin python functions
        return PArguments.isPythonFrame(frame) && locationWantedForTraceback(element.getLocation());
    }

    public boolean catchingFrameWantedForTraceback() {
        return (frame != null || frameInfo != null) && locationWantedForTraceback(exception.getCatchLocation());
    }

    private static boolean locationWantedForTraceback(Node location) {
        return location != null && location.getRootNode() != null && !location.getRootNode().isInternal();
    }
}
