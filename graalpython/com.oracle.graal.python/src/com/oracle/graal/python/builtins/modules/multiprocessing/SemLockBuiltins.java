/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EXIT__;
import static com.oracle.graal.python.runtime.PosixConstants.O_CREAT;
import static com.oracle.graal.python.runtime.PosixConstants.O_EXCL;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.SemLockBuiltinsClinicProviders.SemLockNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSemLock)
public class SemLockBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = SemLockBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SemLockBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("SEM_VALUE_MAX", PosixConstants.SEM_VALUE_MAX.defined ? PosixConstants.SEM_VALUE_MAX.getValueIfDefined() : Integer.MAX_VALUE);
        super.initialize(core);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "SemLock", parameterNames = {"$cls", "kind", "value", "maxvalue", "name", "unlink"})
    @ArgumentClinic(name = "kind", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "maxvalue", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "unlink", conversion = ArgumentClinic.ClinicConversion.IntToBoolean)
    @GenerateNodeFactory
    abstract static class SemLockNode extends PythonClinicBuiltinNode {
        @Specialization
        static PSemLock construct(VirtualFrame frame, Object cls, int kind, int value, int maxValue, TruffleString name, boolean unlink,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            if (kind != PGraalPySemLock.RECURSIVE_MUTEX && kind != PGraalPySemLock.SEMAPHORE) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.UNRECOGNIZED_KIND);
            }
            Object posixName = posixLib.createPathFromString(posixSupport, name);
            long handle;
            try {
                handle = posixLib.semOpen(posixSupport, posixName, O_CREAT.value | O_EXCL.value, 0600, value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (unlink) {
                try {
                    posixLib.semUnlink(posixSupport, posixName);
                } catch (PosixException e) {
                    try {
                        posixLib.semClose(posixSupport, handle);
                    } catch (PosixException ex) {
                        // Ignore, we're already on an error path
                    }
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            return PFactory.createSemLock(language, cls, getInstanceShape.execute(cls), handle, kind, maxValue, name);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SemLockNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "handle", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class HandleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSemLock self) {
            return self.getHandle();
        }
    }

    @Builtin(name = "kind", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class KindNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSemLock self) {
            return self.getKind();
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSemLock self) {
            return self.getName();
        }
    }

    @Builtin(name = "maxvalue", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MaxValueNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSemLock self) {
            return self.getMaxValue();
        }
    }

    @Builtin(name = "acquire", minNumOfPositionalArgs = 1, parameterNames = {"$self", "block", "timeout"})
    @ArgumentClinic(name = "block", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class AcquireNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static boolean acquire(VirtualFrame frame, PSemLock self, boolean blocking, Object timeoutObj,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (self.getKind() == PSemLock.RECURSIVE_MUTEX && self.isMine()) {
                self.increaseCount();
                return true;
            }
            boolean hasDeadline = !(timeoutObj instanceof PNone);
            long deadlineNs = 0;
            if (hasDeadline) {
                double timeout = asDoubleNode.execute(frame, inliningTarget, timeoutObj);
                if (timeout < 0) {
                    timeout = 0;
                }
                long timeoutNs = (long) (timeout * 1e9);
                long nowNs = System.currentTimeMillis() * 1000_000;
                deadlineNs = nowNs + timeoutNs;
            }
            /* Check whether we can acquire without releasing the GIL and blocking */
            boolean acquired;
            try {
                acquired = posixLib.semTryWait(posixSupport, self.getHandle());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (blocking && !acquired) {
                try {
                    gil.release(true);
                    try {
                        if (hasDeadline) {
                            acquired = posixLib.semTimedWait(posixSupport, self.getHandle(), deadlineNs);
                        } else {
                            posixLib.semWait(posixSupport, self.getHandle());
                            acquired = true;
                        }
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            if (acquired) {
                self.increaseCount();
                self.setLastThreadId(PThread.getThreadId(Thread.currentThread()));
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SemLockBuiltinsClinicProviders.AcquireNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReleaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone release(VirtualFrame frame, PSemLock self,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Cached PRaiseNode raiseNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (self.getKind() == PSemLock.RECURSIVE_MUTEX) {
                if (!self.isMine()) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AssertionError, ErrorMessages.ATTEMP_TO_RELEASE_RECURSIVE_LOCK);
                }
                if (self.getCount() > 1) {
                    self.decreaseCount();
                    return PNone.NONE;
                }
            } else {
                int sval;
                try {
                    try {
                        sval = posixLib.semGetValue(posixSupport, self.getHandle());
                        if (sval >= self.getMaxValue()) {
                            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SEMAPHORE_RELEASED_TOO_MANY_TIMES);
                        }
                    } catch (UnsupportedPosixFeatureException e) {
                        /* We will only check properly the maxvalue == 1 case */
                        if (self.getMaxValue() == 1) {
                            if (posixLib.semTryWait(posixSupport, self.getHandle())) {
                                /* it was not locked so undo wait and raise */
                                posixLib.semPost(posixSupport, self.getHandle());
                                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SEMAPHORE_RELEASED_TOO_MANY_TIMES);
                            }
                        }
                    }
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }
            try {
                posixLib.semPost(posixSupport, self.getHandle());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            self.decreaseCount();
            return PNone.NONE;
        }
    }

    @Builtin(name = J___ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object enter(VirtualFrame frame, PSemLock self,
                        @Cached AcquireNode acquireNode) {
            return acquireNode.execute(frame, self, true, PNone.NO_VALUE);
        }
    }

    @Builtin(name = J___EXIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object exit(VirtualFrame frame, PSemLock self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback,
                        @Cached ReleaseNode releaseNode) {
            return releaseNode.execute(frame, self);
        }
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSemLock self) {
            return self.getCount();
        }
    }

    @Builtin(name = "_is_mine", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsMineNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean get(PSemLock self) {
            return self.isMine();
        }
    }

    @Builtin(name = "_get_value", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends PythonUnaryBuiltinNode {
        @Specialization
        int get(VirtualFrame frame, PSemLock self,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int sval = posixLib.semGetValue(posixSupport, self.getHandle());
                /*
                 * some posix implementations use negative numbers to indicate the number of waiting
                 * threads
                 */
                if (sval < 0) {
                    sval = 0;
                }
                return sval;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } catch (UnsupportedPosixFeatureException e) {
                // Not available on Darwin
                throw raiseNode.raise(inliningTarget, NotImplementedError);
            }
        }
    }

    @Builtin(name = "_is_zero", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsZeroNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean get(VirtualFrame frame, PSemLock self,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                try {
                    return posixLib.semGetValue(posixSupport, self.getHandle()) == 0;
                } catch (UnsupportedPosixFeatureException e) {
                    if (posixLib.semTryWait(posixSupport, self.getHandle())) {
                        posixLib.semPost(posixSupport, self.getHandle());
                        return false;
                    } else {
                        return true;
                    }
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "_after_fork", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AfterForkNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object afterFork(PSemLock self) {
            self.setCount(0);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_rebuild", parameterNames = {"$cls", "handle", "kind", "maxvalue", "name"}, isClassmethod = true)
    @ArgumentClinic(name = "handle", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "kind", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "maxvalue", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class RebuildNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object rebuild(VirtualFrame frame, Object cls, @SuppressWarnings("unused") long origHandle, int kind, int maxValue, TruffleString name,
                        @Bind Node inliningTarget,
                        @Bind("getPosixSupport()") PosixSupport posixSupport,
                        @CachedLibrary("posixSupport") PosixSupportLibrary posixLib,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Object posixName = posixLib.createPathFromString(posixSupport, name);
            long handle;
            try {
                handle = posixLib.semOpen(posixSupport, posixName);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PFactory.createSemLock(language, cls, getInstanceShape.execute(cls), handle, kind, maxValue, name);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SemLockBuiltinsClinicProviders.RebuildNodeClinicProviderGen.INSTANCE;
        }
    }
}
