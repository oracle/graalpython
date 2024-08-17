/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.builtins;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyListObject__allocated;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyListObject__ob_item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;
import static com.oracle.graal.python.nodes.ErrorMessages.DESCRIPTOR_REQUIRES_S_OBJ_RECEIVED_P;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.AppendNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ForeignSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/** See the docs on {@link com.oracle.graal.python.builtins.objects.list.ListBuiltins}. */
public abstract class ListNodes {

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetListStorageNode extends PNodeWithContext {

        public abstract SequenceStorage execute(Node inliningTarget, Object seq);

        @Specialization
        static SequenceStorage doPList(PList list) {
            return list.getSequenceStorage();
        }

        @InliningCutoff
        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, seq)", "interop.hasArrayElements(seq)"}, limit = "1")
        static SequenceStorage doForeign(Node inliningTarget, Object seq,
                        @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached InlinedBranchProfile errorProfile) {
            try {
                long size = interop.getArraySize(seq);
                return new ForeignSequenceStorage(seq, PInt.long2int(inliningTarget, size, errorProfile));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @InliningCutoff
        @Fallback
        static SequenceStorage doFallback(Node inliningTarget, Object seq) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, DESCRIPTOR_REQUIRES_S_OBJ_RECEIVED_P, "list", seq);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class UpdateListStorageNode extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, Object list, SequenceStorage oldStorage, SequenceStorage newStorage);

        @Specialization
        static void doPList(Node inliningTarget, PList list, SequenceStorage oldStorage, SequenceStorage newStorage,
                        @Exclusive @Cached InlinedConditionProfile generalizedProfile) {
            if (generalizedProfile.profile(inliningTarget, oldStorage != newStorage)) {
                list.setSequenceStorage(newStorage);
            }
        }

        @Fallback
        static void doForeign(Node inliningTarget, Object list, SequenceStorage oldStorage, SequenceStorage newStorage,
                        @Exclusive @Cached InlinedConditionProfile generalizedProfile,
                        @Cached ForeignSequenceStorage.ClearNode clearNode,
                        @Cached(inline = false) SequenceStorageNodes.ConcatBaseNode concatNode) {
            if (generalizedProfile.profile(inliningTarget, oldStorage != newStorage)) {
                // clear() + extend() to replace the contents
                clearNode.execute(inliningTarget, (ForeignSequenceStorage) oldStorage);
                var afterExtendStorage = concatNode.execute(oldStorage, oldStorage, newStorage);
                assert afterExtendStorage == oldStorage;
            }
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ClearListStorageNode extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, Object list);

        @Specialization
        static void doPList(PList list) {
            list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
        }

        @Fallback
        static void doForeign(Node inliningTarget, Object list,
                        @Cached GetListStorageNode getStorageNode,
                        @Cached ForeignSequenceStorage.ClearNode clearNode) {
            var sequenceStorage = getStorageNode.execute(inliningTarget, list);
            clearNode.execute(inliningTarget, (ForeignSequenceStorage) sequenceStorage);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetClassForNewListNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, Object list);

        @Specialization
        static Object doPList(Node inliningTarget, PList list,
                        @Cached GetClassNode.GetPythonObjectClassNode getClassNode) {
            return getClassNode.execute(inliningTarget, list);
        }

