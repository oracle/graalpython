/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_CHECK_BASICSIZE_FOR_GETSTATE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_COPYREG;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_PICKLE_OBJECT_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_A_NOT_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NEWOBJ_EX__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NEWOBJ__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SLOTNAMES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETNEWARGS_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_ELLIPSIS;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_BYTES;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_FROZENSET;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_TUPLE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_EMPTY_UNICODE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_NONE;
import static com.oracle.graal.python.runtime.object.IDUtils.ID_NOTIMPLEMENTED;
import static com.oracle.graal.python.runtime.object.IDUtils.getId;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.ObjectNodesFactory.GetFullyQualifiedNameNodeGen;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.CallSlotDescrSet;
import com.oracle.graal.python.lib.PyDictCheckNode;
import com.oracle.graal.python.lib.PyImportImport;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttrO;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsAnyBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.IDUtils;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class ObjectNodes {

    public static final TruffleString T__REDUCE_EX = tsLiteral("_reduce_ex");
    public static final TruffleString T__SLOTNAMES = tsLiteral("_slotnames");

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class GetObjectIdNode extends Node {

        public abstract long execute(Node inliningTarget, Object self);

        protected static Assumption getSingleThreadedAssumption(Node node) {
            return PythonLanguage.get(node).singleThreadedAssumption;
        }

        protected static boolean isIDableObject(Object object) {
            return object instanceof PythonObject || object instanceof PythonAbstractNativeObject;
        }

        @Specialization(guards = "isIDableObject(self)", assumptions = "getSingleThreadedAssumption(readNode)")
        static long singleThreadedObject(Node inliningTarget, PythonAbstractObject self,
                        @Shared @Cached HiddenAttr.ReadNode readNode,
                        @Shared @Cached HiddenAttr.WriteNode writeNode) {
            Object objectId = readNode.execute(inliningTarget, self, HiddenAttr.OBJECT_ID, null);
            if (objectId == null) {
                objectId = PythonContext.get(readNode).getNextObjectId();
                writeNode.execute(inliningTarget, self, HiddenAttr.OBJECT_ID, objectId);
            }
            assert objectId instanceof Long : "internal object id hidden key must be a long at this point";
            return (long) objectId;
        }

        @Specialization(guards = "isIDableObject(self)", replaces = "singleThreadedObject")
        static long multiThreadedObject(Node inliningTarget, PythonAbstractObject self,
                        @Shared @Cached HiddenAttr.ReadNode readNode,
                        @Shared @Cached HiddenAttr.WriteNode writeNode) {
            Object objectId = readNode.execute(inliningTarget, self, HiddenAttr.OBJECT_ID, null);
            if (objectId == null) {
                synchronized (self) {
                    objectId = readNode.execute(inliningTarget, self, HiddenAttr.OBJECT_ID, null);
                    if (objectId == null) {
                        objectId = PythonContext.get(readNode).getNextObjectId();
                        writeNode.execute(inliningTarget, self, HiddenAttr.OBJECT_ID, objectId);
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
     * {@link PythonBuiltinClassType} and {@link PythonBuiltinClass}, {@link PNone},
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
    @GenerateInline(false)       // footprint reduction 92 -> 73
    public abstract static class GetIdNode extends PNodeWithContext {
        public abstract Object execute(Object self);

        @Specialization
        static Object id(PBytes self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Shared @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Shared @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, inliningTarget, self) == 0) {
                return ID_EMPTY_BYTES;
            }
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        static Object id(PFrozenSet self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Shared @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Shared @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, inliningTarget, self) == 0) {
                return ID_EMPTY_FROZENSET;
            }
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        static Object id(PTuple self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Shared @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Shared @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, inliningTarget, self) == 0) {
                return ID_EMPTY_TUPLE;
            }
            return getObjectIdNode.execute(inliningTarget, self);
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
        static Object id(PythonBuiltinClass self) {
            return getId(self.getType());
        }

        @Specialization
        static Object id(PythonAbstractNativeObject self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        static Object id(boolean self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            PythonContext context = PythonContext.get(getObjectIdNode);
            Object bool = self ? context.getTrue() : context.getFalse();
            return getObjectIdNode.execute(inliningTarget, bool);
        }

        @Specialization
        static Object id(double self,
                        @Bind PythonLanguage language) {
            return IDUtils.getId(language, self);
        }

        @Specialization
        static Object id(PFloat self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        static Object id(long self,
                        @Bind PythonLanguage language) {
            return IDUtils.getId(language, self);
        }

        @Specialization
        static Object id(PInt self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        static Object id(int self) {
            return IDUtils.getId(self);
        }

        @Specialization
        static Object id(TruffleString self,
                        @Bind Node inliningTarget) {
            if (self.isEmpty()) {
                return ID_EMPTY_UNICODE;
            }
            return PythonContext.get(inliningTarget).getNextStringId(self);
        }

        @Specialization
        static Object id(PString self,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode,
                        @Cached StringNodes.StringMaterializeNode materializeNode) {
            if (isInternedStringNode.execute(inliningTarget, self)) {
                return id(materializeNode.execute(inliningTarget, self), inliningTarget);
            }
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization
        Object id(PythonNativeVoidPtr self) {
            return self.getNativePointer();
        }

        @Specialization
        Object id(PCell self) {
            return PythonContext.get(this).getNextObjectId(self);
        }

        protected static boolean isDefaultCase(PythonObject object) {
            return !(object instanceof PBytes ||
                            object instanceof PFrozenSet ||
                            object instanceof PTuple ||
                            object instanceof PInt ||
                            object instanceof PFloat ||
                            object instanceof PString ||
                            object instanceof PythonBuiltinClass);
        }

        @Specialization(guards = "isDefaultCase(self)")
        static Object id(PythonObject self,
                        @Bind Node inliningTarget,
                        @Shared @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "isForeignObjectNode.execute(inliningTarget, self)", limit = "1")
        static Object idForeign(Object self,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
            return PythonContext.get(isForeignObjectNode).getNextObjectId(self);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetIdentityHashNode extends Node {
        public abstract int execute(Node inliningTarget, Object object);

        @Specialization
        static int idHash(Object object,
                        @Cached(inline = false) GetIdNode getIdNode) {
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
    @GenerateInline(false)       // footprint reduction 64 -> 45
    abstract static class GetNewArgsNode extends Node {
        public abstract Pair<Object, Object> execute(VirtualFrame frame, Object obj);

        @Specialization
        static Pair<Object, Object> dispatch(VirtualFrame frame, Object obj,
                        @Bind Node inliningTarget,
                        @Cached GetNewArgsInternalNode getNewArgsInternalNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            Object getNewArgsExAttr = lookupAttr.execute(frame, inliningTarget, obj, T___GETNEWARGS_EX__);
            Object getNewArgsAttr = lookupAttr.execute(frame, inliningTarget, obj, T___GETNEWARGS__);
            return getNewArgsInternalNode.execute(frame, getNewArgsExAttr, getNewArgsAttr);
        }

        @ImportStatic(PGuards.class)
        @GenerateInline(false) // 32 -> 13
        abstract static class GetNewArgsInternalNode extends Node {
            public abstract Pair<Object, Object> execute(VirtualFrame frame, Object getNewArgsExAttr, Object getNewArgsAttr);

            @Specialization(guards = "!isNoValue(getNewArgsExAttr)")
            static Pair<Object, Object> doNewArgsEx(VirtualFrame frame, Object getNewArgsExAttr, @SuppressWarnings("unused") Object getNewArgsAttr,
                            @Bind Node inliningTarget,
                            @Exclusive @Cached CallNode callNode,
                            @Exclusive @Cached PyTupleCheckNode tupleCheckNode,
                            @Cached PyDictCheckNode isDictSubClassNode,
                            @Cached SequenceStorageNodes.GetItemNode getItemNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached PyObjectSizeNode sizeNode,
                            @Exclusive @Cached PRaiseNode raiseNode) {
                Object newargs = callNode.execute(frame, getNewArgsExAttr);
                if (!tupleCheckNode.execute(inliningTarget, newargs)) {
                    throw raiseNode.raise(inliningTarget, TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, T___GETNEWARGS_EX__, "tuple", newargs);
                }
                int length = sizeNode.execute(frame, inliningTarget, newargs);
                if (length != 2) {
                    throw raiseNode.raise(inliningTarget, ValueError, SHOULD_RETURN_A_NOT_B, T___GETNEWARGS_EX__, "tuple of length 2", length);
                }

                SequenceStorage sequenceStorage = getSequenceStorageNode.execute(inliningTarget, newargs);
                Object args = getItemNode.execute(sequenceStorage, 0);
                Object kwargs = getItemNode.execute(sequenceStorage, 1);

                if (!tupleCheckNode.execute(inliningTarget, args)) {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "first item of the tuple returned by __getnewargs_ex__", "tuple", args);
                }
                if (!isDictSubClassNode.execute(inliningTarget, kwargs)) {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "second item of the tuple returned by __getnewargs_ex__", "dict", kwargs);
                }

                return Pair.create(args, kwargs);
            }

            @Specialization(guards = "!isNoValue(getNewArgsAttr)")
            static Pair<Object, Object> doNewArgs(VirtualFrame frame, @SuppressWarnings("unused") PNone getNewArgsExAttr, Object getNewArgsAttr,
                            @Bind Node inliningTarget,
                            @Exclusive @Cached CallNode callNode,
                            @Exclusive @Cached PyTupleCheckNode tupleCheckNode,
                            @Exclusive @Cached PRaiseNode raiseNode) {
                Object args = callNode.execute(frame, getNewArgsAttr);
                if (!tupleCheckNode.execute(inliningTarget, args)) {
                    throw raiseNode.raise(inliningTarget, TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, T___GETNEWARGS__, "tuple", args);
                }
                return Pair.create(args, PNone.NONE);
            }

            @Specialization
            static Pair<Object, Object> doHasNeither(@SuppressWarnings("unused") PNone getNewArgsExAttr, @SuppressWarnings("unused") PNone getNewArgsAttr) {
                return Pair.create(PNone.NONE, PNone.NONE);
            }
        }

    }

    // typeobject.c:_PyType_GetSlotNames but with copied array return
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetSlotNamesNode extends Node {

        public abstract Object[] execute(VirtualFrame frame, Node inliningTarget, Object type);

        @Specialization
        static Object[] getstate(VirtualFrame frame, Node inliningTarget, Object type,
                        @Cached(value = "createForceType()", inline = false) ReadAttributeFromObjectNode read,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                        @Cached PyImportImport importNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PRaiseNode raiseNode) {
            Object slotnames = read.execute(type, T___SLOTNAMES__);
            boolean hadCachedSlotnames = false;
            if (slotnames != PNone.NO_VALUE) {
                hadCachedSlotnames = true;
            } else {
                Object copyreg = importNode.execute(frame, inliningTarget, T_COPYREG);
                slotnames = callMethod.execute(frame, inliningTarget, copyreg, T__SLOTNAMES, type);
            }
            if (slotnames instanceof PList slotnamesList) {
                return toArrayNode.execute(inliningTarget, slotnamesList.getSequenceStorage());
            } else if (slotnames == PNone.NONE) {
                return EMPTY_OBJECT_ARRAY;
            } else if (hadCachedSlotnames) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.COPYREG_SLOTNAMES_DIDN_T_RETURN_A_LIST_OR_NONE);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_SLOTNAMES_SHOULD_BE_A_LIST_OR_NONE_NOT_P, type, slotnames);
            }
        }
    }

    // typeobject.c:object_getstate_default
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ObjectGetStateDefaultNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object obj, boolean required);

        @Specialization
        static Object getstate(VirtualFrame frame, Node inliningTarget, Object obj, boolean required,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetItemSizeNode getItemsizeNode,
                        @Cached GetSlotNamesNode getSlotNamesNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectLookupAttrO lookupAttr,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached CheckBasesizeForGetState checkBasesize,
                        @Cached PRaiseNode raiseNode) {
            Object state;
            Object type = getClassNode.execute(inliningTarget, obj);
            if (required && getItemsizeNode.execute(inliningTarget, type) != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
            }

            Object dict = lookupAttr.execute(frame, inliningTarget, obj, T___DICT__);
            if (dict != PNone.NO_VALUE && sizeNode.execute(frame, inliningTarget, dict) > 0) {
                state = dict;
            } else {
                state = PNone.NONE;
            }

            Object[] slotnames = getSlotNamesNode.execute(frame, inliningTarget, type);

            if (required && !checkBasesize.execute(inliningTarget, obj, type, slotnames.length)) {
                throw raiseNode.raise(inliningTarget, TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
            }

            if (slotnames.length > 0) {
                HashingStorage slotsStorage = EconomicMapStorage.create(slotnames.length);
                boolean haveSlots = false;
                for (Object o : slotnames) {
                    Object value = lookupAttr.execute(frame, inliningTarget, obj, o);
                    if (value != PNone.NO_VALUE) {
                        slotsStorage = setHashingStorageItem.execute(frame, inliningTarget, slotsStorage, o, value);
                        haveSlots = true;
                    }
                }
                if (haveSlots) {
                    /*
                     * If we found some slot attributes, pack them in a tuple along the original
                     * attribute dictionary.
                     */
                    PythonLanguage language = PythonLanguage.get(inliningTarget);
                    PDict slotsState = PFactory.createDict(language, slotsStorage);
                    state = PFactory.createTuple(language, new Object[]{state, slotsState});
                }
            }

            return state;
        }
    }

    // typeobject.c:object_getstate
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ObjectGetStateNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object obj, boolean required);

        @Specialization
        static Object get(VirtualFrame frame, Node inliningTarget, Object self, boolean required,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached ObjectGetStateDefaultNode getStateDefaultNode,
                        @Cached(inline = false) CallNode callNode) {
            Object getstate = getAttr.execute(frame, inliningTarget, self, T___GETSTATE__);
            if (getstate instanceof PBuiltinMethod getstateMethod &&
                            getstateMethod.getSelf() == self &&
                            getstateMethod.getFunction() instanceof PBuiltinFunction getstateFunction &&
                            getstateFunction.getBuiltinNodeFactory() instanceof ObjectBuiltinsFactory.GetStateNodeFactory) {
                return getStateDefaultNode.execute(frame, inliningTarget, self, required);
            }
            return callNode.execute(frame, getstate);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CheckBasesizeForGetState extends Node {
        public abstract boolean execute(Node inliningTarget, Object obj, Object type, int slotNum);

        @Specialization
        static boolean doNative(@SuppressWarnings("unused") PythonAbstractNativeObject obj, Object type, int slotNum,
                        @Cached(inline = false) PythonToNativeNode toSulongNode,
                        @Cached(inline = false) CExtNodes.PCallCapiFunction callCapiFunction) {
            Object result = callCapiFunction.call(FUN_CHECK_BASICSIZE_FOR_GETSTATE, toSulongNode.execute(type), slotNum);
            return (int) result == 0;
        }

        @Fallback
        static boolean doManaged(Object obj, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") int slotNum) {
            /*
             * CPython checks the type's basesize against the basesize of the 'object' type,
             * effectively testing that the object doesn't have any C-level fields. Since we don't
             * have basesize for managed types, we approximate the check by checking that the
             * object's Java type is PythonObject, assuming that subclasses would have Java fields
             * that would correspond to C fields.
             *
             * See: typeobject.c:_PyObject_GetState
             */
            return obj.getClass() == PythonObject.class;
        }
    }

    // typeobject.c:_common_reduce
    @GenerateInline
    @GenerateCached(false)
    abstract static class CommonReduceNode extends PNodeWithContext {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object obj, int proto);

        @Specialization(guards = "proto >= 2")
        static Object reduceNewObj(VirtualFrame frame, Node inliningTarget, Object obj, @SuppressWarnings("unused") int proto,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Exclusive @Cached PyImportImport importNode,
                        @Cached InlinedConditionProfile newObjProfile,
                        @Cached InlinedConditionProfile hasArgsProfile,
                        @Cached(inline = false) GetNewArgsNode getNewArgsNode,
                        @Cached ObjectGetStateNode getStateNode,
                        @Cached(inline = false) BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectGetIter getIter,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            Object cls = getClassNode.execute(inliningTarget, obj);
            TpSlots slots = getSlots.execute(inliningTarget, cls);
            if (slots.tp_new() == null) {
                throw raiseNode.raise(inliningTarget, TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
            }

            Pair<Object, Object> rv = getNewArgsNode.execute(frame, obj);
            Object args = rv.getLeft();
            Object kwargs = rv.getRight();
            Object newobj, newargs;

            Object copyReg = importNode.execute(frame, inliningTarget, T_COPYREG);

            boolean hasargs = args != PNone.NONE;

            if (newObjProfile.profile(inliningTarget, kwargs == PNone.NONE || sizeNode.execute(frame, inliningTarget, kwargs) == 0)) {
                newobj = lookupAttr.execute(frame, inliningTarget, copyReg, T___NEWOBJ__);
                Object[] newargsVals;
                if (hasArgsProfile.profile(inliningTarget, hasargs)) {
                    SequenceStorage sequenceStorage = getSequenceStorageNode.execute(inliningTarget, args);
                    Object[] vals = toArrayNode.execute(inliningTarget, sequenceStorage);
                    newargsVals = new Object[vals.length + 1];
                    newargsVals[0] = cls;
                    System.arraycopy(vals, 0, newargsVals, 1, vals.length);
                } else {
                    newargsVals = new Object[]{cls};
                }
                newargs = PFactory.createTuple(language, newargsVals);
            } else if (hasArgsProfile.profile(inliningTarget, hasargs)) {
                newobj = lookupAttr.execute(frame, inliningTarget, copyReg, T___NEWOBJ_EX__);
                newargs = PFactory.createTuple(language, new Object[]{cls, args, kwargs});
            } else {
                throw raiseNode.raiseBadInternalCall(inliningTarget);
            }

            boolean objIsList = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PList);
            boolean objIsDict = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PDict);
            boolean required = !hasargs && !objIsDict && !objIsList;

            Object state = getStateNode.execute(frame, inliningTarget, obj, required);
            Object listitems = objIsList ? getIter.execute(frame, inliningTarget, obj) : PNone.NONE;
            Object dictitems = objIsDict ? getIter.execute(frame, inliningTarget, callMethod.execute(frame, inliningTarget, obj, T_ITEMS)) : PNone.NONE;

            return PFactory.createTuple(language, new Object[]{newobj, newargs, state, listitems, dictitems});
        }

        @Specialization(guards = "proto < 2")
        static Object reduceCopyReg(VirtualFrame frame, Node inliningTarget, Object obj, int proto,
                        @Exclusive @Cached PyImportImport importNode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethod) {
            Object copyReg = importNode.execute(frame, inliningTarget, T_COPYREG);
            return callMethod.execute(frame, inliningTarget, copyReg, T__REDUCE_EX, obj, proto);
        }
    }

    /**
     * Returns the fully qualified name of a class.
     *
     * The fully qualified name includes the name of the module (unless it is the
     * {@link BuiltinNames#J_BUILTINS} module).
     */
    @GenerateUncached
    @ImportStatic(SpecialAttributeNames.class)
    @GenerateInline(false)       // footprint reduction 76 -> 57
    public abstract static class GetFullyQualifiedNameNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Object cls);

        @Specialization
        static TruffleString get(VirtualFrame frame, Object cls,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CastToTruffleStringNode cast,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object moduleNameObject = lookupAttr.execute(frame, inliningTarget, cls, T___MODULE__);
            Object qualNameObject = lookupAttr.execute(frame, inliningTarget, cls, T___QUALNAME__);
            if (qualNameObject == PNone.NO_VALUE) {
                return StringLiterals.T_VALUE_UNKNOWN;
            }
            TruffleString qualName = cast.execute(inliningTarget, qualNameObject);
            if (moduleNameObject == PNone.NO_VALUE) {
                return qualName;
            }
            TruffleString moduleName = cast.execute(inliningTarget, moduleNameObject);
            if (equalNode.execute(moduleName, BuiltinNames.T_BUILTINS, TS_ENCODING)) {
                return qualName;
            }
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, moduleName);
            appendStringNode.execute(sb, T_DOT);
            appendStringNode.execute(sb, qualName);
            return toStringNode.execute(sb);
        }

        @NeverDefault
        public static GetFullyQualifiedNameNode create() {
            return GetFullyQualifiedNameNodeGen.create();
        }
    }

    /**
     * Returns the fully qualified name of the class of an object.
     *
     * The fully qualified name includes the name of the module (unless it is the
     * {@link BuiltinNames#T_BUILTINS} module).
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class GetFullyQualifiedClassNameNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Node inliningTarget, Object self);

        @Specialization
        static TruffleString get(VirtualFrame frame, Node inliningTarget, Object self,
                        @Cached GetClassNode getClass,
                        @Cached(inline = false) GetFullyQualifiedNameNode getFullyQualifiedNameNode) {
            return getFullyQualifiedNameNode.execute(frame, getClass.execute(inliningTarget, self));
        }
    }

    /**
     * Default repr for objects that don't override {@code __repr__}
     */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    public abstract static class DefaultObjectReprNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Node inliningTarget, Object object);

        public final TruffleString executeCached(Frame frame, Object object) {
            return execute(frame, this, object);
        }

        @Specialization
        static TruffleString repr(VirtualFrame frame, Node inliningTarget, Object self,
                        @Cached GetFullyQualifiedClassNameNode getFullyQualifiedClassNameNode,
                        @Cached(inline = false) SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString fqcn = getFullyQualifiedClassNameNode.execute(frame, inliningTarget, self);
            return simpleTruffleStringFormatNode.format("<%s object at 0x%s>", fqcn, PythonAbstractNativeObject.systemHashCodeAsHexString(self));
        }

        @NeverDefault
        public static DefaultObjectReprNode create() {
            return ObjectNodesFactory.DefaultObjectReprNodeGen.create();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GenericSetAttrNode extends Node {
        /**
         * If {@code value} argument may be {@link PNone#NO_VALUE}, which indicates that we want to
         * delete the attribute.
         */
        public abstract void execute(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key, Object value, WriteAttributeToObjectNode writeNode);

        public abstract void execute(Node inliningTarget, VirtualFrame frame, Object object, Object key, Object value, WriteAttributeToObjectNode writeNode);

        @Specialization
        static void doStringKey(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key, Object value, WriteAttributeToObjectNode writeNode,
                        @SuppressWarnings("unused") @Shared @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached InlinedConditionProfile hasDescriptor,
                        @Shared @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Shared @Cached CallSlotDescrSet callSetNode,
                        @Shared @Cached(inline = false) LookupAttributeInMRONode.Dynamic getExisting,
                        @Shared @Cached(inline = false) ReadAttributeFromObjectNode attrRead,
                        @Shared @Cached InlinedBranchProfile deleteNonExistingBranchProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            setAttr(inliningTarget, frame, object, key, value, writeNode, getClassNode, hasDescriptor,
                            getDescrSlotsNode, callSetNode, getExisting, attrRead, deleteNonExistingBranchProfile,
                            raiseNode);
        }

        @Specialization
        @InliningCutoff
        static void doGeneric(Node inliningTarget, VirtualFrame frame, Object object, Object keyObject, Object value, WriteAttributeToObjectNode writeNode,
                        @Shared @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached InlinedConditionProfile hasDescriptor,
                        @Shared @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Shared @Cached CallSlotDescrSet callSetNode,
                        @Shared @Cached(inline = false) LookupAttributeInMRONode.Dynamic getExisting,
                        @Shared @Cached(inline = false) ReadAttributeFromObjectNode attrRead,
                        @Shared @Cached InlinedBranchProfile deleteNonExistingBranchProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            TruffleString key = castAttributeKey(inliningTarget, keyObject, castKeyToStringNode, raiseNode);
            setAttr(inliningTarget, frame, object, key, value, writeNode, getClassNode, hasDescriptor,
                            getDescrSlotsNode, callSetNode, getExisting, attrRead, deleteNonExistingBranchProfile,
                            raiseNode);
        }

        public static TruffleString castAttributeKey(Node inliningTarget, Object keyObject, CastToTruffleStringNode castKeyToStringNode, PRaiseNode raiseNode) {
            try {
                return castKeyToStringNode.execute(inliningTarget, keyObject);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ATTR_NAME_MUST_BE_STRING, keyObject);
            }
        }

        private static void setAttr(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key,
                        Object value, WriteAttributeToObjectNode writeNode, GetClassNode getClassNode,
                        InlinedConditionProfile hasDescriptor, GetObjectSlotsNode getDescrSlotsNode,
                        CallSlotDescrSet callSetNode, LookupAttributeInMRONode.Dynamic getExisting,
                        ReadAttributeFromObjectNode attrRead, InlinedBranchProfile deleteNonExistingBranchProfile,
                        PRaiseNode raiseNode) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            if (hasDescriptor.profile(inliningTarget, !PGuards.isNoValue(descr))) {
                var slots = getDescrSlotsNode.execute(inliningTarget, descr);
                if (slots.tp_descr_set() != null) {
                    callSetNode.execute(frame, inliningTarget, slots.tp_descr_set(), descr, object, value);
                    return;
                }
            }

            boolean writeValue = true;
            if (value == PNone.NO_VALUE) {
                Object currentValue = attrRead.execute(object, key);
                if (currentValue == PNone.NO_VALUE) {
                    deleteNonExistingBranchProfile.enter(inliningTarget);
                    writeValue = false;
                }
            }

            if (writeValue) {
                boolean wroteAttr = writeNode.execute(object, key, value);
                if (wroteAttr) {
                    return;
                }
            }

            if (descr != PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.HAS_NO_ATTR, object, key);
            }
        }

        public static GenericSetAttrNode getUncached() {
            return ObjectNodesFactory.GenericSetAttrNodeGen.getUncached();
        }
    }

    /**
     * Equivalent of {@code _PyObject_GenericSetAttrWithDict}, but only for non-null dict parameter.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GenericSetAttrWithDictNode extends Node {
        /**
         * If {@code value} argument may be {@link PNone#NO_VALUE}, which indicates that we want to
         * delete the attribute.
         */
        public abstract void execute(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key, Object value, PDict dict);

        @Specialization
        static void doIt(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key, Object value, PDict dict,
                        @Cached GetClassNode getClassNode,
                        @Cached InlinedConditionProfile hasDescriptor,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached CallSlotDescrSet callSetNode,
                        @Cached(inline = false) LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached HashingStorageDelItem delHashingStorageItem,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached InlinedConditionProfile isDeleteProfile,
                        @Cached InlinedBranchProfile changedStorageProfile,
                        @Cached(inline = false) RaiseAttributeErrorNode raiseAttributeErrorNode) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            if (hasDescriptor.profile(inliningTarget, !PGuards.isNoValue(descr))) {
                var slots = getDescrSlotsNode.execute(inliningTarget, descr);
                if (slots.tp_descr_set() != null) {
                    callSetNode.execute(frame, inliningTarget, slots.tp_descr_set(), descr, object, value);
                    return;
                }
            }

            if (isDeleteProfile.profile(inliningTarget, value == PNone.NO_VALUE)) {
                boolean found = delHashingStorageItem.execute(frame, inliningTarget, dict.getDictStorage(), key, dict);
                if (!found) {
                    raiseAttributeError(object, key, raiseAttributeErrorNode, type);
                }
            } else {
                HashingStorage storage = dict.getDictStorage();
                HashingStorage newStorage = setHashingStorageItem.execute(frame, inliningTarget, storage, key, value);
                if (storage != newStorage) {
                    changedStorageProfile.enter(inliningTarget);
                    dict.setDictStorage(newStorage);
                }
            }
        }

        @InliningCutoff
        private static void raiseAttributeError(Object object, TruffleString key, RaiseAttributeErrorNode raiseAttributeErrorNode, Object type) {
            raiseAttributeErrorNode.execute(object, key, type);
        }

        @GenerateInline(false) // used lazily
        @GenerateUncached
        public abstract static class RaiseAttributeErrorNode extends Node {
            public abstract void execute(Object object, Object key, Object type);

            @Specialization
            static void doIt(Object object, Object key, Object type,
                            @Bind Node inliningTarget,
                            @Cached PRaiseNode raiseNode,
                            @Cached IsSubtypeNode isSubtypeNode) {
                TruffleString message;
                if (isSubtypeNode.execute(type, PythonBuiltinClassType.PythonClass)) {
                    message = ErrorMessages.TYPE_N_HAS_NO_ATTR;
                } else {
                    message = ErrorMessages.HAS_NO_ATTR;
                }
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AttributeError, message, object, key);
            }
        }

        public static GenericSetAttrNode getUncached() {
            return ObjectNodesFactory.GenericSetAttrNodeGen.getUncached();
        }
    }
}
