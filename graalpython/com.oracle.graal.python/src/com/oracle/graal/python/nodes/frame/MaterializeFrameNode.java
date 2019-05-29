/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * This node makes sure that the current frame has a filled-in PFrame object with a backref
 * container that will be filled in by the caller.
 **/
public abstract class MaterializeFrameNode extends Node {

    public final PFrame execute(VirtualFrame frame, boolean markAsEscaped, Frame frameToMaterialize) {
        return execute(frame, markAsEscaped, false, frameToMaterialize);
    }

    public final PFrame execute(VirtualFrame frame, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize) {
        PFrame.Reference info = PArguments.getCurrentFrameInfo(frameToMaterialize);
        assert info != null && info.getCallNode() != null : "cannot materialize a frame without location information";
        Node callNode = info.getCallNode();
        return execute(frame, callNode, markAsEscaped, forceSync, frameToMaterialize);
    }

    public final PFrame execute(VirtualFrame frame, boolean markAsEscaped) {
        return execute(frame, markAsEscaped, frame);
    }

    public final PFrame execute(VirtualFrame frame, Node location, boolean markAsEscaped, boolean forceSync) {
        return execute(frame, location, markAsEscaped, forceSync, frame);
    }

    public abstract PFrame execute(VirtualFrame frame, Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize);

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "!inClassBody(frameToMaterialize)"})
    static PFrame freshPFrame(Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PDict locals = factory.createDictLocals(frameToMaterialize.getFrameDescriptor());
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals, false);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) == null", "inClassBody(frameToMaterialize)"})
    static PFrame freshPFrameInClassBody(Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        // the namespace argument stores the locals
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, PArguments.getArgument(frameToMaterialize, 0), true);
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, syncValuesNode);
    }

    /**
     * The only way this happens is when we created a PFrame to access (possibly custom) locals. In
     * this case, there can be no reference to the PFrame object anywhere else, yet, so we can
     * replace it.
     *
     * @see PFrame#isIncomplete
     **/
    @Specialization(guards = {"getPFrame(frameToMaterialize) != null", "!getPFrame(frameToMaterialize).hasFrame()"})
    static PFrame incompleteFrame(Node location, boolean markAsEscaped, @SuppressWarnings("unused") boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        PDict locals = factory.createDictLocals(frameToMaterialize.getFrameDescriptor());
        PFrame escapedFrame = factory.createPFrame(PArguments.getCurrentFrameInfo(frameToMaterialize), location, locals, inClassBody(frameToMaterialize));
        return doEscapeFrame(frameToMaterialize, escapedFrame, markAsEscaped, forceSync, syncValuesNode);
    }

    @Specialization(guards = {"getPFrame(frameToMaterialize) != null", "getPFrame(frameToMaterialize).hasFrame()"}, replaces = "freshPFrame")
    static PFrame alreadyEscapedFrame(@SuppressWarnings("unused") Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        // TODO: frames: update the location so the line number is correct
        PFrame pyFrame = getPFrame(frameToMaterialize);
        if (forceSync) {
            syncValuesNode.execute(pyFrame, frameToMaterialize);
        }
        if (markAsEscaped) {
            pyFrame.getRef().markAsEscaped();
        }
        return pyFrame;
    }

    @Specialization(guards = {"!inClassBody(frameToMaterialize)"}, replaces = {"freshPFrame", "alreadyEscapedFrame"})
    static PFrame notInClassBody(Node location, boolean markAsEscaped, boolean forceSync, Frame frameToMaterialize,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("syncValuesNode") @Cached SyncFrameValuesNode syncValuesNode) {
        if (getPFrame(frameToMaterialize) != null) {
            return alreadyEscapedFrame(location, markAsEscaped, forceSync, frameToMaterialize, syncValuesNode);
        } else {
            return freshPFrame(location, markAsEscaped, forceSync, frameToMaterialize, factory, syncValuesNode);
        }
    }

    private static PFrame doEscapeFrame(Frame frameToMaterialize, PFrame escapedFrame, boolean markAsEscaped, boolean forceSync, SyncFrameValuesNode syncValuesNode) {
        PFrame.Reference topFrameRef = PArguments.getCurrentFrameInfo(frameToMaterialize);
        topFrameRef.setPyFrame(escapedFrame);

        // on a freshly created PFrame, we do always sync the arguments
        syncArgs(frameToMaterialize, escapedFrame);
        if (forceSync) {
            syncValuesNode.execute(escapedFrame, frameToMaterialize);
        }
        if (markAsEscaped) {
            topFrameRef.markAsEscaped();
        }
        return escapedFrame;
    }

    private static void syncArgs(Frame frameToMaterialize, PFrame escapedFrame) {
        Object[] arguments = frameToMaterialize.getArguments();
        Object[] copiedArgs = new Object[arguments.length];

        // copy only some carefully picked internal arguments
        PArguments.setSpecialArgument(copiedArgs, PArguments.getSpecialArgument(arguments));
        PArguments.setGeneratorFrame(copiedArgs, PArguments.getGeneratorFrame(arguments));
        PArguments.setGlobals(copiedArgs, PArguments.getGlobals(arguments));
        PArguments.setClosure(copiedArgs, PArguments.getClosure(arguments));

        // copy all user arguments
        System.arraycopy(arguments, PArguments.USER_ARGUMENTS_OFFSET, copiedArgs, PArguments.USER_ARGUMENTS_OFFSET, PArguments.getUserArgumentLength(arguments));

        escapedFrame.setArguments(copiedArgs);
    }

    protected static boolean inClassBody(Frame frame) {
        return PArguments.getSpecialArgument(frame) instanceof ClassBodyRootNode;
    }

    protected static PFrame getPFrame(Frame frame) {
        return PArguments.getCurrentFrameInfo(frame).getPyFrame();
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class SyncFrameValuesNode extends Node {

        public abstract void execute(PFrame pyframe, Frame frameToSync);

        @Specialization(guards = {"hasLocalsStorage(pyFrame)", "frameToSync.getFrameDescriptor() == cachedFd"}, //
                        assumptions = "cachedFd.getVersion()", //
                        limit = "1")
        @ExplodeLoop
        static void doLocalsStorageCached(PFrame pyFrame, Frame frameToSync,
                        @Cached("frameToSync.getFrameDescriptor()") FrameDescriptor cachedFd,
                        @Cached(value = "getSlots(cachedFd)", dimensions = 1) FrameSlot[] cachedSlots) {

            try {
                LocalsStorage localsStorage = getLocalsStorage(pyFrame);
                MaterializedFrame target = localsStorage.getFrame();
                assert cachedFd == target.getFrameDescriptor();

                for (int i = 0; i < cachedSlots.length; i++) {
                    FrameSlot slot = cachedSlots[i];
                    if (frameToSync.isBoolean(slot)) {
                        target.setBoolean(slot, frameToSync.getBoolean(slot));
                    } else if (frameToSync.isByte(slot)) {
                        target.setByte(slot, frameToSync.getByte(slot));
                    } else if (frameToSync.isDouble(slot)) {
                        target.setDouble(slot, frameToSync.getDouble(slot));
                    } else if (frameToSync.isFloat(slot)) {
                        target.setFloat(slot, frameToSync.getFloat(slot));
                    } else if (frameToSync.isInt(slot)) {
                        target.setInt(slot, frameToSync.getInt(slot));
                    } else if (frameToSync.isLong(slot)) {
                        target.setLong(slot, frameToSync.getLong(slot));
                    } else if (frameToSync.isObject(slot)) {
                        target.setObject(slot, frameToSync.getObject(slot));
                    }
                }
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "hasLocalsStorage(pyFrame)", replaces = "doLocalsStorageCached")
        static void doLocalsStorageUncached(PFrame pyFrame, Frame frameToSync) {
            FrameDescriptor fd = frameToSync.getFrameDescriptor();
            FrameSlot[] cachedSlots = getSlots(fd);
            try {
                LocalsStorage localsStorage = getLocalsStorage(pyFrame);
                MaterializedFrame target = localsStorage.getFrame();
                assert fd == target.getFrameDescriptor();

                for (int i = 0; i < cachedSlots.length; i++) {
                    FrameSlot slot = cachedSlots[i];
                    if (frameToSync.isBoolean(slot)) {
                        target.setBoolean(slot, frameToSync.getBoolean(slot));
                    } else if (frameToSync.isByte(slot)) {
                        target.setByte(slot, frameToSync.getByte(slot));
                    } else if (frameToSync.isDouble(slot)) {
                        target.setDouble(slot, frameToSync.getDouble(slot));
                    } else if (frameToSync.isFloat(slot)) {
                        target.setFloat(slot, frameToSync.getFloat(slot));
                    } else if (frameToSync.isInt(slot)) {
                        target.setInt(slot, frameToSync.getInt(slot));
                    } else if (frameToSync.isLong(slot)) {
                        target.setLong(slot, frameToSync.getLong(slot));
                    } else if (frameToSync.isObject(slot)) {
                        target.setObject(slot, frameToSync.getObject(slot));
                    }
                }
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "!hasLocalsStorage(pyFrame)")
        @SuppressWarnings("unused")
        static void doGenericDict(PFrame pyFrame, Frame frameToSync) {
            // nothing to do
        }

        protected static FrameSlot[] getSlots(FrameDescriptor fd) {
            return fd.getSlots().toArray(new FrameSlot[0]);
        }

        protected static boolean hasLocalsStorage(PFrame pyFrame) {
            Object localsObject = pyFrame.getLocalsDict();
            return localsObject instanceof PDict && ((PDict) localsObject).getDictStorage() instanceof LocalsStorage;
        }

        protected static LocalsStorage getLocalsStorage(PFrame pyFrame) {
            return (LocalsStorage) ((PDict) pyFrame.getLocalsDict()).getDictStorage();
        }
    }
}
