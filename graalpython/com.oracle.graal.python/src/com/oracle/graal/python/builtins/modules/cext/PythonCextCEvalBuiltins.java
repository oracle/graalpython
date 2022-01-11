/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins.AllocateLockNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.AcquireLockNode;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.ReleaseLockNode;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextCEvalBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextCEvalBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyThread_allocate_lock")
    @GenerateNodeFactory
    public abstract static class PyThreadAllocateLockNode extends PythonBuiltinNode {
        @Specialization
        public Object allocate(VirtualFrame frame,
                        @Cached AllocateLockNode allocateNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return allocateNode.execute(frame, PNone.NO_VALUE, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyThread_acquire_lock", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyThreadAcquireLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        public int acquire(VirtualFrame frame, PLock lock, int waitflag,
                        @Cached AcquireLockNode acquireNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return ((boolean) acquireNode.execute(frame, lock, waitflag, PNone.NONE)) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyThread_release_lock", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyThreadReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object release(VirtualFrame frame, PLock lock,
                        @Cached ReleaseLockNode releaseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return releaseNode.execute(frame, lock);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyEval_GetBuiltins")
    @GenerateNodeFactory
    public abstract static class PyEvalGetBuiltinsNode extends PythonBuiltinNode {
        @Specialization
        public Object release(@Cached GetDictIfExistsNode getDictNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                PythonModule cext = getCore().getBuiltins();
                return getDictNode.execute(cext);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }
}
