/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectNotTypeNodeGen;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectTpDictNodeGen;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
@GenerateInline(false) // footprint reduction 120 -> 103
public abstract class WriteAttributeToObjectNode extends ObjectAttributeNode {

    public abstract boolean execute(Object primary, Object key, Object value);

    public abstract boolean execute(Object primary, HiddenKey key, Object value);

    @NeverDefault
    public static WriteAttributeToObjectNode create() {
        return WriteAttributeToObjectNotTypeNodeGen.create();
    }

    @NeverDefault
    public static WriteAttributeToObjectNode create(boolean forceType) {
        if (forceType) {
            return WriteAttributeToObjectTpDictNodeGen.create();
        }
        return WriteAttributeToObjectNotTypeNodeGen.create();
    }

    @NeverDefault
    public static WriteAttributeToObjectNode createForceType() {
        return WriteAttributeToObjectTpDictNodeGen.create();
    }

    public static WriteAttributeToObjectNode getUncached() {
        return WriteAttributeToObjectNotTypeNodeGen.getUncached();
    }

    public static WriteAttributeToObjectNode getUncached(boolean forceType) {
        if (forceType) {
            return WriteAttributeToObjectTpDictNodeGen.getUncached();
        }
        return WriteAttributeToObjectNotTypeNodeGen.getUncached();
    }

    protected static boolean isAttrWritable(PythonObject self, Object key) {
        if (isHiddenKey(key)) {
            return true;
        }
        return (self.getShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0;
    }

    private static TruffleString castKey(Node inliningTarget, CastToTruffleStringNode castNode, Object value) {
        try {
            return castNode.execute(inliningTarget, value);
        } catch (CannotCastException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    protected static boolean writeToDynamicStorageNoTypeGuard(Object obj, Object key, GetDictIfExistsNode getDict) {
        if (isHiddenKey(key)) {
            return true;
        }
        return getDict.execute(obj) == null && !PythonManagedClass.isInstance(obj);
    }

    // Specialization for cases that have no special handling and can just delegate to
    // WriteAttributeToDynamicObjectNode. Note that the fast-path for String keys and the inline
    // cache in WriteAttributeToDynamicObjectNode perform better in some configurations than if we
    // cast the key here and used DynamicObjectLibrary directly
    @Specialization(guards = {"isAttrWritable(object, key)", "writeToDynamicStorageNoTypeGuard(object, key, getDict)"})
    static boolean writeToDynamicStorageNoType(PythonObject object, Object key, Object value,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Cached WriteAttributeToDynamicObjectNode writeNode) {
        // Objects w/o dict that are not classes do not have any special handling
        writeNode.execute(object, key, value);
        return true;
    }

    // Specializations for no dict & PythonManagedClass -> requires calling onAttributeUpdate
    @Specialization(guards = {"isAttrWritable(klass, key)", "!isHiddenKey(key)", "getDict.execute(klass) == null"})
    boolean writeToDynamicStorageBuiltinType(PythonBuiltinClass klass, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Shared("castToStr") @Cached CastToTruffleStringNode castToStrNode,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared("dylib") @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") DynamicObjectLibrary dylib,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (PythonContext.get(this).isInitialized()) {
            throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), klass);
        } else {
            return writeToDynamicStorageManagedClass(klass, key, value, inliningTarget, castToStrNode, callAttrUpdate, dylib, codePointLengthNode, codePointAtIndexNode);
        }
    }

