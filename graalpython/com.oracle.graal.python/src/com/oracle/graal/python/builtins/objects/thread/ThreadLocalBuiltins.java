/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.CallSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PThreadLocal)
public final class ThreadLocalBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ThreadLocalBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadLocalBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone repr(@SuppressWarnings("unused") PThreadLocal self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        PDict repr(VirtualFrame frame, PThreadLocal self,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict) {
            return getThreadLocalDict.execute(frame, self);
        }
    }

    @Slot(value = SlotKind.tp_get_attro, isComplex = true)
    @ImportStatic(PGuards.class)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends GetAttrBuiltinNode {
        @Child private CallSlotDescrGet callGetNode;
        @Child private GetClassNode getDescClassNode;

        @Specialization
        Object doIt(VirtualFrame frame, PThreadLocal object, Object keyObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Cached HashingStorageGetItem getDictStorageItem,
                        @Cached InlinedConditionProfile hasDescrProfile,
                        @Cached InlinedConditionProfile hasDescrGetProfile,
                        @Cached InlinedConditionProfile hasValueProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // Note: getting thread local dict has potential side-effects, don't move
            PDict localDict = getThreadLocalDict.execute(frame, object);
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = lookup.execute(type, key);
            TpSlot descrGetSlot = null;
            boolean hasDescr = hasDescrProfile.profile(inliningTarget, descr != PNone.NO_VALUE);
            boolean hasDescrGet = false;
            if (hasDescr) {
                var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                descrGetSlot = descrSlots.tp_descr_get();
                hasDescrGet = hasDescrGetProfile.profile(inliningTarget, descrGetSlot != null);
                if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return dispatch(frame, object, type, descr, descrGetSlot);
                }
            }
            Object value = getDictStorageItem.execute(frame, inliningTarget, localDict.getDictStorage(), key);
            if (hasValueProfile.profile(inliningTarget, value != null)) {
                return value;
            }
            if (hasDescr) {
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return dispatch(frame, object, type, descr, descrGetSlot);
                }
            }
            throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }

        private Object dispatch(VirtualFrame frame, Object object, Object type, Object descr, TpSlot get) {
            if (callGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetNode = insert(CallSlotDescrGet.create());
            }
            return callGetNode.executeCached(frame, get, descr, object, type);
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.executeCached(desc);
        }

        public static ObjectBuiltins.GetAttributeNode create() {
            return ObjectBuiltinsFactory.GetAttributeNodeFactory.create();
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Child private CallSlotDescrSet callSetNode;
        @Child private HashingStorageSetItem setHashingStorageItem;
        @CompilationFinal private boolean changedStorage;

        @Specialization
        protected PNone doStringKey(VirtualFrame frame, PThreadLocal object, Object keyObject, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // Note: getting thread local dict has potential side-effects, don't move
            PDict localDict = getThreadLocalDict.execute(frame, object);
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(inliningTarget, keyObject);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObject);
            }
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                TpSlots descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                if (descrSlots.tp_descr_set() != null) {
                    ensureCallSetNode().executeCached(frame, descrSlots.tp_descr_set(), descr, object, value);
                    return PNone.NONE;
                }
            }
            writeAttribute(frame, localDict, key, value);
            return PNone.NONE;
        }

        private CallSlotDescrSet ensureCallSetNode() {
            if (callSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSetNode = insert(CallSlotDescrSet.create());
            }
            return callSetNode;
        }

        private void writeAttribute(VirtualFrame frame, PDict dict, Object key, Object value) {
            if (setHashingStorageItem == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setHashingStorageItem = insert(HashingStorageSetItem.create());
            }
            HashingStorage storage = dict.getDictStorage();
            HashingStorage newStorage = setHashingStorageItem.executeCached(frame, storage, key, value);
            if (storage != newStorage) {
                if (!changedStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    changedStorage = true;
                }
                dict.setDictStorage(newStorage);
            }
        }
    }

    @Builtin(name = J___DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Child private GetClassNode getDescClassNode;

        @Specialization
        PNone doIt(VirtualFrame frame, PThreadLocal object, Object keyObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict,
                        @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(T___DELETE__)") LookupAttributeInMRONode lookupDeleteNode,
                        @Cached CallBinaryMethodNode callDelete,
                        @Cached HashingStorageDelItem delHashingStorageItem,
                        @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // Note: getting thread local dict has potential side-effects, don't move
            PDict localDict = getThreadLocalDict.execute(frame, object);
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDescClass(descr);
                Object delete = lookupDeleteNode.execute(dataDescClass);
                if (PGuards.isCallable(delete)) {
                    callDelete.executeObject(frame, delete, descr, object);
                    return PNone.NONE;
                }
            }
            Object found = delHashingStorageItem.executePop(inliningTarget, localDict.getDictStorage(), key, localDict);
            if (found != null) {
                return PNone.NONE;
            }
            if (descr != PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
            }
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.executeCached(desc);
        }
    }
}
