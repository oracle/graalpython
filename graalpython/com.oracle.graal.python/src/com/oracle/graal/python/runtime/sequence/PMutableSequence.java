/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.sequence;

import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public abstract class PMutableSequence extends PSequence {

    public PMutableSequence(LazyPythonClass cls) {
        super(cls);
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.LenNode lenNode,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize) {
        final int len = lenNode.execute(getSequenceStorageNode.execute(this));
        try {
            normalize.execute(index, len, IndexNodes.NormalizeIndexNode.INDEX_OUT_OF_BOUNDS);
        } catch (PException e) {
            return false;
        }
        return true;
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.LenNode lenNode) {
        final int len = lenNode.execute(getSequenceStorageNode.execute(this));
        return index == len;
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.LenNode lenNode,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize) {
        final int len = lenNode.execute(getSequenceStorageNode.execute(this));
        try {
            normalize.execute(index, len, IndexNodes.NormalizeIndexNode.INDEX_OUT_OF_BOUNDS);
        } catch (PException e) {
            return false;
        }
        return true;
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.SetItemScalarNode setItem) {
        setItem.execute(getSequenceStorageNode.execute(this), PInt.intValueExact(index), value);
    }

    @ExportMessage
    public void removeArrayElement(long index,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.DeleteItemNode delItem) {
        delItem.execute(getSequenceStorageNode.execute(this), PInt.intValueExact(index));
    }
}
