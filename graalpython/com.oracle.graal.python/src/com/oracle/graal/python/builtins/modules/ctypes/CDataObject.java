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
package com.oracle.graal.python.builtins.modules.ctypes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public class CDataObject extends PythonBuiltinObject {

    final Pointer b_ptr; /* pointer to memory block */
    final boolean b_needsfree; /* need _we_ free the memory? */
    CDataObject b_base; /* pointer to base object or NULL */
    int b_size; /* size of memory block in bytes */
    int b_length; /* number of references we need */
    int b_index; /* index of this object into base's b_object list */
    Object b_objects; /* dictionary of references we need to keep, or Py_None */

    public CDataObject(Object cls, Shape instanceShape, Pointer b_ptr, int b_size, boolean b_needsfree) {
        super(cls, instanceShape);
        this.b_ptr = b_ptr;
        this.b_size = b_size;
        this.b_needsfree = b_needsfree;
    }

    public static CDataObjectWrapper createWrapper(Object delegate, StgDictObject dictObject, byte[] storage) {
        return new CDataObjectWrapper(delegate, dictObject, storage);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    Object acquire(@SuppressWarnings("unused") int flags) {
        return this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return b_size;
    }

    @ExportMessage
    TruffleString getFormatString(
                    @Bind("$node") Node inliningTarget,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached StgDictBuiltins.PyTypeStgDictNode stgDictNode) {
        Object itemType = getClassNode.execute(inliningTarget, this);
        StgDictObject dict = stgDictNode.execute(inliningTarget, itemType);
        return dict.format;
    }

    @ExportMessage
    int getItemSize(
                    @Bind("$node") Node inliningTarget,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached StgDictBuiltins.PyTypeStgDictNode stgDictNode,
                    @Cached PyObjectTypeCheck typeCheck) {
        Object itemType = getClassNode.execute(inliningTarget, this);
        while (typeCheck.execute(inliningTarget, itemType, PythonBuiltinClassType.PyCArrayType)) {
            StgDictObject stgDict = stgDictNode.execute(inliningTarget, itemType);
            itemType = stgDict.proto;
        }
        StgDictObject itemDict = stgDictNode.execute(inliningTarget, itemType);
        return itemDict.size;
    }

    @ExportMessage
    byte readByte(int byteIndex,
                    @Bind("$node") Node inliningTarget,
                    @Cached PointerNodes.ReadByteNode readByteNode) {
        return readByteNode.execute(inliningTarget, b_ptr.withOffset(byteIndex));
    }

    @ExportMessage
    void readIntoByteArray(int srcOffset, byte[] dest, int destOffset, int length,
                    @Bind("$node") Node inliningTarget,
                    @Cached PointerNodes.ReadBytesNode readBytesNode) {
        readBytesNode.execute(inliningTarget, dest, destOffset, b_ptr.withOffset(srcOffset), length);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    void writeByte(int byteIndex, byte value,
                    @Bind("$node") Node inliningTarget,
                    @Shared @Cached PointerNodes.WriteBytesNode writeBytesNode) {
        writeBytesNode.execute(inliningTarget, b_ptr.withOffset(byteIndex), new byte[]{value});
    }

    @ExportMessage
    void writeFromByteArray(int destOffset, byte[] src, int srcOffset, int length,
                    @Bind("$node") Node inliningTarget,
                    @Shared @Cached PointerNodes.WriteBytesNode writeBytesNode) {
        writeBytesNode.execute(inliningTarget, b_ptr.withOffset(destOffset), src, srcOffset, length);
    }

    // TODO we could expose the internal array if available

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    public static final class CDataObjectWrapper extends PythonAbstractObjectNativeWrapper {

        private final byte[] storage;
        private final StgDictObject stgDict;

        private String[] members;

        public CDataObjectWrapper(Object delegate, StgDictObject stgDict, byte[] storage) {
            super(delegate);
            this.storage = storage;
            assert stgDict != null;
            this.stgDict = stgDict;
        }

        private int getIndex(String field, CastToJavaStringNode toJavaStringNode) {
            String[] fields = getMembers(true, toJavaStringNode);
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].equals(field)) {
                    return i;
                }
            }
            return -1;
        }

        @ExportMessage
        boolean hasMembers() {
            return this.stgDict.fieldsNames.length > 0;
        }

        @ExportMessage
        String[] getMembers(@SuppressWarnings("unused") boolean includeInternal,
                        @Shared @Cached CastToJavaStringNode toJavaStringNode) {
            if (members == null) {
                members = new String[this.stgDict.fieldsNames.length];
                for (int i = 0; i < this.stgDict.fieldsNames.length; i++) {
                    members[i] = toJavaStringNode.execute(this.stgDict.fieldsNames[i]);
                }
            }
            return members;
        }

        @ExportMessage
        boolean isMemberReadable(String member,
                        @Shared @Cached CastToJavaStringNode toJavaStringNode) {
            return getIndex(member, toJavaStringNode) != -1;
        }

        @ExportMessage
        boolean isMemberModifiable(String member,
                        @Shared @Cached CastToJavaStringNode toJavaStringNode) {
            return isMemberReadable(member, toJavaStringNode);
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        Object readMember(String member,
                        @Shared @Cached CastToJavaStringNode toJavaStringNode) throws UnknownIdentifierException {
            int idx = getIndex(member, toJavaStringNode);
            if (idx != -1) {
                return CtypesNodes.getValue(stgDict.fieldsTypes[idx], storage, stgDict.fieldsOffsets[idx]);
            }
            throw UnknownIdentifierException.create(member);
        }

        @ExportMessage
        void writeMember(String member, Object value,
                        @Shared @Cached CastToJavaStringNode toJavaStringNode) throws UnknownIdentifierException {
            int idx = getIndex(member, toJavaStringNode);
            if (idx != -1) {
                CtypesNodes.setValue(stgDict.fieldsTypes[idx], storage, stgDict.fieldsOffsets[idx], value);
                return;
            }
            throw UnknownIdentifierException.create(member);
        }

        // TO POINTER / AS POINTER / TO NATIVE

        @ExportMessage
        boolean isPointer() {
            return isNative();
        }

        @ExportMessage
        long asPointer() {
            return getNativePointer();
        }

        @ExportMessage
        void toNative(
                        @Bind("$node") Node inliningTarget,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached CApiTransitions.FirstToNativeNode firstToNativeNode) {
            if (!isNative(inliningTarget, isNativeProfile)) {
                setNativePointer(firstToNativeNode.execute(inliningTarget, this));
            }
        }
    }
}
