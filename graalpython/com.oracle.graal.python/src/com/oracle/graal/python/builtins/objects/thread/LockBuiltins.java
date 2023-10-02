/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.TIMEOUT_MAX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PLock, PythonBuiltinClassType.PRLock})
public final class LockBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LockBuiltinsFactory.getFactories();
    }

    public static final boolean DEFAULT_BLOCKING = AbstractPythonLock.DEFAULT_BLOCKING;
    public static final double UNSET_TIMEOUT = AbstractPythonLock.UNSET_TIMEOUT;

    @Builtin(name = "acquire", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @ArgumentClinic(name = "blocking", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "LockBuiltins.DEFAULT_BLOCKING", useDefaultForNone = true)
    @ArgumentClinic(name = "timeout", conversion = ArgumentClinic.ClinicConversion.Double, defaultValue = "LockBuiltins.UNSET_TIMEOUT", useDefaultForNone = true)
    @ImportStatic({LockBuiltins.class, AbstractPythonLock.class})
    @GenerateNodeFactory
    public abstract static class AcquireLockNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return LockBuiltinsClinicProviders.AcquireLockNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "!blocking"})
        static boolean nonBlocking(PLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout) {
            // acquire lock
            return self.acquireNonBlocking();
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "!blocking"})
        static boolean nonBlocking(PRLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout) {
            // acquire lock
            return self.acquireNonBlocking();
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "!blocking"})
        static boolean nonBlocking(PSemLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout) {
            // acquire lock
            return self.acquireNonBlocking();
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "timeout == UNSET_TIMEOUT", "blocking"})
        boolean acBlocking(PLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout,
                        @Cached.Shared("g") @Cached GilNode gil) {
            // acquire lock
            gil.release(true);
            try {
                return self.acquireBlocking(this);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "timeout == UNSET_TIMEOUT", "blocking"})
        boolean acBlocking(PRLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout,
                        @Cached.Shared("g") @Cached GilNode gil) {
            // acquire lock
            gil.release(true);
            try {
                return self.acquireBlocking(this);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "timeout == UNSET_TIMEOUT", "blocking"})
        boolean acBlocking(PSemLock self, @SuppressWarnings("unused") boolean blocking, @SuppressWarnings("unused") double timeout,
                        @Cached.Shared("g") @Cached GilNode gil) {
            // acquire lock
            gil.release(true);
            try {
                return self.acquireBlocking(this);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(guards = {"!invalidArgs(blocking, timeout)", "timeout != UNSET_TIMEOUT", "blocking"})
        boolean acTimeOut(AbstractPythonLock self, @SuppressWarnings("unused") boolean blocking, double timeout,
                        @Cached.Shared("g") @Cached GilNode gil) {
            // acquire lock
            gil.release(true);
            try {
                return self.acquireTimeout(this, timeout);
            } finally {
                gil.acquire();
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"invalidArgs(blocking, timeout)", "timeout != UNSET_TIMEOUT", "!blocking"})
        static boolean err1(AbstractPythonLock self, boolean blocking, double timeout,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.CANT_SPECIFY_TIMEOUT_FOR_NONBLOCKING);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"invalidArgs(blocking, timeout)", "timeout != UNSET_TIMEOUT", "isNeg(timeout)"})
        static boolean err2(AbstractPythonLock self, boolean blocking, double timeout,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.TIMEOUT_VALUE_MUST_BE_POSITIVE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"invalidArgs(blocking, timeout)", "timeout != UNSET_TIMEOUT", "timeout > TIMEOUT_MAX"})
        static boolean err3(AbstractPythonLock self, boolean blocking, double timeout,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(OverflowError, ErrorMessages.TIMEOUT_VALUE_TOO_LARGE);
        }

        protected static boolean invalidArgs(boolean blocking, double timeout) {
            return timeout != UNSET_TIMEOUT && (!blocking || timeout < 0 || timeout > TIMEOUT_MAX);
        }

        protected boolean isNeg(double timeout) {
            return timeout < 0;
        }
    }

    @Builtin(name = "acquire_lock", minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    public abstract static class AcquireLockLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object acquire(VirtualFrame frame, PLock self, Object blocking, Object timeout,
                        @Cached AcquireLockNode acquireLockNode) {
            return acquireLockNode.execute(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1, parameterNames = {"self", "blocking", "timeout"})
    @GenerateNodeFactory
    abstract static class EnterLockNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object acquire(VirtualFrame frame, AbstractPythonLock self, Object blocking, Object timeout,
                        @Cached AcquireLockNode acquireLockNode) {
            return acquireLockNode.execute(frame, self, blocking, timeout);
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doRelease(PLock self) {
            self.release();
            return PNone.NONE;
        }

        @Specialization
        Object doRelease(PRLock self) {
            if (!self.isOwned()) {
                throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.LOCK_NOT_HELD);
            }
            self.release();
            return PNone.NONE;
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitLockNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object exit(AbstractPythonLock self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback) {
            self.release();
            return PNone.NONE;
        }
    }

    @Builtin(name = "locked", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsLockedLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isLocked(PLock self) {
            return self.locked();
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(PLock self,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<%s %s object at %d>",
                            (self.locked()) ? "locked" : "unlocked",
                            GetNameNode.executeUncached(GetPythonObjectClassNode.executeUncached(self)),
                            self.hashCode());
        }

        @Specialization
        TruffleString repr(PRLock self,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<%s %s object owner=%d count=%d at %d>",
                            (self.locked()) ? "locked" : "unlocked",
                            GetNameNode.executeUncached(GetPythonObjectClassNode.executeUncached(self)),
                            self.getOwnerId(),
                            self.getCount(),
                            self.hashCode());
        }
    }
}
