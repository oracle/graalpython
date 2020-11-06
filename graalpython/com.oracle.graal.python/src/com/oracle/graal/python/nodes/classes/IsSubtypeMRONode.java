/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class IsSubtypeMRONode extends PNodeWithContext {

    public static IsSubtypeMRONode create() {
        return IsSubtypeMRONodeGen.create();
    }

    public abstract boolean execute(Object derived, Object clazz);

    @Specialization
    static boolean isSubtype(PythonBuiltinClassType derived, PythonBuiltinClassType clazz,
                    @Shared("equalsProfile") @Cached ConditionProfile equalsProfile,
                    @Shared("innerEqualsProfile") @Cached ConditionProfile innerEqualsProfile,
                    @Shared("falseProfile") @Cached BranchProfile falseProfile) {
        if (equalsProfile.profile(derived == clazz)) {
            return true;
        }
        PythonBuiltinClassType current = derived;
        while (current != PythonBuiltinClassType.PythonObject) {
            if (innerEqualsProfile.profile(derived == clazz)) {
                return true;
            }
            current = current.getBase();
        }
        falseProfile.enter();
        return false;
    }

    @Specialization
    static boolean isSubtype(PythonBuiltinClass derived, PythonBuiltinClassType clazz,
                    @Shared("equalsProfile") @Cached ConditionProfile equalsProfile,
                    @Shared("innerEqualsProfile") @Cached ConditionProfile innerEqualsProfile,
                    @Shared("falseProfile") @Cached BranchProfile falseProfile) {
        return isSubtype(derived.getType(), clazz, equalsProfile, innerEqualsProfile, falseProfile);
    }

    @Specialization
    static boolean isSubtype(PythonBuiltinClassType derived, PythonBuiltinClass clazz,
                    @Shared("equalsProfile") @Cached ConditionProfile equalsProfile,
                    @Shared("innerEqualsProfile") @Cached ConditionProfile innerEqualsProfile,
                    @Shared("falseProfile") @Cached BranchProfile falseProfile) {
        return isSubtype(derived, clazz.getType(), equalsProfile, innerEqualsProfile, falseProfile);
    }

    protected static boolean isBuiltinClass(PythonAbstractClass clazz) {
        return clazz instanceof PythonBuiltinClass;
    }

    @Specialization(guards = "!isBuiltinClass(clazz)")
    static boolean isSubtype(@SuppressWarnings("unused") PythonBuiltinClassType derived, @SuppressWarnings("unused") PythonClass clazz) {
        // a builtin class never derives from a non-builtin class
        return false;
    }

    @Specialization(guards = "!isBuiltinClass(clazz)")
    static boolean isSubtype(@SuppressWarnings("unused") PythonBuiltinClass derived, @SuppressWarnings("unused") PythonClass clazz) {
        // a builtin class never derives from a non-builtin class
        return false;
    }

    @Specialization
    static boolean isSubtype(PythonClass derived, PythonBuiltinClassType clazz,
                    @Cached IsBuiltinClassProfile profile,
                    @Shared("getMroNode") @Cached GetMroNode getMroNode,
                    @Shared("falseProfile") @Cached BranchProfile falseProfile) {

        for (PythonAbstractClass mro : getMroNode.execute(derived)) {
            if (profile.profileClass(mro, clazz)) {
                return true;
            }
        }
        falseProfile.enter();
        return false;
    }

    @Specialization
    static boolean isSubtype(PythonClass derived, PythonClass clazz,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Shared("getMroNode") @Cached GetMroNode getMroNode,
                    @Shared("falseProfile") @Cached BranchProfile falseProfile) {
        for (PythonAbstractClass mro : getMroNode.execute(derived)) {
            if (isSameTypeNode.execute(mro, clazz)) {
                return true;
            }
        }
        falseProfile.enter();
        return false;
    }
}
