/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.BuiltinFunctions.CompileNode.PyCF_ALLOW_TOP_LEVEL_AWAIT;
import static com.oracle.graal.python.builtins.modules.BuiltinFunctions.CompileNode.PyCF_ONLY_AST;
import static com.oracle.graal.python.builtins.modules.BuiltinFunctions.CompileNode.PyCF_TYPE_COMMENTS;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = AstModuleBuiltins.J__AST)
public final class AstModuleBuiltins extends PythonBuiltins {

    private static final HiddenKey AST_STATE_KEY = new HiddenKey("ast_state");

    static final String J__AST = "_ast";
    static final TruffleString T__AST = tsLiteral(J__AST);
    static final TruffleString T_AST = tsLiteral("ast");
    static final TruffleString T__FIELDS = tsLiteral("_fields");
    static final TruffleString T__ATTRIBUTES = tsLiteral("_attributes");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("PyCF_ONLY_AST", PyCF_ONLY_AST);
        addBuiltinConstant("PyCF_TYPE_COMMENTS", PyCF_TYPE_COMMENTS);
        addBuiltinConstant("PyCF_ALLOW_TOP_LEVEL_AWAIT", PyCF_ALLOW_TOP_LEVEL_AWAIT);

        PythonBuiltinClass clsAst = core.lookupType(PythonBuiltinClassType.AST);
        PTuple emptyTuple = core.factory().createTuple(EMPTY_OBJECT_ARRAY);
        clsAst.setAttribute(T__FIELDS, emptyTuple);
        clsAst.setAttribute(T__ATTRIBUTES, emptyTuple);
        // TODO clsAst.setAttribute('__match_args__', emptyTuple);
        PythonModule astModule = core.lookupBuiltinModule(T__AST);
        AstTypeFactory astTypeFactory = new AstTypeFactory(core.getLanguage(), core.factory(), astModule);
        AstState state = new AstState(astTypeFactory, core.lookupType(PythonBuiltinClassType.AST));
        astModule.setAttribute(AST_STATE_KEY, state);
    }

    private static AstState getAstState(PythonContext context) {
        return (AstState) ReadAttributeFromObjectNode.getUncached().execute(context.lookupBuiltinModule(T__AST), AST_STATE_KEY);
    }

    @TruffleBoundary
    public static Object sst2Obj(PythonContext context, ModTy mod) {
        return mod.accept(new Sst2ObjVisitor(getAstState(context)));
    }

    @TruffleBoundary
    public static ModTy obj2sst(PythonContext context, Object obj) {
        return new Obj2Sst(getAstState(context)).obj2ModTy(obj);
    }

    @TruffleBoundary
    public static boolean isAst(PythonContext context, Object obj) {
        return Obj2SstBase.isInstanceOf(obj, getAstState(context).clsAst);
    }
}
