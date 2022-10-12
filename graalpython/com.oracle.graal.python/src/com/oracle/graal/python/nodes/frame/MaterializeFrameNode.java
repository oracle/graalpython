/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNodeGen.SyncFrameValuesNodeGen;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This node makes sure that the current frame has a filled-in PFrame object with a backref
 * container that will be filled in by the caller.
 **/
@ReportPolymorphism
@GenerateUncached
public abstract class MaterializeFrameNode extends Node {

    public static MaterializeFrameNode create() {
        return MaterializeFrameNodeGen.create();
    }

    public static MaterializeFrameNode getUncached() {
        return MaterializeFrameNodeGen.getUncached();
    }

    public final PFrame execute(Frame frame, boolean markAsEscaped, Frame frameToMaterialize) {
        return execute(frame, markAsEscaped, false, frameToMaterialize);
    }

    public final PFrame execute(Frame frame, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        PFrame.Reference info = PArguments.getCurrentFrameInfo(frameToMaterialize);
        assert info != null && info.getCallNode() != null : "cannot materialize a frame without location information";
        Node callNode = info.getCallNode();
        return execute(frame, callNode, markAsEscaped, forceSync, false, frameToMaterialize);
    }

    public final PFrame execute(Frame frame, boolean markAsEscaped) {
        return execute(frame, markAsEscaped, frame);
    }

    public final PFrame execute(Frame frame, Node location, boolean markAsEscaped, boolean forceSync) {
        return execute(frame, location, markAsEscaped, forceSync, false);
    }

    public final PFrame execute(Frame frame, Node location, boolean markAsEscaped, boolean forceSync, boolean updateLocationIfMissing) {
        return execute(frame, location, markAsEscaped, forceSync, updateLocationIfMissing, frame);
    }

    public final PFrame execute(Frame frame, Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        return execute(frame, location, markAsEscaped, forceSync, false, frameToMaterialize);
    }