        @Fallback
        static Object doForeign(Object list) {
            assert InteropLibrary.getUncached().hasArrayElements(list);
            // Avoid creating a PList object with type ForeignList
            return PythonBuiltinClassType.PList;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class ConstructListNode extends PNodeWithContext {

        public abstract PList execute(Frame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static PList none(@SuppressWarnings("unused") PNone none,
                        @Bind PythonLanguage language) {
            return PFactory.createList(language);
        }

        @Specialization(guards = "isBuiltinList(list)")
        // Don't use PSequence, that might copy storages that we don't allow for lists
        static PList fromList(PList list,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached SequenceStorageNodes.CopyNode copyNode) {
            return PFactory.createList(language, copyNode.execute(inliningTarget, list.getSequenceStorage()));
        }

        @Specialization(guards = "!isNoValue(iterable)")
        static PList listIterable(VirtualFrame frame, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode createStorageFromIteratorNode,
                        @Bind PythonLanguage language) {
            Object iterObj = getIter.execute(frame, inliningTarget, iterable);
            SequenceStorage storage = createStorageFromIteratorNode.execute(frame, iterObj);
            return PFactory.createList(language, storage);
        }

        @NeverDefault
        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }

        public static ConstructListNode getUncached() {
            return ConstructListNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FastConstructListNode extends PNodeWithContext {

        public abstract PList execute(Frame frame, Node inliningTarget, Object value);

        @Specialization(guards = "isBuiltinList(value)")
        protected static PList doPList(PList value) {
            return value;
        }

        @Fallback
        protected PList doGeneric(VirtualFrame frame, Object value,
                        @Cached(inline = false) ConstructListNode constructListNode) {
            return constructListNode.execute(frame, value);
        }

    }

    /**
     * This node takes a bit of care to avoid compiling code for switching storages. In the
     * interpreter, it will use a different {@link AppendNode} than in the compiled code, so the
     * specializations for the compiler are different. This is useful, because in the interpreter we
     * will report back type and size changes to the origin of the list via {@link PList#getOrigin}
     * and {@link PList.ListOrigin#reportUpdatedCapacity}. Thus, there's a chance that the compiled
     * code will only see lists of the correct size and storage type.
     */
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 17
    @OperationProxy.Proxyable
    public abstract static class AppendNode extends PNodeWithContext {
        private static final BranchProfile[] DISABLED = new BranchProfile[]{BranchProfile.getUncached()};

        public abstract void execute(Object list, Object value);

        @NeverDefault
        public static BranchProfile[] getUpdateStoreProfile() {
            return new BranchProfile[1];
        }

        public static BranchProfile[] getUpdateStoreProfileUncached() {
            return DISABLED;
        }

        @Specialization
        public static void appendObjectGeneric(PList list, Object value,
                        @Bind("this") Node inliningTarget,
                        // @Exclusive for truffle-interpreted-performance
                        @Exclusive @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached(value = "getUpdateStoreProfile()", uncached = "getUpdateStoreProfileUncached()", dimensions = 1) BranchProfile[] updateStoreProfile) {
            if (updateStoreProfile[0] == null) {
                // Executed for the first time. We don't pollute the AppendNode specializations,
                // yet, in case we're transitioning exactly once, because we'll pontentially pass
                // that information on to the list origin and it'll never happen again.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SequenceStorage newStore = SequenceStorageNodes.AppendNode.executeUncached(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                updateStoreProfile[0] = BranchProfile.create();
                list.setSequenceStorage(newStore);
                if (list.getOrigin() != null && newStore instanceof ArrayBasedSequenceStorage newArrayBasedStore) {
                    list.getOrigin().reportUpdatedCapacity(newArrayBasedStore);
                }
            } else {
                SequenceStorage newStore = appendNode.execute(inliningTarget, list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                if (list.getSequenceStorage() != newStore) {
                    updateStoreProfile[0].enter();
                    list.setSequenceStorage(newStore);
                }
                if (CompilerDirectives.inInterpreter() && list.getOrigin() != null && newStore instanceof ArrayBasedSequenceStorage newArrayBasedStore) {
                    list.getOrigin().reportUpdatedCapacity(newArrayBasedStore);
                }
            }
        }

        @Fallback
        public static void appendObjectForeign(Object list, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetListStorageNode getStorageNode,
                        @Exclusive @Cached SequenceStorageNodes.AppendNode appendNode) {
            var storage = getStorageNode.execute(inliningTarget, list);
            SequenceStorage newStore = appendNode.execute(inliningTarget, storage, value, ListGeneralizationNode.SUPPLIER);
            assert newStore == storage;
        }

        @NeverDefault
        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    public abstract static class GetNativeListStorage extends Node {
        public abstract NativeSequenceStorage execute(PythonAbstractNativeObject list);

        @Specialization
        NativeSequenceStorage getNative(PythonAbstractNativeObject list,
                        @Cached CStructAccess.ReadPointerNode getContents,
                        @Cached CStructAccess.ReadI64Node readI64Node) {
            assert IsSubtypeNode.getUncached().execute(GetClassNode.executeUncached(list), PythonBuiltinClassType.PList);
            Object array = getContents.readFromObj(list, PyListObject__ob_item);
            int size = (int) readI64Node.readFromObj(list, PyVarObject__ob_size);
            int allocated = (int) readI64Node.readFromObj(list, PyListObject__allocated);
            return NativeObjectSequenceStorage.create(array, size, allocated, false);
        }
    }
}
