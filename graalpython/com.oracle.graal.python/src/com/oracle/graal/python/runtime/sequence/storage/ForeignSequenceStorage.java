/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

public final class ForeignSequenceStorage extends SequenceStorage {

    private final Object foreignArray;

    /*
     * We create a new ForeignSequenceStorage every time we need a SequenceStorage for a foreign
     * array, that way the length is not stale. There is anyway no reasonable way to store the
     * ForeignSequenceStorage on the foreign array.
     */
    public ForeignSequenceStorage(Object foreignArray, int size) {
        assert IsForeignObjectNode.executeUncached(foreignArray);
        assert InteropLibrary.getUncached().hasArrayElements(foreignArray);
        this.foreignArray = foreignArray;
        this.length = size;
        this.capacity = size;
    }

    public int getArraySize(Node inliningTarget, InteropLibrary interop, InlinedBranchProfile errorProfile) {
        long size;
        try {
            size = interop.getArraySize(foreignArray);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return PInt.long2int(inliningTarget, size, errorProfile);
    }

    @Override
    public void setNewLength(int length) {
        this.length = length;
        this.capacity = length;
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Generic;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "ForeignSequenceStorage[]";
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadNoConversionNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, ForeignSequenceStorage storage, int index);

        @InliningCutoff
        @Specialization
        static Object read(Node inliningTarget, ForeignSequenceStorage storage, int index,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                return interop.readArrayElement(storage.foreignArray, index);
            } catch (UnsupportedMessageException ex) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.ITEM_S_OF_S_OBJ_IS_NOT_READABLE, index, storage.foreignArray);
            } catch (InvalidArrayIndexException ex) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.INVALID_INDEX_S, index);
            } finally {
                gil.acquire();
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, ForeignSequenceStorage storage, int index);

        @InliningCutoff
        @Specialization
        static Object read(Node inliningTarget, ForeignSequenceStorage storage, int index,
                        @Cached ReadNoConversionNode readNoConversionNode,
                        @Cached(inline = false) PForeignToPTypeNode toPythonNode) {
            Object value = readNoConversionNode.execute(inliningTarget, storage, index);
            return toPythonNode.executeConvert(value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WriteNode extends PNodeWithContext {
        public abstract void execute(Node inliningTarget, ForeignSequenceStorage storage, int index, Object value);

        @InliningCutoff
        @Specialization
        static void write(Node inliningTarget, ForeignSequenceStorage storage, int index, Object value,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                interop.writeArrayElement(storage.foreignArray, index, value);
            } catch (InvalidArrayIndexException e) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.INVALID_INDEX_S, index);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.ITEM_S_OF_S_OBJ_IS_NOT_WRITABLE, index, storage.foreignArray);
            } catch (UnsupportedTypeException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_P_NOT_SUPPORTED_BY_FOREIGN_OBJ, value);
            } finally {
                gil.acquire();
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class RemoveNode extends PNodeWithContext {
        public abstract void execute(Node inliningTarget, ForeignSequenceStorage storage, int index);

        @InliningCutoff
        @Specialization
        static void remove(Node inliningTarget, ForeignSequenceStorage storage, int index,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                interop.removeArrayElement(storage.foreignArray, index);
            } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.ITEM_S_OF_S_OBJ_IS_NOT_REMOVABLE, index, storage.foreignArray);
            } finally {
                gil.acquire();
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ClearNode extends PNodeWithContext {
        public abstract void execute(Node inliningTarget, ForeignSequenceStorage storage);

        @InliningCutoff
        @Specialization
        static void clear(Node inliningTarget, ForeignSequenceStorage storage,
                        @Cached RemoveNode removeNode) {
            int size = storage.length;
            for (int i = size - 1; i >= 0; i--) {
                removeNode.execute(inliningTarget, storage, i);
            }
            storage.setNewLength(0);
        }
    }

}
