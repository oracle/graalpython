/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.multiprocessing;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;

import java.util.List;
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.SharedMultiprocessingData;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PGraalPySemLock})
public final class GraalPySemLockBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalPySemLockBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("SEM_VALUE_MAX", Integer.MAX_VALUE);
        super.initialize(core);
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getCount(PGraalPySemLock self) {
            return self.getCount();
        }
    }

    @Builtin(name = "_is_mine", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsMineNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isMine(PGraalPySemLock self) {
            return self.isMine();
        }
    }

    @Builtin(name = "_is_zero", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsZeroNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isZero(PGraalPySemLock self) {
            return self.isZero();
        }
    }

    @Builtin(name = "_get_value", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getValue(PGraalPySemLock self) {
            return self.getValue();
        }
    }

    @Builtin(name = "handle", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetHandleNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int getHandle(PGraalPySemLock self) {
            return self.hashCode();
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString getName(PGraalPySemLock self) {
            return self.getName();
        }
    }

    @Builtin(name = "maxvalue", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetMaxValue extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getMax(@SuppressWarnings("unused") PGraalPySemLock self) {
            return Integer.MAX_VALUE;
        }
    }

    @Builtin(name = "kind", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetKindNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getKind(PGraalPySemLock self) {
            return self.getKind();
        }
    }

    @Builtin(name = "acquire", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @ArgumentClinic(name = "blocking", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class AcquireNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GraalPySemLockBuiltinsClinicProviders.AcquireNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isFast(PGraalPySemLock self) {
            return self.getKind() == PGraalPySemLock.RECURSIVE_MUTEX && self.isMine();
        }

        @Specialization(guards = "isFast(self)")
        static boolean fast(PGraalPySemLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") Object timeout) {
            self.increaseCount();
            return true;
        }

        @Specialization(guards = "!isFast(self)")
        static boolean slow(VirtualFrame frame, PGraalPySemLock self, boolean blocking, Object timeoutObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached GilNode gil) {
            boolean hasDeadline = !(timeoutObj instanceof PNone);
            long timeoutMs = 0;
            if (hasDeadline) {
                timeoutMs = (long) (asDoubleNode.execute(frame, inliningTarget, timeoutObj) * 1000);
                if (timeoutMs < 0) {
                    timeoutMs = 0;
                }
            }
            boolean acquired = self.acquireNonBlocking();
            if (acquired) {
                return true;
            }
            if (!blocking) {
                return false;
            }
            gil.release(true);
            try {
                if (hasDeadline) {
                    return self.acquireTimeout(inliningTarget, timeoutMs);
                } else {
                    return self.acquireBlocking(inliningTarget);
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class EnterLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doEnter(VirtualFrame frame, PGraalPySemLock self, Object blocking, Object timeout,
                        @Cached AcquireNode acquireNode) {
            return acquireNode.execute(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = "_rebuild", minNumOfPositionalArgs = 4, parameterNames = {"handle", "kind", "maxvalue", "name"})
    @ArgumentClinic(name = "kind", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class RebuildNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GraalPySemLockBuiltinsClinicProviders.RebuildNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doEnter(@SuppressWarnings("unused") Object handle, int kind, @SuppressWarnings("unused") Object maxvalue, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory) {
            SharedMultiprocessingData multiprocessing = PythonContext.get(inliningTarget).getSharedMultiprocessingData();
            Semaphore semaphore = multiprocessing.getNamedSemaphore(name);
            if (semaphore == null) {
                // TODO can this even happen? cpython simply creates a semlock object with the
                // provided handle
                semaphore = newSemaphore();
            }
            return factory.createGraalPySemLock(PythonBuiltinClassType.PGraalPySemLock, name, kind, semaphore);
        }

        @TruffleBoundary
        private static Semaphore newSemaphore() {
            return new Semaphore(0);
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doRelease(PGraalPySemLock self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.getKind() == PGraalPySemLock.RECURSIVE_MUTEX) {
                if (!self.isMine()) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AssertionError, ErrorMessages.ATTEMP_TO_RELEASE_RECURSIVE_LOCK);
                }
                if (self.getCount() > 1) {
                    self.decreaseCount();
                    return PNone.NONE;
                }
                assert self.getCount() == 1;
            }
            self.release();
            return PNone.NONE;
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitLockNode extends PythonBuiltinNode {
        @Specialization
        static Object exit(PGraalPySemLock self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback) {
            self.release();
            return PNone.NONE;
        }
    }

}
