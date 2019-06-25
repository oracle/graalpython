/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeClassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
public abstract class GetClassNode extends PNodeWithContext {
    private final ValueProfile classProfile = ValueProfile.createClassProfile();

    /*
     * =============== Changes in this class must be mirrored in GetLazyClassNode ===============
     */

    public static GetClassNode create() {
        return GetClassNodeFactory.CachedNodeGen.create();
    }

    public static GetClassNode getUncached() {
        return UncachedNode.INSTANCE;
    }

    public abstract PythonBuiltinClass execute(boolean object);

    public abstract PythonBuiltinClass execute(int object);

    public abstract PythonBuiltinClass execute(long object);

    public abstract PythonBuiltinClass execute(double object);

    public final PythonAbstractClass execute(Object object) {
        return executeGetClass(classProfile.profile(object));
    }

    protected abstract PythonAbstractClass executeGetClass(Object object);

    abstract static class CachedNode extends GetClassNode {
        private final ContextReference<PythonContext> contextRef = PythonLanguage.getContextRef();

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") GetSetDescriptor object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @Specialization
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") GetSetDescriptor object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.GetSetDescriptor);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PCell object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @Specialization
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PCell object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PCell);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PNone object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @Specialization
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PNone object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PNone);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PNotImplemented object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(PNotImplemented object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PNotImplemented);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PEllipsis object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(PEllipsis object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PEllipsis);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") boolean object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(boolean object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.Boolean);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") int object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(int object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") long object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(long object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") double object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(double object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PFloat);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") String object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected PythonBuiltinClass getIt(String object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PString);
        }

        @Specialization
        protected PythonAbstractClass getIt(PythonAbstractNativeObject object,
                        @Cached("create()") GetNativeClassNode getNativeClassNode) {
            return getNativeClassNode.execute(object);
        }

        @Specialization(assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PythonNativeVoidPtr object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @Specialization
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
        }

        @Specialization
        protected PythonAbstractClass getPythonClassGeneric(PythonObject object,
                        @Cached("create()") GetLazyClassNode getLazyClass,
                        @Cached("createIdentityProfile()") ValueProfile profile,
                        @Cached("createBinaryProfile()") ConditionProfile getClassProfile) {
            LazyPythonClass lazyClass = getLazyClass.execute(object);
            if (getClassProfile.profile(lazyClass instanceof PythonBuiltinClassType)) {
                return profile.profile(contextRef.get().getCore().lookupType((PythonBuiltinClassType) lazyClass));
            } else {
                return profile.profile((PythonAbstractClass) lazyClass);
            }
        }

        @Specialization(guards = "isForeignObject(object)", assumptions = "singleContextAssumption()")
        protected PythonBuiltinClass getIt(@SuppressWarnings("unused") TruffleObject object,
                        @Cached("getIt(object)") PythonBuiltinClass klass) {
            return klass;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isForeignObject(object)")
        protected PythonBuiltinClass getIt(TruffleObject object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.ForeignObject);
        }
    }

    private static final class UncachedNode extends GetClassNode {
        private static final UncachedNode INSTANCE = new UncachedNode();

        private final ContextReference<PythonContext> contextRef = lookupContextReference(PythonLanguage.class);

        @Override
        public PythonBuiltinClass execute(boolean object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.Boolean);
        }

        @Override
        public PythonBuiltinClass execute(int object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
        }

        @Override
        public PythonBuiltinClass execute(long object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PInt);
        }

        @Override
        public PythonBuiltinClass execute(double object) {
            return contextRef.get().getCore().lookupType(PythonBuiltinClassType.PFloat);
        }

        @Override
        protected PythonAbstractClass executeGetClass(Object object) {
            return GetClassNode.getItSlowPath(contextRef, object);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }

    private static PythonAbstractClass getItSlowPath(ContextReference<PythonContext> contextRef, Object o) {
        PythonCore core = contextRef.get().getCore();
        if (PGuards.isForeignObject(o)) {
            return core.lookupType(PythonBuiltinClassType.ForeignObject);
        } else if (o instanceof PCell) {
            return core.lookupType(PythonBuiltinClassType.PCell);
        } else if (o instanceof String) {
            return core.lookupType(PythonBuiltinClassType.PString);
        } else if (o instanceof Boolean) {
            return core.lookupType(PythonBuiltinClassType.Boolean);
        } else if (o instanceof Double || o instanceof Float) {
            return core.lookupType(PythonBuiltinClassType.PFloat);
        } else if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte || o instanceof PythonNativeVoidPtr) {
            return core.lookupType(PythonBuiltinClassType.PInt);
        } else if (o instanceof PythonObject) {
            return ((PythonObject) o).getPythonClass();
        } else if (o instanceof PythonAbstractNativeObject) {
            return GetNativeClassNode.getUncached().execute((PythonAbstractNativeObject) o);
        } else if (o instanceof PEllipsis) {
            return core.lookupType(PythonBuiltinClassType.PEllipsis);
        } else if (o instanceof PNotImplemented) {
            return core.lookupType(PythonBuiltinClassType.PNotImplemented);
        } else if (o instanceof PNone) {
            return core.lookupType(PythonBuiltinClassType.PNone);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unknown type " + o.getClass().getName());
        }
    }
}
