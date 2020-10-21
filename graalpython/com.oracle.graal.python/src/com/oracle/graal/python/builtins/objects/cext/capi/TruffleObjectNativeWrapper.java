/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.GP_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_BASE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_TYPE;

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ReadNativeMemberDispatchNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.WriteNativeMemberNode;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public class TruffleObjectNativeWrapper extends PythonNativeWrapper {

    // every 'PyObject *' provides 'ob_base', 'ob_type', and 'ob_refcnt'
    @CompilationFinal(dimensions = 1) private static final String[] MEMBERS = {GP_OBJECT, OB_BASE.getMemberName(), OB_TYPE.getMemberName(), OB_REFCNT.getMemberName()};

    public TruffleObjectNativeWrapper(Object foreignObject) {
        super(foreignObject);
    }

    public static TruffleObjectNativeWrapper wrap(Object foreignObject) {
        assert !CApiGuards.isNativeWrapper(foreignObject) : "attempting to wrap a native wrapper";
        return new TruffleObjectNativeWrapper(foreignObject);
    }

    @ExplodeLoop
    private static int indexOf(String member) {
        for (int i = 0; i < MEMBERS.length; i++) {
            if (MEMBERS[i].equals(member)) {
                return i;
            }
        }
        return -1;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @ExplodeLoop
    boolean isMemberReadable(String member) {
        return indexOf(member) != -1;
    }

    @ExportMessage
    boolean isMemberModifiable(String member) {
        // we only allow to write to 'ob_refcnt'
        return OB_REFCNT.getMemberName().equals(member);
    }

    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(MEMBERS);
    }

    @ExportMessage(name = "readMember")
    abstract static class ReadNode {

        @Specialization(guards = {"key == cachedObBase", "isObBase(cachedObBase)"}, limit = "1")
        static Object doObBaseCached(TruffleObjectNativeWrapper object, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedObBase) {
            return object;
        }

        @Specialization
        static Object execute(TruffleObjectNativeWrapper object, String key,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Cached ReadNativeMemberDispatchNode readNativeMemberNode) throws UnsupportedMessageException, UnknownIdentifierException {
            Object delegate = lib.getDelegate(object);

            // special key for the debugger
            if (GP_OBJECT.equals(key)) {
                return delegate;
            }
            return readNativeMemberNode.execute(delegate, object, key);
        }

        protected static boolean isObBase(String key) {
            return OB_BASE.getMemberName().equals(key);
        }
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached WriteNativeMemberNode writeNativeMemberNode) throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
        if (OB_REFCNT.getMemberName().equals(member)) {
            Object delegate = lib.getDelegate(this);
            writeNativeMemberNode.execute(delegate, this, member, value);
        } else {
            CompilerDirectives.transferToInterpreter();
            if (indexOf(member) == -1) {
                throw UnknownIdentifierException.create(member);
            }
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isPointer(
                    @Cached IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    long asPointer(
                    @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    void toNative(
                    @Cached ToNativeNode toNativeNode) {
        toNativeNode.execute(this);
    }

    @ExportMessage
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType(
                    @Cached PGetDynamicTypeNode getDynamicTypeNode) {
        return getDynamicTypeNode.execute(this);
    }
}
