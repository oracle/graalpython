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

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(defineModule = AstModuleBuiltins.J__AST)
public class AstModuleBuiltins extends PythonBuiltins {

    static final String J__AST = "_ast";
    static final TruffleString T__AST = tsLiteral(J__AST);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("PyCF_ONLY_AST", 0);
    }

    @Override
    @SuppressWarnings("unused")
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule astModule = core.lookupBuiltinModule(T__AST);
        PythonBuiltinClass astType = core.lookupType(PythonBuiltinClassType.AST);
        /*
         * These classes are not builtin in CPython and the ast module relies on them having
         * writable class fields.
         */
        PythonLanguage language = core.getLanguage();
        PythonObjectFactory factory = core.factory();
        PythonClass mod_type = makeType(language, factory, astModule, "mod", astType);
        PythonClass Module_type = makeType(language, factory, astModule, "Module", mod_type);
        PythonClass Interactive_type = makeType(language, factory, astModule, "Interactive", mod_type);
        PythonClass Expression_type = makeType(language, factory, astModule, "Expression", mod_type);
        PythonClass FunctionType_type = makeType(language, factory, astModule, "FunctionType", mod_type);
        PythonClass Suite_type = makeType(language, factory, astModule, "Suite", mod_type);
        PythonClass stmt_type = makeType(language, factory, astModule, "stmt", astType);
        PythonClass FunctionDef_type = makeType(language, factory, astModule, "FunctionDef", stmt_type);
        PythonClass AsyncFunctionDef_type = makeType(language, factory, astModule, "AsyncFunctionDef", stmt_type);
        PythonClass ClassDef_type = makeType(language, factory, astModule, "ClassDef", stmt_type);
        PythonClass Return_type = makeType(language, factory, astModule, "Return", stmt_type);
        PythonClass Delete_type = makeType(language, factory, astModule, "Delete", stmt_type);
        PythonClass Assign_type = makeType(language, factory, astModule, "Assign", stmt_type);
        PythonClass AugAssign_type = makeType(language, factory, astModule, "AugAssign", stmt_type);
        PythonClass AnnAssign_type = makeType(language, factory, astModule, "AnnAssign", stmt_type);
        PythonClass For_type = makeType(language, factory, astModule, "For", stmt_type);
        PythonClass AsyncFor_type = makeType(language, factory, astModule, "AsyncFor", stmt_type);
        PythonClass While_type = makeType(language, factory, astModule, "While", stmt_type);
        PythonClass If_type = makeType(language, factory, astModule, "If", stmt_type);
        PythonClass With_type = makeType(language, factory, astModule, "With", stmt_type);
        PythonClass AsyncWith_type = makeType(language, factory, astModule, "AsyncWith", stmt_type);
        PythonClass Raise_type = makeType(language, factory, astModule, "Raise", stmt_type);
        PythonClass Try_type = makeType(language, factory, astModule, "Try", stmt_type);
        PythonClass Assert_type = makeType(language, factory, astModule, "Assert", stmt_type);
        PythonClass Import_type = makeType(language, factory, astModule, "Import", stmt_type);
        PythonClass ImportFrom_type = makeType(language, factory, astModule, "ImportFrom", stmt_type);
        PythonClass Global_type = makeType(language, factory, astModule, "Global", stmt_type);
        PythonClass Nonlocal_type = makeType(language, factory, astModule, "Nonlocal", stmt_type);
        PythonClass Expr_type = makeType(language, factory, astModule, "Expr", stmt_type);
        PythonClass Pass_type = makeType(language, factory, astModule, "Pass", stmt_type);
        PythonClass Break_type = makeType(language, factory, astModule, "Break", stmt_type);
        PythonClass Continue_type = makeType(language, factory, astModule, "Continue", stmt_type);
        PythonClass expr_type = makeType(language, factory, astModule, "expr", astType);
        PythonClass BoolOp_type = makeType(language, factory, astModule, "BoolOp", expr_type);
        PythonClass NamedExpr_type = makeType(language, factory, astModule, "NamedExpr", expr_type);
        PythonClass BinOp_type = makeType(language, factory, astModule, "BinOp", expr_type);
        PythonClass UnaryOp_type = makeType(language, factory, astModule, "UnaryOp", expr_type);
        PythonClass Lambda_type = makeType(language, factory, astModule, "Lambda", expr_type);
        PythonClass IfExp_type = makeType(language, factory, astModule, "IfExp", expr_type);
        PythonClass Dict_type = makeType(language, factory, astModule, "Dict", expr_type);
        PythonClass Set_type = makeType(language, factory, astModule, "Set", expr_type);
        PythonClass ListComp_type = makeType(language, factory, astModule, "ListComp", expr_type);
        PythonClass SetComp_type = makeType(language, factory, astModule, "SetComp", expr_type);
        PythonClass DictComp_type = makeType(language, factory, astModule, "DictComp", expr_type);
        PythonClass GeneratorExp_type = makeType(language, factory, astModule, "GeneratorExp", expr_type);
        PythonClass Await_type = makeType(language, factory, astModule, "Await", expr_type);
        PythonClass Yield_type = makeType(language, factory, astModule, "Yield", expr_type);
        PythonClass YieldFrom_type = makeType(language, factory, astModule, "YieldFrom", expr_type);
        PythonClass Compare_type = makeType(language, factory, astModule, "Compare", expr_type);
        PythonClass Call_type = makeType(language, factory, astModule, "Call", expr_type);
        PythonClass FormattedValue_type = makeType(language, factory, astModule, "FormattedValue", expr_type);
        PythonClass JoinedStr_type = makeType(language, factory, astModule, "JoinedStr", expr_type);
        PythonClass Constant_type = makeType(language, factory, astModule, "Constant", expr_type);
        PythonClass Attribute_type = makeType(language, factory, astModule, "Attribute", expr_type);
        PythonClass Subscript_type = makeType(language, factory, astModule, "Subscript", expr_type);
        PythonClass Starred_type = makeType(language, factory, astModule, "Starred", expr_type);
        PythonClass Name_type = makeType(language, factory, astModule, "Name", expr_type);
        PythonClass List_type = makeType(language, factory, astModule, "List", expr_type);
        PythonClass Tuple_type = makeType(language, factory, astModule, "Tuple", expr_type);
        PythonClass expr_context_type = makeType(language, factory, astModule, "expr_context", astType);
        PythonClass Load_type = makeType(language, factory, astModule, "Load", expr_context_type);
        PythonClass Store_type = makeType(language, factory, astModule, "Store", expr_context_type);
        PythonClass Del_type = makeType(language, factory, astModule, "Del", expr_context_type);
        PythonClass AugLoad_type = makeType(language, factory, astModule, "AugLoad", expr_context_type);
        PythonClass AugStore_type = makeType(language, factory, astModule, "AugStore", expr_context_type);
        PythonClass Param_type = makeType(language, factory, astModule, "Param", expr_context_type);
        PythonClass slice_type = makeType(language, factory, astModule, "slice", astType);
        PythonClass Slice_type = makeType(language, factory, astModule, "Slice", slice_type);
        PythonClass ExtSlice_type = makeType(language, factory, astModule, "ExtSlice", slice_type);
        PythonClass Index_type = makeType(language, factory, astModule, "Index", slice_type);
        PythonClass boolop_type = makeType(language, factory, astModule, "boolop", astType);
        PythonClass And_type = makeType(language, factory, astModule, "And", boolop_type);
        PythonClass Or_type = makeType(language, factory, astModule, "Or", boolop_type);
        PythonClass operator_type = makeType(language, factory, astModule, "operator", astType);
        PythonClass Add_type = makeType(language, factory, astModule, "Add", operator_type);
        PythonClass Sub_type = makeType(language, factory, astModule, "Sub", operator_type);
        PythonClass Mult_type = makeType(language, factory, astModule, "Mult", operator_type);
        PythonClass MatMult_type = makeType(language, factory, astModule, "MatMult", operator_type);
        PythonClass Div_type = makeType(language, factory, astModule, "Div", operator_type);
        PythonClass Mod_type = makeType(language, factory, astModule, "Mod", operator_type);
        PythonClass Pow_type = makeType(language, factory, astModule, "Pow", operator_type);
        PythonClass LShift_type = makeType(language, factory, astModule, "LShift", operator_type);
        PythonClass RShift_type = makeType(language, factory, astModule, "RShift", operator_type);
        PythonClass BitOr_type = makeType(language, factory, astModule, "BitOr", operator_type);
        PythonClass BitXor_type = makeType(language, factory, astModule, "BitXor", operator_type);
        PythonClass BitAnd_type = makeType(language, factory, astModule, "BitAnd", operator_type);
        PythonClass FloorDiv_type = makeType(language, factory, astModule, "FloorDiv", operator_type);
        PythonClass unaryop_type = makeType(language, factory, astModule, "unaryop", astType);
        PythonClass Invert_type = makeType(language, factory, astModule, "Invert", unaryop_type);
        PythonClass Not_type = makeType(language, factory, astModule, "Not", unaryop_type);
        PythonClass UAdd_type = makeType(language, factory, astModule, "UAdd", unaryop_type);
        PythonClass USub_type = makeType(language, factory, astModule, "USub", unaryop_type);
        PythonClass cmpop_type = makeType(language, factory, astModule, "cmpop", astType);
        PythonClass Eq_type = makeType(language, factory, astModule, "Eq", cmpop_type);
        PythonClass NotEq_type = makeType(language, factory, astModule, "NotEq", cmpop_type);
        PythonClass Lt_type = makeType(language, factory, astModule, "Lt", cmpop_type);
        PythonClass LtE_type = makeType(language, factory, astModule, "LtE", cmpop_type);
        PythonClass Gt_type = makeType(language, factory, astModule, "Gt", cmpop_type);
        PythonClass GtE_type = makeType(language, factory, astModule, "GtE", cmpop_type);
        PythonClass Is_type = makeType(language, factory, astModule, "Is", cmpop_type);
        PythonClass IsNot_type = makeType(language, factory, astModule, "IsNot", cmpop_type);
        PythonClass In_type = makeType(language, factory, astModule, "In", cmpop_type);
        PythonClass NotIn_type = makeType(language, factory, astModule, "NotIn", cmpop_type);
        PythonClass comprehension_type = makeType(language, factory, astModule, "comprehension", astType);
        PythonClass excepthandler_type = makeType(language, factory, astModule, "excepthandler", astType);
        PythonClass ExceptHandler_type = makeType(language, factory, astModule, "ExceptHandler", excepthandler_type);
        PythonClass arguments_type = makeType(language, factory, astModule, "arguments", astType);
        PythonClass arg_type = makeType(language, factory, astModule, "arg", astType);
        PythonClass keyword_type = makeType(language, factory, astModule, "keyword", astType);
        PythonClass alias_type = makeType(language, factory, astModule, "alias", astType);
        PythonClass withitem_type = makeType(language, factory, astModule, "withitem", astType);
        PythonClass type_ignore_type = makeType(language, factory, astModule, "type_ignore", astType);
        PythonClass TypeIgnore_type = makeType(language, factory, astModule, "TypeIgnore", type_ignore_type);
    }

    private static PythonClass makeType(PythonLanguage language, PythonObjectFactory factory, PythonModule astModule, String name, PythonAbstractClass base) {
        TruffleString tsName = toTruffleStringUncached(name);
        PythonClass newType = factory.createPythonClassAndFixupSlots(language, PythonBuiltinClassType.PythonClass, tsName, new PythonAbstractClass[]{base});
        astModule.setAttribute(tsName, newType);
        return newType;
    }
}
