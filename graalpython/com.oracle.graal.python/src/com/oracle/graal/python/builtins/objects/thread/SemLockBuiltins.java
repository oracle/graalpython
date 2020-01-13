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
package com.oracle.graal.python.builtins.objects.thread;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.AcquireLockNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PSemLock})
public class SemLockBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SemLockBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put("SEM_VALUE_MAX", Integer.MAX_VALUE);
        super.initialize(core);
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonUnaryBuiltinNode {
        @Specialization
        int getCount(PSemLock self) {
            return self.getCount();
        }
    }

    @Builtin(name = "_is_mine", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsMineNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isMine(PSemLock self) {
            return self.isMine();
        }
    }

    @Builtin(name = "_get_value", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends PythonUnaryBuiltinNode {
        @Specialization
        int getValue(PSemLock self) {
            return self.getValue();
        }
    }

    @Builtin(name = "handle", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetHandleNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int getHandle(PSemLock self) {
            return self.hashCode();
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getName(@SuppressWarnings("unused") PSemLock self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "maxvalue", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetMaxValue extends PythonUnaryBuiltinNode {
        @Specialization
        Object getMax(@SuppressWarnings("unused") PSemLock self) {
            return Integer.MAX_VALUE;
        }
    }

    @Builtin(name = "kind", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetKindNode extends PythonUnaryBuiltinNode {
        @Specialization
        int getKind(PSemLock self) {
            return self.getKind();
        }
    }

    @Builtin(name = "acquire", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class AcquireNode extends PythonTernaryBuiltinNode {

        @Specialization
        boolean doAcquire(VirtualFrame frame, PSemLock self, Object blocking, Object timeout,
                        @Cached AcquireLockNode acquireLockNode) {
            if (self.getKind() == PSemLock.RECURSIVE_MUTEX && self.isMine()) {
                self.increaseCount();
                return true;
            }
            return acquireLockNode.doAcquire(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class EnterLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doEnter(VirtualFrame frame, AbstractPythonLock self, Object blocking, Object timeout,
                        @Cached AcquireLockNode acquireLockNode) {
            return acquireLockNode.execute(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doRelease(PSemLock self) {
            if (self.getKind() == PSemLock.RECURSIVE_MUTEX) {
                if (!self.isMine()) {
                    throw raise(PythonBuiltinClassType.AssertionError, "attempt to release recursive lock not owned by thread");
                }
                if (self.getCount() > 1) {
                    self.decreaseCount();
                    return PNone.NONE;
                }
                assert self.getCount() == 1;
            }
            self.release();
            self.decreaseCount();
            return PNone.NONE;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitLockNode extends PythonBuiltinNode {
        @Specialization
        Object exit(AbstractPythonLock self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback) {
            self.release();
            return PNone.NONE;
        }
    }

}