    @Specialization(guards = {"isAttrWritable(klass, key)", "!isHiddenKey(key)", "getDict.execute(klass) == null"})
    static boolean writeToDynamicStoragePythonClass(PythonClass klass, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Exclusive @Cached CastToTruffleStringNode castToStrNode,
                    @Exclusive @Cached InlinedBranchProfile callAttrUpdate,
                    @Exclusive @Cached InlinedBranchProfile updateFlags,
                    @Shared("dylib") @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") DynamicObjectLibrary dylib,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (value == PNone.NO_VALUE) {
            updateFlags.enter(inliningTarget);
            dylib.setShapeFlags(klass, dylib.getShapeFlags(klass) | HAS_NO_VALUE_PROPERTIES);
        }
        return writeToDynamicStorageManagedClass(klass, key, value, inliningTarget, castToStrNode, callAttrUpdate, dylib, codePointLengthNode, codePointAtIndexNode);
    }

    private static boolean writeToDynamicStorageManagedClass(PythonManagedClass klass, Object key, Object value, Node inliningTarget, CastToTruffleStringNode castToStrNode,
                    InlinedBranchProfile callAttrUpdate, DynamicObjectLibrary dylib, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        CompilerAsserts.partialEvaluationConstant(klass.getClass());
        TruffleString strKey = castKey(inliningTarget, castToStrNode, key);
        try {
            dylib.put(klass, strKey, value);
            return true;
        } finally {
            if (!klass.canSkipOnAttributeUpdate(strKey, value, codePointLengthNode, codePointAtIndexNode)) {
                callAttrUpdate.enter(inliningTarget);
                klass.onAttributeUpdate(strKey, value);
            }
        }
    }

    // write to the dict: the basic specialization for non-classes
    @Specialization(guards = {"!isHiddenKey(key)", "dict != null", "!isManagedClass(object)"})
    static boolean writeToDictNoType(@SuppressWarnings("unused") PythonObject object, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(object)") PDict dict,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem) {
        return writeToDict(dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
    }

    // write to the dict & PythonManagedClass -> requires calling onAttributeUpdate
    @Specialization(guards = {"!isHiddenKey(key)", "dict != null"})
    boolean writeToDictBuiltinType(PythonBuiltinClass klass, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict,
                    @Shared("castToStr") @Cached CastToTruffleStringNode castToStrNode,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (PythonContext.get(this).isInitialized()) {
            throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), klass);
        } else {
            return writeToDictManagedClass(klass, dict, key, value, inliningTarget, castToStrNode, callAttrUpdate, updateStorage, setHashingStorageItem, codePointLengthNode, codePointAtIndexNode);
        }
    }

    @Specialization(guards = {"!isHiddenKey(key)", "dict != null"})
    static boolean writeToDictClass(PythonClass klass, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict,
                    @Shared("castToStr") @Cached CastToTruffleStringNode castToStrNode,
                    @Shared("callAttrUpdate") @Cached InlinedBranchProfile callAttrUpdate,
                    @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                    @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                    @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        return writeToDictManagedClass(klass, dict, key, value, inliningTarget, castToStrNode, callAttrUpdate, updateStorage, setHashingStorageItem, codePointLengthNode, codePointAtIndexNode);
    }

    private static boolean writeToDictManagedClass(PythonManagedClass klass, PDict dict, Object key, Object value, Node inliningTarget, CastToTruffleStringNode castToStrNode,
                    InlinedBranchProfile callAttrUpdate, InlinedBranchProfile updateStorage, HashingStorageSetItem setHashingStorageItem, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        CompilerAsserts.partialEvaluationConstant(klass.getClass());
        TruffleString strKey = castKey(inliningTarget, castToStrNode, key);
        try {
            return writeToDict(dict, strKey, value, inliningTarget, updateStorage, setHashingStorageItem);
        } finally {
            if (!klass.canSkipOnAttributeUpdate(strKey, value, codePointLengthNode, codePointAtIndexNode)) {
                callAttrUpdate.enter(inliningTarget);
                klass.onAttributeUpdate(strKey, value);
            }
        }
    }

    static boolean writeToDict(PDict dict, Object key, Object value,
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
    static boolean doPBCT(PythonBuiltinClassType object, Object key, Object value,
                    @Cached WriteAttributeToObjectNode recursive) {
        return recursive.execute(PythonContext.get(recursive).lookupType(object), key, value);
    }

    protected static boolean isErrorCase(GetDictIfExistsNode getDict, Object object, Object key) {
        if (object instanceof PythonObject) {
            PythonObject self = (PythonObject) object;
            if (isAttrWritable(self, key) && (isHiddenKey(key) || getDict.execute(self) == null)) {
                return false;
            }
            if (!isHiddenKey(key) && getDict.execute(self) != null) {
                return false;
            }
        }
        if (object instanceof PythonAbstractNativeObject && !isHiddenKey(key)) {
            return false;
        }
        if (object instanceof PythonBuiltinClassType) {
            return false;
        }
        return true;
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 124 -> 107
    protected abstract static class WriteAttributeToObjectNotTypeNode extends WriteAttributeToObjectNode {
        @Specialization(guards = {"!isHiddenKey(key)"})
        static boolean writeNativeObject(PythonAbstractNativeObject object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                        @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                        @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            /*
             * The dict of native objects that stores the object attributes is located at 'objectPtr
             * + Py_TYPE(objectPtr)->tp_dictoffset'. 'PythonObjectLibrary.getDict' will exactly load
             * the dict from there.
             */
            PDict dict = getDict.execute(object);
            if (dict != null) {
                return writeToDict(dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
            }
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }

        @Specialization(guards = "isErrorCase(getDict, object, key)")
        static boolean doError(Object object, Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateInline(false) // footprint reduction 132 -> 115
    protected abstract static class WriteAttributeToObjectTpDictNode extends WriteAttributeToObjectNode {

        /*
         * Simplest case: the key object is a String (so it cannot be a hidden key) and it's not a
         * special method slot.
         */
        @Specialization(guards = "!canBeSpecial(keyObj, codePointLengthNode, codePointAtIndexNode)")
        static boolean writeNativeClassSimple(PythonAbstractNativeObject object, TruffleString keyObj, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CStructAccess.ReadObjectNode getNativeDict,
                        @Shared("setHashingStorageItem") @Cached HashingStorageSetItem setHashingStorageItem,
                        @Shared("updateStorage") @Cached InlinedBranchProfile updateStorage,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @SuppressWarnings("unused") @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @SuppressWarnings("unused") @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {

            /*
             * For native types, the type attributes are stored in a dict that is located in
             * 'typePtr->tp_dict'. So, this is different to a native object (that is not a type) and
             * we need to load the dict differently. We must not use 'PythonObjectLibrary.getDict'
             * here but read member 'tp_dict'.
             */
            Object dict = getNativeDict.readFromObj(object, PyTypeObject__tp_dict);
            if (dict instanceof PDict) {
                return writeToDict((PDict) dict, keyObj, value, inliningTarget, updateStorage, setHashingStorageItem);
            }
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, keyObj);
        }

        @Specialization(guards = "!isHiddenKey(keyObj)", replaces = "writeNativeClassSimple")
        static boolean writeNativeClassGeneric(PythonAbstractNativeObject object, Object keyObj, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CStructAccess.ReadObjectNode getNativeDict,
                        @Exclusive @Cached HashingStorageSetItem setHashingStorageItem,
                        @Exclusive @Cached InlinedBranchProfile updateStorage,
                        @Exclusive @Cached InlinedBranchProfile canBeSpecialSlot,
                        @Exclusive @Cached CastToTruffleStringNode castKeyNode,
                        @Cached IsTypeNode isTypeNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.EqualNode equalNode) {
            try {
                /*
                 * For native types, the type attributes are stored in a dict that is located in
                 * 'typePtr->tp_dict'. So, this is different to a native object (that is not a type)
                 * and we need to load the dict differently. We must not use
                 * 'PythonObjectLibrary.getDict' here but read member 'tp_dict'.
                 */
                Object dict = getNativeDict.readFromObj(object, PyTypeObject__tp_dict);
                if (dict instanceof PDict) {
                    return writeToDict((PDict) dict, keyObj, value, inliningTarget, updateStorage, setHashingStorageItem);
                }
                throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, keyObj);
            } finally {
                try {
                    TruffleString key = castKeyNode.execute(inliningTarget, keyObj);
                    if (SpecialMethodSlot.canBeSpecial(key, codePointLengthNode, codePointAtIndexNode)) {
                        canBeSpecialSlot.enter(inliningTarget);
                        SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlot(key, codePointLengthNode, codePointAtIndexNode, equalNode);
                        if (slot != null && isTypeNode.execute(inliningTarget, object)) {
                            SpecialMethodSlot.fixupSpecialMethodSlot(object, slot, value);
                        }
                    }
                } catch (CannotCastException e) {
                    // fall through; it cannot be a special method slot
                }
            }
        }

        @Specialization(guards = "isErrorCase(getDict, object, key)")
        static boolean doError(Object object, Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }
    }
}
