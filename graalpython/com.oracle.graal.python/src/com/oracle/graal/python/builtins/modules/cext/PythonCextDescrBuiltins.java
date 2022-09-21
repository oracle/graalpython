/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.isClassOrStaticMethod;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsClinicProviders.PyDescrNewClassMethodClinicProviderGen;
import com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsClinicProviders.PyDescrNewGetSetNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextDescrBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextDescrBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyDictProxy_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictProxyNewNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static Object values(VirtualFrame frame, Object obj,
                        @Cached BuiltinConstructors.MappingproxyNode mappingNode) {
            return mappingNode.execute(frame, PythonBuiltinClassType.PMappingproxy, obj);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyDescr_NewGetSet", minNumOfPositionalArgs = 6, parameterNames = {"name", "cls", "getter", "setter", "doc", "closure"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class PyDescrNewGetSetNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PyDescrNewGetSetNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doNativeCallable(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        @Cached PythonCextBuiltins.CreateGetSetNode createGetSetNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, doc, closure,
                            getLanguage(), factory());
            return toSulongNode.execute(descr);
        }
    }

    // directly called without landing function
    @Builtin(name = "PyDescr_NewClassMethod", parameterNames = {"methodDefPtr", "name", "doc", "flags", "wrapper", "cfunc", "primary"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class PyDescrNewClassMethod extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PyDescrNewClassMethodClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doNativeCallable(Object methodDefPtr, TruffleString name, Object doc, int flags, Object wrapper, Object methObj, Object primary,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Cached PythonCextBuiltins.NewClassMethodNode newClassMethodNode,
                        @Cached CExtNodes.ToNewRefNode newRefNode) {
            Object type = asPythonObjectNode.execute(primary);
            Object func = newClassMethodNode.execute(methodDefPtr, name, methObj, flags, wrapper, type, doc, factory());
            if (!isClassOrStaticMethod(flags)) {
                /*
                 * NewClassMethodNode only wraps method with METH_CLASS and METH_STATIC set but we
                 * need to do so here.
                 */
                func = factory().createClassmethodFromCallableObj(func);
            }
            return newRefNode.execute(func);
        }
    }
}