    public abstract PFrame execute(Frame frame, Node location, boolean markAsEscaped, boolean forceSync, boolean updateLocationIfMissing, Frame frameToMaterialize);

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "isGeneratorFrame(frameToMaterialize)"})
    static PFrame freshPFrameForGenerator(Node location, @SuppressWarnings("unused") boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync,
                    @SuppressWarnings("unused") boolean updateLocationIfMissing, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, PArguments.getGeneratorFrameLocals(frameToMaterialize));
        PArguments.synchronizeArgs(frameToMaterialize, escapedFrame);
        PFrame.Reference topFrameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        topFrameRef.setPyFrame(escapedFrame);
        return escapedFrame;
    }

    @Specialization(guards = {"cachedFD == frameToMaterialize.getFrameDescriptor()", "getPFrame(frameToMaterialize) == null", "!isGeneratorFrame(frameToMaterialize)"}, limit = "1")
    static PFrame freshPFrameCachedFD(VirtualFrame frame, Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync,
                    @SuppressWarnings("unused") boolean updateLocationIfMissing, Frame frameToMaterialize,
                    @Cached("frameToMaterialize.getFrameDescriptor()") FrameDescriptor cachedFD,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PDict locals = factory.createDictLocals(cachedFD);
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals);
        return doEscapeFrame(frame, frameToMaterialize, escapedFrame, location, markAsEscaped, forceSync && !inModuleRoot(location) && !inClassBody(location), syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!isGeneratorFrame(frameToMaterialize)"}, replaces = "freshPFrameCachedFD")
    static PFrame freshPFrame(VirtualFrame frame, Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, @SuppressWarnings("unused") boolean updateLocationIfMissing,
                    Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PDict locals = factory.createDictLocals(frameToMaterialize.getFrameDescriptor());
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals);
        return doEscapeFrame(frame, frameToMaterialize, escapedFrame, location, markAsEscaped, forceSync && !inModuleRoot(location) && !inClassBody(location), syncValuesNode);
    }

    /**
     * The only way this happens is when we created a PFrame to access (possibly custom) locals. In
     * this case, there can be no reference to the PFrame object anywhere else, yet, so we can
     * replace it.
     *
     * @see PFrame#isIncomplete
     **/
    @Specialization(guards = {"getPFrame(frameToMaterialize) != null", "!getPFrame(frameToMaterialize).isAssociated()"})
    static PFrame incompleteFrame(VirtualFrame frame, Node location, boolean markAsEscaped, boolean forceSync, @SuppressWarnings("unused") boolean updateLocationIfMissing, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        Object locals = getPFrame(frameToMaterialize).getLocalsDict();
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals);
        return doEscapeFrame(frame, frameToMaterialize, escapedFrame, location, markAsEscaped, forceSync && !inModuleRoot(location) && !inClassBody(location), syncValuesNode);
    }

    public static boolean isBytecodeFrame(Frame frameToSync) {
        return frameToSync.getFrameDescriptor().getInfo() instanceof FrameInfo;
    }

    private static void processBytecodeFrame(Frame frameToMaterialize, PFrame pyFrame) {
        if (isBytecodeFrame(frameToMaterialize)) {
            FrameInfo info = (FrameInfo) frameToMaterialize.getFrameDescriptor().getInfo();
            pyFrame.setLasti(info.getBci(frameToMaterialize));
            pyFrame.setLocation(info.getRootNode());
        }
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) != null", "getPFrame(frameToMaterialize).isAssociated()"})
    static PFrame alreadyEscapedFrame(VirtualFrame frame, Node location, boolean markAsEscaped, boolean forceSync, boolean updateLocationIfMissing, Frame frameToMaterialize,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode,
                    @Cached ConditionProfile syncProfile) {
        PFrame pyFrame = getPFrame(frameToMaterialize);
        if (syncProfile.profile(forceSync && !inModuleRoot(location) && !inClassBody(location))) {
            syncValuesNode.execute(frame, pyFrame, frameToMaterialize, location);
        }
        if (markAsEscaped) {
            pyFrame.getRef().markAsEscaped();
        }
        if (!updateLocationIfMissing || pyFrame.getLocation() == null) {
            // update the location so the line number is correct
            pyFrame.setLocation(location);
        }
        processBytecodeFrame(frameToMaterialize, pyFrame);
        return pyFrame;
    }

    @Specialization(replaces = {"freshPFrame", "alreadyEscapedFrame", "incompleteFrame"})
    static PFrame generic(VirtualFrame frame, Node location, boolean markAsEscaped, boolean forceSync, boolean updateLocationIfMissing, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode,
                    @Cached ConditionProfile syncProfile) {
        PFrame pyFrame = getPFrame(frameToMaterialize);
        if (pyFrame != null) {
            if (pyFrame.isAssociated()) {
                return alreadyEscapedFrame(frame, location, markAsEscaped, forceSync, updateLocationIfMissing, frameToMaterialize, syncValuesNode, syncProfile);
            } else {
                return incompleteFrame(frame, location, markAsEscaped, forceSync, updateLocationIfMissing, frameToMaterialize, factory, syncValuesNode);
            }
        } else {
            if (isGeneratorFrame(frameToMaterialize)) {
                return freshPFrameForGenerator(location, markAsEscaped, forceSync, updateLocationIfMissing, frameToMaterialize, factory);
            } else {
                return freshPFrame(frame, location, markAsEscaped, forceSync, updateLocationIfMissing, frameToMaterialize, factory, syncValuesNode);
            }
        }
    }

    private static PFrame doEscapeFrame(VirtualFrame frame, Frame frameToMaterialize, PFrame escapedFrame, Node location, boolean markAsEscaped, boolean forceSync,
                    SyncFrameValuesNode syncValuesNode) {
        PFrame.Reference topFrameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        topFrameRef.setPyFrame(escapedFrame);

        // on a freshly created PFrame, we do always sync the arguments
        PArguments.synchronizeArgs(frameToMaterialize, escapedFrame);
        if (forceSync) {
            syncValuesNode.execute(frame, escapedFrame, frameToMaterialize, location);
        }
        if (markAsEscaped) {
            topFrameRef.markAsEscaped();
        }
        processBytecodeFrame(frameToMaterialize, escapedFrame);
        return escapedFrame;
    }

    protected static boolean isGeneratorFrame(Frame frame) {
        return PArguments.isGeneratorFrame(frame);
    }

    protected static PFrame getPFrame(Frame frame) {
        return PArguments.getCurrentFrameInfo(frame).getPyFrame();
    }

    protected static boolean inModuleRoot(Node location) {
        assert location != null;
        if (location instanceof PRootNode) {
            return location instanceof ModuleRootNode;
        } else {
            return location.getRootNode() instanceof ModuleRootNode;
        }
    }

    protected static boolean inClassBody(Node location) {
        assert location != null;
        if (location instanceof PRootNode) {
            return location instanceof ClassBodyRootNode;
        } else {
            return location.getRootNode() instanceof ClassBodyRootNode;
        }
    }

    /**
     * When refreshing the frame values in the locals dict, there are 4 cases:
     * <ol>
     * <li>The locals object is a {@code dict} with a {@code LocalsStorage} having the same frame
     * descriptor as the frame to synchronize. This then represents the unmodified set of frame
     * variables and need to be refreshed.</li>
     * <li>The locals object is a {@code dict} with a storage <b>different</b> to
     * {@code LocalsStorage}. This may happen if someone retrieves the locals and manually assigns
     * to the dict. Then the storage changes. In this case we must also refresh the frame values.
     * </li>
     * <li>The locals object is some arbitrary custom mapping object (excluding the above 2 cases).
     * In this case, all code was already working on the custom object and we don't need to do
     * anything.</li>
     * <li>The locals object is a {@code dict} with a {@code LocalsStorage} having a different frame
     * descriptor as the frame to synchronize. This is common when {@code exec} / {@code eval} and
     * inheriting the locals of the caller. In this case, we don't need to do anything.</li>
     * </ol>
     */
    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    public abstract static class SyncFrameValuesNode extends Node {

        public abstract void execute(Frame frame, PFrame pyframe, Frame frameToSync, Node location);

        @Specialization(guards = {"!isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)", "frameToSync.getFrameDescriptor() == cachedFd",
                        "cachedFd.getNumberOfSlots() < 32"}, limit = "1")
        @ExplodeLoop
        static void doLocalsStorageCachedAST(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile,
                        @Cached("frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd) {
            LocalsStorage localsStorage = getLocalsStorage(pyFrame);
            MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
            assert cachedFd == target.getFrameDescriptor();

            for (int slot = 0; slot < cachedFd.getNumberOfSlots(); slot++) {
                if (FrameSlotIDs.isUserFrameSlot(cachedFd.getSlotName(slot))) {
                    PythonUtils.copyFrameSlot(frameToSync, target, slot);
                }
            }
        }

        @Specialization(guards = {"!isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)", "frameToSync.getFrameDescriptor() == cachedFd"}, limit = "1")
        static void doLocalsStorageLoopAST(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile,
                        @Cached("frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd) {
            LocalsStorage localsStorage = getLocalsStorage(pyFrame);
            MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
            assert cachedFd == target.getFrameDescriptor();

            for (int slot = 0; slot < cachedFd.getNumberOfSlots(); slot++) {
                if (FrameSlotIDs.isUserFrameSlot(cachedFd.getSlotName(slot))) {
                    PythonUtils.copyFrameSlot(frameToSync, target, slot);
                }
            }
        }

        @Specialization(guards = {"!isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)"}, replaces = {"doLocalsStorageCachedAST", "doLocalsStorageLoopAST"})
        static void doLocalsStorageUncachedAST(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile) {
            FrameDescriptor fd = frameToSync.getFrameDescriptor();
            try {
                LocalsStorage localsStorage = getLocalsStorage(pyFrame);
                MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
                assert fd == target.getFrameDescriptor();

                for (int slot = 0; slot < fd.getNumberOfSlots(); slot++) {
                    if (FrameSlotIDs.isUserFrameSlot(fd.getSlotName(slot))) {
                        PythonUtils.copyFrameSlot(frameToSync, target, slot);
                    }
                }
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = { //
                        "!isBytecodeFrame(frameToSync)",
                        "isDictWithCustomStorage(pyFrame)",
                        "!hasCustomLocals(frameToSync, pyFrame)",
                        "frameToSync.getFrameDescriptor() == cachedFd",
        }, //
                        limit = "1")
        @ExplodeLoop
        static void doGenericDictCachedAST(VirtualFrame frame, PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("frameToSync.getFrameDescriptor()") @SuppressWarnings("unused") FrameDescriptor cachedFd,
                        @Cached(value = "getProfiles(cachedFd.getNumberOfSlots())", dimensions = 1) ConditionProfile[] profiles,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @Cached HashingStorageGetItem getItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            // This can happen if someone received the locals dict using 'locals()' or similar and
            // then assigned to the dictionary. Assigning will switch the storage. But we still must
            // refresh the values.

            // The cast is guaranteed by the guard.
            PDict localsDict = (PDict) pyFrame.getLocalsDict();

            for (int slot = 0; slot < cachedFd.getNumberOfSlots(); slot++) {
                Object identifier = cachedFd.getSlotName(slot);
                if (FrameSlotIDs.isUserFrameSlot(identifier)) {
                    Object value = frameToSync.getValue(slot);
                    if (value != null) {
                        setItemNode.execute(frame, localsDict, identifier, resolveCellValue(profiles[slot], value));
                    } else {
                        // delete variable
                        HashingStorage storage = localsDict.getDictStorage();
                        HashingStorage newStore = null;
                        // TODO: FIXME: this might call __hash__ twice
                        boolean hasKey = getItem.hasKey(frame, storage, identifier);
                        if (hasKey) {
                            newStore = lib.delItemWithFrame(storage, identifier, hasFrame, frame);
                        }

                        if (hasKey) {
                            if (newStore != storage) {
                                updatedStorage.enter();
                                localsDict.setDictStorage(newStore);
                            }
                        }
                    }
                }
            }
        }

        @Specialization(guards = {"!isBytecodeFrame(frameToSync)", "isDictWithCustomStorage(pyFrame)", "!hasCustomLocals(frameToSync, pyFrame)"}, replaces = "doGenericDictCachedAST")
        static void doGenericDictAST(VirtualFrame frame, PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @Cached HashingStorageGetItem getItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            // This can happen if someone received the locals dict using 'locals()' or similar and
            // then assigned to the dictionary. Assigning will switch the storage. But we still must
            // refresh the values.

            FrameDescriptor fd = frameToSync.getFrameDescriptor();
            // The cast is guaranteed by the guard.
            PDict localsDict = (PDict) pyFrame.getLocalsDict();

            for (int slot = 0; slot < fd.getNumberOfSlots(); slot++) {
                Object identifier = fd.getSlotName(slot);
                if (FrameSlotIDs.isUserFrameSlot(identifier)) {
                    Object value = frameToSync.getValue(slot);
                    if (value != null) {
                        setItemNode.execute(frame, localsDict, identifier, resolveCellValue(ConditionProfile.getUncached(), value));
                    } else {
                        // delete variable
                        HashingStorage storage = localsDict.getDictStorage();
                        Object key = identifier;
                        HashingStorage newStore = null;
                        // TODO: FIXME: this might call __hash__ twice
                        boolean hasKey = getItem.hasKey(frame, storage, identifier);
                        if (hasKey) {
                            newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
                        }

                        if (hasKey) {
                            if (newStore != storage) {
                                updatedStorage.enter();
                                localsDict.setDictStorage(newStore);
                            }
                        }
                    }
                }
            }
        }

        @Specialization(guards = {"isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)", "frameToSync.getFrameDescriptor() == cachedFd",
                        "variableSlotCount(cachedFd) < 32"}, limit = "1")
        @ExplodeLoop
        static void doLocalsStorageCachedExploded(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile,
                        @Cached("frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd) {
            LocalsStorage localsStorage = getLocalsStorage(pyFrame);
            MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
            assert cachedFd == target.getFrameDescriptor();
            int slotCount = variableSlotCount(cachedFd);
            for (int slot = 0; slot < slotCount; slot++) {
                PythonUtils.copyFrameSlot(frameToSync, target, slot);
            }
        }

        @Specialization(guards = {"isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)",
                        "frameToSync.getFrameDescriptor() == cachedFd"}, replaces = "doLocalsStorageCachedExploded", limit = "1")
        static void doLocalsStorageCachedLoop(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile,
                        @Cached("frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd) {
            LocalsStorage localsStorage = getLocalsStorage(pyFrame);
            MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
            assert cachedFd == target.getFrameDescriptor();
            int slotCount = variableSlotCount(cachedFd);
            for (int slot = 0; slot < slotCount; slot++) {
                PythonUtils.copyFrameSlot(frameToSync, target, slot);
            }
        }

        @Specialization(guards = {"isBytecodeFrame(frameToSync)", "hasLocalsStorage(pyFrame, frameToSync, frameProfile)"}, replaces = {"doLocalsStorageCachedLoop", "doLocalsStorageCachedExploded"})
        static void doLocalsStorageUncached(PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("createClassProfile()") ValueProfile frameProfile) {
            LocalsStorage localsStorage = getLocalsStorage(pyFrame);
            MaterializedFrame target = frameProfile.profile(localsStorage.getFrame());
            int slotCount = variableSlotCount(frameToSync.getFrameDescriptor());
            for (int slot = 0; slot < slotCount; slot++) {
                PythonUtils.copyFrameSlot(frameToSync, target, slot);
            }
        }

        @Specialization(guards = { //
                        "isBytecodeFrame(frameToSync)",
                        "isDictWithCustomStorage(pyFrame)",
                        "!hasCustomLocals(frameToSync, pyFrame)",
                        "frameToSync.getFrameDescriptor() == cachedFd",
                        "variableSlotCount(cachedFd) < 32"
        }, limit = "1")
        @ExplodeLoop
        static void doGenericDictCachedExploded(VirtualFrame frame, PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("frameToSync.getFrameDescriptor()") @SuppressWarnings("unused") FrameDescriptor cachedFd,
                        @Cached(value = "getProfiles(variableSlotCount(cachedFd))", dimensions = 1) ConditionProfile[] profiles,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            // This can happen if someone received the locals dict using 'locals()' or similar and
            // then assigned to the dictionary. Assigning will switch the storage. But we still must
            // refresh the values.

            // The cast is guaranteed by the guard.
            PDict localsDict = (PDict) pyFrame.getLocalsDict();
            FrameInfo info = (FrameInfo) cachedFd.getInfo();
            int slotCount = info.getVariableCount();
            for (int slot = 0; slot < slotCount; slot++) {
                ConditionProfile profile = profiles[slot];
                syncDict(frame, slot, info, frameToSync, localsDict, getItem, setItem, lib, hasFrame, updatedStorage, profile);
            }
        }

        @Specialization(guards = { //
                        "isBytecodeFrame(frameToSync)",
                        "isDictWithCustomStorage(pyFrame)",
                        "!hasCustomLocals(frameToSync, pyFrame)",
                        "frameToSync.getFrameDescriptor() == cachedFd",
        }, limit = "1", replaces = "doGenericDictCachedExploded")
        static void doGenericDictCachedLoop(VirtualFrame frame, PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached("frameToSync.getFrameDescriptor()") @SuppressWarnings("unused") FrameDescriptor cachedFd,
                        @Cached(value = "getProfiles(variableSlotCount(cachedFd))", dimensions = 1) ConditionProfile[] profiles,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            // This can happen if someone received the locals dict using 'locals()' or similar and
            // then assigned to the dictionary. Assigning will switch the storage. But we still must
            // refresh the values.

            // The cast is guaranteed by the guard.
            PDict localsDict = (PDict) pyFrame.getLocalsDict();
            FrameInfo info = (FrameInfo) cachedFd.getInfo();
            int slotCount = info.getVariableCount();
            for (int slot = 0; slot < slotCount; slot++) {
                ConditionProfile profile = profiles[slot];
                syncDict(frame, slot, info, frameToSync, localsDict, getItem, setItem, lib, hasFrame, updatedStorage, profile);
            }
        }

        @Specialization(guards = {"isBytecodeFrame(frameToSync)", "isDictWithCustomStorage(pyFrame)", "!hasCustomLocals(frameToSync, pyFrame)"}, replaces = {"doGenericDictCachedExploded",
                        "doGenericDictCachedLoop"})
        static void doGenericDict(VirtualFrame frame, PFrame pyFrame, Frame frameToSync, @SuppressWarnings("unused") Node location,
                        @Cached BranchProfile updatedStorage,
                        @Cached ConditionProfile hasFrame,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            // This can happen if someone received the locals dict using 'locals()' or similar and
            // then assigned to the dictionary. Assigning will switch the storage. But we still must
            // refresh the values.

            // The cast is guaranteed by the guard.
            PDict localsDict = (PDict) pyFrame.getLocalsDict();
            FrameInfo info = (FrameInfo) frameToSync.getFrameDescriptor().getInfo();
            int slotCount = info.getVariableCount();
            for (int slot = 0; slot < slotCount; slot++) {
                ConditionProfile profile = ConditionProfile.getUncached();
                syncDict(frame, slot, info, frameToSync, localsDict, getItem, setItem, lib, hasFrame, updatedStorage, profile);
            }
        }

        @Specialization(guards = "hasCustomLocals(frameToSync, pyFrame)")
        @SuppressWarnings("unused")
        static void doCustomLocalsObject(PFrame pyFrame, Frame frameToSync, Node location) {
            // nothing to do; we already worked on the custom object
        }

        private static void syncDict(VirtualFrame frame, int slot, FrameInfo info, Frame frameToSync, PDict localsDict,
                        HashingStorageGetItem getItem, HashingStorageSetItem setItem, HashingStorageLibrary lib,
                        ConditionProfile hasFrame, BranchProfile updatedStorage, ConditionProfile profile) {
            HashingStorage storage = localsDict.getDictStorage();
            TruffleString identifier = info.getVariableName(slot);
            Object value = frameToSync.getValue(slot);
            if (value != null && profile.profile(value instanceof PCell)) {
                value = ((PCell) value).getRef();
            }
            HashingStorage newStore;
            if (value != null) {
                newStore = setItem.execute(frame, storage, identifier, value);
                if (newStore != storage) {
                    updatedStorage.enter();
                    localsDict.setDictStorage(newStore);
                }
            } else {
                // delete variable
                // TODO: FIXME: this might call __hash__ twice
                boolean hasKey = getItem.hasKey(frame, storage, identifier);
                if (hasKey) {
                    newStore = lib.delItemWithFrame(storage, identifier, hasFrame, frame);
                    if (newStore != storage) {
                        updatedStorage.enter();
                        localsDict.setDictStorage(newStore);
                    }
                }
            }
        }

        // @ImportStatic doesn't work on this for some reason
        protected static boolean isBytecodeFrame(Frame frameToSync) {
            return MaterializeFrameNode.isBytecodeFrame(frameToSync);
        }

        protected static int variableSlotCount(FrameDescriptor fd) {
            FrameInfo info = (FrameInfo) fd.getInfo();
            return info.getVariableCount();
        }

        protected static ConditionProfile[] getProfiles(int n) {
            ConditionProfile[] profiles = new ConditionProfile[n];
            for (int i = 0; i < profiles.length; i++) {
                profiles[i] = ConditionProfile.createBinaryProfile();
            }
            return profiles;
        }

        /**
         * Guard that tests if the locals object is a dict having a {@link LocalsStorage} using the
         * same frame descriptor as the frame to synchronize. That means, the dict represents
         * unmodified set of frame values of the frame to sync.
         */
        protected static boolean hasLocalsStorage(PFrame pyFrame, Frame frameToSync, ValueProfile frameProfile) {
            Object localsObject = pyFrame.getLocalsDict();
            return localsObject instanceof PDict && getFd(((PDict) localsObject).getDictStorage(), frameProfile) == frameToSync.getFrameDescriptor();
        }

        protected static boolean isDictWithCustomStorage(PFrame pyFrame) {
            Object localsObject = pyFrame.getLocalsDict();
            // do not allow subclasses of 'dict'
            return localsObject instanceof PDict && PGuards.isBuiltinDict((PDict) localsObject);
        }

        protected static boolean hasCustomLocals(Frame frameToSync, PFrame pyFrame) {
            return PArguments.getSpecialArgument(frameToSync) == pyFrame.getLocalsDict();
        }

        protected static LocalsStorage getLocalsStorage(PFrame pyFrame) {
            return (LocalsStorage) ((PDict) pyFrame.getLocalsDict()).getDictStorage();
        }

        private static FrameDescriptor getFd(HashingStorage storage, ValueProfile frameProfile) {
            if (storage instanceof LocalsStorage) {
                return frameProfile.profile(((LocalsStorage) storage).getFrame()).getFrameDescriptor();
            }
            return null;
        }

        private static Object resolveCellValue(ConditionProfile profile, Object value) {
            if (profile.profile(value instanceof PCell)) {
                return ((PCell) value).getRef();
            }
            return value;
        }

        public static SyncFrameValuesNode create() {
            return SyncFrameValuesNodeGen.create();
        }
    }
}
