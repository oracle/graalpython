/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyASCIIObject__length;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyASCIIObject__state;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyASCIIObject__state_ready_shift;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyUnicodeObject__data;
import static com.oracle.graal.python.util.PythonUtils.isBitSet;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Casts a Python string to a TruffleString without coercion. <b>ATTENTION:</b> If the cast fails,
 * because the object is not a Python string, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(PGuards.class)
public abstract class CastToTruffleStringNode extends PNodeWithContext {

    public abstract TruffleString execute(Node inliningTarget, Object x) throws CannotCastException;

    public final TruffleString executeCached(Object x) throws CannotCastException {
        return execute(this, x);
    }

    public static TruffleString executeUncached(Object x) throws CannotCastException {
        return CastToTruffleStringNodeGen.getUncached().execute(null, x);
    }

    @Specialization
    static TruffleString doTruffleString(TruffleString x) {
        return x;
    }

    @Specialization(guards = "x.isMaterialized()")
    static TruffleString doPStringMaterialized(PString x) {
        return x.getMaterialized();
    }

    @Specialization(guards = "!x.isMaterialized()")
    static TruffleString doPStringGeneric(Node inliningTarget, PString x,
                    @Cached StringMaterializeNode materializeNode) {
        return materializeNode.execute(inliningTarget, x);
    }

    @GenerateUncached
    @GenerateInline(false) // Footprint reduction 48 -> 29
    public abstract static class ReadNativeStringNode extends PNodeWithContext {

        public abstract TruffleString execute(Object pointer);

        @Specialization
        static TruffleString read(Object pointer,
                        @Cached CStructAccess.ReadI32Node readI32,
                        @Cached CStructAccess.ReadI64Node readI64,
                        @Cached CStructAccess.ReadPointerNode readPointer,
                        @Cached CStructAccess.ReadByteNode readByte,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached TruffleString.FromNativePointerNode fromNative,
                        @Cached TruffleString.FromByteArrayNode fromBytes) {
            int state = readI32.read(pointer, PyASCIIObject__state);
            boolean ready = isBitSet(state, PyASCIIObject__state_ready_shift);
            if (!ready) {
                throw CompilerDirectives.shouldNotReachHere("not implemented - need to call _PyUnicode_Ready for native string");
            }
            int kind = (state >> CFields.PyASCIIObject__state_kind_shift) & 0x7;
            Object data = readPointer.read(pointer, PyUnicodeObject__data);
            long length = readI64.read(pointer, PyASCIIObject__length);

            Encoding encoding;
            if (kind == 1) {
                // isBitSet(state, PyASCIIObject__state_ascii_shift))
                // ascii doesn't matter, codepoint 0-127 are the same in ascii and latin1
                encoding = Encoding.ISO_8859_1;
            } else if (kind == 2) {
                encoding = Encoding.UTF_16LE;
            } else {
                assert kind == 4;
                encoding = Encoding.UTF_32LE;
            }
            int bytes = PythonUtils.toIntError(length * kind);

            if (lib.isPointer(data) || data instanceof Long) {
                return fromNative.execute(data, 0, bytes, encoding, false);
            }
            byte[] result = readByte.readByteArray(pointer, bytes);
            return fromBytes.execute(result, encoding, false);
        }
    }

    @Specialization
    @InliningCutoff
    static TruffleString doNativeObject(Node inliningTarget, PythonNativeObject x,
                    @Cached GetClassNode getClassNode,
                    @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                    @Cached(inline = false) ReadNativeStringNode read) {
        if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, x), PythonBuiltinClassType.PString)) {
            return read.execute(x.getPtr());
        }
        // the object's type is not a subclass of 'str'
        throw CannotCastException.INSTANCE;
    }

    @Specialization(guards = {"!isString(x)", "!isNativeObject(x)"})
    static TruffleString doUnsupported(@SuppressWarnings("unused") Object x) {
        throw CannotCastException.INSTANCE;
    }

    @NeverDefault
    public static CastToTruffleStringNode create() {
        return CastToTruffleStringNodeGen.create();
    }

    @NeverDefault
    public static CastToTruffleStringNode getUncached() {
        return CastToTruffleStringNodeGen.getUncached();
    }
}
