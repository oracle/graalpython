/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeClassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;

@NodeChildren({@NodeChild(value = "object", type = PNode.class)})
public abstract class GetClassNode extends PNode {
    public static GetClassNode create(PNode object) {
        return GetClassNodeGen.create(object);
    }

    public static GetClassNode create() {
        return GetClassNodeGen.create(null);
    }

    public abstract PythonClass execute(Object object);

    private PythonBuiltinClass cacheClass(Class<?> klass) {
        return getCore().lookupType(klass);
    }

    @Specialization
    protected PythonClass getIt(@SuppressWarnings("unused") GetSetDescriptor object,
                    @Cached("cacheGetSet()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheGetSet() {
        return cacheClass(GetSetDescriptor.class);
    }

    @Specialization(guards = "!isNone(object)")
    protected PythonClass getIt(PythonObject object) {
        return object.getPythonClass();
    }

    PythonBuiltinClass cacheNone() {
        return cacheClass(PNone.class);
    }

    @Specialization
    protected PythonClass getIt(@SuppressWarnings("unused") PNone object,
                    @Cached("cacheNone()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheNotImplemented() {
        return cacheClass(PNotImplemented.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(PNotImplemented object,
                    @Cached("cacheNotImplemented()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheEllipsis() {
        return cacheClass(PEllipsis.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(PEllipsis object,
                    @Cached("cacheEllipsis()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheBool() {
        return cacheClass(Boolean.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(boolean object,
                    @Cached("cacheBool()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheInt() {
        return cacheClass(PInt.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(int object,
                    @Cached("cacheInt()") PythonBuiltinClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(long object,
                    @Cached("cacheInt()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheFloat() {
        return cacheClass(PFloat.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(double object,
                    @Cached("cacheFloat()") PythonBuiltinClass klass) {
        return klass;
    }

    PythonBuiltinClass cacheString() {
        return cacheClass(PString.class);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(String object,
                    @Cached("cacheString()") PythonBuiltinClass klass) {
        return klass;
    }

    @Specialization
    protected PythonClass getIt(PException object) {
        return object.getType();
    }

    @Specialization
    protected PythonClass getIt(PythonNativeObject object,
                    @Cached("create()") GetNativeClassNode getNativeClassNode) {
        return getNativeClassNode.execute(object);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isForeignObject(object)")
    protected PythonClass getIt(TruffleObject object) {
        return getCore().getForeignClass();
    }

    @TruffleBoundary
    public static PythonClass getItSlowPath(Object o) {
        if (PGuards.isForeignObject(o)) {
            return PythonLanguage.getCore().getForeignClass();
        }
        return PythonLanguage.getCore().lookupType(o.getClass());
    }

    @TruffleBoundary
    public static String getNameSlowPath(Object o) {
        if (PGuards.isForeignObject(o)) {
            return BuiltinNames.FOREIGN;
        }
        return PythonBuiltinClassType.fromClass(o.getClass()).name();
    }
}
