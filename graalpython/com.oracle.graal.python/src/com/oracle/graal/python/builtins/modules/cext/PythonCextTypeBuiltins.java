/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CreateTypeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextTypeBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyType_Lookup", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTypeLookup extends PythonBinaryBuiltinNode {
        @Specialization
        Object doGeneric(Object type, Object name,
                        @Cached CExtNodes.AsPythonObjectNode typeAsPythonObjectNode,
                        @Cached CExtNodes.AsPythonObjectNode nameAsPythonObjectNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttributeInMRONode,
                        @Cached CExtNodes.ToBorrowedRefNode toBorrowedRefNode) {
            Object result = lookupAttributeInMRONode.execute(typeAsPythonObjectNode.execute(type), nameAsPythonObjectNode.execute(name));
            if (result == PNone.NO_VALUE) {
                return getContext().getNativeNull();
            }
            return toBorrowedRefNode.execute(result);
        }
    }

    @Builtin(name = "PyType_IsSubtype", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PythonOptions.class)
    abstract static class PyTypeIsSubtypeNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"isSingleContext()", "a == cachedA", "b == cachedB"})
        static int doCached(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PythonNativeWrapper a, @SuppressWarnings("unused") PythonNativeWrapper b,
                        @Cached(value = "a", weak = true) @SuppressWarnings("unused") PythonNativeWrapper cachedA,
                        @Cached(value = "b", weak = true) @SuppressWarnings("unused") PythonNativeWrapper cachedB,
                        @Cached("doSlow(frame, a, b)") int result) {
            return result;
        }

        protected static Class<?> getClazz(Object v) {
            return v.getClass();
        }

        @Specialization(replaces = "doCached", guards = {"cachedClassA == getClazz(a)", "cachedClassB == getClazz(b)"}, limit = "getVariableArgumentInlineCacheLimit()")
        static int doCachedClass(VirtualFrame frame, Object a, Object b,
                        @Cached("getClazz(a)") Class<?> cachedClassA,
                        @Cached("getClazz(b)") Class<?> cachedClassB,
                        @Cached CExtNodes.ToJavaNode leftToJavaNode,
                        @Cached CExtNodes.ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(cachedClassA.cast(a));
            Object ub = rightToJavaNode.execute(cachedClassB.cast(b));
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        @Specialization(replaces = {"doCached", "doCachedClass"})
        static int doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached CExtNodes.ToJavaNode leftToJavaNode,
                        @Cached CExtNodes.ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(a);
            Object ub = rightToJavaNode.execute(b);
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        static int doSlow(VirtualFrame frame, Object derived, Object cls) {
            return doGeneric(frame, derived, cls, ToJavaNodeGen.getUncached(), ToJavaNodeGen.getUncached(), IsSubtypeNodeGen.getUncached());
        }
    }

    @Builtin(name = "PyTruffle_CreateType", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffleCreateType extends PythonQuaternaryBuiltinNode {
        @Specialization
        static PythonClass createType(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespaceOrig, Object metaclass,
                        @Cached CreateTypeNode createType) {
            return createType.execute(frame, namespaceOrig, name, bases, metaclass, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
