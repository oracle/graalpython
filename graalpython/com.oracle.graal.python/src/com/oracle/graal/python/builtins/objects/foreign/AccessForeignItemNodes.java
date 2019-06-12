/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.GetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.RemoveForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.SetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;

abstract class AccessForeignItemNodes {

    @TypeSystemReference(PythonArithmeticTypes.class)
    protected abstract static class AccessForeignItemBaseNode extends PNodeWithContext {
        @Child PRaiseNode raiseNode;

        protected PException raise(PythonBuiltinClassType type, String msg, Object... arguments) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode.raise(type, msg, arguments);
        }

        protected static boolean isSlice(Object o) {
            return o instanceof PSlice;
        }

        protected static boolean isString(Object o) {
            return o instanceof String || o instanceof PString;
        }

        protected int getForeignSize(Object object, InteropLibrary lib) throws UnsupportedMessageException {
            long foreignSizeObj = lib.getArraySize(object);
            if (foreignSizeObj <= Integer.MAX_VALUE) {
                return (int) foreignSizeObj;
            }
            throw raise(TypeError, "number %s cannot fit into index-sized integer", foreignSizeObj);
        }

        protected SliceInfo materializeSlice(PSlice idxSlice, Object object, InteropLibrary lib) throws UnsupportedMessageException {
            int foreignSize = getForeignSize(object, lib);
            return idxSlice.computeIndices(foreignSize);
        }
    }

    protected abstract static class GetForeignItemNode extends AccessForeignItemBaseNode {
        @Child private PForeignToPTypeNode toPythonNode;

        public abstract Object execute(Object object, Object idx);

        @Specialization
        public Object doForeignObjectSlice(Object object, PSlice idxSlice,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("create()") PythonObjectFactory factory) {
            SliceInfo mslice;
            try {
                mslice = materializeSlice(idxSlice, object, lib);
            } catch (UnsupportedMessageException e) {
                throw raiseAttributeError(object);
            }
            Object[] values = new Object[mslice.length];
            for (int i = mslice.start, j = 0; i < mslice.stop; i += mslice.step, j++) {
                values[j] = readForeignValue(object, i, lib);
            }
            return factory.createList(values);
        }

        @Specialization
        public Object doForeignKey(Object object, String key,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.hasMembers(object)) {
                if (lib.isMemberReadable(object, key)) {
                    try {
                        return lib.readMember(object, key);
                    } catch (UnsupportedMessageException e) {
                        throw raiseAttributeErrorDisambiguated(object, key, lib);
                    } catch (UnknownIdentifierException e) {
                        // fall through
                    }
                }
                throw raise(KeyError, key);
            }
            throw raiseAttributeErrorDisambiguated(object, key, lib);
        }

        @Specialization(guards = {"!isSlice(idx)", "!isString(idx)"})
        public Object doForeignObject(Object object, Object idx,
                        @Cached CastToIndexNode castIndex,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return readForeignValue(object, castIndex.execute(idx), lib);
        }

        private PException raiseAttributeErrorDisambiguated(Object object, String key, InteropLibrary lib) {
            if (lib.hasArrayElements(object)) {
                throw raise(TypeError, "'%p' object cannot be interpreted as an integer", key);
            } else {
                throw raiseAttributeError(object);
            }
        }

        private Object readForeignValue(Object object, long index, InteropLibrary lib) {
            if (lib.hasArrayElements(object)) {
                if (lib.isArrayElementReadable(object, index)) {
                    try {
                        return getToPythonNode().executeConvert(lib.readArrayElement(object, index));
                    } catch (UnsupportedMessageException ex) {
                        throw raiseAttributeError(object);
                    } catch (InvalidArrayIndexException ex) {
                        throw raiseIndexError(index);
                    }
                }
                throw raiseIndexError(index);
            }
            throw raiseAttributeError(object);
        }

        private PException raiseIndexError(long index) {
            return raise(IndexError, "invalid index %s", index);
        }

        private PException raiseAttributeError(Object object) {
            return raise(AttributeError, "%s instance has no attribute '__getitem__'", object);
        }

        private PForeignToPTypeNode getToPythonNode() {
            if (toPythonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toPythonNode = insert(PForeignToPTypeNode.create());
            }
            return toPythonNode;
        }

        public static GetForeignItemNode create() {
            return GetForeignItemNodeGen.create();
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    protected abstract static class SetForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(VirtualFrame frame, Object object, Object idx, Object value);

        @Specialization
        public Object doForeignObjectSlice(VirtualFrame frame, Object object, PSlice idxSlice, PSequence pvalues,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached("create()") PTypeToForeignNode valueToForeignNode) {
            try {
                SliceInfo mslice = materializeSlice(idxSlice, object, lib);
                for (int i = mslice.start, j = 0; i < mslice.stop; i += mslice.step, j++) {
                    Object convertedValue = valueToForeignNode.executeConvert(getItemNode.executeObject(frame, pvalues, j));
                    writeForeignValue(object, i, convertedValue, lib);
                }
                return PNone.NONE;
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(AttributeError, "%s instance has no attribute '__setitem__'", object);
            }
        }

        @Specialization
        public Object doForeignKey(Object object, String key, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.hasMembers(object)) {
                if (lib.isMemberWritable(object, key)) {
                    try {
                        lib.writeMember(object, key, value);
                        return PNone.NONE;
                    } catch (UnsupportedMessageException e) {
                        throw raiseAttributeReadOnlyDisambiguated(object, key, lib);
                    } catch (UnknownIdentifierException e) {
                        throw raiseAttributeError(key);
                    } catch (UnsupportedTypeException e) {
                        throw raiseAttributeReadOnly(key);
                    }
                }
                throw raiseAttributeReadOnlyDisambiguated(object, key, lib);
            }
            throw raiseAttributeError(key);
        }

        private PException raiseAttributeReadOnlyDisambiguated(Object object, String key, InteropLibrary lib) {
            if (lib.hasArrayElements(object)) {
                throw raise(TypeError, "'%p' object cannot be interpreted as an integer", key);
            } else {
                throw raiseAttributeReadOnly(key);
            }
        }

        private PException raiseAttributeReadOnly(String key) {
            return raise(AttributeError, "attribute %s is read-only", key);
        }

        private PException raiseAttributeError(String key) {
            return raise(AttributeError, "foreign object has no attribute '%s'", key);
        }

        @Specialization(guards = {"!isSlice(idx)", "!isString(idx)"})
        public Object doForeignObject(Object object, Object idx, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToIndexNode castIndex,
                        @Cached("create()") PTypeToForeignNode valueToForeignNode) {
            try {
                int convertedIdx = castIndex.execute(idx);
                Object convertedValue = valueToForeignNode.executeConvert(value);
                writeForeignValue(object, convertedIdx, convertedValue, lib);
                return PNone.NONE;
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(AttributeError, "%s instance has no attribute '__setitem__'", object);
            }
        }

        private void writeForeignValue(Object object, int idx, Object value, InteropLibrary lib) throws UnsupportedMessageException, UnsupportedTypeException {
            if (lib.hasArrayElements(object)) {
                if (lib.isArrayElementWritable(object, idx)) {
                    try {
                        lib.writeArrayElement(object, idx, value);
                        return;
                    } catch (InvalidArrayIndexException ex) {
                        // fall through
                    }
                }
                throw raise(IndexError, "invalid index %s", idx);
            }
            throw raise(AttributeError, "%s instance has no attribute '__setitem__'", object);
        }

        public static SetForeignItemNode create() {
            return SetForeignItemNodeGen.create();
        }
    }

    protected abstract static class RemoveForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(Object object, Object idx);

        @Specialization
        public Object doForeignObjectSlice(Object object, PSlice idxSlice,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {

            try {
                SliceInfo mslice = materializeSlice(idxSlice, object, lib);
                for (int i = mslice.start; i < mslice.stop; i += mslice.step) {
                    removeForeignValue(object, i, lib);
                }
                return PNone.NONE;
            } catch (UnsupportedMessageException e) {
                throw raiseAttributeError(object);
            }
        }

        @Specialization
        public Object doForeignKey(Object object, String key,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.hasMembers(object)) {
                if (lib.isMemberRemovable(object, key)) {
                    try {
                        lib.removeMember(object, key);
                        return PNone.NONE;
                    } catch (UnsupportedMessageException e) {
                        throw raiseAttributeReadOnlyDisambiguated(object, key, lib);
                    } catch (UnknownIdentifierException e) {
                        throw raiseAttributeError(key);
                    }
                }
                throw raiseAttributeReadOnlyDisambiguated(object, key, lib);
            }
            throw raiseAttributeError(key);
        }

        private PException raiseAttributeError(String key) {
            return raise(AttributeError, "foreign object has no attribute '%s'", key);
        }

        private PException raiseAttributeReadOnlyDisambiguated(Object object, String key, InteropLibrary lib) {
            if (lib.hasArrayElements(object)) {
                throw raise(TypeError, "'%p' object cannot be interpreted as an integer", key);
            } else {
                throw raise(AttributeError, "attribute %s is read-only", key);
            }
        }

        @Specialization(guards = "!isSlice(idx)")
        public Object doForeignObject(Object object, Object idx,
                        @Cached CastToIndexNode castIndex,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.hasArrayElements(object)) {
                try {
                    int convertedIdx = castIndex.execute(idx);
                    return removeForeignValue(object, convertedIdx, lib);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw raiseAttributeError(object);
        }

        private PException raiseAttributeError(Object object) {
            return raise(AttributeError, "%s instance has no attribute '__delitem__'", object);
        }

        private Object removeForeignValue(Object object, int idx, InteropLibrary lib) throws UnsupportedMessageException {
            if (lib.isArrayElementRemovable(object, idx)) {
                try {
                    lib.removeArrayElement(object, idx);
                } catch (InvalidArrayIndexException ex) {
                    throw raise(IndexError, "invalid index %s", idx);
                }
            }
            throw raiseAttributeError(object);
        }

        public static RemoveForeignItemNode create() {
            return RemoveForeignItemNodeGen.create();
        }
    }
}
