/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@SuppressWarnings("truffle-inlining")
@ImportStatic({PGuards.class, CApiGuards.class})
public abstract class GetNativeWrapperNode extends PNodeWithContext {

    public abstract PythonNativeWrapper execute(Object value);

    @Specialization
    static PythonAbstractObjectNativeWrapper doString(TruffleString str,
                    @Bind("this") Node inliningTarget,
                    @Cached PythonObjectFactory factory,
                    @Exclusive @Cached InlinedConditionProfile noWrapperProfile) {
        return PythonObjectNativeWrapper.wrap(factory.createString(str), inliningTarget, noWrapperProfile);
    }

    @Specialization
    static PythonAbstractObjectNativeWrapper doBoolean(boolean b,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached InlinedConditionProfile profile) {
        Python3Core core = PythonContext.get(inliningTarget);
        PInt boxed = b ? core.getTrue() : core.getFalse();
        PythonAbstractObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
        if (profile.profile(inliningTarget, nativeWrapper == null)) {
            CompilerDirectives.transferToInterpreter();
            nativeWrapper = PrimitiveNativeWrapper.createBool(b);
            boxed.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    @Specialization(guards = "isSmallInteger(i)")
    static PrimitiveNativeWrapper doIntegerSmall(int i,
                    @Bind("this") Node inliningTarget) {
        PythonContext context = PythonContext.get(inliningTarget);
        if (context.getCApiContext() != null) {
            return context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
        }
        return PrimitiveNativeWrapper.createInt(i);
    }

    @Specialization(guards = "!isSmallInteger(i)")
    static PrimitiveNativeWrapper doInteger(int i) {
        return PrimitiveNativeWrapper.createInt(i);
    }

    public static PrimitiveNativeWrapper doLongSmall(long l, PythonContext context) {
        if (context.getCApiContext() != null) {
            return context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
        }
        return PrimitiveNativeWrapper.createLong(l);
    }

    @Specialization(guards = "isSmallLong(l)")
    static PrimitiveNativeWrapper doLongSmall(long l,
                    @Bind("this") Node inliningTarget) {
        return doLongSmall(l, PythonContext.get(inliningTarget));
    }

    @Specialization(guards = "!isSmallLong(l)")
    static PrimitiveNativeWrapper doLong(long l) {
        return PrimitiveNativeWrapper.createLong(l);
    }

    @Specialization(guards = "!isNaN(d)")
    static PrimitiveNativeWrapper doDouble(double d) {
        return PrimitiveNativeWrapper.createDouble(d);
    }

    @Specialization(guards = "isNaN(d)")
    static PythonNativeWrapper doDoubleNaN(@SuppressWarnings("unused") double d,
                    @Bind("this") Node inliningTarget) {
        PFloat boxed = PythonContext.get(inliningTarget).getNaN();
        PythonAbstractObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
        // Use a counting profile since we should enter the branch just once per context.
        if (nativeWrapper == null) {
            // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
            // code will happen just once per context.
            CompilerDirectives.transferToInterpreter();
            nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
            boxed.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    @Specialization(guards = "isSpecialSingleton(object)")
    static PythonNativeWrapper doSingleton(PythonAbstractObject object,
                    @Bind("this") Node inliningTarget) {
        PythonContext context = PythonContext.get(inliningTarget);
        PythonAbstractObjectNativeWrapper nativeWrapper = context.getCApiContext().getSingletonNativeWrapper(object);
        assert nativeWrapper != null;
        return nativeWrapper;
    }

    @Specialization
    static PythonNativeWrapper doPythonClassUncached(PythonManagedClass object,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached TypeNodes.GetNameNode getNameNode) {
        return PythonClassNativeWrapper.wrap(object, getNameNode.execute(inliningTarget, object));
    }

    @Specialization
    static PythonNativeWrapper doPythonTypeUncached(PythonBuiltinClassType object,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached TypeNodes.GetNameNode getNameNode) {
        return PythonClassNativeWrapper.wrap(PythonContext.get(getNameNode).lookupType(object), getNameNode.execute(inliningTarget, object));
    }

    @Specialization(guards = {"!isClass(inliningTarget, object, isTypeNode)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, limit = "1")
    static PythonNativeWrapper runAbstractObject(PythonAbstractObject object,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached InlinedConditionProfile noWrapperProfile,
                    @SuppressWarnings("unused") @Cached IsTypeNode isTypeNode) {
        assert object != PNone.NO_VALUE;
        return PythonObjectNativeWrapper.wrap(object, inliningTarget, noWrapperProfile);
    }

    @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
    static PythonNativeWrapper doForeignObject(Object object,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
        assert !CApiTransitions.isBackendPointerObject(object);
        assert !(object instanceof String);
        return TruffleObjectNativeWrapper.wrap(object);
    }

    protected static boolean isNaN(double d) {
        return Double.isNaN(d);
    }
}
