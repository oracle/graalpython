/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotSimpleBuiltinBase;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.Dynamic;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/**
 * For managed slots implementations (either builtins or Python code) the calling convention
 * difference between {@code tp_getattr} and {@code tp_getattro} does not make a difference, it only
 * makes a difference for the native slots. We distinguish them using different execute methods on
 * the call slot node.
 */
public class TpSlotGetAttr {

    public abstract static class TpSlotGetAttrBuiltin<T extends GetAttrBuiltinNode> extends TpSlotSimpleBuiltinBase<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotGetAttrBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, BuiltinSlotWrapperSignature.BINARY, PExternalFunctionWrapper.BINARYFUNC);
        }

        final GetAttrBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            PBuiltinFunction builtin = super.createBuiltin(core, type, tsName, wrapper);
            if (builtin == null) {
                return null;
            }
            return TpSlotBuiltinCallTargetRegistry.registerCallTarget(core, builtin, callTargetIndex);
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class GetAttrBuiltinNode extends PythonBinaryBuiltinNode {
        public abstract Object executeGetAttr(VirtualFrame frame, Object obj, TruffleString name);

        // We leave the generic execute intentionally not implemented, such that explicit calls to
        // "__getattr__" can be AST inlined, and such that when the Python wrapper is explicitly
        // called, the builtin does the conversion of the second argument to TruffleString itself.
        //
        // The builtins should have a @Specialization that does the coercion to TruffleString and
        // also fast-path specialization for TruffleString as the 2nd argument.
    }

    public static final class TpSlotGetAttrPython extends TpSlotPython {
        final TruffleWeakReference<Object> getattr;
        final TruffleWeakReference<Object> getattribute;
        final TruffleWeakReference<Object> type;

        public TpSlotGetAttrPython(Object getattribute, Object getattr, Object type) {
            this.getattr = asWeakRef(getattr);
            this.getattribute = asWeakRef(getattribute);
            this.type = new TruffleWeakReference<>(type);
        }

        public static TpSlotGetAttrPython create(Object[] callables, TruffleString[] callableNames, Object type) {
            assert callables.length == 2;
            assert callableNames == null || (callableNames[0].equals(T___GETATTRIBUTE__) && callableNames[1].equals(T___GETATTR__));
            return new TpSlotGetAttrPython(callables[0], callables[1], type);
        }

        @Override
        public TpSlotPython forNewType(Object klass) {
            Object newGetattribute = Dynamic.getUncached().execute(klass, T___GETATTRIBUTE__);
            Object newGetattr = Dynamic.getUncached().execute(klass, T___GETATTR__);
            if (newGetattr != getGetattr() || newGetattribute != getGetattribute()) {
                return new TpSlotGetAttrPython(newGetattribute, newGetattr, getType());
            }
            return this;
        }

        public Object getGetattr() {
            return safeGet(getattr);
        }

        public Object getGetattribute() {
            return safeGet(getattribute);
        }

        public Object getType() {
            return safeGet(type);
        }
    }

    /**
     * For managed types, this calls {@link TpSlots#combined_tp_getattro_getattr()}, because their
     * signature difference is not visible on the managed level. For native slots, this calls
     * {@link TpSlots#tp_getattro()} if available, otherwise {@link TpSlots#tp_getattr()} - at least
     * one of those must be available, i.e., the caller is expected to check precondition
     * {@code combined_tp_getattro_getattr() != null}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotGetAttrNode extends Node {
        public final Object execute(VirtualFrame frame, Node inliningTarget, TpSlots slots, Object self, TruffleString name) {
            assert slots.combined_tp_getattro_getattr() != null;
            return executeImpl(frame, inliningTarget, slots, slots.combined_tp_getattro_getattr(), self, name);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, TpSlots slots, TpSlot tp_get_attro_attr, Object self, TruffleString name);

        @Specialization
        static Object callManagedSlot(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlots slots, TpSlotManaged slot, Object self, TruffleString name,
                        @Cached CallManagedSlotGetAttrNode slotNode) {
            return slotNode.execute(frame, inliningTarget, slot, self, name);
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, TruffleString name,
                        @Cached(inline = false) CallNativeSlotGetAttrNode callNativeSlot) {
            return callNativeSlot.execute(frame, slots, slot, self, name);
        }
    }

    /**
     * Variant of {@link CallSlotGetAttrNode} that accepts generic object as the name.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotGetAttrONode extends Node {
        public final Object execute(VirtualFrame frame, Node inliningTarget, TpSlots slots, Object self, Object name) {
            assert slots.combined_tp_getattro_getattr() != null;
            return executeImpl(frame, inliningTarget, slots, slots.combined_tp_getattro_getattr(), self, name);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, TpSlots slots, TpSlot tp_get_attro_attr, Object self, Object name);

        @Specialization
        static Object callManagedSlot(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlots slots, TpSlotManaged slot, Object self, Object name,
                        @Cached CallManagedSlotGetAttrNode slotNode) {
            return slotNode.execute(frame, inliningTarget, slot, self, name);
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, Object name,
                        @Cached(inline = false) CallNativeSlotGetAttrNode callNativeSlot) {
            return callNativeSlot.execute(frame, slots, slot, self, name);
        }
    }

    @GenerateInline(false) // Used lazily
    @GenerateUncached
    abstract static class CallNativeSlotGetAttrNode extends Node {
        abstract Object execute(VirtualFrame frame, TpSlots slots, TpSlotNative tp_get_attro_attr, Object self, Object name);

        @Specialization
        static Object callNative(VirtualFrame frame, TpSlots slots, TpSlotNative slot, Object self, Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached InlinedConditionProfile isGetAttrProfile,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached FreeNode freeNode,
                        @Cached("createFor(inliningTarget)") IndirectCallData callData,
                        @Cached PythonToNativeNode toNativeNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached NativeToPythonTransferNode toPythonNode,
                        @Cached PyObjectCheckFunctionResultNode checkResultNode) {
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, null);
            boolean isGetAttr = isGetAttrProfile.profile(inliningTarget, slots.tp_getattr() == slot);
            Object nameArg;
            if (isGetAttr) {
                nameArg = asCharPointerNode.execute(name);
            } else {
                nameArg = toNativeNode.execute(name);
            }
            Object state = IndirectCallContext.enter(frame, threadState, callData);
            Object result;
            try {
                result = lib.execute(slot.callable, toNativeNode.execute(self), nameArg);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                if (frame != null) {
                    PArguments.setException(frame, threadState.getCaughtException());
                }
                IndirectCallContext.exit(frame, threadState, state);
                if (isGetAttr) {
                    freeNode.free(nameArg);
                }
            }
            return checkResultNode.execute(threadState, T___GETATTR__, toPythonNode.execute(result));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    @SuppressWarnings("rawtypes")
    public abstract static class CallManagedSlotGetAttrNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object name);

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, TruffleString name);

        @Specialization(guards = "slot == cachedSlot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotGetAttrBuiltin slot, Object self, TruffleString name,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotGetAttrBuiltin cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") GetAttrBuiltinNode slotNode) {
            // Assumption: most of the code is going to pass TruffleString, we have a slow-path
            // fallback for other cases at the end
            return slotNode.executeGetAttr(frame, self, name);
        }

        @Specialization(guards = "isNoValue(slot.getattr)")
        static Object callPythonSimple(VirtualFrame frame, Node inliningTarget, TpSlotGetAttrPython slot, Object self, Object name,
                        @Exclusive @Cached BinaryPythonSlotDispatcherNode callPythonFun) {
            // equivalent of typeobject.c:slot_tp_getattro, which is used if there is no __getattr__
            // hook
            return callPythonFun.execute(frame, inliningTarget, slot.getattribute, slot.type, self, name);
        }

        @Specialization(guards = "!isNoValue(slot.getattr)")
        static Object callPythonSimple(VirtualFrame frame, Node inliningTarget, TpSlotGetAttrPython slot, Object self, Object name,
                        @Exclusive @Cached BinaryPythonSlotDispatcherNode callPythonFun,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            // equivalent of slot_tp_getattr_hook
            Object type = slot.getType();
            Object getattr = slot.getGetattr();
            // Note: "evil" __getattribute__ can delete or update the __getattr__ attribute, so we
            // retrieve it before calling it. This has the same semantics as CPython, which also
            // captures __getattr__ before calling __getattribute__
            try {
                // TODO: CPython calls PyObject_GenericGetAttr if there is no __getattribute__. Can
                // we create a type that does not inherit tp_getattro and so no __getattribute__ is
                // created for it in add_operators?
                return callPythonFun.execute(frame, inliningTarget, slot.getGetattribute(), type, self, name);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                if (getattr == null) {
                    throw pe;
                } else {
                    return callPythonFun.execute(frame, inliningTarget, getattr, type, self, name);
                }
            }
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotGetAttrBuiltin slot, Object self, Object name,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, name);
            return BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
