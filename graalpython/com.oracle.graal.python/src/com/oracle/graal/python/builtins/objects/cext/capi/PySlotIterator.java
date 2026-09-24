/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef_Slot__slot;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef_Slot__value;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySlot__sl_id;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PySlot__sl_uint64;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayPtrField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayShortField;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/** Java counterpart of CPython's {@code _PySlotIterator}; type slots can be added later. */
final class PySlotIterator {
    static final int SLOT_PY_MOD_CREATE = 1;
    static final int SLOT_PY_MOD_EXEC = 2;
    static final int SLOT_PY_MOD_MULTIPLE_INTERPRETERS = 3;
    static final int SLOT_PY_MOD_GIL = 4;
    static final int SLOT_PY_MOD_CREATE_NEW = 84;
    static final int SLOT_PY_MOD_EXEC_NEW = 85;
    static final int SLOT_PY_MOD_MULTIPLE_INTERPRETERS_NEW = 86;
    static final int SLOT_PY_MOD_GIL_NEW = 87;
    static final int SLOT_SUBSLOTS = 92;
    static final int SLOT_PY_MOD_SLOTS = 94;
    static final int SLOT_INVALID = 0xffff;
    static final int SLOT_PY_MOD_NAME = 100;
    static final int SLOT_PY_MOD_DOC = 101;
    static final int SLOT_PY_MOD_STATE_SIZE = 102;
    static final int SLOT_PY_MOD_METHODS = 103;
    static final int SLOT_PY_MOD_STATE_TRAVERSE = 104;
    static final int SLOT_PY_MOD_STATE_CLEAR = 105;
    static final int SLOT_PY_MOD_STATE_FREE = 106;
    static final int SLOT_PY_MOD_ABI = 109;
    static final int SLOT_PY_MOD_TOKEN = 110;

    static final int PY_SLOT_OPTIONAL = 0x0001;
    static final int PY_SLOT_STATIC = 0x0002;
    static final int PY_SLOT_INTPTR = 0x0004;
    private static final int MAX_NESTING = 5;

    enum SlotKind {
        MODULE,
        TYPE
    }

    private enum StructKind {
        SLOT,
        MODULE_DEF_SLOT,
        TYPE_SLOT
    }

    static final class Slot {
        private final int id;
        private final int flags;
        private final long value;

        private Slot(int id, int flags, long value) {
            this.id = id;
            this.flags = flags;
            this.value = value;
        }

        int id() {
            return id;
        }

        int flags() {
            return flags;
        }

        long pointer() {
            return value;
        }

        long function() {
            return value;
        }

        long size() {
            return value;
        }

        long unsigned() {
            return value;
        }
    }

    private static final class State {
        private long slots;
        private int index;
        private StructKind structKind;

        private State(long slots, StructKind structKind) {
            this.slots = slots;
            this.structKind = structKind;
        }
    }

    private final Node node;
    private final TruffleString name;
    private final SlotKind kind;
    private final State[] states = new State[MAX_NESTING];
    private final boolean[] seen = new boolean[SLOT_PY_MOD_TOKEN + 1];
    private int level;
    private Slot current;

    private PySlotIterator(Node node, TruffleString name, long slots, SlotKind kind, StructKind structKind) {
        this.node = node;
        this.name = name;
        this.kind = kind;
        states[0] = new State(slots, structKind);
    }

    static PySlotIterator init(Node node, TruffleString name, long slots, SlotKind kind) {
        return new PySlotIterator(node, name, slots, kind, StructKind.SLOT);
    }

    static PySlotIterator initLegacy(Node node, TruffleString name, long slots, SlotKind kind) {
        return new PySlotIterator(node, name, slots, kind, StructKind.MODULE_DEF_SLOT);
    }

    Slot current() {
        return current;
    }

    boolean sawSlot(int id) {
        return id > 0 && id < seen.length && seen[id];
    }

