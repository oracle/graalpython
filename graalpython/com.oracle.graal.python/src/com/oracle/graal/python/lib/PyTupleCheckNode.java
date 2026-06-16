/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/** Equivalent of CPython's {@code PyTuple_Check}. */
@GenerateInline
@GenerateUncached
@GenerateCached(false)
public abstract class PyTupleCheckNode extends Node {
    public abstract boolean execute(Node inliningTarget, Object object);

    public static boolean executeUncached(Object object) {
        return PyTupleCheckNodeGen.getUncached().execute(null, object);
    }

    @Specialization
    public static boolean doGeneric(Node inliningTarget, Object object,
                    @Cached InlinedBranchProfile isPTupleProfile,
                    @Cached InlinedBranchProfile isNativeProfile) {
        if (object instanceof PTuple) {
            isPTupleProfile.enter(inliningTarget);
            return true;
        }
        if (object instanceof PythonAbstractNativeObject nativeObject) {
            isNativeProfile.enter(inliningTarget);
            return checkNative(nativeObject);
        }
        return false;
    }

    public static boolean checkNative(PythonAbstractNativeObject nativeObject) {
        long obType = readPtrField(nativeObject.pointer, PyObject__ob_type);
        boolean isTupleSubclass = (readLongField(obType, CFields.PyTypeObject__tp_flags) & TypeFlags.TUPLE_SUBCLASS) != 0L;
        assert IsBuiltinObjectProfile.profileObjectUncached(nativeObject, PythonBuiltinClassType.PTuple) == isTupleSubclass;
        return isTupleSubclass;
    }

    @GenerateInline(false)
    public abstract static class CachedNode extends Node {
        public abstract boolean execute(Object object);

        @Specialization
        static boolean doGeneric(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyTupleCheckNode tupleCheck) {
            return tupleCheck.execute(inliningTarget, object);
        }

        @NeverDefault
        public static CachedNode create() {
            return PyTupleCheckNodeGen.CachedNodeGen.create();
        }
    }
}
