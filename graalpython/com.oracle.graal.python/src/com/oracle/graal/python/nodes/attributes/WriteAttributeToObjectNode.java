/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.attributes;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dict;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.HAS_NO_VALUE_PROPERTIES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false) // footprint reduction 120 -> 103
public abstract class WriteAttributeToObjectNode extends PNodeWithContext {

    public abstract boolean execute(Object primary, TruffleString key, Object value);

    public static WriteAttributeToObjectNode getUncached() {
        return WriteAttributeToObjectNodeGen.getUncached();
    }

    static boolean isAttrWritable(PythonObject self) {
        return (self.getShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0;
    }

    static boolean writeToDynamicStorageNoTypeGuard(Object obj, GetDictIfExistsNode getDict) {
        return getDict.execute(obj) == null && !PythonManagedClass.isInstance(obj);
    }

    // Specialization for cases that have no special handling and can just delegate to
    // WriteAttributeToPythonObjectNode.
    @Specialization(guards = {"isAttrWritable(object)", "writeToDynamicStorageNoTypeGuard(object, getDict)"})
    static boolean writeToDynamicStorageNoType(PythonObject object, TruffleString key, Object value,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Cached WriteAttributeToPythonObjectNode writeNode) {
        // Objects w/o dict that are not classes do not have any special handling
        writeNode.execute(object, key, value);
        return true;
    }

    // Specializations for no dict & PythonManagedClass -> requires calling onAttributeUpdate
    @Specialization(guards = {"isAttrWritable(klass)", "getDict.execute(klass) == null"})
    boolean writeToDynamicStorageBuiltinType(PythonBuiltinClass klass, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared @Cached DynamicObject.PutNode putNode,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (PythonContext.get(this).isInitialized()) {
            throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, key, klass);
        } else {
            return writeToDynamicStorageManagedClass(klass, key, value, inliningTarget, callAttrUpdate, putNode, codePointLengthNode, codePointAtIndexNode);
        }
    }

    @Specialization(guards = {"isAttrWritable(klass)", "getDict.execute(klass) == null"})
    static boolean writeToDynamicStoragePythonClass(PythonClass klass, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Exclusive @Cached InlinedBranchProfile callAttrUpdate,
                    @Exclusive @Cached InlinedBranchProfile updateFlags,
                    @Shared @Cached DynamicObject.PutNode putNode,
                    @Cached DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
                    @Cached DynamicObject.SetShapeFlagsNode setShapeFlagsNode,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (value == PNone.NO_VALUE) {
            updateFlags.enter(inliningTarget);
            klass.addShapeFlag(HAS_NO_VALUE_PROPERTIES, getShapeFlagsNode, setShapeFlagsNode);
        }
        return writeToDynamicStorageManagedClass(klass, key, value, inliningTarget, callAttrUpdate, putNode, codePointLengthNode, codePointAtIndexNode);
    }

    private static boolean writeToDynamicStorageManagedClass(PythonManagedClass klass, TruffleString key, Object value, Node inliningTarget,
                    InlinedBranchProfile callAttrUpdate, DynamicObject.PutNode putNode, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        CompilerAsserts.partialEvaluationConstant(klass.getClass());
        try {
            putNode.execute(klass, key, value);
            return true;
        } finally {
            if (!klass.canSkipOnAttributeUpdate(key, value, codePointLengthNode, codePointAtIndexNode)) {
                callAttrUpdate.enter(inliningTarget);
                klass.onAttributeUpdate(key, value);
            }
        }
    }

