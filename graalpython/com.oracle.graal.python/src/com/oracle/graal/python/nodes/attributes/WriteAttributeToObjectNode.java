/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectNotTypeNodeGen;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectTpDictNodeGen;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@ImportStatic(PythonOptions.class)
public abstract class WriteAttributeToObjectNode extends ObjectAttributeNode {

    public abstract boolean execute(Object primary, Object key, Object value);

    public static WriteAttributeToObjectNode create() {
        return WriteAttributeToObjectNotTypeNodeGen.create();
    }

    public static WriteAttributeToObjectNode createForceType() {
        return WriteAttributeToObjectTpDictNodeGen.create();
    }

    public static WriteAttributeToObjectNode getUncached() {
        return WriteAttributeToObjectNotTypeNodeGen.getUncached();
    }

    protected static boolean isAttrWritable(PythonObject self, Object key) {
        if (isHiddenKey(key)) {
            return true;
        }
        return (self.getShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0;
    }

    private static void handlePossiblePythonClass(HandlePythonClassProfiles profiles, PythonObject object, Object keyObj, Object value) {
        if (PythonManagedClass.isInstance(object)) {
            profiles.isManagedClass.enter();
            handlePythonClass(profiles, PythonManagedClass.cast(object), keyObj, value);
        }
    }

    private static void handlePythonClass(HandlePythonClassProfiles profiles, PythonManagedClass object, Object keyObj, Object value) {
        String key = profiles.castKey(keyObj);
        if (key == null) {
            return;
        }
        object.invalidateFinalAttribute(key);
        // Note: we need to handle builtin classes here, because during initialization we are
        // setting attributes of some builtin types to Python functions (when given builtin method
        // is not intrinsified in Java)
        if (SpecialMethodSlot.canBeSpecial(key)) {
            profiles.isSpecialKey.enter();
            SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlot(key);
            if (slot != null) {
                SpecialMethodSlot.fixupSpecialMethodSlot(object, slot, value);
            }
        }
    }

    // write to the DynamicObject
    @Specialization(guards = {
                    "isAttrWritable(object, key)",
                    "isHiddenKey(key) || !lib.hasDict(object)"
    }, limit = "1")
    static boolean writeToDynamicStorage(PythonObject object, Object key, Object value,
                    @CachedLibrary("object") @SuppressWarnings("unused") PythonObjectLibrary lib,
                    @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                    @Exclusive @Cached HandlePythonClassProfiles handlePythonClassProfiles) {
        try {
            return writeAttributeToDynamicObjectNode.execute(object.getStorage(), key, value);
        } finally {
            handlePossiblePythonClass(handlePythonClassProfiles, object, key, value);
        }
    }

    // write to the dict
    @Specialization(guards = {
                    "!isHiddenKey(key)",
                    "lib.hasDict(object)"
    }, limit = "1")
    static boolean writeToDict(PythonObject object, Object key, Object value,
                    @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached BranchProfile updateStorage,
                    @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                    @Exclusive @Cached HandlePythonClassProfiles handlePythonClassProfiles) {
        try {
            return writeToDict(lib.getDict(object), key, value, updateStorage, hlib);
        } finally {
            handlePossiblePythonClass(handlePythonClassProfiles, object, key, value);
        }
    }

    static boolean writeToDict(PDict dict, Object key, Object value,
                    BranchProfile updateStorage,
                    HashingStorageLibrary hlib) {
        assert dict != null;
        HashingStorage dictStorage = dict.getDictStorage();
        HashingStorage hashingStorage = hlib.setItem(dictStorage, key, value);
        if (dictStorage != hashingStorage) {
            updateStorage.enter();
            dict.setDictStorage(hashingStorage);
        }
        return true;
    }

    @Specialization(guards = "isErrorCase(lib, object, key)")
    static boolean doError(Object object, Object key, @SuppressWarnings("unused") Object value,
                    @CachedLibrary(limit = "1") @SuppressWarnings("unused") PythonObjectLibrary lib,
                    @Exclusive @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
    }

    @Specialization
    static boolean doPBCT(PythonBuiltinClassType object, Object key, Object value,
                    @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                    @Cached WriteAttributeToObjectNode recursive) {
        return recursive.execute(contextRef.get().getCore().lookupType(object), key, value);
    }

    protected static boolean isErrorCase(PythonObjectLibrary lib, Object object, Object key) {
        if (object instanceof PythonObject) {
            PythonObject self = (PythonObject) object;
            if (isAttrWritable(self, key) && (isHiddenKey(key) || !lib.hasDict(self))) {
                return false;
            }
            if (!isHiddenKey(key) && lib.hasDict(self)) {
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
    protected abstract static class WriteAttributeToObjectNotTypeNode extends WriteAttributeToObjectNode {
        @Specialization(guards = {"!isHiddenKey(key)"}, limit = "1")
        static boolean writeNativeObject(PythonAbstractNativeObject object, Object key, Object value,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @Cached BranchProfile updateStorage,
                        @Cached PRaiseNode raiseNode) {
            /*
             * The dict of native objects that stores the object attributes is located at 'objectPtr
             * + Py_TYPE(objectPtr)->tp_dictoffset'. 'PythonObjectLibrary.getDict' will exactly load
             * the dict from there.
             */
            PDict dict = lib.getDict(object);
            if (dict != null) {
                return writeToDict(dict, key, value, updateStorage, hlib);
            }
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }
    }

    protected abstract static class WriteAttributeToObjectTpDictNode extends WriteAttributeToObjectNode {
        @Child private IsTypeNode isTypeNode;
        @Child private CastToJavaStringNode castKeyNode;

        @Specialization(guards = "!isHiddenKey(keyObj)")
        boolean writeNativeClass(PythonAbstractNativeObject object, Object keyObj, Object value,
                        @Cached GetTypeMemberNode getNativeDict,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @Cached BranchProfile updateStorage,
                        @Cached BranchProfile canBeSpecialSlot,
                        @Cached PRaiseNode raiseNode) {
            try {
                /*
                 * For native types, the type attributes are stored in a dict that is located in
                 * 'typePtr->tp_dict'. So, this is different to a native object (that is not a type)
                 * and we need to load the dict differently. We must not use
                 * 'PythonObjectLibrary.getDict' here but read member 'tp_dict'.
                 */
                Object dict = getNativeDict.execute(object, NativeMember.TP_DICT);
                if (dict instanceof PDict) {
                    return writeToDict((PDict) dict, keyObj, value, updateStorage, hlib);
                }
                throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, keyObj);
            } finally {
                String key = castKey(keyObj);
                if (SpecialMethodSlot.canBeSpecial(key)) {
                    canBeSpecialSlot.enter();
                    SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlot(key);
                    if (slot != null && ensureIsTypeNode().execute(object)) {
                        SpecialMethodSlot.fixupSpecialMethodSlot(object, slot, value);
                    }
                }
            }
        }

        private IsTypeNode ensureIsTypeNode() {
            if (isTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTypeNode = insert(IsTypeNode.create());
            }
            return isTypeNode;
        }

        private String castKey(Object keyObj) {
            if (castKeyNode == null) {
                if (keyObj instanceof String) {
                    return (String) keyObj;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    castKeyNode = insert(CastToJavaStringNode.create());
                }
            }
            return castKeyNode.execute(keyObj);
        }
    }

    protected static final class HandlePythonClassProfiles extends Node {
        private static final HandlePythonClassProfiles UNCACHED = new HandlePythonClassProfiles(BranchProfile.getUncached(), BranchProfile.getUncached(), BranchProfile.getUncached(),
                        CastToJavaStringNode.getUncached());
        final BranchProfile isManagedClass;
        final BranchProfile isUserClass;
        final BranchProfile isSpecialKey;
        @Child CastToJavaStringNode castKeyNode;

        public HandlePythonClassProfiles(BranchProfile isManagedClass, BranchProfile isUserClass, BranchProfile isSpecialKey, CastToJavaStringNode castKeyNode) {
            this.isManagedClass = isManagedClass;
            this.isUserClass = isUserClass;
            this.isSpecialKey = isSpecialKey;
            this.castKeyNode = castKeyNode;
        }

        public static HandlePythonClassProfiles create() {
            return new HandlePythonClassProfiles(BranchProfile.create(), BranchProfile.create(), BranchProfile.create(), null);
        }

        public static HandlePythonClassProfiles getUncached() {
            return UNCACHED;
        }

        String castKey(Object key) {
            if (castKeyNode == null) {
                // fast-path w/o node for two most common situations
                if (key instanceof String) {
                    return (String) key;
                } else if (isHiddenKey(key)) {
                    return null;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castKeyNode = insert(CastToJavaStringNode.create());
            }
            try {
                return castKeyNode.execute(key);
            } catch (CannotCastException ex) {
                return null;
            }
        }
    }
}
