/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class GraalHPyDebugContext extends GraalHPyContext {
    private static final int DEFAULT_CLOSED_HANDLES_QUEUE_MAX_SIZE = 1024;

    private int currentGeneration;
    private int age;
    private long[] debugHandleInfo = new long[]{0};
    private int closedHandlesQueueMaxSize = DEFAULT_CLOSED_HANDLES_QUEUE_MAX_SIZE;
    private final LinkedList<GraalHPyHandle> closedHandles = new LinkedList<>();
    private Object onInvalidHandleCallback;

    public GraalHPyDebugContext(GraalHPyContext context) {
        super(context.getContext(), context.getLLVMLibrary());
        trackConstants();
        setHPyContextNativeType(context.getNativeType());
        setHPyNativeType(context.getHPyNativeType());
        setHPyFieldNativeType(context.getHPyFieldNativeType());
        setHPyArrayNativeType(context.getHPyArrayNativeType());
        setWcharSize(context.getWcharSize());
    }

    public int getClosedHandlesQueueMaxSize() {
        return closedHandlesQueueMaxSize;
    }

    public void setClosedHandlesQueueMaxSize(int closedHandlesQueueMaxSize) {
        this.closedHandlesQueueMaxSize = closedHandlesQueueMaxSize;
    }

    public Object getOnInvalidHandleCallback() {
        return onInvalidHandleCallback;
    }

    public void setOnInvalidHandleCallback(Object onInvalidHandleCallback) {
        this.onInvalidHandleCallback = onInvalidHandleCallback;
    }

    /**
     * Since the initialization of the context members cannot use {@link #createHandle(Object)}, we
     * track the constants of the debug mode separately here. The reason why we can't use
     * {@link #createHandle(Object)} is that the {@link #debugHandleInfo} will be initialized after
     * the context members and that would cause an NPE.
     */
    private void trackConstants() {
        for (Object member : hpyContextMembers) {
            if (member instanceof GraalHPyHandle) {
                trackHandle((GraalHPyHandle) member);
            }
        }
    }

    @Override
    protected String getName() {
        return "HPy Debug Mode ABI (GraalVM backend)";
    }

    @TruffleBoundary
    public ArrayList<GraalHPyHandle> getOpenHandles(int generation) {
        ArrayList<GraalHPyHandle> openHandles = new ArrayList<>();
        for (int i = 0; i < debugHandleInfo.length; i++) {
            if (getGeneration(debugHandleInfo[i]) >= generation) {
                openHandles.add(getObjectForHPyHandle(i));
            }
        }
        return openHandles;
    }

    public Queue<GraalHPyHandle> getClosedHandles() {
        return closedHandles;
    }

    public int getCurrentGeneration() {
        return currentGeneration;
    }

    public long getDebugHandleInfo(GraalHPyHandle handle) {
        return debugHandleInfo[handle.getIdDebug(this)];
    }

    public int newGeneration() {
        age = 0;
        return ++currentGeneration;
    }

    private void trackHandle(GraalHPyHandle handle) {
        int id = handle.getIdDebug(this);
        if (id >= debugHandleInfo.length) {
            int newSize = Math.max(16, debugHandleInfo.length * 2);
            debugHandleInfo = PythonUtils.arrayCopyOf(debugHandleInfo, newSize);
        }
        debugHandleInfo[id] = toBits(currentGeneration, age++);
    }

    @Override
    public GraalHPyHandle createHandle(Object delegate) {
        GraalHPyHandle handle = super.createHandle(delegate);
        trackHandle(handle);
        return handle;
    }

    @Override
    public synchronized GraalHPyHandle getObjectForHPyHandle(int handle) {
        try {
            return super.getObjectForHPyHandle(handle);
        } catch (ArrayIndexOutOfBoundsException e) {
            onInvalidHandle(handle);
            return GraalHPyHandle.NULL_HANDLE;
        }
    }

    @Override
    @TruffleBoundary
    public synchronized boolean releaseHPyHandleForObject(int handleId) {
        if (handleId < 0) {
            onInvalidHandle(handleId);
            return false;
        } else {
            GraalHPyHandle handle = super.getObjectForHPyHandle(handleId);
            super.releaseHPyHandleForObject(handleId);
            debugHandleInfo[handleId] = -1;
            if (!closedHandles.isEmpty() && closedHandles.size() >= closedHandlesQueueMaxSize) {
                closedHandles.removeFirst();
            }
            closedHandles.add(handle);
            return true;
        }
    }

    private static int getGeneration(long bits) {
        return (int) (bits >>> Integer.SIZE);
    }

    private static long toBits(int generation, int age) {
        return ((long) generation << Integer.SIZE) | age;
    }

    @Override
    @TruffleBoundary
    void onInvalidHandle(int id) {
        assert GraalHPyHandle.wasAllocated(id);
        if (onInvalidHandleCallback != null) {
            CallNode.getUncached().execute(onInvalidHandleCallback);
        } else {
            CExtCommonNodes.fatalError(null, getContext(), null, "Invalid usage of already closed handle", -1);
        }
    }
}