    // write to the dict: the basic specialization for non-classes
    @Specialization(guards = {"dict != null", "!isManagedClass(object)", "!isNoValue(value)"})
    static boolean writeToDictNoType(@SuppressWarnings("unused") PythonObject object, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(object)") PDict dict,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem) {
        return writeToDict(dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
    }

    // write to the dict & PythonManagedClass -> requires calling onAttributeUpdate
    @Specialization(guards = {"dict != null", "!isNoValue(value)"})
    boolean writeToDictBuiltinType(PythonBuiltinClass klass, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (PythonContext.get(this).isInitialized()) {
            throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), klass);
        } else {
            return writeToDictManagedClass(klass, dict, key, value, inliningTarget, callAttrUpdate, updateStorage, setHashingStorageItem, codePointLengthNode, codePointAtIndexNode);
        }
    }

    @Specialization(guards = {"dict != null", "!isNoValue(value)"})
    static boolean writeToDictClass(PythonClass klass, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        return writeToDictManagedClass(klass, dict, key, value, inliningTarget, callAttrUpdate, updateStorage, setHashingStorageItem, codePointLengthNode, codePointAtIndexNode);
    }

    // @Exclusive for truffle-interpreted-performance
    @Specialization(guards = {"dict != null", "isNoValue(value)", "!isPythonBuiltinClass(obj)"})
    static boolean deleteFromPythonObject(PythonObject obj, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(obj)") PDict dict,
                    @Exclusive @Cached InlinedBranchProfile callAttrUpdate,
                    @Cached HashingStorageNodes.HashingStorageDelItem hashingStorageDelItem,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        try {
            HashingStorage dictStorage = dict.getDictStorage();
            return hashingStorageDelItem.execute(inliningTarget, dictStorage, key, dict);
        } finally {
            if (obj instanceof PythonManagedClass klass) {
                if (!klass.canSkipOnAttributeUpdate(key, value, codePointLengthNode, codePointAtIndexNode)) {
                    callAttrUpdate.enter(inliningTarget);
                    klass.onAttributeUpdate(key, value);
                }
            }
        }
    }

    @Specialization(guards = {"dict != null", "isNoValue(value)"})
    static boolean deleteFromPythonBuiltinClass(PythonBuiltinClass klass, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict) {
        throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, key, klass);
    }

    private static boolean writeToDictManagedClass(PythonManagedClass klass, PDict dict, TruffleString key, Object value, Node inliningTarget,
                    InlinedBranchProfile callAttrUpdate, InlinedBranchProfile updateStorage, HashingStorageSetItem setHashingStorageItem, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        CompilerAsserts.partialEvaluationConstant(klass.getClass());
        try {
            return writeToDict(dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
        } finally {
            if (!klass.canSkipOnAttributeUpdate(key, value, codePointLengthNode, codePointAtIndexNode)) {
                callAttrUpdate.enter(inliningTarget);
                klass.onAttributeUpdate(key, value);
            }
        }
    }

    static boolean writeToDict(PDict dict, TruffleString key, Object value,
                    Node inliningTarget,
                    InlinedBranchProfile updateStorage,
                    HashingStorageSetItem setHashingStorageItem) {
        assert dict != null;
        HashingStorage dictStorage = dict.getDictStorage();
        // The assumption is that the key is a string with the default __hash__ and __eq__, so we do
        // not need to pass the frame. This is not entirely correct: GR-41728
        HashingStorage hashingStorage = setHashingStorageItem.execute(null, inliningTarget, dictStorage, key, value);
        if (dictStorage != hashingStorage) {
            updateStorage.enter(inliningTarget);
            dict.setDictStorage(hashingStorage);
        }
        return true;
    }

    @Specialization
    static boolean doPBCT(PythonBuiltinClassType object, TruffleString key, Object value,
                    @Cached WriteAttributeToObjectNode recursive) {
        return recursive.execute(PythonContext.get(recursive).lookupType(object), key, value);
    }

    private static void checkNativeImmutable(Node inliningTarget, PythonAbstractNativeObject object, TruffleString key,
                    CStructAccess.ReadI64Node getNativeFlags,
                    PRaiseNode raiseNode) {
        long flags = getNativeFlags.readFromObj(object, CFields.PyTypeObject__tp_flags);
        if ((flags & TypeFlags.IMMUTABLETYPE) != 0) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, key, object);
        }
    }

    @Specialization
    static boolean writeNativeObjectOrClass(PythonAbstractNativeObject object, TruffleString key, Object value,
                    @Bind Node inliningTarget,
                    @Cached InlinedConditionProfile isTypeProfile,
                    @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Cached CStructAccess.ReadI64Node getNativeFlags,
                    @Cached CStructAccess.ReadObjectNode getNativeDict,
                    @Exclusive @Cached HashingStorageSetItem setHashingStorageItem,
                    @Exclusive @Cached InlinedBranchProfile updateStorage,
                    @Exclusive @Cached InlinedBranchProfile canBeSpecialSlot,
                    @Cached IsTypeNode isTypeNode,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        boolean isType = isTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, object));
        try {
            Object dict;
            if (isType) {
                checkNativeImmutable(inliningTarget, object, key, getNativeFlags, raiseNode);
                /*
                 * For native types, the type attributes are stored in a dict that is located in
                 * 'typePtr->tp_dict'. So, this is different to a native object (that is not a type)
                 * and we need to load the dict differently. We must not use
                 * 'PythonObjectLibrary.getDict' here but read member 'tp_dict'.
                 */
                dict = getNativeDict.readFromObj(object, PyTypeObject__tp_dict);
            } else {
                /*
                 * The dict of native objects that stores the object attributes is located at
                 * 'objectPtr + Py_TYPE(objectPtr)->tp_dictoffset'. 'PythonObjectLibrary.getDict'
                 * will exactly load the dict from there.
                 */
                dict = getDict.execute(object);
            }
            if (dict instanceof PDict) {
                return writeToDict((PDict) dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
            }
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        } finally {
            if (isType && TpSlots.canBeSpecialMethod(key, codePointLengthNode, codePointAtIndexNode)) {
                canBeSpecialSlot.enter(inliningTarget);
                // In theory, we should do this only in type's default tp_setattr(o) slots,
                // one could probably bypass that by using different metaclass and
                // overriding tp_setattr and delegate to object's tp_setattr that does not
                // have this hook
                TpSlots.updateSlot(object, key);
            }
        }
    }

    @Specialization(guards = "isErrorCase(getDict, object)")
    static boolean doError(Object object, TruffleString key, Object value,
                    @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind Node inliningTarget) {
        throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
    }

    static boolean isErrorCase(GetDictIfExistsNode getDict, Object object) {
        if (object instanceof PythonObject self) {
            if (isAttrWritable(self) && (getDict.execute(self) == null)) {
                return false;
            }
            if (getDict.execute(self) != null) {
                return false;
            }
        }
        if (object instanceof PythonAbstractNativeObject) {
            return false;
        }
        if (object instanceof PythonBuiltinClassType) {
            return false;
        }
        return true;
    }

}
