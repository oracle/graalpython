/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public abstract class PythonLocalScope implements TruffleObject {

    private final Map<String, Integer> slots;
    private final RootNode root;
    private final SourceSection sourceSection;

    PythonLocalScope(RootNode root, Map<String, Integer> slots) {
        assert root != null;
        this.root = root;
        this.slots = slots;
        this.sourceSection = root.getSourceSection();
    }

    public static PythonLocalScope create(Node node, Frame frame) {
        assert node != null;
        assert frame != null;

        RootNode root = node.getRootNode();
        PBytecodeDSLRootNode dslRoot = PBytecodeDSLRootNode.cast(root);
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER && dslRoot != null) {
            BytecodeNode bytecodeNode = BytecodeNode.get(node);
            BytecodeFrame bytecodeFrame;
            if (bytecodeNode != null) {
                bytecodeFrame = bytecodeNode.createMaterializedFrame(0, frame.materialize());
            } else {
                assert false : String.format("root: %s, node: %s", dslRoot, node);
                bytecodeFrame = null;
            }
            return BytecodeFrameLocalScope.create(dslRoot, bytecodeFrame);
        }

        Frame actualFrame = frame;
        if (PGenerator.isGeneratorFrame(frame)) {
            actualFrame = PGenerator.getGeneratorFrame(frame);
        }
        return FrameLocalScope.create(root, actualFrame.materialize());
    }

    public static PythonLocalScope createEmpty(RootNode root) {
        return FrameLocalScope.create(root, null);
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

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
        if (hasFrame()) {
            Integer slot = findFrameSlot(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return readSlotValue(slot);
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
        return slots.containsKey(member) && hasFrame();
    }

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
        if (hasFrame()) {
            Integer slot = findFrameSlot(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                writeSlotValue(slot, PForeignToPTypeNode.getUncached().executeConvert(value));
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
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    String getLanguageId() {
        return PythonLanguage.ID;
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

    protected abstract boolean hasFrame();

    protected abstract Object readSlotValue(int slot);

    protected abstract void writeSlotValue(int slot, Object value);

    private static final class FrameLocalScope extends PythonLocalScope {
        private final MaterializedFrame frame;

        private FrameLocalScope(RootNode root, Map<String, Integer> slots, MaterializedFrame frame) {
            super(root, slots);
            this.frame = frame;
        }

        @TruffleBoundary
        private static FrameLocalScope create(RootNode root, MaterializedFrame frame) {
            LinkedHashMap<String, Integer> slotsMap = new LinkedHashMap<>();
            FrameDescriptor fd = frame == null ? root.getFrameDescriptor() : frame.getFrameDescriptor();
            int slots = fd.getNumberOfSlots();
            for (int slot = 0; slot < slots; slot++) {
                Object identifier = fd.getSlotName(slot);
                if (identifier != null && (frame == null || frame.getValue(slot) != null)) {
                    slotsMap.put(identifier.toString(), slot);
                }
            }
            return new FrameLocalScope(root, slotsMap, frame);
        }

        @Override
        protected boolean hasFrame() {
            return frame != null;
        }

        @Override
        protected Object readSlotValue(int slot) {
            return frame.getValue(slot);
        }

        @Override
        protected void writeSlotValue(int slot, Object value) {
            frame.setObject(slot, value);
        }
    }

    private static final class BytecodeFrameLocalScope extends PythonLocalScope {
        private final BytecodeFrame bytecodeFrame;

        private BytecodeFrameLocalScope(PBytecodeDSLRootNode root, Map<String, Integer> slots, BytecodeFrame bytecodeFrame) {
            super(root, slots);
            this.bytecodeFrame = bytecodeFrame;
        }

        @TruffleBoundary
        private static BytecodeFrameLocalScope create(PBytecodeDSLRootNode root, BytecodeFrame bytecodeFrame) {
            LinkedHashMap<String, Integer> slotsMap = new LinkedHashMap<>();
            if (bytecodeFrame != null) {
                BytecodeDSLCodeUnit codeUnit = root.getCodeUnit();
                int varIndex = 0;
                varIndex = addSlots(varIndex, codeUnit.varnames, bytecodeFrame, slotsMap);
                varIndex = addSlots(varIndex, codeUnit.cellvars, bytecodeFrame, slotsMap);
                addSlots(varIndex, codeUnit.freevars, bytecodeFrame, slotsMap);
            }
            return new BytecodeFrameLocalScope(root, slotsMap, bytecodeFrame);
        }

        private static int addSlots(int startIndex, TruffleString[] names, BytecodeFrame frame, LinkedHashMap<String, Integer> slots) {
            int varIndex = startIndex;
            for (TruffleString name : names) {
                if (frame.getLocalValue(varIndex) != null) {
                    slots.put(name.toJavaStringUncached(), varIndex);
                }
                varIndex++;
            }
            return varIndex;
        }

        @Override
        protected boolean hasFrame() {
            return bytecodeFrame != null;
        }

        @Override
        protected Object readSlotValue(int slot) {
            return bytecodeFrame.getLocalValue(slot);
        }

        @Override
        protected void writeSlotValue(int slot, Object value) {
            bytecodeFrame.setLocalValue(slot, value);
        }
    }
}
