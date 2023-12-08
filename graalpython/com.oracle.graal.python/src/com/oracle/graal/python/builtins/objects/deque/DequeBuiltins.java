/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.deque;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.ErrorMessages.DEQUE_MUTATED_DURING_REMOVE;
import static com.oracle.graal.python.nodes.ErrorMessages.DEQUE_REMOVE_X_NOT_IN_DEQUE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.StringLiterals.T_ELLIPSIS_IN_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexCustomMessageNode;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeDelItemNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeGetItemNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeInplaceMulNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeInsertNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeMulNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeRMulNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeRotateNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltinsClinicProviders.DequeSetItemNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode.GeNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode.GtNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode.LeNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode.LtNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDeque)
public final class DequeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DequeBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        // setting None means that this type is unhashable
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    // deque.__init__(self, [iterable, [maxlen]])
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "iterable", "maxlen"})
    @GenerateNodeFactory
    public abstract static class DequeInitNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isNoValue(iterable)")
        @SuppressWarnings("unused")
        static PNone doNothing(PDeque self, PNone iterable, PNone maxlen) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(iterable)")
        static PNone doIterable(VirtualFrame frame, PDeque self, Object iterable, @SuppressWarnings("unused") PNone maxlen,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile sizeZeroProfile,
                        @Exclusive @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached GetNextNode getNextNode,
                        @Exclusive @Cached IsBuiltinObjectProfile isStopIterationProfile) {
            if (sizeZeroProfile.profile(inliningTarget, self.getSize() != 0)) {
                self.clear();
            }
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            while (true) {
                try {
                    self.append(getNextNode.execute(frame, iterator));
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.StopIteration, isStopIterationProfile);
                    break;
                }
            }
            return PNone.NONE;
        }

        @Specialization(replaces = {"doNothing", "doIterable"})
        static PNone doGeneric(VirtualFrame frame, PDeque self, Object iterable, Object maxlenObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile sizeZeroProfile,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Exclusive @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached GetNextNode getNextNode,
                        @Exclusive @Cached IsBuiltinObjectProfile isTypeErrorProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile isStopIterationProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!PGuards.isPNone(maxlenObj)) {
                try {
                    int maxlen = castToIntNode.execute(inliningTarget, maxlenObj);
                    if (maxlen < 0) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.MAXLEN_MUST_BE_NONNEG);
                    }
                    self.setMaxLength(maxlen);
                } catch (PException e) {
                    /*
                     * CastToJavaIntExactNode will throw a TypeError; we need to convert to
                     * OverflowError
                     */
                    e.expect(inliningTarget, TypeError, isTypeErrorProfile);
                    throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
                } catch (CannotCastException e) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
            }

            if (iterable != PNone.NO_VALUE) {
                doIterable(frame, self, iterable, PNone.NO_VALUE, inliningTarget, sizeZeroProfile, getIter, getNextNode, isStopIterationProfile);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "maxlen", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class DequeMaxLengthNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getMaxLength() < 0")
        static PNone doNone(@SuppressWarnings("unused") PDeque self) {
            return PNone.NONE;
        }

        @Specialization(guards = "self.getMaxLength() >= 0")
        static int doInt(PDeque self) {
            return self.getMaxLength();
        }
    }

    // deque.append(x)
    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone doGeneric(PDeque self, Object arg) {
            self.append(arg);
            return PNone.NONE;
        }
    }

    // deque.appendleft(x)
    @Builtin(name = "appendleft", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeAppendLeftNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone doGeneric(PDeque self, Object arg) {
            self.appendLeft(arg);
            return PNone.NONE;
        }
    }

    // deque.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PNone doGeneric(PDeque self) {
            self.clear();
            return PNone.NONE;
        }
    }

    // deque.copy()
    @Builtin(name = J___COPY__, minNumOfPositionalArgs = 1)
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeCopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDeque doGeneric(PDeque self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            PDeque copy = factory.createDeque(getClassNode.execute(inliningTarget, self));
            copy.setMaxLength(self.getMaxLength());
            copy.addAll(self);
            return copy;
        }
    }

    // deque.count(v)
    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeCountNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int doGeneric(PDeque self, Object value) {
            int n = 0;
            int startState = self.getState();
            for (Object item : self.data) {
                if (PyObjectRichCompareBool.EqNode.compareUncached(item, value)) {
                    n++;
                }
                if (startState != self.getState()) {
                    throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.DEQUE_MUTATED_DURING_ITERATION);
                }
            }
            return n;
        }
    }

    // deque.extend()
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @SuppressWarnings("truffle-static-method")
    public abstract static class DequeExtendNode extends PythonBinaryBuiltinNode {

        void appendOperation(PDeque self, Object item) {
            self.append(item);
        }

        @Specialization(guards = "self == other")
        @TruffleBoundary
        PNone doSelf(PDeque self, @SuppressWarnings("unused") PDeque other) {
            Object[] items = self.data.toArray();
            for (Object item : items) {
                appendOperation(self, item);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone doGeneric(VirtualFrame frame, PDeque self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile selfIsOtherProfile,
                        @Cached InlinedConditionProfile maxLenZeroProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile isStopIterationProfile) {
            if (selfIsOtherProfile.profile(inliningTarget, self == other)) {
                return doSelf(self, self);
            }

            Object it = getIter.execute(frame, inliningTarget, other);
            if (maxLenZeroProfile.profile(inliningTarget, self.getMaxLength() == 0)) {
                consumeIterator(frame, it, getNextNode, inliningTarget, isStopIterationProfile);
                return PNone.NONE;
            }

            while (true) {
                try {
                    appendOperation(self, getNextNode.execute(frame, it));
                } catch (PException e) {
                    e.expect(inliningTarget, StopIteration, isStopIterationProfile);
                    break;
                }
            }
            consumeIterator(frame, it, getNextNode, inliningTarget, isStopIterationProfile);
            return PNone.NONE;
        }

        private static void consumeIterator(VirtualFrame frame, Object it, GetNextNode getNextNode, Node inliningTarget, IsBuiltinObjectProfile isStopIterationProfile) {
            while (true) {
                try {
                    getNextNode.execute(frame, it);
                } catch (PException e) {
                    e.expect(inliningTarget, StopIteration, isStopIterationProfile);
                    break;
                }
            }
        }
    }

    // deque.extendleft()
    @Builtin(name = "extendleft", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeExtendLeftNode extends DequeExtendNode {
        @Override
        void appendOperation(PDeque self, Object item) {
            self.appendLeft(item);
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "v", "start", "stop"})
    @GenerateNodeFactory
    public abstract static class DequeIndexNode extends PythonQuaternaryBuiltinNode {

        @Specialization(guards = {"isNoValue(start)", "isNoValue(stop)"})
        static int doWithoutSlice(VirtualFrame frame, PDeque self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone stop,
                        @Bind("this") Node inliningTarget,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doWithIntSlice(frame, self, value, 0, self.getSize(), inliningTarget, eqNode, raiseNode);
        }

        @Specialization
        static int doWithIntSlice(VirtualFrame frame, PDeque self, Object value, int start, int stop,
                        @Bind("this") Node inliningTarget,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int size = self.getSize();
            int normStart = normalize(start, size);
            int normStop = normalize(stop, size);
            int startState = self.getState();
            if (normStop > size) {
                normStop = size;
            }
            if (normStart > normStop) {
                normStart = normStop;
            }
            Iterator<Object> iterator = self.iterator();
            for (int idx = 0; idx < normStop; idx++) {
                /*
                 * Note: A 'ConcurrentModificationException' should not be possible because we
                 * manually check for modifications during iteration.
                 */
                Object item = next(iterator);
                if (normStart <= idx) {
                    if (eqNode.compare(frame, inliningTarget, item, value)) {
                        return idx;
                    }
                    if (startState != self.getState()) {
                        throw raiseNode.get(inliningTarget).raise(RuntimeError, ErrorMessages.DEQUE_MUTATED_DURING_ITERATION);
                    }
                }
            }
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.S_IS_NOT_DEQUE, value);
        }

        @Specialization
        static int doGeneric(VirtualFrame frame, PDeque self, Object value, Object start, Object stop,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached PyNumberAsSizeNode startIndexNode,
                        @Cached PyNumberAsSizeNode stopIndexNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            int istart;
            int istop;
            if (start != PNone.NO_VALUE) {
                istart = castToIntNode.execute(inliningTarget, startIndexNode.executeLossy(frame, inliningTarget, start));
            } else {
                istart = 0;
            }
            if (stop != PNone.NO_VALUE) {
                istop = castToIntNode.execute(inliningTarget, stopIndexNode.executeLossy(frame, inliningTarget, stop));
            } else {
                istop = self.getSize();
            }
            return doWithIntSlice(frame, self, value, istart, istop, inliningTarget, eqNode, raiseNode);
        }

        private static int normalize(int i, int size) {
            int res = i;
            if (res < 0) {
                res += size;
            }
            return Math.max(res, 0);
        }

        @TruffleBoundary
        private static Object next(Iterator<?> it) {
            return it.next();
        }
    }

    // deque.insert()
    @Builtin(name = "insert", minNumOfPositionalArgs = 3, parameterNames = {"$self", "index", "object"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "index", conversion = ClinicConversion.Index)
    public abstract static class DequeInsertNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeInsertNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        PNone doGeneric(PDeque self, int index, Object value) {
            int n = self.getSize();
            if (self.getMaxLength() == n) {
                throw PRaiseNode.raiseUncached(this, IndexError, ErrorMessages.DEQUE_AT_MAX_SIZE);
            }

            // shortcuts for simple cases
            if (index >= n) {
                self.append(value);
            } else if (index <= -n || index == 0) {
                self.appendLeft(value);
            } else {
                DequeRotateNode.rotate(self, -index);
                if (index < 0) {
                    self.append(value);
                } else {
                    self.appendLeft(value);
                }
                DequeRotateNode.rotate(self, index);
            }

            return PNone.NONE;
        }
    }

    // deque.pop()
    @Builtin(name = "pop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequePopNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PDeque self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object value = self.pop();
            if (value == null) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.POP_FROM_EMPTY_DEQUE);
            }
            return value;
        }
    }

    // deque.popleft()
    @Builtin(name = "popleft", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequePopLeftNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PDeque self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object value = self.popLeft();
            if (value == null) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.POP_FROM_EMPTY_DEQUE);
            }
            return value;
        }
    }

    // deque.remove(v)
    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeRemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doGeneric(PDeque self, Object value) {
            // CPython captures the size before iteration
            int n = self.getSize();
            for (int i = 0; i < n; i++) {
                try {
                    boolean result = PyObjectRichCompareBool.EqNode.compareUncached(self.peekLeft(), value);
                    if (n != self.getSize()) {
                        throw PRaiseNode.raiseUncached(this, IndexError, DEQUE_MUTATED_DURING_REMOVE);
                    }
                    if (result) {
                        Object removed = self.popLeft();
                        assert removed != null;
                        DequeRotateNode.doRight(self, i);
                        return PNone.NONE;
                    } else {
                        // this is basically 'DequeRotateNode.doLeft(self, -1)'
                        self.append(self.popLeft());
                    }
                } catch (PException e) {
                    /*
                     * In case of an error during comparison, we need to restore the original deque
                     * by rotating.
                     */
                    DequeRotateNode.doRight(self, i);
                    throw e;
                }
            }
            throw PRaiseNode.raiseUncached(this, ValueError, DEQUE_REMOVE_X_NOT_IN_DEQUE);
        }
    }

    // deque.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        PNone doGeneric(PDeque self) {
            Object[] items = new Object[self.getSize()];
            for (int i = 0; i < items.length; i++) {
                items[i] = self.data.pollLast();
            }
            for (int i = 0; i < items.length; i++) {
                self.data.addLast(items[i]);
            }
            return PNone.NONE;
        }
    }

    // deque.rotate([n])
    @Builtin(name = "rotate", minNumOfPositionalArgs = 1, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index, defaultValue = "1")
    public abstract static class DequeRotateNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeRotateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.getSize() <= 1")
        @TruffleBoundary
        @SuppressWarnings("unused")
        static PNone doEmptyOrSingleElement(PDeque self, int n) {
            return PNone.NONE;
        }

        @Specialization(guards = "n >= 0")
        @TruffleBoundary
        static PNone doRight(PDeque self, int n) {
            if (self.getSize() > 1) {
                int effectiveRot = n % self.getSize();
                for (int i = 0; i < effectiveRot; i++) {
                    self.appendLeft(self.pop());
                }
            }
            return PNone.NONE;
        }

        @Specialization(guards = "n < 0")
        @TruffleBoundary
        static PNone doLeft(PDeque self, int n) {
            if (self.getSize() > 1) {
                int effectiveRot = -n % self.getSize();
                for (int i = 0; i < effectiveRot; i++) {
                    self.append(self.popLeft());
                }
            }
            return PNone.NONE;
        }

        static void rotate(PDeque self, int n) {
            if (n < 0) {
                doLeft(self, n);
            } else {
                doRight(self, n);
            }
        }
    }

    // SEQUENCE METHODS

    // deque.__len__()
    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeLenNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int doGeneric(PDeque self) {
            return self.getSize();
        }
    }

    // deque.__iadd__(v)
    @Builtin(name = J___IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeInplaceAddNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDeque doDeque(PDeque self, PDeque other) {
            if (self == other) {
                // we need to create a snapshot of 'self'
                self.addAll(self.data.toArray());
            } else {
                self.addAll(other);
            }
            return self;
        }

        @Specialization
        static PDeque doOther(VirtualFrame frame, PDeque self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile isStopIterationProfile) {
            if (other instanceof PDeque) {
                return doDeque(self, (PDeque) other);
            }
            assert self != other;
            /*
             * Funnily, CPython's implementation 'deque_inplace_concat' also allows to concat
             * non-deque objects (whereas 'deque_concat' just accepts deque objects).
             */
            Object iterator = getIter.execute(frame, inliningTarget, other);
            while (true) {
                try {
                    self.append(getNextNode.execute(frame, iterator));
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.StopIteration, isStopIterationProfile);
                    break;
                }
            }
            return self;
        }
    }

    // deque.__add__(v)
    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeAddNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDeque doDeque(PDeque self, PDeque other) {
            PDeque newDeque = PythonObjectFactory.getUncached().createDeque();
            newDeque.setMaxLength(self.getMaxLength());
            newDeque.addAll(self);
            newDeque.addAll(other);
            return newDeque;
        }

        @Specialization(replaces = "doDeque")
        static PDeque doGeneric(PDeque self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!(other instanceof PDeque)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CAN_ONLY_CONCATENATE_DEQUE_NOT_P_TO_DEQUE, other);
            }
            return doDeque(self, (PDeque) other);
        }
    }

    // deque.__mul__(v)
    @Builtin(name = J___IMUL__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeInplaceMulNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeInplaceMulNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PDeque doGeneric(PDeque self, int n) {
            return doGeneric(this, self, n);
        }

        @TruffleBoundary
        static PDeque doGeneric(Node node, PDeque self, int n) {
            int size = self.getSize();
            if (size == 0 || n == 1) {
                return self;
            }
            if (n <= 1) {
                self.clear();
                return self;
            }

            if (size > Integer.MAX_VALUE / n) {
                throw PRaiseNode.raiseUncached(node, MemoryError);
            }

            // Reduce the number of repetitions when maxlen would be exceeded
            int repetitions = n;
            if (self.getMaxLength() >= 0 && n * size > self.getMaxLength()) {
                repetitions = (self.getMaxLength() + size - 1) / size;
            }

            Object[] items = self.data.toArray();
            for (int i = 0; i < repetitions - 1; i++) {
                self.addAll(items);
            }
            return self;
        }
    }

    // deque.__mul__(v)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeMulNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeMulNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        PDeque doGeneric(PDeque self, int n) {
            PDeque newDeque = PythonObjectFactory.getUncached().createDeque();
            newDeque.setMaxLength(self.getMaxLength());
            newDeque.addAll(self);
            return DequeInplaceMulNode.doGeneric(this, newDeque, n);
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeRMulNode extends DequeMulNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeRMulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        boolean doGeneric(PDeque self, Object value) {
            int startState = self.getState();
            for (Object item : self.data) {
                if (PyObjectRichCompareBool.EqNode.compareUncached(item, value)) {
                    return true;
                }
                if (startState != self.getState()) {
                    throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.DEQUE_MUTATED_DURING_ITERATION);
                }
            }
            return false;
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeGetItemNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeGetItemNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        Object doGeneric(PDeque self, int idx,
                        @Cached NormalizeIndexCustomMessageNode normalizeIndexNode) {
            int normIdx = normalizeIndexNode.execute(idx, self.getSize(), ErrorMessages.DEQUE_INDEX_OUT_OF_RANGE);
            return doGetItem(self, normIdx);
        }

        @TruffleBoundary
        Object doGetItem(PDeque self, int idx) {
            assert 0 <= idx && idx < self.getSize();
            Iterator<Object> it = self.data.iterator();
            for (int i = 0; i < idx; i++) {
                it.next();
            }
            return it.next();
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3, parameterNames = {"$self", "n", "value"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeSetItemNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeSetItemNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doGeneric(PDeque self, int idx, Object value,
                        @Cached NormalizeIndexCustomMessageNode normalizeIndexNode) {
            int normIdx = normalizeIndexNode.execute(idx, self.getSize(), ErrorMessages.DEQUE_INDEX_OUT_OF_RANGE);
            self.setItem(normIdx, value != PNone.NO_VALUE ? value : null);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "n"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "n", conversion = ClinicConversion.Index)
    public abstract static class DequeDelItemNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DequeDelItemNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doGeneric(PDeque self, int idx,
                        @Cached NormalizeIndexCustomMessageNode normalizeIndexNode) {
            int normIdx = normalizeIndexNode.execute(idx, self.getSize(), ErrorMessages.DEQUE_INDEX_OUT_OF_RANGE);
            self.setItem(normIdx, null);
            return PNone.NONE;
        }
    }

    // deque.__bool__()
    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeBoolNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(PDeque self) {
            return self.getSize() != 0;
        }
    }

    // deque.__iter__()
    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeIterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDequeIter doGeneric(PDeque self,
                        @Cached PythonObjectFactory factory) {
            return factory.createDequeIter(self);
        }
    }

    // deque.__reversed__()
    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DequeReversedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PDequeIter doGeneric(PDeque self,
                        @Cached PythonObjectFactory factory) {
            return factory.createDequeRevIter(self);
        }
    }

    // deque.__repr__()
    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DequeReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        TruffleString repr(PDeque self) {
            if (!getContext().reprEnter(self)) {
                return T_ELLIPSIS_IN_BRACKETS;
            }
            EncapsulatingNodeReference ref = EncapsulatingNodeReference.getCurrent();
            Node outerNode = ref.set(this);
            try {
                Object[] items = self.data.toArray();
                PList asList = PythonObjectFactory.getUncached().createList(items);
                int maxLength = self.getMaxLength();
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                sb.appendStringUncached(GetNameNode.executeUncached(GetPythonObjectClassNode.executeUncached(self)));
                sb.appendStringUncached(T_LPAREN);
                sb.appendStringUncached(PyObjectStrAsTruffleStringNode.executeUncached(asList));
                if (maxLength != -1) {
                    sb.appendStringUncached(toTruffleStringUncached(", maxlen="));
                    sb.appendIntNumberUncached(maxLength);
                }
                sb.appendStringUncached(T_RPAREN);
                return sb.toStringUncached();
            } finally {
                ref.set(outerNode);
                getContext().reprLeave(self);
            }
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DequeReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(VirtualFrame frame, PDeque self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object dict = lookupAttr.execute(frame, inliningTarget, self, T___DICT__);
            if (PGuards.isNoValue(dict) || sizeNode.execute(frame, inliningTarget, dict) <= 0) {
                dict = PNone.NONE;
            }
            Object it = getIter.execute(frame, inliningTarget, self);
            PTuple emptyTuple = factory.createEmptyTuple();
            int maxLength = self.getMaxLength();
            if (maxLength != -1) {
                return factory.createTuple(new Object[]{clazz, factory.createTuple(new Object[]{emptyTuple, maxLength}), dict, it});
            }
            return factory.createTuple(new Object[]{clazz, emptyTuple, dict, it});

        }
    }

    @SuppressWarnings("truffle-static-method")
    public abstract static class DequeCompareNode extends PythonBinaryBuiltinNode {

        @Child PyObjectRichCompareBool.EqNode eqNode;

        @Specialization(guards = "shortcutIdentityCheck(self, other)")
        @SuppressWarnings("unused")
        static boolean doSame(PDeque self, PDeque other) {
            return true;
        }

        @Specialization(guards = "!isPDeque(other)")
        @SuppressWarnings("unused")
        static Object doOther(PDeque self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, PDeque self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIterSelf,
                        @Cached PyObjectGetIter getIterOther,
                        @Cached GetNextNode selfItNextNode,
                        @Cached GetNextNode otherItNextNode,
                        @Cached IsBuiltinObjectProfile profile) {
            if (!isPDeque(other)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            if (shortcutIdentityCheck(self, other)) {
                return true;
            }

            PDeque otherDeque = (PDeque) other;
            if (!shortcutLengthCheck(self, otherDeque)) {
                return false;
            }

            Object ait = getIterSelf.execute(frame, inliningTarget, self);
            Object bit = getIterOther.execute(frame, inliningTarget, otherDeque);
            while (true) {
                try {
                    Object selfItem = selfItNextNode.execute(frame, ait);
                    Object otherItem = otherItNextNode.execute(frame, bit);
                    if (!compareEq(frame, selfItem, otherItem)) {
                        return compare(frame, selfItem, otherItem);
                    }
                } catch (PException e) {
                    e.expect(inliningTarget, StopIteration, profile);
                    return compare(frame, self.getSize(), otherDeque.getSize());
                }
            }
        }

        static boolean isPDeque(Object object) {
            return object instanceof PDeque;
        }

        @SuppressWarnings("unused")
        boolean shortcutIdentityCheck(Object self, Object other) {
            return false;
        }

        @SuppressWarnings("unused")
        boolean shortcutLengthCheck(PDeque self, PDeque other) {
            return true;
        }

        final boolean compareEq(VirtualFrame frame, Object selfItem, Object otherItem) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(PyObjectRichCompareBool.EqNode.create());
            }
            return eqNode.compareCached(frame, selfItem, otherItem);
        }

        @SuppressWarnings("unused")
        boolean compare(VirtualFrame frame, Object selfItem, Object otherItem) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeEqNode extends DequeCompareNode {
        @Specialization(guards = "!shortcutLengthCheck(self, other)", insertBefore = "doGeneric")
        @SuppressWarnings("unused")
        static boolean doDifferentLength(PDeque self, PDeque other) {
            return false;
        }

        @Override
        boolean shortcutIdentityCheck(Object self, Object other) {
            return self == other;
        }

        @Override
        boolean shortcutLengthCheck(PDeque self, PDeque other) {
            return self.getSize() == other.getSize();
        }

        @Override
        boolean compare(VirtualFrame frame, Object selfItem, Object otherItem) {
            return compareEq(frame, selfItem, otherItem);
        }
    }

    public abstract static class DequeRelCompareNode extends DequeCompareNode {

        @Child private BinaryComparisonNode comparisonNode;
        @Child private PyObjectIsTrueNode isTrueNode;

        @Override
        boolean compare(VirtualFrame frame, Object selfItem, Object otherItem) {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(createCmp());
            }
            if (isTrueNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTrueNode = insert(PyObjectIsTrueNode.create());
            }
            return isTrueNode.executeCached(frame, comparisonNode.executeObject(frame, selfItem, otherItem));
        }

        BinaryComparisonNode createCmp() {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeLeNode extends DequeRelCompareNode {
        @Override
        boolean shortcutIdentityCheck(Object self, Object other) {
            return self == other;
        }

        @Override
        BinaryComparisonNode createCmp() {
            return LeNode.create();
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeLtNode extends DequeRelCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return LtNode.create();
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeGeNode extends DequeRelCompareNode {
        @Override
        boolean shortcutIdentityCheck(Object self, Object other) {
            return self == other;
        }

        @Override
        BinaryComparisonNode createCmp() {
            return GeNode.create();
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DequeGtNode extends DequeRelCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return GtNode.create();
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
