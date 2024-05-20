/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
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

    final Map<String, Integer> slots;
    final RootNode root;
    final Frame frame;
    final SourceSection sourceSection;

    PythonLocalScope(Map<String, Integer> slotsMap, RootNode root, Frame frame) {
        assert root != null;
        this.slots = slotsMap;
        this.root = root;
        this.sourceSection = root.getSourceSection();
        this.frame = frame;
    }

    @TruffleBoundary
    static PythonLocalScope createLocalScope(RootNode root, MaterializedFrame frame) {
        LinkedHashMap<String, Integer> slotsMap = new LinkedHashMap<>();

        FrameDescriptor fd = frame == null ? root.getFrameDescriptor() : frame.getFrameDescriptor();
        for (int slot = 0; slot < fd.getNumberOfSlots(); slot++) {
            Object identifier = fd.getSlotName(slot);
            if (identifier != null && (frame == null || frame.getValue(slot) != null)) {
                slotsMap.put(identifier.toString(), slot);
            }
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
    Integer findFrameSlot(String member) {
        return slots.get(member);
    }

    private boolean hasFrame() {
        return frame != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
        if (hasFrame()) {
            Integer slot = findFrameSlot(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return frame.getValue(slot);
            }
        } else {
            throw UnsupportedMessageException.create();
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
    @TruffleBoundary
    void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
        if (hasFrame()) {
            Integer slot = findFrameSlot(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                frame.setObject(slot, PForeignToPTypeNode.getUncached().executeConvert(value));
            }
        } else {
            throw UnsupportedMessageException.create();

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
