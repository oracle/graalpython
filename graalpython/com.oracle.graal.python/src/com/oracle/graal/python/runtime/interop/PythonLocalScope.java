/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.interop;

import static com.oracle.graal.python.nodes.PNodeUtil.getRootSourceSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * represents the inner local scope, which relies on the frame to retrieve variables values
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonLocalScope implements TruffleObject {

    static final int LIMIT = 3;

    final Map<String, ? extends FrameSlot> slots;
    final RootNode root;
    final Frame frame;
    final SourceSection sourceSection;

    PythonLocalScope(Map<String, ? extends FrameSlot> slotsMap, RootNode root, Frame frame) {
        assert root != null;
        this.slots = slotsMap;
        this.root = root;
        this.sourceSection = getRootSourceSection(root);
        this.frame = frame;
    }

    @TruffleBoundary
    static FrameSlot getSlot(List<? extends FrameSlot> slots, int idx) {
        return slots.get(idx);
    }

    @TruffleBoundary
    static List<? extends FrameSlot> getSlotsList(FrameDescriptor frameDescriptor) {
        return frameDescriptor.getSlots();
    }

    @TruffleBoundary
    static int getListSize(List<? extends FrameSlot> slots) {
        return slots.size();
    }

    @TruffleBoundary
    static void add(Map<String, FrameSlot> slotsMap, FrameSlot slot) {
        slotsMap.put(Objects.toString(slot.getIdentifier()), slot);
    }

    @TruffleBoundary
    static Map<String, FrameSlot> createMap(List<? extends FrameSlot> slots, int size) {
        Map<String, FrameSlot> slotsMap = new LinkedHashMap<>(size);
        for (FrameSlot slot : slots) {
            add(slotsMap, slot);
        }
        return slotsMap;
    }

    @TruffleBoundary
    static Map<String, FrameSlot> createEmptyMap() {
        return Collections.emptyMap();
    }

    @TruffleBoundary
    static Map<String, FrameSlot> createSingletonMap(FrameSlot slot) {
        return Collections.singletonMap(Objects.toString(slot.getIdentifier()), slot);
    }

    @TruffleBoundary
    static Map<String, FrameSlot> createMap(FrameSlot slot1, FrameSlot slot2, int size) {
        Map<String, FrameSlot> slotsMap = new LinkedHashMap<>(size);
        slotsMap.put(Objects.toString(slot1.getIdentifier()), slot1);
        slotsMap.put(Objects.toString(slot2.getIdentifier()), slot2);
        return slotsMap;
    }

    static PythonLocalScope createLocalScope(RootNode root, Frame frame) {
        Map<String, FrameSlot> slotsMap = null;
        FrameSlot singleSlot = null;
        FrameDescriptor frameDescriptor = frame == null ? root.getFrameDescriptor() : frame.getFrameDescriptor();
        List<? extends FrameSlot> slots = getSlotsList(frameDescriptor);
        int size = getListSize(slots);
        if (frame == null) {
            if (size > 1) {
                return new PythonLocalScope(createMap(slots, size), root, null);
            } else if (size == 1) {
                return new PythonLocalScope(createSingletonMap(getSlot(slots, 0)), root, null);
            }
        } else {
            for (int i = 0; i < size; i++) {
                // Filter out slots with null values, e.g. <return_val>.
                FrameSlot slot = getSlot(slots, i);
                if (!FrameSlotIDs.isUserFrameSlot(slot.getIdentifier()) || frame.getValue(slot) == null) {
                    continue;
                }
                if (slotsMap != null) {
                    add(slotsMap, slot);
                } else if (singleSlot == null) {
                    singleSlot = slot;
                } else {
                    slotsMap = createMap(singleSlot, slot, size);
                    singleSlot = null;
                }
            }
            if (singleSlot != null) {
                return new PythonLocalScope(createSingletonMap(singleSlot), root, frame);
            }
        }
        if (slotsMap == null) {
            slotsMap = createEmptyMap();
        }
        return new PythonLocalScope(slotsMap, root, frame);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @TruffleBoundary
    static FrameSlot findFrameSlot(PythonLocalScope scope, String member) {
        return scope.slots.get(member);
    }

    static boolean hasFrame(PythonLocalScope scope) {
        return scope.frame != null;
    }

    @ExportMessage
    static class ReadMember {

        @Specialization(guards = {"hasFrame(receiver)", "cachedMember.equals(member)"}, limit = "LIMIT")
        static Object doCached(PythonLocalScope receiver, @SuppressWarnings("unused") String member,
                        @Cached("member") String cachedMember,
                        // We cache the member's slot for fast-path access
                        @Cached(value = "findFrameSlot(receiver, member)") FrameSlot slot) throws UnknownIdentifierException {
            return doRead(receiver, cachedMember, slot);
        }

        @Specialization(guards = "hasFrame(receiver)", replaces = "doCached")
        static Object doGeneric(PythonLocalScope receiver, String member) throws UnknownIdentifierException {
            FrameSlot slot = findFrameSlot(receiver, member);
            return doRead(receiver, member, slot);
        }

        @Specialization(guards = "!hasFrame(receiver)")
        static Object error(@SuppressWarnings("unused") PythonLocalScope receiver, @SuppressWarnings("unused") String member) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        private static Object doRead(PythonLocalScope receiver, String member, FrameSlot slot) throws UnknownIdentifierException {
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return receiver.frame.getValue(slot);
            }
        }
    }

    @TruffleBoundary
    protected String[] getVariableNames() {
        return slots.keySet().toArray(new String[0]);
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(getVariableNames());
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return slots.containsKey(member);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberModifiable(String member) {
        return slots.containsKey(member) && frame != null;
    }

    @ExportMessage
    static class WriteMember {

        @Specialization(guards = {"hasFrame(receiver)", "cachedMember.equals(member)"}, limit = "LIMIT")
        static void doCached(PythonLocalScope receiver, @SuppressWarnings("unused") String member, Object value,
                        @Cached("member") String cachedMember,
                        // We cache the member's slot for fast-path access
                        @Cached(value = "findFrameSlot(receiver, member)") FrameSlot slot) throws UnknownIdentifierException {
            doWrite(receiver, cachedMember, value, slot);
        }

        @Specialization(guards = "hasFrame(receiver)", replaces = "doCached")
        static void doGeneric(PythonLocalScope receiver, String member, Object value) throws UnknownIdentifierException {
            FrameSlot slot = findFrameSlot(receiver, member);
            doWrite(receiver, member, value, slot);
        }

        @Specialization(guards = "!hasFrame(receiver)")
        static void error(@SuppressWarnings("unused") PythonLocalScope receiver, @SuppressWarnings("unused") String member,
                        @SuppressWarnings("unused") Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        private static void doWrite(PythonLocalScope receiver, String member, Object value, FrameSlot slot) throws UnknownIdentifierException {
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                receiver.frame.setObject(slot, value);
            }
        }
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return sourceSection != null;
    }

    @ExportMessage
    @TruffleBoundary
    SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (sourceSection == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceSection;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return PythonLanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        String name = root.getName();
        return name == null ? "local" : name;
    }
}
