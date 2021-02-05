/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.control;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.truffle.api.nodes.NodeCost.NONE;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.UnaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(cost = NONE)
public final class GetNextNode extends PNodeWithContext {

    private GetNextNode(LookupAndCallUnaryNode nextCall) {
        this.nextCall = nextCall;
    }

    public static GetNextNode create() {
        return new GetNextNode(null);
    }

    public static GetNextNode create(LookupAndCallUnaryNode nextCall) {
        return new GetNextNode(nextCall);
    }

    @Child private PythonObjectLibrary lib;
    @Child private GetNextFastPathNode fastPathNode;
    @Child private LookupAndCallUnaryNode nextCall;

    public Object execute(VirtualFrame frame, Object iterator) {
        ensureFastPathAndLib();
        return fastPathNode.execute(frame, iterator, lib.getLazyPythonClass(iterator), this);
    }

    public boolean executeBoolean(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return ensureNextCall().executeBoolean(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public int executeInt(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return ensureNextCall().executeInt(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public long executeLong(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return ensureNextCall().executeLong(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public double executeDouble(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return ensureNextCall().executeDouble(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    private LookupAndCallUnaryNode ensureNextCall() {
        if (nextCall == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nextCall = insert(LookupAndCallUnaryNode.create(__NEXT__, NoAttrHandlerSupplier.INSTANCE));
        }
        return nextCall;
    }

    private void ensureFastPathAndLib() {
        if (fastPathNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fastPathNode = insert(GetNextNodeFactory.GetNextFastPathNodeGen.create());
        }
        if (lib == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lib = insert(PythonObjectLibrary.getFactory().createDispatched(4));
        }
    }

    public abstract static class GetNextFastPathNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object iterator, Object lazyClass, GetNextNode getNextNode);

        protected static boolean isNext(PythonBuiltinClassType klassType, NodeFactory<? extends PythonUnaryBuiltinNode> factory) {
            Object slot = SpecialMethodSlot.Next.getValue(klassType);
            return slot instanceof UnaryBuiltinInfo && ((UnaryBuiltinInfo) slot).getUnaryFactory() == factory;
        }

        protected static boolean isNext(PythonManagedClass klass, NodeFactory<? extends PythonUnaryBuiltinNode> factory) {
            Object slot = SpecialMethodSlot.Next.getValue(klass);
            return slot instanceof PBuiltinFunction && ((PBuiltinFunction) slot).getBuiltinNodeFactory() == factory;
        }

        protected static boolean isNext(Object lazyClass, NodeFactory<? extends PythonUnaryBuiltinNode> factory) {
            if (lazyClass instanceof PythonBuiltinClassType) {
                return isNext((PythonBuiltinClassType) lazyClass, factory);
            } else if (lazyClass instanceof PythonManagedClass) {
                return isNext((PythonManagedClass) lazyClass, factory);
            }
            return false;
        }

        protected static boolean isIteratorNext(Object lazyClass) {
            return isNext(lazyClass, IteratorBuiltinsFactory.NextNodeFactory.getInstance());
        }

        protected static boolean isGeneratorNext(Object lazyClass) {
            return isNext(lazyClass, GeneratorBuiltinsFactory.NextNodeFactory.getInstance());
        }

        protected static boolean isEnumerateNext(Object lazyClass) {
            return isNext(lazyClass, EnumerateBuiltinsFactory.NextNodeFactory.getInstance());
        }

        @Specialization(guards = "isIteratorNext(lazyClass)")
        Object doIterator(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass, @SuppressWarnings("unused") GetNextNode getNextNode,
                        @Cached IteratorBuiltins.NextNode nextNode) {
            return nextNode.call(frame, object);
        }

        @Specialization(guards = "isGeneratorNext(lazyClass)")
        Object doGenerator(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass,
                        @SuppressWarnings("unused") GetNextNode getNextNode,
                        @Cached GeneratorBuiltins.NextNode nextNode) {
            return nextNode.call(frame, object);
        }

        @Specialization(guards = "isEnumerateNext(lazyClass)")
        Object doEnumerate(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass,
                        @SuppressWarnings("unused") GetNextNode getNextNode,
                        @Cached EnumerateBuiltins.NextNode nextNode) {
            return nextNode.call(frame, object);
        }

        @Specialization(replaces = {"doEnumerate", "doIterator", "doGenerator"})
        Object doGeneric(VirtualFrame frame, Object iterator, @SuppressWarnings("unused") Object lazyClass, GetNextNode getNextNode) {
            return getNextNode.ensureNextCall().executeObject(frame, iterator);
        }
    }

    @GenerateUncached
    public abstract static class GetNextWithoutFrameNode extends PNodeWithContext {

        public abstract Object executeWithGlobalState(Object iterator);

        private static Object checkResult(PRaiseNode raiseNode, ConditionProfile notAnIterator, Object result, Object iterator) {
            if (notAnIterator.profile(result == PNone.NO_VALUE)) {
                throw raiseNode.raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, iterator, __NEXT__);
            }
            return result;
        }

        @Specialization
        Object doObject(Object iterator,
                        @Cached LookupAndCallUnaryDynamicNode nextCall,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile notAnIterator) {
            return checkResult(raiseNode, notAnIterator, nextCall.executeObject(iterator, __NEXT__), iterator);
        }
    }

    protected static final class NoAttrHandlerSupplier implements Supplier<NoAttributeHandler> {
        private static final NoAttrHandlerSupplier INSTANCE = new NoAttrHandlerSupplier();

        @Override
        public NoAttributeHandler get() {
            return new NoAttributeHandler() {
                @Child private PRaiseNode raiseNode = PRaiseNode.create();

                @Override
                public Object execute(Object receiver) {
                    throw raiseNode.raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, __NEXT__);
                }
            };
        }
    }
}
