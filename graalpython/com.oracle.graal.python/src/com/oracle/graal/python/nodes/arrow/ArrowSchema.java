/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow;

import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.util.PythonUtils;
import sun.misc.Unsafe;

import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.POINTER_SIZE;

/**
 * C Data Interface of ArrowSchema
 * <p>
 * Represents a wrapper for the following C structure:
 *
 * <pre>
 *   struct ArrowSchema {
 *       // Array type description
 *       const char* format;                            0
 *       const char* name;                              8
 *       const char* metadata;                          16
 *       int64_t flags;                                 24
 *       int64_t n_children;                            32
 *       struct ArrowSchema** children;                 40
 *       struct ArrowSchema* dictionary;                48
 *
 *       // Release callback
 *       void (*release)(struct ArrowSchema*);          56
 *       // Opaque producer-specific data
 *       void* private_data;                            64
 *   };
 * </pre>
 */
public class ArrowSchema {

    private static final Unsafe unsafe = PythonUtils.initUnsafe();
    private static final int SIZE_OF = 72;
    public static final byte[] CAPSULE_NAME = PyCapsule.capsuleName("arrow_schema");
    private static final byte NULL = 0;

    private static final long FORMAT_INDEX = 0;
    private static final long NAME_INDEX = POINTER_SIZE;
    private static final long METADATA_INDEX = 2 * POINTER_SIZE;
    private static final long FLAGS_INDEX = 3 * POINTER_SIZE;
    private static final long N_CHILDREN_INDEX = 4 * POINTER_SIZE;
    private static final long CHILDREN_INDEX = 5 * POINTER_SIZE;
    private static final long DICTIONARY_INDEX = 6 * POINTER_SIZE;
    private static final long RELEASE_CALLBACK_INDEX = 7 * POINTER_SIZE;
    private static final long PRIVATE_DATA_INDEX = 8 * POINTER_SIZE;

    public final long memoryAddr;

    private ArrowSchema(long memoryAddr) {
        this.memoryAddr = memoryAddr;
    }

    public static ArrowSchema allocate() {
        var arrowSchema = new ArrowSchema(unsafe.allocateMemory(SIZE_OF));
        arrowSchema.markRelease();
        return arrowSchema;
    }

    public static ArrowSchema allocateFromSnapshot(Snapshot snapshot) {
        var arrowSchema = new ArrowSchema(unsafe.allocateMemory(SIZE_OF));
        arrowSchema.load(snapshot);
        return arrowSchema;
    }

    public static ArrowSchema wrap(long arrowSchemaPointer) {
        return new ArrowSchema(arrowSchemaPointer);
    }

    public long getFormat() {
        return unsafe.getLong(memoryAddr);
    }

    public boolean isReleased() {
        return unsafe.getLong(memoryAddr + RELEASE_CALLBACK_INDEX) == NULL;
    }

    public void markRelease() {
        unsafe.putLong(memoryAddr + RELEASE_CALLBACK_INDEX, NULL);
    }

    public void load(Snapshot snapshot) {
        unsafe.putLong(memoryAddr + FORMAT_INDEX, snapshot.format);
        unsafe.putLong(memoryAddr + NAME_INDEX, snapshot.name);
        unsafe.putLong(memoryAddr + METADATA_INDEX, snapshot.metadata);
        unsafe.putLong(memoryAddr + FLAGS_INDEX, snapshot.flags);
        unsafe.putLong(memoryAddr + N_CHILDREN_INDEX, snapshot.n_children);
        unsafe.putLong(memoryAddr + CHILDREN_INDEX, snapshot.children);
        unsafe.putLong(memoryAddr + DICTIONARY_INDEX, snapshot.dictionary);
        unsafe.putLong(memoryAddr + RELEASE_CALLBACK_INDEX, snapshot.release);
        unsafe.putLong(memoryAddr + PRIVATE_DATA_INDEX, snapshot.private_data);
    }

    public static class Snapshot {

        public long format = 0L;
        public long name = 0L;
        public long metadata = 0L;
        public long flags = 0L;
        public long n_children = 0L;
        public long children = 0L;
        public long dictionary = 0L;
        public long release = 0L;
        public long private_data = 0L;

    }

}
