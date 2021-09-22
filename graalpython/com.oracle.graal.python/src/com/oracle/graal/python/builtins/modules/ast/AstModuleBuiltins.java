/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

@CoreFunctions(defineModule = "_ast")
public class AstModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("PyCF_ONLY_AST", 0);
    }

    @Override
    @SuppressWarnings("unused")
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule astModule = core.lookupBuiltinModule("_ast");
        PythonBuiltinClass astType = core.lookupType(PythonBuiltinClassType.AST);
        /*
         * These classes are not builtin in CPython and the ast module relies on them having
         * writable class fields.
         */
        PythonClass mod_type = makeType(astModule, "mod", astType);
        PythonClass Module_type = makeType(astModule, "Module", mod_type);
        PythonClass Interactive_type = makeType(astModule, "Interactive", mod_type);
        PythonClass Expression_type = makeType(astModule, "Expression", mod_type);
        PythonClass FunctionType_type = makeType(astModule, "FunctionType", mod_type);
        PythonClass Suite_type = makeType(astModule, "Suite", mod_type);
        PythonClass stmt_type = makeType(astModule, "stmt", astType);
        PythonClass FunctionDef_type = makeType(astModule, "FunctionDef", stmt_type);
        PythonClass AsyncFunctionDef_type = makeType(astModule, "AsyncFunctionDef", stmt_type);
        PythonClass ClassDef_type = makeType(astModule, "ClassDef", stmt_type);
        PythonClass Return_type = makeType(astModule, "Return", stmt_type);
        PythonClass Delete_type = makeType(astModule, "Delete", stmt_type);
        PythonClass Assign_type = makeType(astModule, "Assign", stmt_type);
        PythonClass AugAssign_type = makeType(astModule, "AugAssign", stmt_type);
        PythonClass AnnAssign_type = makeType(astModule, "AnnAssign", stmt_type);
        PythonClass For_type = makeType(astModule, "For", stmt_type);
        PythonClass AsyncFor_type = makeType(astModule, "AsyncFor", stmt_type);
        PythonClass While_type = makeType(astModule, "While", stmt_type);
        PythonClass If_type = makeType(astModule, "If", stmt_type);
        PythonClass With_type = makeType(astModule, "With", stmt_type);
        PythonClass AsyncWith_type = makeType(astModule, "AsyncWith", stmt_type);
        PythonClass Raise_type = makeType(astModule, "Raise", stmt_type);
        PythonClass Try_type = makeType(astModule, "Try", stmt_type);
        PythonClass Assert_type = makeType(astModule, "Assert", stmt_type);
        PythonClass Import_type = makeType(astModule, "Import", stmt_type);
        PythonClass ImportFrom_type = makeType(astModule, "ImportFrom", stmt_type);
        PythonClass Global_type = makeType(astModule, "Global", stmt_type);
        PythonClass Nonlocal_type = makeType(astModule, "Nonlocal", stmt_type);
        PythonClass Expr_type = makeType(astModule, "Expr", stmt_type);
        PythonClass Pass_type = makeType(astModule, "Pass", stmt_type);
        PythonClass Break_type = makeType(astModule, "Break", stmt_type);
        PythonClass Continue_type = makeType(astModule, "Continue", stmt_type);
        PythonClass expr_type = makeType(astModule, "expr", astType);
        PythonClass BoolOp_type = makeType(astModule, "BoolOp", expr_type);
        PythonClass NamedExpr_type = makeType(astModule, "NamedExpr", expr_type);
        PythonClass BinOp_type = makeType(astModule, "BinOp", expr_type);
        PythonClass UnaryOp_type = makeType(astModule, "UnaryOp", expr_type);
        PythonClass Lambda_type = makeType(astModule, "Lambda", expr_type);
        PythonClass IfExp_type = makeType(astModule, "IfExp", expr_type);
        PythonClass Dict_type = makeType(astModule, "Dict", expr_type);
        PythonClass Set_type = makeType(astModule, "Set", expr_type);
        PythonClass ListComp_type = makeType(astModule, "ListComp", expr_type);
        PythonClass SetComp_type = makeType(astModule, "SetComp", expr_type);
        PythonClass DictComp_type = makeType(astModule, "DictComp", expr_type);
        PythonClass GeneratorExp_type = makeType(astModule, "GeneratorExp", expr_type);
        PythonClass Await_type = makeType(astModule, "Await", expr_type);
        PythonClass Yield_type = makeType(astModule, "Yield", expr_type);
        PythonClass YieldFrom_type = makeType(astModule, "YieldFrom", expr_type);
        PythonClass Compare_type = makeType(astModule, "Compare", expr_type);
        PythonClass Call_type = makeType(astModule, "Call", expr_type);
        PythonClass FormattedValue_type = makeType(astModule, "FormattedValue", expr_type);
        PythonClass JoinedStr_type = makeType(astModule, "JoinedStr", expr_type);
        PythonClass Constant_type = makeType(astModule, "Constant", expr_type);
        PythonClass Attribute_type = makeType(astModule, "Attribute", expr_type);
        PythonClass Subscript_type = makeType(astModule, "Subscript", expr_type);
        PythonClass Starred_type = makeType(astModule, "Starred", expr_type);
        PythonClass Name_type = makeType(astModule, "Name", expr_type);
        PythonClass List_type = makeType(astModule, "List", expr_type);
        PythonClass Tuple_type = makeType(astModule, "Tuple", expr_type);
        PythonClass expr_context_type = makeType(astModule, "expr_context", astType);
        PythonClass Load_type = makeType(astModule, "Load", expr_context_type);
        PythonClass Store_type = makeType(astModule, "Store", expr_context_type);
        PythonClass Del_type = makeType(astModule, "Del", expr_context_type);
        PythonClass AugLoad_type = makeType(astModule, "AugLoad", expr_context_type);
        PythonClass AugStore_type = makeType(astModule, "AugStore", expr_context_type);
        PythonClass Param_type = makeType(astModule, "Param", expr_context_type);
        PythonClass slice_type = makeType(astModule, "slice", astType);
        PythonClass Slice_type = makeType(astModule, "Slice", slice_type);
        PythonClass ExtSlice_type = makeType(astModule, "ExtSlice", slice_type);
        PythonClass Index_type = makeType(astModule, "Index", slice_type);
        PythonClass boolop_type = makeType(astModule, "boolop", astType);
        PythonClass And_type = makeType(astModule, "And", boolop_type);
        PythonClass Or_type = makeType(astModule, "Or", boolop_type);
        PythonClass operator_type = makeType(astModule, "operator", astType);
        PythonClass Add_type = makeType(astModule, "Add", operator_type);
        PythonClass Sub_type = makeType(astModule, "Sub", operator_type);
        PythonClass Mult_type = makeType(astModule, "Mult", operator_type);
        PythonClass MatMult_type = makeType(astModule, "MatMult", operator_type);
        PythonClass Div_type = makeType(astModule, "Div", operator_type);
        PythonClass Mod_type = makeType(astModule, "Mod", operator_type);
        PythonClass Pow_type = makeType(astModule, "Pow", operator_type);
        PythonClass LShift_type = makeType(astModule, "LShift", operator_type);
        PythonClass RShift_type = makeType(astModule, "RShift", operator_type);
        PythonClass BitOr_type = makeType(astModule, "BitOr", operator_type);
        PythonClass BitXor_type = makeType(astModule, "BitXor", operator_type);
        PythonClass BitAnd_type = makeType(astModule, "BitAnd", operator_type);
        PythonClass FloorDiv_type = makeType(astModule, "FloorDiv", operator_type);
        PythonClass unaryop_type = makeType(astModule, "unaryop", astType);
        PythonClass Invert_type = makeType(astModule, "Invert", unaryop_type);
        PythonClass Not_type = makeType(astModule, "Not", unaryop_type);
        PythonClass UAdd_type = makeType(astModule, "UAdd", unaryop_type);
        PythonClass USub_type = makeType(astModule, "USub", unaryop_type);
        PythonClass cmpop_type = makeType(astModule, "cmpop", astType);
        PythonClass Eq_type = makeType(astModule, "Eq", cmpop_type);
        PythonClass NotEq_type = makeType(astModule, "NotEq", cmpop_type);
        PythonClass Lt_type = makeType(astModule, "Lt", cmpop_type);
        PythonClass LtE_type = makeType(astModule, "LtE", cmpop_type);
        PythonClass Gt_type = makeType(astModule, "Gt", cmpop_type);
        PythonClass GtE_type = makeType(astModule, "GtE", cmpop_type);
        PythonClass Is_type = makeType(astModule, "Is", cmpop_type);
        PythonClass IsNot_type = makeType(astModule, "IsNot", cmpop_type);
        PythonClass In_type = makeType(astModule, "In", cmpop_type);
        PythonClass NotIn_type = makeType(astModule, "NotIn", cmpop_type);
        PythonClass comprehension_type = makeType(astModule, "comprehension", astType);
        PythonClass excepthandler_type = makeType(astModule, "excepthandler", astType);
        PythonClass ExceptHandler_type = makeType(astModule, "ExceptHandler", excepthandler_type);
        PythonClass arguments_type = makeType(astModule, "arguments", astType);
        PythonClass arg_type = makeType(astModule, "arg", astType);
        PythonClass keyword_type = makeType(astModule, "keyword", astType);
        PythonClass alias_type = makeType(astModule, "alias", astType);
        PythonClass withitem_type = makeType(astModule, "withitem", astType);
        PythonClass type_ignore_type = makeType(astModule, "type_ignore", astType);
        PythonClass TypeIgnore_type = makeType(astModule, "TypeIgnore", type_ignore_type);
    }

    private static PythonClass makeType(PythonModule astModule, String name, PythonAbstractClass base) {
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        PythonClass newType = factory.createPythonClassAndFixupSlots(PythonBuiltinClassType.PythonClass, name, new PythonAbstractClass[]{base});
        astModule.setAttribute(name, newType);
        return newType;
    }
}
