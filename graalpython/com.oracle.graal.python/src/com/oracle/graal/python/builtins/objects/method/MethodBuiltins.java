/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FUNC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetDefaultsNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetKeywordDefaultsNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMethod)
public class MethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __FUNC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FuncNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self) {
            return self.getFunction();
        }

        @Specialization
        protected Object doIt(PBuiltinMethod self) {
            return self.getFunction();
        }
    }

    @Builtin(name = __CODE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PMethod self,
                        @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getCode) {
            return getCode.executeObject(frame, self.getFunction(), __CODE__);
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBuiltinNode {
        @Specialization(limit = "1")
        protected Object doIt(PMethod self,
                        @CachedLibrary("self.getFunction()") PythonObjectLibrary lib) {
            PDict dict = lib.getDict(self.getFunction());
            if (dict == null) {
                // A native object should already have a dict,
                // so it's safe to assume it's a PythonObject
                dict = factory().createDictFixedStorage((PythonObject) self.getFunction());
                try {
                    lib.setDict(self.getFunction(), dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PMethod self, Object keyObj,
                        @Cached("create()") ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached CastToJavaStringNode castKeyToStringNode) {
            String key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return objectGetattrNode.execute(frame, self.getFunction(), key);
            }
        }

        @Specialization(guards = "!isPMethod(self)")
        Object getattribute(Object self, @SuppressWarnings("unused") Object key) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __GETATTRIBUTE__, "method", self);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reprMethod(VirtualFrame frame, PMethod method,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode callReprNode,
                        @Cached CastToJavaStringNode toJavaStringNode,
                        @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            Object self = method.getSelf();
            Object func = method.getFunction();
            String defname = "?";

            Object funcName = pol.lookupAttribute(func, frame, __QUALNAME__);
            if (funcName == PNone.NO_VALUE) {
                funcName = pol.lookupAttribute(func, frame, __NAME__);
            }

            try {
                return strFormat("<bound method %s of %s>", toJavaStringNode.execute(funcName), callReprNode.executeObject(frame, self));
            } catch (CannotCastException e) {
                return strFormat("<bound method %s of %s>", defname, callReprNode.executeObject(frame, self));
            }
        }

        @TruffleBoundary
        private static String strFormat(String fmt, Object... objects) {
            return String.format(fmt, objects);
        }
    }

    @Builtin(name = __DEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodDefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object defaults(PMethod self,
                        @Cached("create()") GetDefaultsNode getDefaultsNode) {
            Object[] argDefaults = getDefaultsNode.execute(self);
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : factory().createTuple(argDefaults);
        }
    }

    @Builtin(name = __KWDEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodKwdefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object kwDefaults(PMethod self,
                        @Cached("create()") GetKeywordDefaultsNode getKeywordDefaultsNode) {
            PKeyword[] kwdefaults = getKeywordDefaultsNode.execute(self);
            return (kwdefaults.length > 0) ? factory().createDict(kwdefaults) : PNone.NONE;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(@SuppressWarnings("unused") Object obj) {
            // TODO we should not override '__reduce__' but properly distinguish between heap/non
            // heap types
            throw raise(TypeError, ErrorMessages.CANT_PICKLE_FUNC_OBJS);
        }
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        PMethod doGeneric(@SuppressWarnings("unused") PMethod self, Object obj, @SuppressWarnings("unused") Object cls) {
            if (self.getSelf() != null) {
                return self;
            }
            return factory().createMethod(obj, self.getFunction());
        }
    }
}
