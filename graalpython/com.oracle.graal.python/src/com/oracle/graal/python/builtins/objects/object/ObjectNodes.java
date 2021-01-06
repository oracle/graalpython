/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_PICKLE_OBJECT_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.COPYREG_SLOTNAMES;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_A_NOT_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SLOTNAMES_SHOULD_BE_A_NOT_B;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NEWOBJ_EX__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NEWOBJ__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTNAMES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETNEWARGS_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_ELLIPSIS;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_BYTES;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_FROZENSET;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_TUPLE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_UNICODE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_NONE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_NOTIMPLEMENTED;
import static com.oracle.graal.python.runtime.object.IDUtils.getId;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithState;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.IDUtils;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ObjectNodes {

    @GenerateUncached
    abstract static class GetObjectIdNode extends Node {
        private static final HiddenKey OBJECT_ID = new HiddenKey("_id");

        public abstract long execute(Object self);

        protected static Assumption getSingleThreadedAssumption() {
            return PythonLanguage.getCurrent().singleThreadedAssumption;
        }

        protected static boolean isIDableObject(Object object) {
            return object instanceof PythonObject || object instanceof PythonAbstractNativeObject;
        }

        @Specialization(guards = "isIDableObject(self)", assumptions = "getSingleThreadedAssumption()")
        static long singleThreadedObject(Object self,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached WriteAttributeToDynamicObjectNode writeNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object objectId = readNode.execute(self, OBJECT_ID);
            if (objectId == PNone.NO_VALUE) {
                objectId = context.getNextObjectId();
                writeNode.execute(self, OBJECT_ID, objectId);
            }
            assert objectId instanceof Long : "internal object id hidden key must be a long at this point";
            return (long) objectId;
        }

        @Specialization(guards = "isIDableObject(self)", replaces = "singleThreadedObject")
        static long multiThreadedObject(Object self,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached WriteAttributeToDynamicObjectNode writeNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object objectId = readNode.execute(self, OBJECT_ID);
            if (objectId == PNone.NO_VALUE) {
                synchronized (self) {
                    objectId = readNode.execute(self, OBJECT_ID);
                    if (objectId == PNone.NO_VALUE) {
                        objectId = context.getNextObjectId();
                        writeNode.execute(self, OBJECT_ID, objectId);
                    }
                }
            }
            assert objectId instanceof Long : "internal object id hidden key must be a long at this point";
            return (long) objectId;
        }
    }

    /**
     * Implements the contract from {@code builtin_id}. All objects have their own unique id
     * computed as follows:
     *
     * <ul>
     * <li>{@link PythonObject}, {@link PythonAbstractNativeObject}: auto incremented <b>62 bit</b>
     * {@link Long} counter</li>
     * <li><i>Foreign objects</i>, {@link String}: auto incremented <b>62 bit</b> {@link Long}
     * counter</li>
     * <li>{@link Integer}, {@link Long}: the actual value if value fits in a <b>62 bit</b> unsigned
     * {@link Long}, else a <b>126 bit</b>
     * {@link com.oracle.graal.python.builtins.objects.ints.PInt} (long long id)</li>
     * <li>{@link Double}: the IEEE754 representation if it fits in a <b>63 bit</b> unsigned
     * {@link Long}, else a <b>127 bit</b>
     * {@link com.oracle.graal.python.builtins.objects.ints.PInt} (long long id)</li>
     * </ul>
     *
     * <br>
     * In addition the following types have predefined (reserved ids):
     * {@link PythonBuiltinClassType}, {@link PNone},
     * {@link com.oracle.graal.python.builtins.objects.PNotImplemented},
     * {@link com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis}
     *
     * <br>
     * Ids are reserved also for <b>empty</b>:
     * {@link com.oracle.graal.python.builtins.objects.bytes.PBytes},
     * {@link com.oracle.graal.python.builtins.objects.set.PFrozenSet}, {@link String} and
     * {@link com.oracle.graal.python.builtins.objects.tuple.PTuple}
     */
    @ImportStatic({PythonOptions.class, PGuards.class})
    @GenerateUncached
    public abstract static class GetIdNode extends Node {
        public abstract Object execute(Object self);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object id(PBytes self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsBuiltinClassProfile isBuiltin,
                        @CachedLibrary("self") PythonObjectLibrary pol) {
            if (isBuiltin.profileIsAnyBuiltinObject(self) && pol.length(self) == 0) {
                return ID_EMPTY_BYTES;
            }
            return getObjectIdNode.execute(self);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object id(PFrozenSet self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsBuiltinClassProfile isBuiltin,
                        @CachedLibrary("self") PythonObjectLibrary pol) {
            if (isBuiltin.profileIsAnyBuiltinObject(self) && pol.length(self) == 0) {
                return ID_EMPTY_FROZENSET;
            }
            return getObjectIdNode.execute(self);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object id(PTuple self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsBuiltinClassProfile isBuiltin,
                        @CachedLibrary("self") PythonObjectLibrary pol) {
            if (isBuiltin.profileIsAnyBuiltinObject(self) && pol.length(self) == 0) {
                return ID_EMPTY_TUPLE;
            }
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(@SuppressWarnings("unused") PEllipsis self) {
            return ID_ELLIPSIS;
        }

        @Specialization
        static Object id(@SuppressWarnings("unused") PNone self) {
            return ID_NONE;
        }

        @Specialization
        static Object id(@SuppressWarnings("unused") PNotImplemented self) {
            return ID_NOTIMPLEMENTED;
        }

        @Specialization
        static Object id(PythonBuiltinClassType self) {
            return getId(self);
        }

        @Specialization
        static Object id(PythonAbstractNativeObject self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(boolean self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            Object bool = self ? context.getCore().getTrue() : context.getCore().getFalse();
            return getObjectIdNode.execute(bool);
        }

        @Specialization
        static Object id(double self,
                        @Cached PythonObjectFactory factory) {
            return IDUtils.getId(self, factory);
        }

        @Specialization
        static Object id(PFloat self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(long self,
                        @Cached PythonObjectFactory factory) {
            return IDUtils.getId(self, factory);
        }

        @Specialization
        static Object id(PInt self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(int self) {
            return IDUtils.getId(self);
        }

        @Specialization
        static Object id(String self,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            if (self.length() == 0) {
                return ID_EMPTY_UNICODE;
            }
            return context.getNextStringId(self);
        }

        @Specialization
        static Object id(PString self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached StringNodes.StringMaterializeNode materializeNode) {
            return id(materializeNode.execute(self), context);
        }

        @Specialization
        static Object id(PythonObject self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization(guards = "pol.isForeignObject(self)", limit = "getCallSiteInlineCacheMaxDepth()")
        static Object idForeign(Object self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary pol) {
            return context.getNextObjectId(self);
        }
    }

    @GenerateUncached
    public abstract static class GetIdentityHashNode extends Node {
        public abstract int execute(Object object);

        @Specialization
        static int idHash(Object object,
                        @Cached GetIdNode getIdNode) {
            final Object id = getIdNode.execute(object);
            if (id instanceof Long) {
                return Long.hashCode((long) id);
            } else {
                assert id instanceof PInt;
                return Long.hashCode(((PInt) id).longValue());
            }
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class FastIsListSubClassNode extends Node {
        abstract boolean execute(VirtualFrame frame, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static boolean isList(VirtualFrame frame, PList object) {
            return true;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean isList(VirtualFrame frame, Object object,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached IsBuiltinClassProfile objProfile,
                        @CachedLibrary(value = "object") PythonObjectLibrary pol) {
            Object type = pol.getLazyPythonClass(object);
            if (objProfile.profileClass(type, PythonBuiltinClassType.PList)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PList);
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class FastIsTupleSubClassNode extends Node {
        abstract boolean execute(VirtualFrame frame, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static boolean isList(VirtualFrame frame, PTuple object) {
            return true;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean isList(VirtualFrame frame, Object object,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached IsBuiltinClassProfile objProfile,
                        @CachedLibrary(value = "object") PythonObjectLibrary pol) {
            Object type = pol.getLazyPythonClass(object);
            if (objProfile.profileClass(type, PythonBuiltinClassType.PDict)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PDict);
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class FastIsDictSubClassNode extends Node {
        abstract boolean execute(VirtualFrame frame, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static boolean isList(VirtualFrame frame, PDict object) {
            return true;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean isList(VirtualFrame frame, Object object,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached IsBuiltinClassProfile objProfile,
                        @CachedLibrary(value = "object") PythonObjectLibrary pol) {
            Object type = pol.getLazyPythonClass(object);
            if (objProfile.profileClass(type, PythonBuiltinClassType.PList)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PList);
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class GetNewArgsNode extends Node {
        public abstract Pair<Object, Object> execute(VirtualFrame frame, Object obj);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Pair<Object, Object> dispatch(VirtualFrame frame, Object obj,
                        @Cached GetNewArgsInternalNode getNewArgsInternalNode,
                        @CachedLibrary(value = "obj") PythonObjectLibrary pol) {
            Object getNewArgsExAttr = pol.lookupAttribute(obj, frame, __GETNEWARGS_EX__);
            Object getNewArgsAttr = pol.lookupAttribute(obj, frame, __GETNEWARGS__);
            return getNewArgsInternalNode.execute(frame, getNewArgsExAttr, getNewArgsAttr);
        }

        abstract static class GetNewArgsInternalNode extends PNodeWithState {
            public abstract Pair<Object, Object> execute(VirtualFrame frame, Object getNewArgsExAttr, Object getNewArgsAttr);

            @Specialization(guards = "!isNoValue(getNewArgsExAttr)")
            Pair<Object, Object> doNewArgsEx(VirtualFrame frame, Object getNewArgsExAttr, @SuppressWarnings("unused") Object getNewArgsAttr,
                            @Cached FastIsTupleSubClassNode isTupleSubClassNode,
                            @Cached FastIsDictSubClassNode isDictSubClassNode,
                            @Cached SequenceStorageNodes.GetItemNode getItemNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary pol) {
                Object newargs = pol.callObject(getNewArgsExAttr, frame);
                if (!isTupleSubClassNode.execute(frame, newargs)) {
                    throw raise(TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, __GETNEWARGS_EX__, "tuple", newargs);
                }
                int length = pol.length(newargs);
                if (length != 2) {
                    throw raise(ValueError, SHOULD_RETURN_A_NOT_B, __GETNEWARGS_EX__, "tuple of length 2", length);
                }

                SequenceStorage sequenceStorage = getSequenceStorageNode.execute(newargs);
                Object args = getItemNode.execute(frame, sequenceStorage, 0);
                Object kwargs = getItemNode.execute(frame, sequenceStorage, 1);

                if (!isTupleSubClassNode.execute(frame, args)) {
                    throw raise(TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "first item of the tuple returned by __getnewargs_ex__", "tuple", args);
                }
                if (!isDictSubClassNode.execute(frame, kwargs)) {
                    throw raise(TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "second item of the tuple returned by __getnewargs_ex__", "dict", kwargs);
                }

                return Pair.create(args, kwargs);
            }

            @Specialization(guards = "!isNoValue(getNewArgsAttr)", limit = "getCallSiteInlineCacheMaxDepth()")
            Pair<Object, Object> doNewArgs(VirtualFrame frame, @SuppressWarnings("unused") PNone getNewArgsExAttr, Object getNewArgsAttr,
                            @Cached FastIsTupleSubClassNode isTupleSubClassNode,
                            @CachedLibrary(value = "getNewArgsAttr") PythonObjectLibrary pol) {
                Object args = pol.callObject(getNewArgsAttr, frame);
                if (!isTupleSubClassNode.execute(frame, args)) {
                    throw raise(TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, __GETNEWARGS__, "tuple", args);
                }
                return Pair.create(args, PNone.NONE);
            }

            @Specialization
            Pair<Object, Object> doHasNeither(@SuppressWarnings("unused") PNone getNewArgsExAttr, @SuppressWarnings("unused") PNone getNewArgsAttr) {
                return Pair.create(PNone.NONE, PNone.NONE);
            }
        }

    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class GetSlotNamesNode extends Node {
        public abstract Object execute(VirtualFrame frame, Object cls, Object copyReg);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object dispatch(VirtualFrame frame, Object cls, Object copyReg,
                        @Cached GetSlotNamesInternalNode getSlotNamesInternalNode,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @CachedLibrary(value = "cls") PythonObjectLibrary pol,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hashLib) {
            Object slotNames = PNone.NO_VALUE;
            Object clsDict = pol.lookupAttribute(cls, frame, __DICT__);
            if (!PGuards.isNoValue(clsDict)) {
                HashingStorage hashingStorage = getHashingStorageNode.execute(frame, clsDict);
                Object item = hashLib.getItem(hashingStorage, __SLOTNAMES__);
                if (item != null) {
                    slotNames = item;
                }
            }
            return getSlotNamesInternalNode.execute(frame, cls, copyReg, slotNames);
        }

        abstract static class GetSlotNamesInternalNode extends PNodeWithState {
            public abstract Object execute(VirtualFrame frame, Object cls, Object copyReg, Object slotNames);

            @Specialization(guards = "!isNoValue(slotNames)")
            Object getSlotNames(VirtualFrame frame, Object cls, @SuppressWarnings("unused") Object copyReg, Object slotNames,
                            @Cached FastIsListSubClassNode isListSubClassNode) {
                Object names = slotNames;
                if (!PGuards.isNone(names) && !isListSubClassNode.execute(frame, names)) {
                    throw raise(TypeError, SLOTNAMES_SHOULD_BE_A_NOT_B, cls, "list or None", names);
                }
                return names;
            }

            @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
            Object getCopyRegSlotNames(VirtualFrame frame, Object cls, Object copyReg, @SuppressWarnings("unused") PNone slotNames,
                            @Cached FastIsListSubClassNode isListSubClassNode,
                            @CachedLibrary(value = "copyReg") PythonObjectLibrary pol) {
                Object names = pol.lookupAndCallRegularMethod(copyReg, frame, "_slotnames", cls);
                if (!PGuards.isNone(names) && !isListSubClassNode.execute(frame, names)) {
                    throw raise(TypeError, COPYREG_SLOTNAMES);
                }
                return names;
            }
        }

    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class GetStateNode extends Node {
        public abstract Object execute(VirtualFrame frame, Object obj, boolean required, Object copyReg);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object dispatch(VirtualFrame frame, Object obj, boolean required, Object copyReg,
                        @Cached GetStateInternalNode getStateInternalNode,
                        @CachedLibrary(value = "obj") PythonObjectLibrary pol) {
            Object getStateAttr = pol.lookupAttribute(obj, frame, __GETSTATE__);
            return getStateInternalNode.execute(frame, obj, required, copyReg, getStateAttr);
        }

        abstract static class GetStateInternalNode extends PNodeWithState {
            public abstract Object execute(VirtualFrame frame, Object obj, boolean required, Object copyReg, Object getStateAttr);

            @Specialization(guards = "!isNoValue(getStateAttr)", limit = "getCallSiteInlineCacheMaxDepth()")
            Object getState(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") boolean required, @SuppressWarnings("unused") Object copyReg, Object getStateAttr,
                            @CachedLibrary(value = "getStateAttr") PythonObjectLibrary pol) {
                return pol.callObject(getStateAttr, frame);
            }

            @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
            Object getStateFromSlots(VirtualFrame frame, Object obj, boolean required, Object copyReg, @SuppressWarnings("unused") PNone getStateAttr,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                            @Cached TypeNodes.GetItemsizeNode getItemsizeNode,
                            @Cached CastToJavaStringNode toJavaStringNode,
                            @Cached GetSlotNamesNode getSlotNamesNode,
                            @CachedLibrary(value = "obj") PythonObjectLibrary pol,
                            @CachedLibrary(limit = "1") HashingStorageLibrary hlib) {
                Object state;
                Object type = pol.getLazyPythonClass(obj);
                if (required && getItemsizeNode.execute(type) != 0) {
                    throw raise(TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
                }

                state = pol.lookupAttribute(obj, frame, __DICT__);
                if (PGuards.isNoValue(state)) {
                    state = PNone.NONE;
                }

                // we skip the assert that type is a type since we are certain of that in this case
                Object slotnames = getSlotNamesNode.execute(frame, type, copyReg);
                // TODO check basicsize typeobject.c:4213

                if (!PGuards.isNone(slotnames)) {
                    SequenceStorage sequenceStorage = getSequenceStorageNode.execute(slotnames);
                    Object[] names = toArrayNode.execute(sequenceStorage);
                    if (names.length > 0) {
                        HashingStorage slotsStorage = EconomicMapStorage.create(names.length);
                        boolean haveSlots = false;
                        for (Object o : names) {
                            try {
                                String name = toJavaStringNode.execute(o);
                                Object value = pol.lookupAttribute(obj, frame, name);
                                if (!PGuards.isNoValue(value)) {
                                    hlib.setItem(slotsStorage, name, value);
                                    haveSlots = true;
                                }
                            } catch (CannotCastException cce) {
                                throw raise(TypeError, ATTR_NAME_MUST_BE_STRING, o);
                            }
                        }
                        if (haveSlots) {
                            state = factory().createTuple(new Object[]{state, factory().createDict(slotsStorage)});
                        }
                    }
                }

                return state;
            }
        }
    }

    abstract static class CommonReduceNode extends PNodeWithState {
        protected static final String MOD_COPYREG = "copyreg";

        public abstract Object execute(VirtualFrame frame, Object obj, int proto);

        static ImportNode.ImportExpression createImportCopyReg() {
            return ImportNode.createAsExpression(MOD_COPYREG);
        }

        @Specialization(guards = "proto >= 2")
        public Object reduceNewObj(VirtualFrame frame, Object obj, @SuppressWarnings("unused") int proto,
                        @Cached("createImportCopyReg()") ImportNode.ImportExpression importNode,
                        @Cached ConditionProfile newObjProfile,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached GetNewArgsNode getNewArgsNode,
                        @Cached GetStateNode getStateNode,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary pol) {
            Object cls = pol.lookupAttribute(obj, frame, __CLASS__);
            if (pol.lookupAttribute(cls, frame, __NEW__) == PNone.NO_VALUE) {
                throw raise(TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
            }

            Pair<Object, Object> rv = getNewArgsNode.execute(frame, obj);
            Object args = rv.getLeft();
            Object kwargs = rv.getRight();
            Object newobj, newargs;

            Object copyReg = importNode.execute(frame);

            boolean hasargs = args != PNone.NONE;

            if (newObjProfile.profile(kwargs == PNone.NONE || pol.length(kwargs) == 0)) {
                newobj = pol.lookupAttribute(copyReg, frame, __NEWOBJ__);
                Object[] newargsVals;
                if (hasArgsProfile.profile(hasargs)) {
                    SequenceStorage sequenceStorage = getSequenceStorageNode.execute(args);
                    Object[] vals = toArrayNode.execute(sequenceStorage);
                    newargsVals = new Object[vals.length + 1];
                    newargsVals[0] = cls;
                    System.arraycopy(vals, 0, newargsVals, 1, vals.length);
                } else {
                    newargsVals = new Object[]{cls};
                }
                newargs = factory().createTuple(newargsVals);
            } else if (hasArgsProfile.profile(hasargs)) {
                newobj = pol.lookupAttribute(copyReg, frame, __NEWOBJ_EX__);
                newargs = factory().createTuple(new Object[]{cls, args, kwargs});
            } else {
                throw raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
            }

            boolean objIsList = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PList);
            boolean objIsDict = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PDict);
            boolean required = !hasargs && !objIsDict && !objIsList;

            Object state = getStateNode.execute(frame, obj, required, copyReg);
            Object listitems = objIsList ? pol.getIterator(obj) : PNone.NONE;
            Object dictitems = objIsDict ? pol.getIterator(pol.lookupAndCallRegularMethod(obj, frame, ITEMS)) : PNone.NONE;

            return factory().createTuple(new Object[]{newobj, newargs, state, listitems, dictitems});
        }

        @Specialization(guards = "proto < 2")
        public Object reduceCopyReg(VirtualFrame frame, Object obj, int proto,
                        @Cached("createImportCopyReg()") ImportNode.ImportExpression importNode,
                        @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            Object copyReg = importNode.execute(frame);
            return pol.lookupAndCallRegularMethod(copyReg, frame, "_reduce_ex", obj, proto);
        }
    }
}