    boolean next() {
        while (true) {
            State state = states[level];
            if (state.slots == NULLPTR) {
                if (level == 0) {
                    return false;
                }
                level--;
                states[level].index++;
                continue;
            }
            int originalId;
            int flags;
            long value;
            if (state.structKind == StructKind.SLOT) {
                originalId = readStructArrayShortField(state.slots, state.index, PySlot__sl_id);
                flags = readStructArrayShortField(state.slots, state.index, CFields.PySlot__sl_flags);
                value = readStructArrayLongField(state.slots, state.index, PySlot__sl_uint64);
            } else if (state.structKind == StructKind.MODULE_DEF_SLOT) {
                originalId = readStructArrayIntField(state.slots, state.index, PyModuleDef_Slot__slot);
                flags = PY_SLOT_INTPTR;
                value = readStructArrayPtrField(state.slots, state.index, PyModuleDef_Slot__value);
            } else {
                throw new UnsupportedOperationException("type slots are not supported yet");
            }
            int id = resolve(originalId);
            if (id == SLOT_INVALID) {
                if ((flags & PY_SLOT_OPTIONAL) != 0) {
                    state.index++;
                    continue;
                }
                throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, name, originalId);
            }
            if (id == 0) {
                if ((flags & PY_SLOT_OPTIONAL) != 0) {
                    throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_FLAGS_FOR_SLOT_END, flags);
                }
                state.slots = NULLPTR;
                continue;
            }
            if (id == SLOT_SUBSLOTS || id == SLOT_PY_MOD_SLOTS) {
                if (value == NULLPTR) {
                    state.index++;
                    continue;
                }
                if (states[0].structKind == StructKind.MODULE_DEF_SLOT && state.structKind == StructKind.SLOT && (flags & PY_SLOT_STATIC) == 0) {
                    throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.SLOTS_INCLUDED_FROM_MODULE_DEF_MUST_BE_STATIC);
                }
                if (++level >= MAX_NESTING) {
                    throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.MODULE_SLOTS_TOO_DEEPLY_NESTED, name);
                }
                states[level] = new State(value, id == SLOT_SUBSLOTS ? StructKind.SLOT : StructKind.MODULE_DEF_SLOT);
                continue;
            }
            current = new Slot(id, flags, value);
            state.index++;
            validate(state, originalId);
            return true;
        }
    }

    private void validate(State state, int originalId) {
        int id = current.id;
        if (id == SLOT_PY_MOD_METHODS && state.structKind == StructKind.SLOT && (current.flags & PY_SLOT_STATIC) == 0) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.MODULE_METHODS_SLOT_REQUIRES_STATIC, name);
        }
        if (current.value == NULLPTR && rejectsNull(id)) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.NULL_NOT_ALLOWED_FOR_MODULE_SLOT, name, id);
        }
        if (current.value == NULLPTR && id == SLOT_PY_MOD_CREATE && states[0].structKind == StructKind.SLOT) {
            WarningsModuleBuiltins.WarnNode.getUncached().warnFormat(null, null, DeprecationWarning, 1,
                            ErrorMessages.NULL_VALUE_IN_SLOT_DEPRECATED, originalId);
        }
        if (id != SLOT_PY_MOD_EXEC_NEW && id != SLOT_PY_MOD_ABI && sawSlot(id)) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_SLOT, name, id);
        }
        seen[id] = true;
    }

    private int resolve(int id) {
        if (kind != SlotKind.MODULE) {
            return SLOT_INVALID;
        }
        return switch (id) {
            case 0, SLOT_PY_MOD_CREATE_NEW, SLOT_PY_MOD_EXEC_NEW, SLOT_PY_MOD_MULTIPLE_INTERPRETERS_NEW, SLOT_PY_MOD_GIL_NEW, SLOT_SUBSLOTS, SLOT_PY_MOD_SLOTS,
                            SLOT_PY_MOD_NAME, SLOT_PY_MOD_DOC, SLOT_PY_MOD_STATE_SIZE, SLOT_PY_MOD_METHODS, SLOT_PY_MOD_STATE_TRAVERSE, SLOT_PY_MOD_STATE_CLEAR,
                            SLOT_PY_MOD_STATE_FREE, SLOT_PY_MOD_ABI, SLOT_PY_MOD_TOKEN -> id;
            case SLOT_PY_MOD_CREATE -> SLOT_PY_MOD_CREATE_NEW;
            case SLOT_PY_MOD_EXEC -> SLOT_PY_MOD_EXEC_NEW;
            case SLOT_PY_MOD_MULTIPLE_INTERPRETERS -> SLOT_PY_MOD_MULTIPLE_INTERPRETERS_NEW;
            case SLOT_PY_MOD_GIL -> SLOT_PY_MOD_GIL_NEW;
            default -> SLOT_INVALID;
        };
    }

    private static boolean rejectsNull(int id) {
        return switch (id) {
            case SLOT_PY_MOD_EXEC_NEW, SLOT_PY_MOD_NAME, SLOT_PY_MOD_DOC, SLOT_PY_MOD_METHODS, SLOT_PY_MOD_STATE_TRAVERSE, SLOT_PY_MOD_STATE_CLEAR,
                            SLOT_PY_MOD_STATE_FREE, SLOT_PY_MOD_ABI, SLOT_PY_MOD_TOKEN -> true;
            default -> false;
        };
    }
}
