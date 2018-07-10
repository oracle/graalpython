/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.objects.common.HashingStorage.DEFAULT_EQIVALENCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.FastDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.Equivalence;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ContainsKeyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ContainsValueNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.CopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DelItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DiffNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.EqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysEqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@GenerateNodeFactory
public abstract class HashingStorageNodes {

    public static class PythonEquivalence extends Equivalence {
        @Child private LookupAndCallUnaryNode callHashNode = LookupAndCallUnaryNode.create(__HASH__);
        @Child private BinaryComparisonNode callEqNode = BinaryComparisonNode.create(SpecialMethodNames.__EQ__, SpecialMethodNames.__EQ__, "==", null, null);
        @CompilationFinal private int state = 0;

        @Override
        public int hashCode(Object o) {
            try {
                if (state == 0) { // int hash
                    return callHashNode.executeInt(o);
                } else if (state == 1) { // long hash
                    return (int) (callHashNode.executeLong(o));
                } else if (state == 2) { // object hash
                    Object hash = callHashNode.executeObject(o);
                    if (hash instanceof Integer) {
                        return (int) hash;
                    } else if (hash instanceof Long) {
                        return (int) ((long) hash);
                    } else if (hash instanceof PInt) {
                        return ((PInt) hash).intValue();
                    } else {
                        throw hashCodeTypeError();
                    }
                } else {
                    throw new IllegalStateException();
                }
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (ex.getResult() instanceof Integer) {
                    // the default state is executeInt, so when we get here, we already
                    // switched to executeLong and now got an int again, so we cannot
                    // specialize on either primitive type and have to switch to
                    // the executeObject state.
                    state = 2;
                    return (int) ex.getResult();
                } else if (ex.getResult() instanceof Long) {
                    state = 1;
                    return (int) ((long) ex.getResult());
                } else if (ex.getResult() instanceof PInt) {
                    state = 2;
                    return ((PInt) ex.getResult()).intValue();
                } else {
                    throw hashCodeTypeError();
                }
            }
        }

        private PException hashCodeTypeError() {
            return raise(PythonErrorType.TypeError, "__hash__ method should return an integer");
        }

        @Override
        public boolean equals(Object left, Object right) {
            return callEqNode.executeBool(left, right);
        }

    }

    @ImportStatic(PGuards.class)
    abstract static class DictStorageBaseNode extends PBaseNode {
        @Child private GetClassNode getClassNode;
        @Child private LookupAndCallUnaryNode lookupHashAttributeNode;
        @Child private Equivalence equivalenceNode;

        protected Equivalence getEquivalence() {
            if (equivalenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equivalenceNode = insert(new PythonEquivalence());
            }
            return equivalenceNode;
        }

        protected PythonClass getClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(object);
        }

        private final ValueProfile keyTypeProfile = ValueProfile.createClassProfile();

        protected boolean isJavaString(Object o) {
            return o instanceof String || o instanceof PString && wrappedString((PString) o);
        }

        protected static boolean shapeCheck(Shape shape, DynamicObject receiver) {
            return shape != null && shape.check(receiver);
        }

        protected static Shape lookupShape(DynamicObject receiver) {
            CompilerAsserts.neverPartOfCompilation();
            return receiver.getShape();
        }

        protected boolean isHashable(Object key) {
            Object profiledKey = keyTypeProfile.profile(key);
            if (PGuards.isString(profiledKey)) {
                return true;
            } else if (PGuards.isInteger(profiledKey)) {
                return true;
            } else if (profiledKey instanceof Double || PGuards.isPFloat(profiledKey)) {
                return true;
            }

            if (lookupHashAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupHashAttributeNode = insert(LookupAndCallUnaryNode.create(__HASH__));
            }

            try {
                lookupHashAttributeNode.executeObject(key);
                return true;
            } catch (PException e) {
                // ignore
            }
            return false;
        }

        protected PException unhashable(Object key) {
            return raise(TypeError, "unhashable type: '%p'", key);
        }

        protected boolean wrappedString(PString s) {
            return getClass(s).isBuiltin();
        }

        protected EconomicMapStorage switchToEconomicMap(PHashingCollection container, HashingStorage storage) {
            // We cannot store this key in the dynamic object -> switch to generic store
            EconomicMapStorage newStorage = EconomicMapStorage.create(storage.length() + 1, false);
            newStorage.addAll(storage, getEquivalence());
            container.setDictStorage(newStorage);
            return newStorage;
        }

