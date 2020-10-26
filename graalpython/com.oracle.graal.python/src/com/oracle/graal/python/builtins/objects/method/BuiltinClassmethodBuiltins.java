/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.BuiltinNames.GETATTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBuiltinClassMethod)
public class BuiltinClassmethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinClassmethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NAME__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __NAME__);
        }
    }

    @Builtin(name = __QUALNAME__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __QUALNAME__);
        }
    }

    @Builtin(name = __OBJCLASS__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __OBJCLASS__);
        }
    }

    @Builtin(name = __DOC__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __DOC__);
        }
    }

    @Builtin(name = __TEXT_SIGNATURE__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __TEXT_SIGNATURE__);
        }
    }

    @Builtin(name = __REPR__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedLibrary("self.getCallable()") PythonObjectLibrary lib) {
            return lib.lookupAttributeStrict(self.getCallable(), frame, __REPR__);
        }
    }

    @Builtin(name = __REDUCE__, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object name(VirtualFrame frame, PDecoratedMethod self,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            PythonModule builtins = context.getCore().getBuiltins();
            Object gettattr = lib.lookupAttributeStrict(builtins, frame, GETATTR);
            Object type = lib.lookupAttributeStrict(self, frame, __OBJCLASS__);
            Object name = lib.lookupAttributeStrict(self, frame, __NAME__);
            return factory().createTuple(new Object[]{gettattr, factory().createTuple(new Object[]{type, name})});
        }
    }
}
