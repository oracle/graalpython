/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfilesFactory.InlineIsBuiltinClassProfileNodeGen;
import com.oracle.graal.python.nodes.object.BuiltinClassProfilesFactory.IsBuiltinClassExactProfileNodeGen;
import com.oracle.graal.python.nodes.object.BuiltinClassProfilesFactory.IsBuiltinObjectExactProfileNodeGen;
import com.oracle.graal.python.nodes.object.BuiltinClassProfilesFactory.IsBuiltinObjectProfileNodeGen;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class BuiltinClassProfiles {
    private BuiltinClassProfiles() {
    }

    @GenerateCached(false)
    @GenerateUncached
    @GenerateInline
    public abstract static class IsAnyBuiltinClassProfile extends Node {
        public final boolean profileIsAnyBuiltinClass(Node inliningTarget, Object clazz) {
            return execute(inliningTarget, clazz);
        }

        abstract boolean execute(Node inliningTarget, Object clazz);

        @Specialization
        @SuppressWarnings("unused")
        static boolean doType(Node inliningTarget, PythonBuiltinClassType clazz) {
            return true;
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean doClass(Node inliningTarget, PythonBuiltinClass clazz) {
            return true;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doOthers(Node inliningTarget, Object clazz) {
            return false;
        }
    }

    @GenerateCached(false)
    @GenerateUncached
    @GenerateInline
    public abstract static class IsOtherBuiltinClassProfile extends Node {
        public final boolean profileIsOtherBuiltinClass(Node inliningTarget, Object clazz, PythonBuiltinClassType forbiddenType) {
            return execute(inliningTarget, clazz, forbiddenType);
        }

        abstract boolean execute(Node inliningTarget, Object clazz, PythonBuiltinClassType forbiddenType);

        @Specialization
        static boolean doType(Node inliningTarget, PythonBuiltinClassType clazz, PythonBuiltinClassType forbiddenType) {
            return clazz != forbiddenType;
        }

        @Specialization
        static boolean doClass(Node inliningTarget, PythonBuiltinClass clazz, PythonBuiltinClassType forbiddenType) {
            return clazz.getType() != forbiddenType;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doOthers(Node inliningTarget, Object clazz, PythonBuiltinClassType forbiddenType) {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class IsBuiltinClassExactProfile extends Node {

        public final boolean profileClass(Node inliningTarget, Object type, PythonBuiltinClassType pythonClass) {
            return execute(inliningTarget, type, pythonClass);
        }

        public static boolean profileClassSlowPath(Object type, PythonBuiltinClassType pythonClass) {
            return getUncached().execute(null, type, pythonClass);
        }

        abstract boolean execute(Node inliningTarget, Object clazz, PythonBuiltinClassType pythonClass);

        @Specialization
        static boolean doType(Node inliningTarget, PythonBuiltinClassType clazz, PythonBuiltinClassType pythonClass,
                        @Shared @Cached InlinedConditionProfile profile) {
            return profile.profile(inliningTarget, clazz == pythonClass);
        }

        @Specialization
        static boolean doClass(Node inliningTarget, PythonBuiltinClass clazz, PythonBuiltinClassType pythonClass,
                        @Shared @Cached InlinedConditionProfile profile) {
            return profile.profile(inliningTarget, clazz.getType() == pythonClass);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doOthers(Object clazz, PythonBuiltinClassType pythonClass) {
            return false;
        }

        public static IsBuiltinClassExactProfile getUncached() {
            return IsBuiltinClassExactProfileNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    // TODO: DSL inlining - remove the Inline prefix
    public abstract static class InlineIsBuiltinClassProfile extends Node {
        public static InlineIsBuiltinClassProfile getUncached() {
            return InlineIsBuiltinClassProfileNodeGen.getUncached();
        }

        public static boolean profileClassSlowPath(Object clazz, PythonBuiltinClassType type) {
            if (clazz instanceof PythonBuiltinClassType) {
                return clazz == type;
            } else {
                if (clazz instanceof PythonBuiltinClass) {
                    return ((PythonBuiltinClass) clazz).getType() == type;
                }
                return false;
            }
        }

        public static InlineIsBuiltinClassProfile create() {
            return InlineIsBuiltinClassProfileNodeGen.create();
        }

        public final boolean profileClass(Node inliningTarget, Object type, PythonBuiltinClassType pythonClass) {
            return execute(inliningTarget, type, pythonClass);
        }

        abstract boolean execute(Node inliningTarget, Object clazz, PythonBuiltinClassType pythonClass);

        public final boolean profileClassCached(Object clazz, PythonBuiltinClassType pythonClass) {
            return execute(this, clazz, pythonClass);
        }

        @Specialization(guards = "sameType(clazz, pythonClass)")
        @SuppressWarnings("unused")
        static boolean doSameType(PythonBuiltinClassType clazz, PythonBuiltinClassType pythonClass) {
            return true;
        }

        @Specialization(guards = "sameType(clazz, pythonClass)")
        @SuppressWarnings("unused")
        static boolean doSameType(PythonBuiltinClass clazz, PythonBuiltinClassType pythonClass) {
            return true;
        }

        @Specialization
        static boolean doOthers(Node inliningTarget, Object clazz, PythonBuiltinClassType pythonClass,
                        @Cached InlinedConditionProfile profile,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode) {
            return profile.profile(inliningTarget, isSubtypeNode.execute(clazz, pythonClass));
        }

        protected static boolean sameType(PythonBuiltinClassType a, PythonBuiltinClassType b) {
            return a == b;
        }

        protected static boolean sameType(PythonBuiltinClass a, PythonBuiltinClassType b) {
            return a.getType() == b;
        }
    }

    @GenerateCached(false)
    @GenerateUncached
    @GenerateInline
    public abstract static class IsAnyBuiltinObjectProfile extends Node {
        public final boolean profileIsAnyBuiltinObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj);
        }

        abstract boolean execute(Node inliningTarget, Object obj);

        @Specialization
        static boolean doIt(Node inliningTarget, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsAnyBuiltinClassProfile isAnyBuiltinClass) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            return isAnyBuiltinClass.execute(inliningTarget, clazz);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class IsBuiltinObjectExactProfile extends Node {
        public static boolean profileObjectUncached(Object obj, PythonBuiltinClassType type) {
            return getUncached().profileObject(null, obj, type);
        }

        public static IsBuiltinObjectExactProfile getUncached() {
            return IsBuiltinObjectExactProfileNodeGen.getUncached();
        }

        @NeverDefault
        public static IsBuiltinObjectExactProfile create() {
            return IsBuiltinObjectExactProfileNodeGen.create();
        }

        public final boolean profileObject(Node inliningTarget, Object obj, PythonBuiltinClassType type) {
            return execute(inliningTarget, obj, type);
        }

        abstract boolean execute(Node inliningTarget, Object obj, PythonBuiltinClassType type);

        @Specialization
        static boolean doIt(Node inliningTarget, Object obj, PythonBuiltinClassType type,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassExactProfile isBuiltinClass) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            return isBuiltinClass.profileClass(inliningTarget, clazz, type);
        }
    }

    @GenerateCached(false)
    @GenerateUncached
    @GenerateInline
    public abstract static class IsOtherBuiltinObjectProfile extends Node {
        public final boolean profileIsOtherBuiltinObject(Node inliningTarget, Object obj, PythonBuiltinClassType forbiddenType) {
            return execute(inliningTarget, obj, forbiddenType);
        }

        abstract boolean execute(Node inliningTarget, Object obj, PythonBuiltinClassType forbiddenType);

        @Specialization
        static boolean doIt(Node inliningTarget, Object obj, PythonBuiltinClassType forbiddenType,
                        @Cached GetClassNode getClassNode,
                        @Cached IsOtherBuiltinClassProfile isOtherBuiltinClass) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            return isOtherBuiltinClass.execute(inliningTarget, clazz, forbiddenType);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class IsBuiltinObjectProfile extends Node {
        public static boolean profileObjectUncached(Object obj, PythonBuiltinClassType type) {
            return getUncached().profileObject(null, obj, type);
        }

        public static IsBuiltinObjectProfile getUncached() {
            return BuiltinClassProfilesFactory.IsBuiltinObjectProfileNodeGen.getUncached();
        }

        @NeverDefault
        public static IsBuiltinObjectProfile create() {
            return IsBuiltinObjectProfileNodeGen.create();
        }

        public final boolean profileException(Node inliningTarget, PException obj, PythonBuiltinClassType type) {
            return profileObject(inliningTarget, obj.getUnreifiedException(), type);
        }

        public final boolean profileObject(Node inliningTarget, Object obj, PythonBuiltinClassType type) {
            return execute(inliningTarget, obj, type);
        }

        public final boolean profileObjectCached(Object obj, PythonBuiltinClassType type) {
            return execute(this, obj, type);
        }

        abstract boolean execute(Node inliningTarget, Object obj, PythonBuiltinClassType type);

        @Specialization
        static boolean doIt(Node inliningTarget, Object obj, PythonBuiltinClassType type,
                        @Cached GetClassNode getClassNode,
                        @Cached InlineIsBuiltinClassProfile isBuiltinClassProfile) {
            Object clazz = getClassNode.execute(inliningTarget, obj);
            return isBuiltinClassProfile.execute(inliningTarget, clazz, type);
        }
    }

}