        protected static DynamicObjectStorage switchToFastDictStorage(PHashingCollection container, HashingStorage storage) {
            DynamicObjectStorage newStorage = new FastDictStorage();
            newStorage.addAll(storage, DEFAULT_EQIVALENCE);
            container.setDictStorage(newStorage);
            return newStorage;
        }

        protected static Location lookupLocation(Shape shape, Object name) {
            /* Initialization of cached values always happens in a slow path. */
            CompilerAsserts.neverPartOfCompilation();

            Property property = shape.getProperty(name);
            if (property != null) {
                return property.getLocation();
            }
            return null;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class InitNode extends DictStorageBaseNode {

        protected static final String KEYS = "keys";

        public abstract void execute(PDict self, Object primary, PKeyword[] kwargs);

        @Child private GetNextNode nextNode;
        @Child private SetItemNode setItemNode;
        @Child private LookupInheritedAttributeNode lookupKeysAttributeNode;

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        public void doEmpty(PDict self, @SuppressWarnings("unused") Object iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            self.setDictStorage(new EmptyStorage());
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        public void doKeywords(PDict self, @SuppressWarnings("unused") Object iterable, PKeyword[] kwargs) {
            self.setDictStorage(new KeywordsStorage(kwargs));
        }

        protected static boolean isPDict(PythonObject o) {
            return o instanceof PDict;
        }

        protected boolean hasKeysAttribute(PythonObject o) {
            if (lookupKeysAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupKeysAttributeNode = insert(LookupInheritedAttributeNode.create(KEYS));
            }
            return lookupKeysAttributeNode.execute(o) != PNone.NO_VALUE;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "isEmpty(kwargs)"})
        public void doPDict(PDict self, PDict iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            self.setDictStorage(iterable.getDictStorage().copy(HashingStorage.DEFAULT_EQIVALENCE));
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isEmpty(kwargs)"}, rewriteOn = HashingStorage.UnmodifiableStorageException.class)
        public void doPDictKwargs(PDict self, PDict iterable, PKeyword[] kwargs,
                        @Cached("create()") UnionNode unionNode) {
            HashingStorage dictStorage = unionNode.execute(iterable.getDictStorage(), new KeywordsStorage(kwargs));
            self.setDictStorage(dictStorage);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isEmpty(kwargs)"})
        public void doPDictKwargs(PDict self, PDict iterable, PKeyword[] kwargs) {
            HashingStorage dictStorage = iterable.getDictStorage().copy(HashingStorage.DEFAULT_EQIVALENCE);
            dictStorage.addAll(new KeywordsStorage(kwargs));
            self.setDictStorage(dictStorage);
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isPDict(mapping)", "hasKeysAttribute(mapping)"})
        public void doMapping(PDict self, PythonObject mapping, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {

            // That call must work since 'hasKeysAttribute' checks if it has the 'keys' attribute
            // before.
            Object keysIterable = callKeysNode.executeObject(mapping);

            Object keysIt = getIteratorNode.executeWith(keysIterable);
            while (true) {
                try {
                    Object keyObj = getNextNode().execute(keysIt);
                    Object valueObj = callGetItemNode.executeObject(mapping, keyObj);

                    getSetItemNode().execute(self, self.getDictStorage(), keyObj, valueObj);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    break;
                }
            }
        }

        private GetNextNode getNextNode() {
            if (nextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextNode = insert(GetNextNode.create());
            }
            return nextNode;
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPDict(iterable)", "!hasKeysAttribute(iterable)"})
        public void doSequence(PDict self, PythonObject iterable, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") FastConstructListNode createListNode,
                        @Cached("createBinaryProfile()") ConditionProfile lengthTwoProfile,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {

            Object it = getIterator.executeWith(iterable);

            ArrayList<PSequence> elements = new ArrayList<>();
            PythonClass listClass = getCore().lookupType(PList.class);
            boolean isStringKey = false;
            try {
                while (true) {
                    Object next = getNextNode().execute(it);
                    PSequence element = null;
                    int len = 1;
                    element = createListNode.execute(listClass, next, getClass(next));
                    assert element != null;
                    // This constructs a new list using the builtin type. So, the object cannot
                    // be subclassed and we can directly call 'len()'.
                    len = element.len();

                    if (lengthTwoProfile.profile(len != 2)) {
                        throw raise(ValueError, "dictionary update sequence element #%d has length %d; 2 is required", elements.size(), len);
                    }

                    // really check for Java String since PString can be subclassed
                    isStringKey = isStringKey || element.getItem(0) instanceof String;

                    elements.add(element);
                }
            } catch (PException e) {
                if (e.getType() == getCore().getErrorClass(TypeError)) {
                    throw raise(TypeError, "cannot convert dictionary update sequence element #%d to a sequence", elements.size());
                } else {
                    e.expectStopIteration(getCore(), errorProfile);
                }
            }

            HashingStorage storage = PDict.createNewStorage(isStringKey, elements.size());
            for (int j = 0; j < elements.size(); j++) {
                getSetItemNode().execute(self, storage, elements.get(j).getItem(0), elements.get(j).getItem(1));
            }

            self.setDictStorage(storage);
        }

        @TruffleBoundary
        private static PSequence[] enlarge(PSequence[] elements, int newCapacity) {
            return Arrays.copyOf(elements, newCapacity);
        }

        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    public abstract static class ContainsKeyNode extends DictStorageBaseNode {

        public abstract boolean execute(HashingStorage storage, Object key);

        @Specialization
        @SuppressWarnings("unused")
        protected boolean contains(EmptyStorage storage, Object key) {
            return false;
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(KeywordsStorage storage, Object key,
                        @Cached("createClassProfile()") ValueProfile keyTypeProfile) {
            Object profileKey = keyTypeProfile.profile(key);
            if (profileKey instanceof String) {
                return storage.hasKey(profileKey, DEFAULT_EQIVALENCE);
            } else if (profileKey instanceof PString && wrappedString((PString) profileKey)) {
                return storage.hasKey(((PString) profileKey).getValue(), DEFAULT_EQIVALENCE);
            }
            return false;
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "wrappedString(name)",
                                        "cachedName.equals(name.getValue())",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectPString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") PString name,
                        @SuppressWarnings("unused") @Cached("name.getValue()") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, cachedName)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectPString"}, guards = {"wrappedString(name)", "storage.getStore().getShape().isValid()"})
        protected boolean readUncachedPString(DynamicObjectStorage storage, PString name) {
            return storage.getStore().containsKey(name.getValue());
        }

        @Specialization(guards = {"wrappedString(name)", "!storage.getStore().getShape().isValid()"})
        protected boolean updateShapePString(DynamicObjectStorage storage, PString name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectString"}, guards = "storage.getStore().getShape().isValid()")
        protected boolean readUncached(DynamicObjectStorage storage, String name) {
            return storage.getStore().containsKey(name);
        }

        @Specialization(guards = "!storage.getStore().getShape().isValid()")
        protected boolean updateShape(DynamicObjectStorage storage, String name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(guards = "!isJavaString(name)")
        @SuppressWarnings("unused")
        protected boolean readUncached(DynamicObjectStorage storage, Object name) {
            return false;
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(EconomicMapStorage storage, Object key) {
            return storage.hasKey(key, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(HashMapStorage storage, Object key) {
            return storage.hasKey(key, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(LocalsStorage storage, Object key) {
            return storage.hasKey(key, HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "!isHashable(key)")
        protected boolean doUnhashable(@SuppressWarnings("unused") HashMapStorage storage, Object key) {
            throw unhashable(key);
        }

        public static ContainsKeyNode create() {
            return ContainsKeyNodeGen.create();
        }
    }

    public abstract static class ContainsValueNode extends DictStorageBaseNode {

        public abstract boolean execute(HashingStorage storage, Object value);

        @Specialization
        @SuppressWarnings("unused")
        protected boolean contains(EmptyStorage storage, Object value) {
            return false;
        }

        @Specialization
        protected boolean contains(KeywordsStorage storage, Object value,
                        @Cached("createClassProfile()") ValueProfile keyTypeProfile) {

            Object profileKey = keyTypeProfile.profile(value);
            PKeyword[] store = storage.getStore();
            for (int i = 0; i < store.length; i++) {
            }

            if (profileKey instanceof String) {
                return storage.hasKey(value, DEFAULT_EQIVALENCE);
            }
            return false;
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectString"}, guards = "storage.getStore().getShape().isValid()")
        protected boolean readUncached(DynamicObjectStorage storage, String name) {
            return storage.getStore().containsKey(name);
        }

        @Specialization(guards = "!storage.getStore().getShape().isValid()")
        protected boolean updateShape(DynamicObjectStorage storage, String name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(EconomicMapStorage storage, Object key) {
            return storage.hasKey(key, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(HashMapStorage storage, Object key) {
            return storage.hasKey(key, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        protected boolean contains(LocalsStorage storage, Object key) {
            return storage.hasKey(key, HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "!isHashable(key)")
        protected boolean doUnhashable(@SuppressWarnings("unused") HashMapStorage storage, Object key) {
            throw unhashable(key);
        }

        public static ContainsValueNode create() {
            return ContainsValueNodeGen.create();
        }
    }

    public abstract static class SetItemNode extends DictStorageBaseNode {

        public abstract void execute(PHashingCollection container, HashingStorage storage, Object key, Object value);

        @Specialization
        protected void doEmptyStorage(PHashingCollection container, EmptyStorage storage, String key, Object value) {
            // immediately replace storage since empty storage is immutable
            DynamicObjectStorage newStorage = switchToFastDictStorage(container, storage);
            doDynamicObjectUpdateShape(container, newStorage, key, value);
        }

        @Specialization(guards = "wrappedString(key)")
        protected void doEmptyStorage(PHashingCollection container, EmptyStorage storage, PString key, Object value) {
            // immediately replace storage since empty storage is immutable
            DynamicObjectStorage newStorage = switchToFastDictStorage(container, storage);
            doDynamicObjectUpdateShape(container, newStorage, key.getValue(), value);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        protected void doEmptyStorage(PHashingCollection container, @SuppressWarnings("unused") EmptyStorage storage, Object key, Object value) {
            // immediately replace storage since empty storage is immutable
            EconomicMapStorage newStorage = EconomicMapStorage.create(false);
            newStorage.setItem(key, value, getEquivalence());
            container.setDictStorage(newStorage);
        }

        /**
         * Polymorphic inline cache for writing a property that already exists (no shape change is
         * necessary).
         */
        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())",
                                        "location != null",
                                        "canSet(location, value)"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static void doDynamicObjectExistingCached(@SuppressWarnings("unused") PHashingCollection container, DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        Object value,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name, value)") Location location) {
            try {
                location.set(storage.getStore(), value, shape);

            } catch (IncompatibleLocationException | FinalLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /**
         * Polymorphic inline cache for writing a property that does not exist yet (shape change is
         * necessary).
         */
        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(oldShape, storage.getStore())",
                                        "oldLocation == null",
                                        "canStore(newLocation, value)"
                        }, //
                        assumptions = {
                                        "oldShape.getValidAssumption()",
                                        "newShape.getValidAssumption()"
                        })
        @SuppressWarnings("unused")
        protected static void doDynamicObjectNewCached(PHashingCollection container, DynamicObjectStorage storage, String name, Object value,
                        @Cached("name") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape oldShape,
                        @Cached("lookupLocation(oldShape, name, value)") Location oldLocation,
                        @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                        @Cached("lookupLocation(newShape, name)") Location newLocation) {
            try {
                newLocation.set(storage.getStore(), value, oldShape, newShape);

            } catch (IncompatibleLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectExistingCached", "doDynamicObjectNewCached"}, guards = {"storage.getStore().getShape().isValid()", "!exceedsLimit(storage)"})
        protected static void doDynamicObjectUncached(@SuppressWarnings("unused") PHashingCollection container, DynamicObjectStorage storage, String name, Object value) {
            storage.getStore().define(name, value);
        }

        @Specialization(guards = {"storage.getStore().getShape().isValid()", "exceedsLimit(storage)"})
        protected void doDynamicObjectGeneralize(PHashingCollection container, FastDictStorage storage, String name, Object value) {
            switchToEconomicMap(container, storage).setItem(name, value, getEquivalence());
        }

        @TruffleBoundary
        @Specialization(guards = {"!storage.getStore().getShape().isValid()"})
        protected void doDynamicObjectUpdateShape(PHashingCollection container, DynamicObjectStorage storage, String name, Object value) {
            /*
             * Slow path that we do not handle in compiled code. But no need to invalidate compiled
             * code.
             */
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            doDynamicObjectUncached(container, storage, name, value);
        }

        /**
         * Polymorphic inline cache for writing a property that already exists (no shape change is
         * necessary).
         */
        @Specialization(limit = "3", //
                        guards = {
                                        "wrappedString(name)",
                                        "cachedName.equals(name.getValue())",
                                        "shapeCheck(shape, storage.getStore())",
                                        "location != null",
                                        "canSet(location, value)"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        @SuppressWarnings("unused")
        protected static void doDynamicObjectExistingPStringCached(PHashingCollection container, DynamicObjectStorage storage, PString name,
                        Object value,
                        @Cached("name.getValue()") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, cachedName, value)") Location location) {
            try {
                location.set(storage.getStore(), value, shape);

            } catch (IncompatibleLocationException | FinalLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /**
         * Polymorphic inline cache for writing a property that does not exist yet (shape change is
         * necessary).
         */
        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name.getValue())",
                                        "shapeCheck(oldShape, storage.getStore())",
                                        "oldLocation == null",
                                        "canStore(newLocation, value)"
                        }, //
                        assumptions = {
                                        "oldShape.getValidAssumption()",
                                        "newShape.getValidAssumption()"
                        })
        @SuppressWarnings("unused")
        protected static void doDynamicObjectNewPStringCached(PHashingCollection container, DynamicObjectStorage storage, PString name, Object value,
                        @Cached("name.getValue()") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape oldShape,
                        @Cached("lookupLocation(oldShape, cachedName, value)") Location oldLocation,
                        @Cached("defineProperty(oldShape, cachedName, value)") Shape newShape,
                        @Cached("lookupLocation(newShape, cachedName)") Location newLocation) {
            try {
                newLocation.set(storage.getStore(), value, oldShape, newShape);

            } catch (IncompatibleLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(guards = {
                        "wrappedString(name)",
                        "storage.getStore().getShape().isValid()",
                        "!exceedsLimit(storage)"
        }, //
                        replaces = {
                                        "doDynamicObjectExistingPStringCached",
                                        "doDynamicObjectNewPStringCached"
                        })
        protected static void doDynamicObjectPStringUncached(@SuppressWarnings("unused") PHashingCollection container, DynamicObjectStorage storage, PString name, Object value) {
            storage.getStore().define(name.getValue(), value);
        }

        @Specialization(guards = {"wrappedString(name)", "storage.getStore().getShape().isValid()", "exceedsLimit(storage)"})
        protected void doDynamicObjectPStringGeneralize(PHashingCollection container, DynamicObjectStorage storage, PString name, Object value) {
            switchToEconomicMap(container, storage).setItem(name.getValue(), value, getEquivalence());
        }

        @TruffleBoundary
        @Specialization(guards = {"wrappedString(name)", "!storage.getStore().getShape().isValid()"})
        protected void doDynamicObjectPStringUpdateShape(PHashingCollection container, DynamicObjectStorage storage, PString name, Object value) {
            /*
             * Slow path that we do not handle in compiled code. But no need to invalidate compiled
             * code.
             */
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            doDynamicObjectPStringUncached(container, storage, name, value);
        }

        @Specialization
        protected void doLocalsStringGeneralize(PHashingCollection container, LocalsStorage storage, String key, Object value) {
            switchToFastDictStorage(container, storage).setItem(key, value, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        protected void doLocalsPStringGeneralize(PHashingCollection container, LocalsStorage storage, PString key, Object value) {
            switchToFastDictStorage(container, storage).setItem(key.getValue(), value, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        protected void doLocalsGeneralize(PHashingCollection container, LocalsStorage storage, Object key, Object value) {
            switchToEconomicMap(container, storage).setItem(key, value, getEquivalence());
        }

        @Specialization
        protected void doKeywordsStringGeneralize(PHashingCollection container, KeywordsStorage storage, String key, Object value) {
            switchToFastDictStorage(container, storage).setItem(key, value, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        protected void doKeywordsPStringGeneralize(PHashingCollection container, KeywordsStorage storage, PString key, Object value) {
            switchToFastDictStorage(container, storage).setItem(key, value, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        @SuppressWarnings("unused")
        protected void doDynamicObjectGeneralize(PHashingCollection container, PythonObjectDictStorage storage, Object key, Object value) {
            throw raise(KeyError, "unsupported key: %p", key);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        protected void doDynamicObjectGeneralize(PHashingCollection container, FastDictStorage storage, Object key, Object value) {
            switchToEconomicMap(container, storage).setItem(key, value, getEquivalence());
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        protected void doKeywordsGeneralize(PHashingCollection container, KeywordsStorage storage, Object key, Object value) {
            // immediately replace storage since keywords storage is immutable
            EconomicMapStorage newStorage = EconomicMapStorage.create(storage.length() + 1, false);
            newStorage.addAll(storage, getEquivalence());
            newStorage.setItem(key, value, getEquivalence());
            container.setDictStorage(newStorage);
        }

        @Specialization(guards = "isHashable(key)")
        protected void doHashMap(@SuppressWarnings("unused") PHashingCollection container, EconomicMapStorage storage, Object key, Object value) {
            storage.setItem(key, value, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        protected void doHashMap(@SuppressWarnings("unused") PHashingCollection container, HashMapStorage storage, Object key, Object value) {
            storage.setItem(key, value, getEquivalence());
        }

        @Specialization(guards = "!isHashable(key)")
        @SuppressWarnings("unused")
        protected void doUnhashable(PHashingCollection container, HashingStorage storage, Object key, Object value) {
            throw unhashable(key);
        }

        /**
         * Try to find the given property in the shape. Also returns null when the value cannot be
         * store into the location.
         */
        protected static Location lookupLocation(Shape shape, Object name, Object value) {
            Location location = DictStorageBaseNode.lookupLocation(shape, name);
            if (location == null || !location.canSet(value)) {
                /* Existing property has an incompatible type, so a shape change is necessary. */
                return null;
            }

            return location;
        }

        protected static Shape defineProperty(Shape oldShape, Object name, Object value) {
            return oldShape.defineProperty(name, value, 0);
        }

        protected static boolean canSet(Location location, Object value) {
            return location.canSet(value);
        }

        protected static boolean canStore(Location location, Object value) {
            return location.canStore(value);
        }

        protected static boolean exceedsLimit(DynamicObjectStorage storage) {
            return storage instanceof FastDictStorage && storage.length() + 1 >= DynamicObjectStorage.SIZE_THRESHOLD;
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    public abstract static class GetItemNode extends DictStorageBaseNode {

        public abstract Object execute(HashingStorage storage, Object key);

        @Specialization(guards = "isHashable(key)")
        @SuppressWarnings("unused")
        Object doEmptyStorage(EmptyStorage storage, Object key) {
            return null;
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static Object doDynamicObjectString(DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name)") Location location) {

            return location != null ? location.get(storage.getStore(), shape) : null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectString"}, guards = "storage.getStore().getShape().isValid()")
        protected Object doDynamicObjectUncached(DynamicObjectStorage storage, String name) {
            return storage.getStore().get(name);
        }

        @Specialization(guards = "!storage.getStore().getShape().isValid()")
        protected Object doDynamicObjectUpdateShape(DynamicObjectStorage storage, String name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return doDynamicObjectUncached(storage, name);
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "wrappedString(name)",
                                        "cachedName.equals(name.getValue())",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static Object doDynamicObjectPString(DynamicObjectStorage storage, @SuppressWarnings("unused") PString name,
                        @SuppressWarnings("unused") @Cached("name.getValue()") String cachedName,
                        @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, cachedName)") Location location) {

            return location != null ? location.get(storage.getStore(), shape) : null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectPString"}, guards = {"wrappedString(name)", "storage.getStore().getShape().isValid()"})
        protected Object doDynamicObjectUncachedPString(DynamicObjectStorage storage, PString name) {
            return storage.getStore().get(name.getValue());
        }

        @Specialization(guards = {"wrappedString(name)", "!storage.getStore().getShape().isValid()"})
        protected Object doDynamicObjectUpdateShapePString(DynamicObjectStorage storage, PString name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return doDynamicObjectUncachedPString(storage, name);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        @SuppressWarnings("unused")
        Object doDynamicObject(DynamicObjectStorage storage, Object key) {
            return null;
        }

        @Specialization
        Object doKeywordsString(KeywordsStorage storage, String key) {
            return storage.getItem(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        Object doKeywordsString(KeywordsStorage storage, PString key) {
            return storage.getItem(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        @SuppressWarnings("unused")
        Object doKeywordsObject(KeywordsStorage storage, Object key) {
            return null;
        }

        @Specialization
        Object doLocalsString(LocalsStorage storage, String key) {
            return storage.getItem(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        Object doLocalsString(LocalsStorage storage, PString key) {
            return storage.getItem(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "isHashable(key)"})
        @SuppressWarnings("unused")
        Object doLocalsObject(LocalsStorage storage, Object key) {
            return null;
        }

        @Specialization(guards = "isHashable(key)")
        Object doGeneric(EconomicMapStorage storage, Object key) {
            return storage.getItem(key, getEquivalence());
        }

        @Specialization(guards = "isHashable(key)")
        Object doGeneric(HashMapStorage storage, Object key) {
            return storage.getItem(key, getEquivalence());
        }

        @Specialization(guards = "!isHashable(key)")
        Object doUnhashable(@SuppressWarnings("unused") HashingStorage storage, Object key) {
            throw unhashable(key);
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create();
        }
    }

    public abstract static class EqualsNode extends DictStorageBaseNode {
        @Child private GetItemNode getLeftItemNode;
        @Child private GetItemNode getRightItemNode;

        public abstract boolean execute(HashingStorage selfStorage, HashingStorage other);

        private GetItemNode getLeftItemNode() {
            if (getLeftItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getLeftItemNode = insert(GetItemNode.create());
            }
            return getLeftItemNode;
        }

        private GetItemNode getRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemNode.create());
            }
            return getRightItemNode;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(LocalsStorage selfStorage, LocalsStorage other) {
            if (selfStorage.getFrame().getFrameDescriptor() == other.getFrame().getFrameDescriptor()) {
                return doKeywordsString(selfStorage, other);
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(DynamicObjectStorage selfStorage, DynamicObjectStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    Object leftItem = getLeftItemNode().execute(selfStorage, key);
                    Object rightItem = getRightItemNode().execute(other, key);
                    if (rightItem == null || !getEquivalence().equals(leftItem, rightItem)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(HashingStorage selfStorage, HashingStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    Object leftItem = getLeftItemNode().execute(selfStorage, key);
                    Object rightItem = getRightItemNode().execute(other, key);
                    if (rightItem == null || !getEquivalence().equals(leftItem, rightItem)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean doGeneric(HashingStorage selfStorage, HashingStorage other) {
            return false;
        }

        public static EqualsNode create() {
            return EqualsNodeGen.create();
        }
    }

    public abstract static class KeysEqualsNode extends DictStorageBaseNode {
        @Child private ContainsKeyNode rightContainsKeyNode;

        public abstract boolean execute(HashingStorage selfStorage, HashingStorage other);

        private ContainsKeyNode getRightContainsKeyNode() {
            if (rightContainsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightContainsKeyNode = insert(ContainsKeyNode.create());
            }
            return rightContainsKeyNode;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(LocalsStorage selfStorage, LocalsStorage other) {
            if (selfStorage.getFrame().getFrameDescriptor() == other.getFrame().getFrameDescriptor()) {
                return doKeywordsString(selfStorage, other);
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(DynamicObjectStorage selfStorage, DynamicObjectStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    if (!getRightContainsKeyNode().execute(other, key)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(HashingStorage selfStorage, HashingStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    if (!getRightContainsKeyNode().execute(other, key)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean doGeneric(HashingStorage selfStorage, HashingStorage other) {
            return false;
        }

        public static KeysEqualsNode create() {
            return KeysEqualsNodeGen.create();
        }
    }

    public abstract static class DelItemNode extends DictStorageBaseNode {

        public abstract boolean execute(PHashingCollection dict, HashingStorage dictStorage, Object key);

        @SuppressWarnings("unused")
        @Specialization
        protected boolean doEmptyStorage(PHashingCollection container, EmptyStorage storage, Object value) {
            return false;
        }

        @Specialization
        protected boolean doKeywordsString(PHashingCollection container, KeywordsStorage storage, String key) {
            if (storage.hasKey(key, DEFAULT_EQIVALENCE)) {
                switchToFastDictStorage(container, storage).remove(key, DEFAULT_EQIVALENCE);
                return true;
            }
            return false;
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doKeywordsPString(PHashingCollection container, KeywordsStorage storage, PString key) {
            if (storage.hasKey(key.getValue(), DEFAULT_EQIVALENCE)) {
                switchToFastDictStorage(container, storage).remove(key.getValue(), DEFAULT_EQIVALENCE);
                return true;
            }
            return false;
        }

        @Specialization
        protected boolean doLocalsString(PHashingCollection container, LocalsStorage storage, String key) {
            if (storage.hasKey(key, DEFAULT_EQIVALENCE)) {
                switchToFastDictStorage(container, storage).remove(key, DEFAULT_EQIVALENCE);
                return true;
            }
            return false;
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doLocalsPString(PHashingCollection container, LocalsStorage storage, PString key) {
            if (storage.hasKey(key.getValue(), DEFAULT_EQIVALENCE)) {
                switchToFastDictStorage(container, storage).remove(key.getValue(), DEFAULT_EQIVALENCE);
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doKeywords(PHashingCollection container, KeywordsStorage storage, Object key) {
            // again: nothing to do since keywords storage can only contain String keys
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doLocals(PHashingCollection container, LocalsStorage storage, Object key) {
            return false;
        }

        @Specialization
        protected boolean doDynamicObjectString(@SuppressWarnings("unused") PDict container, DynamicObjectStorage storage, String key) {
            return storage.remove(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doDynamicObjectPString(@SuppressWarnings("unused") PDict container, DynamicObjectStorage storage, PString key) {
            return storage.remove(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doDynamicObject(PHashingCollection container, DynamicObjectStorage storage, Object key) {
            return false;
        }

        @Specialization
        protected boolean doEconomicMap(@SuppressWarnings("unused") PHashingCollection container, EconomicMapStorage storage, Object key) {
            return storage.remove(key, getEquivalence());
        }

        @Specialization
        protected boolean doHashMap(@SuppressWarnings("unused") PHashingCollection container, HashMapStorage storage, Object key) {
            return storage.remove(key, getEquivalence());
        }

        public static DelItemNode create() {
            return DelItemNodeGen.create();
        }
    }

    public abstract static class CopyNode extends DictStorageBaseNode {

        public abstract HashingStorage execute(HashingStorage storage);

        @Specialization
        HashingStorage doLocals(EmptyStorage storage) {
            // immutable, just reuse
            return storage;
        }

        @Specialization
        HashingStorage doKeywords(KeywordsStorage storage) {
            // immutable, just reuse
            return storage;
        }

        @Specialization
        HashingStorage doLocals(LocalsStorage storage) {
            // immutable, just reuse
            return storage;
        }

        @Specialization
        HashingStorage doDynamicObject(DynamicObjectStorage storage) {
            return storage.copy(DEFAULT_EQIVALENCE);
        }

        @Fallback
        HashingStorage doGeneric(HashingStorage storage) {
            return storage.copy(getEquivalence());
        }

        public static CopyNode create() {
            return CopyNodeGen.create();
        }
    }

    public static class IntersectNode extends Node {

        @Child private ContainsKeyNode containsKeyNode;
        @Child private SetItemNode setItemNode;

        public HashingStorage execute(HashingStorage left, HashingStorage right) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(false);
            if (left.length() != 0 && right.length() != 0) {
                if (containsKeyNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    containsKeyNode = insert(ContainsKeyNode.create());
                }
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(SetItemNode.create());
                }

                for (Object leftKey : left.keys()) {
                    if (containsKeyNode.execute(right, leftKey)) {
                        setItemNode.execute(null, newStorage, leftKey, PNone.NO_VALUE);
                    }
                }
            }
            return newStorage;
        }

        public static IntersectNode create() {
            return new IntersectNode();
        }
    }

    public static class UnionNode extends Node {

        public HashingStorage execute(HashingStorage left, HashingStorage right) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(false);
            newStorage.addAll(left);
            newStorage.addAll(right);
            return newStorage;
        }

        public static UnionNode create() {
            return new UnionNode();
        }
    }

    public static class ExclusiveOrNode extends Node {
        @Child private ContainsKeyNode containsKeyNode;
        @Child private SetItemNode setItemNode;

        public HashingStorage execute(HashingStorage left, HashingStorage right) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(false);
            if (left.length() != 0 && right.length() != 0) {
                if (containsKeyNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    containsKeyNode = insert(ContainsKeyNode.create());
                }
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(SetItemNode.create());
                }

                for (Object leftKey : left.keys()) {
                    if (!containsKeyNode.execute(right, leftKey)) {
                        setItemNode.execute(null, newStorage, leftKey, PNone.NO_VALUE);
                    }
                }
                for (Object rightKey : right.keys()) {
                    if (!containsKeyNode.execute(left, rightKey)) {
                        setItemNode.execute(null, newStorage, rightKey, PNone.NO_VALUE);
                    }
                }
            }
            return newStorage;
        }

        public static ExclusiveOrNode create() {
            return new ExclusiveOrNode();
        }
    }

    public abstract static class DiffNode extends DictStorageBaseNode {

        public abstract HashingStorage execute(HashingStorage left, HashingStorage right);

        @Specialization(guards = "left.length() == 0")
        @SuppressWarnings("unused")
        public HashingStorage doLeftEmpty(HashingStorage left, HashingStorage right) {
            return EconomicMapStorage.create(false);
        }

        @Specialization(guards = "right.length() == 0")
        public HashingStorage doRightEmpty(HashingStorage left, @SuppressWarnings("unused") HashingStorage right) {
            return left.copy(getEquivalence());
        }

        @Specialization(guards = {"left.length() != 0", "right.length() != 0"})
        public HashingStorage doNonEmpty(HashingStorage left, HashingStorage right,
                        @Cached("create()") ContainsKeyNode containsKeyNode,
                        @Cached("create()") SetItemNode setItemNode) {

            EconomicMapStorage newStorage = EconomicMapStorage.create(false);
            for (Object leftKey : left.keys()) {
                if (!containsKeyNode.execute(right, leftKey)) {
                    setItemNode.execute(null, newStorage, leftKey, PNone.NO_VALUE);
                }
            }
            return newStorage;
        }

        public static DiffNode create() {
            return DiffNodeGen.create();
        }
    }

}
