/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

final class NativeModuleData {
    /**
     * Stores the native {@code PyModuleDef *} structure if this module was created via the
     * multiphase extension module initialization mechanism. Will be non-null only for legacy definitions.
     */
    long def = NULLPTR;
    /** {@code void *md_state} */
    long state = NULLPTR;

    /** {@code Py_ssize_t md_state_size} */
    long stateSize;

    /** {@code traverseproc md_state_traverse} */
    long stateTraverse = NULLPTR;
    /** {@code inquiry md_state_clear} */
    long stateClear = NULLPTR;
    /** {@code freefunc md_state_free} */
    long stateFree = NULLPTR;

    /** {@code _Py_modexecfunc md_exec} */
    long exec = NULLPTR;      // direct PySlot[] modules
    /** {@code void *md_token} */
    long token = NULLPTR;
    /** {@code bool md_token_is_def} */
    boolean tokenIsDef;
    /** {@code bool md_requires_gil} */
    boolean requiresGil;

    /**
     * Replicates the native references of this module's native state in Java.
     * <p>
     * Since a module can have a native module state where it is valid to store native references to
     * other objects, we need this field to replicate those references in Java if we make the handle
     * table reference weak in order to break possible reference cycles. This field will ever only
     * be set if the module's native definition provides a traverse function (see
     * {@code moduleobject.c: module_traverse}). The condition for this is:
     *
     * <pre>
     * {@code
     * if (m -> md_def && m -> md_def -> m_traverse && (m -> md_def -> m_size <= 0 || m -> md_state != NULL)) {
     *     // ...
     * }
     * }
     * </pre>
     * </p>
     */
    Object[] replicatedNativeReferences;
}
