/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.BuiltinNames.BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.EXEC;
import static com.oracle.graal.python.nodes.PGuards.isDict;
import static com.oracle.graal.python.nodes.PGuards.isString;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public class PythonCextPythonRunBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextPythonRunBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyRun_String", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class PyRunStringNode extends PythonQuaternaryBuiltinNode {

        @Specialization(guards = "checkArgs(source, globals, locals, isMapping)")
        public Object run(VirtualFrame frame, Object source, Object stype, Object globals, Object locals,
                        @SuppressWarnings("unused") @Cached PyMappingCheckNode isMapping,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNull) {
            try {
                PythonModule builtins = getContext().getCore().lookupBuiltinModule(BUILTINS);
                Object compileCallable = lookupNode.execute(frame, builtins, COMPILE);
                Object code = callNode.execute(frame, compileCallable, source, stype, stype);
                Object execCallable = lookupNode.execute(frame, builtins, EXEC);
                return callNode.execute(frame, execCallable, code, globals, locals);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNull.execute();
            }
        }

        @Specialization(guards = "!isString(source) || !isDict(globals)")
        public Object run(VirtualFrame frame, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object stype, @SuppressWarnings("unused") Object globals,
                        @SuppressWarnings("unused") Object locals,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }

        @Specialization(guards = {"isString(source)", "isDict(globals)", "!isMapping.execute(locals)"})
        public Object run(VirtualFrame frame, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object stype, @SuppressWarnings("unused") Object globals, Object locals,
                        @SuppressWarnings("unused") @Cached PyMappingCheckNode isMapping,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, locals);
        }

        protected boolean checkArgs(Object source, Object globals, Object locals, PyMappingCheckNode isMapping) {
            return isString(source) && isDict(globals) && isMapping.execute(locals);
        }
    }
}
