/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_CHECK_BASESIZE_FOR_GETSTATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_PICKLE_OBJECT_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.COPYREG_SLOTNAMES;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_A_NOT_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SHOULD_RETURN_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.SLOTNAMES_SHOULD_BE_A_NOT_B;
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
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.object.ObjectNodesFactory.GetFullyQualifiedNameNodeGen;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyImportImport;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithState;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsAnyBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.IDUtils;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class ObjectNodes {

    public static final TruffleString T__REDUCE_EX = tsLiteral("_reduce_ex");
    public static final TruffleString T__SLOTNAMES = tsLiteral("_slotnames");

    @GenerateUncached
    abstract static class GetObjectIdNode extends Node {
        private static final HiddenKey OBJECT_ID = new HiddenKey("_id");

        public abstract long execute(Object self);

        protected static Assumption getSingleThreadedAssumption(Node node) {
            return PythonLanguage.get(node).singleThreadedAssumption;
        }

        protected static boolean isIDableObject(Object object) {
            return object instanceof PythonObject || object instanceof PythonAbstractNativeObject;
        }

        @Specialization(guards = "isIDableObject(self)", assumptions = "getSingleThreadedAssumption(readNode)")
        static long singleThreadedObject(Object self,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached WriteAttributeToDynamicObjectNode writeNode) {
            Object objectId = readNode.execute(self, OBJECT_ID);
            if (objectId == PNone.NO_VALUE) {
                objectId = PythonContext.get(readNode).getNextObjectId();
                writeNode.execute(self, OBJECT_ID, objectId);
            }
            assert objectId instanceof Long : "internal object id hidden key must be a long at this point";
            return (long) objectId;
        }

        @Specialization(guards = "isIDableObject(self)", replaces = "singleThreadedObject")
        static long multiThreadedObject(Object self,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached WriteAttributeToDynamicObjectNode writeNode) {
            Object objectId = readNode.execute(self, OBJECT_ID);
            if (objectId == PNone.NO_VALUE) {
                synchronized (self) {
                    objectId = readNode.execute(self, OBJECT_ID);
                    if (objectId == PNone.NO_VALUE) {
                        objectId = PythonContext.get(readNode).getNextObjectId();
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
    public abstract static class GetIdNode extends PNodeWithContext {
        public abstract Object execute(Object self);

        @Specialization
        static Object id(PBytes self,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, self) == 0) {
                return ID_EMPTY_BYTES;
            }
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(PFrozenSet self,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, self) == 0) {
                return ID_EMPTY_FROZENSET;
            }
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(PTuple self,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached IsAnyBuiltinObjectProfile isBuiltin,
                        @Cached PyObjectSizeNode sizeNode) {
            if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, self) && sizeNode.execute(null, self) == 0) {
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
        static Object id(PythonBuiltinClass self) {
            return getId(self.getType());
        }

        @Specialization
        static Object id(PythonAbstractNativeObject self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization
        static Object id(boolean self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            PythonContext context = PythonContext.get(getObjectIdNode);
            Object bool = self ? context.getTrue() : context.getFalse();
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
        Object id(TruffleString self) {
            if (self.isEmpty()) {
                return ID_EMPTY_UNICODE;
            }
            return PythonContext.get(this).getNextStringId(self);
        }

        @Specialization
        Object id(PString self,
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode,
                        @Cached StringNodes.StringMaterializeNode materializeNode) {
            if (isInternedStringNode.execute(self)) {
                return id(materializeNode.execute(self));
            }
            return getObjectIdNode.execute(self);
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
                        @Cached ObjectNodes.GetObjectIdNode getObjectIdNode) {
            return getObjectIdNode.execute(self);
        }

        @Specialization(guards = "isForeignObjectNode.execute(self)", limit = "1")
        static Object idForeign(Object self,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
            return PythonContext.get(isForeignObjectNode).getNextObjectId(self);
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

        @Specialization
        static boolean isList(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached InlineIsBuiltinClassProfile objProfile) {
            Object type = getClassNode.execute(inliningTarget, object);
            if (objProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PList)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PList);
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class FastIsTupleSubClassNode extends Node {
        public abstract boolean execute(VirtualFrame frame, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static boolean isTuple(VirtualFrame frame, PTuple object) {
            return true;
        }

        @Specialization
        static boolean isTuple(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached InlineIsBuiltinClassProfile objProfile) {
            Object type = getClassNode.execute(inliningTarget, object);
            if (objProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PTuple)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PTuple);
        }

        @NeverDefault
        public static FastIsTupleSubClassNode create() {
            return ObjectNodesFactory.FastIsTupleSubClassNodeGen.create();
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class FastIsDictSubClassNode extends Node {
        abstract boolean execute(VirtualFrame frame, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static boolean isDict(VirtualFrame frame, PDict object) {
            return true;
        }

        @Specialization
        static boolean isDict(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached InlineIsBuiltinClassProfile objProfile) {
            Object type = getClassNode.execute(inliningTarget, object);
            if (objProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PDict)) {
                return true;
            }
            return isSubClassNode.executeWith(frame, type, PythonBuiltinClassType.PDict);
        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    abstract static class GetNewArgsNode extends Node {
        public abstract Pair<Object, Object> execute(VirtualFrame frame, Object obj);

        @Specialization
        Pair<Object, Object> dispatch(VirtualFrame frame, Object obj,
                        @Cached GetNewArgsInternalNode getNewArgsInternalNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            Object getNewArgsExAttr = lookupAttr.execute(frame, obj, T___GETNEWARGS_EX__);
            Object getNewArgsAttr = lookupAttr.execute(frame, obj, T___GETNEWARGS__);
            return getNewArgsInternalNode.execute(frame, getNewArgsExAttr, getNewArgsAttr);
        }

        abstract static class GetNewArgsInternalNode extends PNodeWithState {
            public abstract Pair<Object, Object> execute(VirtualFrame frame, Object getNewArgsExAttr, Object getNewArgsAttr);

            @Specialization(guards = "!isNoValue(getNewArgsExAttr)")
            Pair<Object, Object> doNewArgsEx(VirtualFrame frame, Object getNewArgsExAttr, @SuppressWarnings("unused") Object getNewArgsAttr,
                            @Shared("callNode") @Cached CallNode callNode,
                            @Shared("tupleCheck") @Cached FastIsTupleSubClassNode isTupleSubClassNode,
                            @Cached FastIsDictSubClassNode isDictSubClassNode,
                            @Cached SequenceStorageNodes.GetItemNode getItemNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached PyObjectSizeNode sizeNode) {
                Object newargs = callNode.execute(frame, getNewArgsExAttr);
                if (!isTupleSubClassNode.execute(frame, newargs)) {
                    throw raise(TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, T___GETNEWARGS_EX__, "tuple", newargs);
                }
                int length = sizeNode.execute(frame, newargs);
                if (length != 2) {
                    throw raise(ValueError, SHOULD_RETURN_A_NOT_B, T___GETNEWARGS_EX__, "tuple of length 2", length);
                }

                SequenceStorage sequenceStorage = getSequenceStorageNode.execute(newargs);
                Object args = getItemNode.execute(sequenceStorage, 0);
                Object kwargs = getItemNode.execute(sequenceStorage, 1);

                if (!isTupleSubClassNode.execute(frame, args)) {
                    throw raise(TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "first item of the tuple returned by __getnewargs_ex__", "tuple", args);
                }
                if (!isDictSubClassNode.execute(frame, kwargs)) {
                    throw raise(TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "second item of the tuple returned by __getnewargs_ex__", "dict", kwargs);
                }

                return Pair.create(args, kwargs);
            }

            @Specialization(guards = "!isNoValue(getNewArgsAttr)")
            Pair<Object, Object> doNewArgs(VirtualFrame frame, @SuppressWarnings("unused") PNone getNewArgsExAttr, Object getNewArgsAttr,
                            @Shared("callNode") @Cached CallNode callNode,
                            @Shared("tupleCheck") @Cached FastIsTupleSubClassNode isTupleSubClassNode) {
                Object args = callNode.execute(frame, getNewArgsAttr);
                if (!isTupleSubClassNode.execute(frame, args)) {
                    throw raise(TypeError, SHOULD_RETURN_TYPE_A_NOT_TYPE_B, T___GETNEWARGS__, "tuple", args);
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
        @Child PyObjectLookupAttr lookupAttr = PyObjectLookupAttr.create();

        public final Object execute(VirtualFrame frame, Object cls, Object copyReg) {
            Object clsDict = lookupAttr.execute(frame, cls, T___DICT__);
            return executeInternal(frame, cls, clsDict, copyReg);
        }

        abstract Object executeInternal(VirtualFrame frame, Object cls, Object clsDict, Object copyReg);

        @Specialization
        Object dispatchDict(VirtualFrame frame, Object cls, PDict clsDict, Object copyReg,
                        @Shared("internal") @Cached GetSlotNamesInternalNode getSlotNamesInternalNode,
                        @Shared("getItem") @Cached HashingStorageGetItem getItem) {
            Object slotNames = getItem.execute(clsDict.getDictStorage(), T__SLOTNAMES);
            slotNames = slotNames == null ? PNone.NO_VALUE : slotNames;
            return getSlotNamesInternalNode.execute(frame, cls, copyReg, slotNames);
        }

        // Fast paths for a common case of PMappingproxy and NO_VALUE
        @Specialization(guards = "isDict(mapping)")
        Object dispatchMappingProxy(VirtualFrame frame, Object cls, @SuppressWarnings("unused") PMappingproxy clsDict, Object copyReg,
                        @Shared("internal") @Cached GetSlotNamesInternalNode getSlotNamesInternalNode,
                        @Bind("clsDict.getMapping()") Object mapping,
                        @Shared("getItem") @Cached HashingStorageGetItem getItem) {
            PDict mappingDict = (PDict) mapping;
            return dispatchDict(frame, cls, mappingDict, copyReg, getSlotNamesInternalNode, getItem);
        }

        @Specialization(guards = "isNoValue(noValue)")
        Object dispatchNoValue(VirtualFrame frame, Object cls, PNone noValue, Object copyReg,
                        @Shared("internal") @Cached GetSlotNamesInternalNode getSlotNamesInternalNode) {
            return getSlotNamesInternalNode.execute(frame, cls, copyReg, noValue);
        }

        @Fallback
        Object dispatchGeneric(VirtualFrame frame, Object cls, Object clsDict, Object copyReg,
                        @Bind("this") Node inliningTarget,
                        @Shared("internal") @Cached GetSlotNamesInternalNode getSlotNamesInternalNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile) {
            /*
             * CPython looks at tp_dict of the type and assumes that it must be a builtin
             * dictionary, otherwise it fails with type error. We do not have tp_dict for managed
             * classes and what comes here is __dict__, so among other things it may be a mapping
             * proxy, but also tp_dict for native classes. We "over-approximate" what CPython does
             * here a bit and just use __getitem__.
             */

            Object slotNames = PNone.NO_VALUE;
            if (!PGuards.isNoValue(clsDict)) {
                try {
                    slotNames = getItemNode.execute(frame, clsDict, T___SLOTNAMES__);
                } catch (PException ex) {
                    ex.expect(inliningTarget, PythonBuiltinClassType.KeyError, isBuiltinClassProfile);
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

            @Specialization
            Object getCopyRegSlotNames(VirtualFrame frame, Object cls, Object copyReg, @SuppressWarnings("unused") PNone slotNames,
                            @Cached FastIsListSubClassNode isListSubClassNode,
                            @Cached PyObjectCallMethodObjArgs callMethod) {
                Object names = callMethod.execute(frame, copyReg, T__SLOTNAMES, cls);
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

        @Specialization
        Object dispatch(VirtualFrame frame, Object obj, boolean required, Object copyReg,
                        @Cached GetStateInternalNode getStateInternalNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            Object getStateAttr = lookupAttr.execute(frame, obj, T___GETSTATE__);
            return getStateInternalNode.execute(frame, obj, required, copyReg, getStateAttr);
        }

        abstract static class GetStateInternalNode extends PNodeWithState {
            public abstract Object execute(VirtualFrame frame, Object obj, boolean required, Object copyReg, Object getStateAttr);

            @Specialization(guards = "!isNoValue(getStateAttr)")
            Object getState(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") boolean required, @SuppressWarnings("unused") Object copyReg, Object getStateAttr,
                            @Cached CallNode callNode) {
                return callNode.execute(frame, getStateAttr);
            }

            @Specialization
            Object getStateFromSlots(VirtualFrame frame, Object obj, boolean required, Object copyReg, @SuppressWarnings("unused") PNone getStateAttr,
                            @Bind("this") Node inliningTarget,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                            @Cached TypeNodes.GetItemSizeNode getItemsizeNode,
                            @Cached CastToTruffleStringNode toStringNode,
                            @Cached GetSlotNamesNode getSlotNamesNode,
                            @Cached InlinedGetClassNode getClassNode,
                            @Cached PyObjectSizeNode sizeNode,
                            @Cached PyObjectLookupAttr lookupAttr,
                            @Cached HashingStorageSetItem setHashingStorageItem,
                            @Cached CheckBasesizeForGetState checkBasesize) {
                Object state;
                Object type = getClassNode.execute(inliningTarget, obj);
                if (required && getItemsizeNode.execute(inliningTarget, type) != 0) {
                    throw raise(TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
                }

                Object dict = lookupAttr.execute(frame, obj, T___DICT__);
                if (!PGuards.isNoValue(dict) && sizeNode.execute(frame, dict) > 0) {
                    state = dict;
                } else {
                    state = PNone.NONE;
                }

                // we skip the assert that type is a type since we are certain of that in this case
                Object slotnames = getSlotNamesNode.execute(frame, type, copyReg);
                Object[] names = EMPTY_OBJECT_ARRAY;
                if (!PGuards.isNone(slotnames)) {
                    SequenceStorage sequenceStorage = getSequenceStorageNode.execute(slotnames);
                    names = toArrayNode.execute(inliningTarget, sequenceStorage);
                }

                if (required && !checkBasesize.execute(obj, type, names.length)) {
                    throw raise(TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
                }

                if (names.length > 0) {
                    HashingStorage slotsStorage = EconomicMapStorage.create(names.length);
                    boolean haveSlots = false;
                    for (Object o : names) {
                        try {
                            TruffleString name = toStringNode.execute(o);
                            Object value = lookupAttr.execute(frame, obj, name);
                            if (!PGuards.isNoValue(value)) {
                                HashingStorage newStorage = setHashingStorageItem.execute(frame, slotsStorage, name, value);
                                assert newStorage == slotsStorage;
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

                return state;
            }
        }
    }

    abstract static class CheckBasesizeForGetState extends Node {
        public abstract boolean execute(Object obj, Object type, int slotNum);

        @Specialization
        boolean doNative(@SuppressWarnings("unused") PythonAbstractNativeObject obj, Object type, int slotNum,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            Object result = callCapiFunction.call(FUN_CHECK_BASESIZE_FOR_GETSTATE, toSulongNode.execute(type), slotNum);
            return (int) result == 0;
        }

        @Fallback
        boolean doManaged(Object obj, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") int slotNum) {
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

    abstract static class CommonReduceNode extends PNodeWithState {
        protected static final TruffleString T_MOD_COPYREG = tsLiteral("copyreg");

        public abstract Object execute(VirtualFrame frame, Object obj, int proto);

        @Specialization(guards = "proto >= 2")
        public Object reduceNewObj(VirtualFrame frame, Object obj, @SuppressWarnings("unused") int proto,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached("create(T___NEW__)") LookupAttributeInMRONode lookupNew,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyImportImport importNode,
                        @Cached InlinedConditionProfile newObjProfile,
                        @Cached InlinedConditionProfile hasArgsProfile,
                        @Cached GetNewArgsNode getNewArgsNode,
                        @Cached GetStateNode getStateNode,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectGetIter getIter) {
            Object cls = getClassNode.execute(inliningTarget, obj);
            if (lookupNew.execute(cls) == PNone.NO_VALUE) {
                throw raise(TypeError, CANNOT_PICKLE_OBJECT_TYPE, obj);
            }

            Pair<Object, Object> rv = getNewArgsNode.execute(frame, obj);
            Object args = rv.getLeft();
            Object kwargs = rv.getRight();
            Object newobj, newargs;

            Object copyReg = importNode.execute(frame, T_MOD_COPYREG);

            boolean hasargs = args != PNone.NONE;

            if (newObjProfile.profile(inliningTarget, kwargs == PNone.NONE || sizeNode.execute(frame, kwargs) == 0)) {
                newobj = lookupAttr.execute(frame, copyReg, T___NEWOBJ__);
                Object[] newargsVals;
                if (hasArgsProfile.profile(inliningTarget, hasargs)) {
                    SequenceStorage sequenceStorage = getSequenceStorageNode.execute(args);
                    Object[] vals = toArrayNode.execute(inliningTarget, sequenceStorage);
                    newargsVals = new Object[vals.length + 1];
                    newargsVals[0] = cls;
                    System.arraycopy(vals, 0, newargsVals, 1, vals.length);
                } else {
                    newargsVals = new Object[]{cls};
                }
                newargs = factory().createTuple(newargsVals);
            } else if (hasArgsProfile.profile(inliningTarget, hasargs)) {
                newobj = lookupAttr.execute(frame, copyReg, T___NEWOBJ_EX__);
                newargs = factory().createTuple(new Object[]{cls, args, kwargs});
            } else {
                throw raiseBadInternalCall();
            }

            boolean objIsList = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PList);
            boolean objIsDict = isSubClassNode.executeWith(frame, cls, PythonBuiltinClassType.PDict);
            boolean required = !hasargs && !objIsDict && !objIsList;

            Object state = getStateNode.execute(frame, obj, required, copyReg);
            Object listitems = objIsList ? getIter.execute(frame, obj) : PNone.NONE;
            Object dictitems = objIsDict ? getIter.execute(frame, callMethod.execute(frame, obj, T_ITEMS)) : PNone.NONE;

            return factory().createTuple(new Object[]{newobj, newargs, state, listitems, dictitems});
        }

        @Specialization(guards = "proto < 2")
        public Object reduceCopyReg(VirtualFrame frame, Object obj, int proto,
                        @Cached PyImportImport importNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object copyReg = importNode.execute(frame, T_MOD_COPYREG);
            return callMethod.execute(frame, copyReg, T__REDUCE_EX, obj, proto);
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
    public abstract static class GetFullyQualifiedNameNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Object cls);

        @Specialization
        static TruffleString get(VirtualFrame frame, Object cls,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CastToTruffleStringNode cast,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object moduleNameObject = lookupAttr.execute(frame, cls, T___MODULE__);
            Object qualNameObject = lookupAttr.execute(frame, cls, T___QUALNAME__);
            if (qualNameObject == PNone.NO_VALUE) {
                return StringLiterals.T_VALUE_UNKNOWN;
            }
            TruffleString qualName = cast.execute(qualNameObject);
            if (moduleNameObject == PNone.NO_VALUE) {
                return qualName;
            }
            TruffleString moduleName = cast.execute(moduleNameObject);
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
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class GetFullyQualifiedClassNameNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Object self);

        @Specialization
        static TruffleString get(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClass,
                        @Cached GetFullyQualifiedNameNode getFullyQualifiedNameNode) {
            return getFullyQualifiedNameNode.execute(frame, getClass.execute(inliningTarget, self));
        }
    }

    /**
     * Default repr for objects that don't override {@code __repr__}
     */
    @GenerateUncached
    public abstract static class DefaultObjectReprNode extends PNodeWithContext {
        public abstract TruffleString execute(Frame frame, Object object);

        @Specialization
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Cached GetFullyQualifiedClassNameNode getFullyQualifiedClassNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString fqcn = getFullyQualifiedClassNameNode.execute(frame, self);
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
        public abstract void execute(Node inliningTarget, VirtualFrame frame, Object object, Object key, Object value, WriteAttributeToObjectNode writeNode);

        @Specialization
        static void doStringKey(Node inliningTarget, VirtualFrame frame, Object object, TruffleString key, Object value, WriteAttributeToObjectNode writeNode,
                        @Shared @Cached InlinedGetClassNode getClassNode,
                        @Shared @Cached CallSetHelper callSetHelper,
                        @Shared @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            boolean calledSet = callSetHelper.execute(inliningTarget, frame, descr, object, value);
            if (calledSet) {
                return;
            }
            boolean wroteAttr = writeNode.execute(object, key, value);
            if (wroteAttr) {
                return;
            }
            if (descr != PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raiseNode.raise(AttributeError, ErrorMessages.HAS_NO_ATTR, object, key);
            }
        }

        @Specialization(replaces = "doStringKey")
        static void doIt(Node inliningTarget, VirtualFrame frame, Object object, Object keyObject, Object value, WriteAttributeToObjectNode writeNode,
                        @Shared @Cached InlinedGetClassNode getClassNode,
                        @Shared @Cached CallSetHelper callSetHelper,
                        @Shared @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Shared @Cached PRaiseNode raiseNode,
                        @Cached CastToTruffleStringNode castKeyToStringNode) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(keyObject);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ATTR_NAME_MUST_BE_STRING, keyObject);
            }
            doStringKey(inliningTarget, frame, object, key, value, writeNode, getClassNode, callSetHelper, getExisting, raiseNode);
        }

        public static GenericSetAttrNode getUncached() {
            return ObjectNodesFactory.GenericSetAttrNodeGen.getUncached();
        }

        @GenerateInline
        @GenerateCached(false)
        @GenerateUncached
        @ImportStatic({SpecialMethodSlot.class, PGuards.class})
        abstract static class CallSetHelper extends Node {
            abstract boolean execute(Node inliningTarget, VirtualFrame frame, Object descr, Object object, Object value);

            @Specialization(guards = "isNoValue(descr)")
            @SuppressWarnings("unused")
            static boolean call(Node inliningTarget, VirtualFrame frame, Object descr, Object object, Object value) {
                return false;
            }

            @Specialization(guards = "!isNoValue(descr)")
            static boolean call(Node inliningTarget, VirtualFrame frame, Object descr, Object object, Object value,
                            @Cached InlinedGetClassNode getClassNode,
                            @Cached(parameters = "Set") LookupCallableSlotInMRONode lookup,
                            @Cached CallTernaryMethodNode call) {
                Object descrClass = getClassNode.execute(inliningTarget, descr);
                Object setMethod = lookup.execute(descrClass);
                if (setMethod == PNone.NO_VALUE) {
                    return false;
                } else {
                    call.execute(frame, setMethod, descr, object, value);
                    return true;
                }
            }
        }
    }

    public abstract static class AbstractSetattrNode extends PythonTernaryBuiltinNode {
        @Child GetClassNode getDescClassNode;
        @Child LookupCallableSlotInMRONode lookupSetNode;
        @Child CallTernaryMethodNode callSetNode;

        public abstract PNone execute(VirtualFrame frame, Object object, TruffleString key, Object value);

        @SuppressWarnings("unused")
        protected boolean writeAttribute(Object object, TruffleString key, Object value) {
            throw CompilerDirectives.shouldNotReachHere("writeAttribute");
        }

        @Specialization
        protected PNone doStringKey(VirtualFrame frame, Object object, TruffleString key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                        @Shared("getExisting") @Cached LookupAttributeInMRONode.Dynamic getExisting) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDescClass(descr);
                Object set = ensureLookupSetNode().execute(dataDescClass);
                if (PGuards.isCallableOrDescriptor(set)) {
                    ensureCallSetNode().execute(frame, set, descr, object, value);
                    return PNone.NONE;
                }
            }
            if (writeAttribute(object, key, value)) {
                return PNone.NONE;
            }
            if (descr != PNone.NO_VALUE) {
                throw raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raise(AttributeError, ErrorMessages.HAS_NO_ATTR, object, key);
            }
        }

        @Specialization(replaces = "doStringKey")
        protected PNone doIt(VirtualFrame frame, Object object, Object keyObject, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                        @Shared("getExisting") @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached CastToTruffleStringNode castKeyToStringNode) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(keyObject);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ATTR_NAME_MUST_BE_STRING, keyObject);
            }
            return doStringKey(frame, object, key, value, inliningTarget, getClassNode, getExisting);
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.execute(desc);
        }

        private LookupCallableSlotInMRONode ensureLookupSetNode() {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Set));
            }
            return lookupSetNode;
        }

        private CallTernaryMethodNode ensureCallSetNode() {
            if (callSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSetNode = insert(CallTernaryMethodNode.create());
            }
            return callSetNode;
        }
    }
}
