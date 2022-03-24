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
package com.oracle.graal.python.compiler;

import static com.oracle.graal.python.compiler.OpCodes.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Stack;

import com.oracle.graal.python.pegparser.ExprContext;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.util.PythonUtils;

public class Compiler implements SSTreeVisitor<Void> {
    String filename;
    ScopeEnvironment env;
    EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
    int optimizationLevel = 0;
    boolean isInteractive = false;
    int nestingLevel = 0;
    CompilationUnit unit;
    Stack<CompilationUnit> stack = new Stack<>();

    public enum Flags {
    }

    public CompilationUnit compile(ModTy mod, String filename, EnumSet<Flags> flags, int optimizationLevel) {
        this.filename = filename == null ? "<module>" : filename;
        this.flags = flags;
        this.env = new ScopeEnvironment(mod);
        enterScope("<module>", CompilationScope.Module, mod);
        mod.accept(this);
        CompilationUnit topUnit = unit;
        if (!unit.currentBlock.isReturn()) {
            if (!(mod instanceof ModTy.Expression)) {
                // add a none return at the end
                addOp(LOAD_NONE);
            }
            addOp(RETURN_VALUE);
        }
        exitScope();
        return topUnit;
        // return (!(mod instanceof ModTy.Expression));
    }

    // helpers

    private void enterScope(String name, CompilationScope scopeType, SSTNode node) {
        enterScope(name, scopeType, node, 0, 0, 0, false, false);
    }

    private void enterScope(String name, CompilationScope scope, SSTNode node, ArgumentsTy args) {
        int argc, pargc, kwargc;
        boolean splat, kwSplat;
        if (args == null) {
            argc = pargc = kwargc = 0;
            splat = kwSplat = false;
        } else {
            argc = args.args == null ? 0 : args.args.length;
            pargc = args.posOnlyArgs == null ? 0 : args.posOnlyArgs.length;
            kwargc = args.kwOnlyArgs == null ? 0 : args.kwOnlyArgs.length;
            splat = args.varArg != null;
            kwSplat = args.kwArg != null;
        }
        enterScope(name, scope, node, argc, pargc, kwargc, splat, kwSplat);
    }

    private void enterScope(String name, CompilationScope scopeType, SSTNode node, int argc, int pargc, int kwargc,
                    boolean hasSplat, boolean hasKwSplat) {
        if (unit != null) {
            stack.add(unit);
        }
        unit = new CompilationUnit(scopeType, env.lookupScope(node), name, unit, stack.size(), argc, pargc, kwargc,
                        hasSplat, hasKwSplat, node.getStartOffset());
        nestingLevel++;
    }

    private void exitScope() {
        nestingLevel--;
        if (!stack.isEmpty()) {
            unit = stack.pop();
        } else {
            unit = null;
        }
    }

    private void checkForbiddenName(String id, ExprContext context) {
        if (context == ExprContext.Store) {
            if (id.equals("__debug__")) {
                // TODO: throw error
            }
        }
        if (context == ExprContext.Delete) {
            if (id.equals("__debug__")) {
                // TODO: throw error
            }
        }
    }

    private boolean containsAnnotations(StmtTy[] stmts) {
        if (stmts == null) {
            return false;
        }
        for (StmtTy stmt : stmts) {
            if (stmt instanceof StmtTy.AnnAssign) {
                return true;
            } else if (stmt instanceof StmtTy.For) {
                return containsAnnotations(((StmtTy.For) stmt).body) || containsAnnotations(((StmtTy.For) stmt).orElse);
            } else if (stmt instanceof StmtTy.While) {
                return containsAnnotations(((StmtTy.While) stmt).body) || containsAnnotations(((StmtTy.While) stmt).orElse);
            } else if (stmt instanceof StmtTy.If) {
                return containsAnnotations(((StmtTy.If) stmt).body) || containsAnnotations(((StmtTy.If) stmt).orElse);
            } else if (stmt instanceof StmtTy.With) {
                return containsAnnotations(((StmtTy.With) stmt).body);
            } else if (stmt instanceof StmtTy.Try) {
                StmtTy.Try tryStmt = (StmtTy.Try) stmt;
                if (tryStmt.handlers != null) {
                    for (StmtTy.Try.ExceptHandler h : tryStmt.handlers) {
                        if (containsAnnotations(h.body)) {
                            return true;
                        }
                    }
                }
                return containsAnnotations(tryStmt.body) || containsAnnotations(tryStmt.finalBody) || containsAnnotations(tryStmt.orElse);
            } else {
                return false;
            }
        }
        return false;
    }

    private Void addOp(OpCodes code) {
        addOp(code, 0, unit.startOffset);
        return null;
    }

    private void addOp(OpCodes code, Block target) {
        Block b = unit.currentBlock;
        b.instr.add(new Instruction(code, 0, target, unit.startOffset));
    }

    private Void addOp(OpCodes code, int arg) {
        addOp(code, arg, unit.startOffset);
        return null;
    }

    private void addOp(OpCodes code, int arg, int srcOffset) {
        Block b = unit.currentBlock;
        b.instr.add(new Instruction(code, arg, null, srcOffset));
    }

    private void addOpName(OpCodes code, HashMap<String, Integer> dict, String name) {
        String mangled = ScopeEnvironment.mangle(unit.privateName, name);
        addOpObject(code, dict, mangled);
    }

    private <T> void addOpObject(OpCodes code, HashMap<T, Integer> dict, T obj) {
        int arg = addObject(dict, obj);
        addOp(code, arg);
    }

    private void addDerefVariableOpcode(ExprContext ctx, int idx) {
        switch (ctx) {
            case Load:
                addOp(unit.scope.isClass() ? LOAD_CLASSDEREF : LOAD_DEREF, idx);
                break;
            case Store:
                addOp(STORE_DEREF, idx);
                break;
            case Delete:
                addOp(DELETE_DEREF, idx);
                break;
        }
    }

    private void addFastVariableOpcode(ExprContext ctx, int idx) {
        switch (ctx) {
            case Load:
                addOp(LOAD_FAST, idx);
                break;
            case Store:
                addOp(STORE_FAST, idx);
                break;
            case Delete:
                addOp(DELETE_FAST, idx);
                break;
        }
    }

    private void addGlobalVariableOpcode(ExprContext ctx, int idx) {
        switch (ctx) {
            case Load:
                addOp(LOAD_GLOBAL, idx);
                break;
            case Store:
                addOp(STORE_GLOBAL, idx);
                break;
            case Delete:
                addOp(DELETE_GLOBAL, idx);
                break;
        }
    }

    private void addNameVariableOpcode(ExprContext ctx, int idx) {
        switch (ctx) {
            case Load:
                addOp(LOAD_NAME, idx);
                break;
            case Store:
                addOp(STORE_NAME, idx);
                break;
            case Delete:
                addOp(DELETE_NAME, idx);
                break;
        }
    }

    private void addNameOp(String name, ExprContext ctx) {
        checkForbiddenName(name, ctx);

        String mangled = ScopeEnvironment.mangle(unit.privateName, name);
        EnumSet<Scope.DefUse> uses = unit.scope.getUseOfName(name);

        if (uses != null) {
            if (uses.contains(Scope.DefUse.Free)) {
                addDerefVariableOpcode(ctx, addObject(unit.freevars, mangled));
                return;
            } else if (uses.contains(Scope.DefUse.Cell)) {
                addDerefVariableOpcode(ctx, addObject(unit.cellvars, mangled));
                return;
            } else if (uses.contains(Scope.DefUse.Local)) {
                if (unit.scope.isFunction()) {
                    addFastVariableOpcode(ctx, addObject(unit.varnames, mangled));
                    return;
                }
            } else if (uses.contains(Scope.DefUse.GlobalImplicit)) {
                if (unit.scope.isFunction()) {
                    addGlobalVariableOpcode(ctx, addObject(unit.names, mangled));
                    return;
                }
            } else if (uses.contains(Scope.DefUse.GlobalExplicit)) {
                addGlobalVariableOpcode(ctx, addObject(unit.names, mangled));
                return;
            }
        }
        addNameVariableOpcode(ctx, addObject(unit.names, mangled));
    }

    private <T> int addObject(HashMap<T, Integer> dict, T o) {
        Integer v = dict.get(o);
        if (v == null) {
            v = dict.size();
            assert v < 256;
            dict.put(o, v);
        }
        return v;
    }

    private String getDocstring(StmtTy[] body) {
        if (body != null && body.length > 0) {
            StmtTy stmt = body[0];
            if (stmt instanceof StmtTy.Expr) {
                ExprTy expr = ((StmtTy.Expr) stmt).value;
                if (expr instanceof ExprTy.Constant) {
                    Object value = ((ExprTy.Constant) expr).value;
                    if (value instanceof String) {
                        return (String) value;
                    }
                }
            }
        }
        return null;
    }

    private int setLocation(int offset) {
        int savedOffset = unit.startOffset;
        unit.startOffset = offset;
        return savedOffset;
    }

    private int setLocation(SSTNode node) {
        return setLocation(node.getStartOffset());
    }

    private class Collector {
        protected final int typeBits;
        protected final int stackItemsPerItem;
        protected int stackItems = 0;
        protected boolean collectionOnStack = false;

        public Collector(int typeBits) {
            this.typeBits = typeBits;
            stackItemsPerItem = typeBits == CollectionBits.DICT ? 2 : 1;
        }

        public Collector(int typeBits, int stackItems) {
            this(typeBits);
            this.stackItems = stackItems;
        }

        public void appendItem() {
            stackItems += stackItemsPerItem;
            if (stackItems + stackItemsPerItem > CollectionBits.MAX_STACK_ELEMENT_COUNT) {
                doFlushStack();
            }
        }

        public void flushStackIfNecessary() {
            if (stackItems > 0) {
                doFlushStack();
            }
        }

        protected void doFlushStack() {
            assert stackItems <= CollectionBits.MAX_STACK_ELEMENT_COUNT;
            if (collectionOnStack) {
                addOp(COLLECTION_ADD_STACK, typeBits | stackItems);
            } else {
                addOp(COLLECTION_FROM_STACK, typeBits | stackItems);
            }
            collectionOnStack = true;
            stackItems = 0;
        }

        public void appendCollection() {
            assert stackItems == 0;
            if (collectionOnStack) {
                addOp(COLLECTION_ADD_COLLECTION, typeBits);
            } else {
                addOp(COLLECTION_FROM_COLLECTION, typeBits);
            }
            collectionOnStack = true;
        }

        public void finishCollection() {
            if (stackItems > 0 || !collectionOnStack) {
                doFlushStack();
            }
        }
    }

    private class KwargsMergingDictCollector extends Collector {
        public KwargsMergingDictCollector() {
            super(CollectionBits.DICT);
        }

        @Override
        protected void doFlushStack() {
            addOp(COLLECTION_FROM_STACK, typeBits | stackItems);
            if (collectionOnStack) {
                addOp(KWARGS_DICT_MERGE);
            }
            collectionOnStack = true;
            stackItems = 0;
        }

        @Override
        public void appendCollection() {
            assert stackItems == 0;
            if (collectionOnStack) {
                addOp(KWARGS_DICT_MERGE);
            } else {
                addOp(COLLECTION_FROM_COLLECTION, typeBits);
            }
            collectionOnStack = true;
        }
    }

    private void collectIntoArray(ExprTy[] nodes, int bits, int alreadyOnStack) {
        Collector collector = new Collector(bits, alreadyOnStack);
        if (nodes != null) {
            for (ExprTy e : nodes) {
                if (e instanceof ExprTy.Starred) {
                    // splat
                    collector.flushStackIfNecessary();
                    e.accept(this);
                    collector.appendCollection();
                } else {
                    e.accept(this);
                    collector.appendItem();
                }
            }
        }
        collector.finishCollection();
    }

    private void collectIntoArray(ExprTy[] nodes, int bits) {
        collectIntoArray(nodes, bits, 0);
    }

    private void collectIntoDict(ExprTy[] keys, ExprTy[] values) {
        Collector collector = new Collector(CollectionBits.DICT);
        if (keys != null) {
            assert keys.length == values.length;
            for (int i = 0; i < keys.length; i++) {
                ExprTy key = keys[i];
                ExprTy value = values[i];
                if (key == null) {
                    // splat
                    collector.flushStackIfNecessary();
                    value.accept(this);
                    collector.appendCollection();
                } else {
                    key.accept(this);
                    value.accept(this);
                    collector.appendItem();
                }
            }
        }
        collector.finishCollection();
    }

    private void collectKeywords(KeywordTy[] keywords) {
        boolean hasSplat = false;
        for (KeywordTy k : keywords) {
            if (k.arg == null) {
                hasSplat = true;
                break;
            }
        }
        if (!hasSplat) {
            Collector collector = new Collector(CollectionBits.KWORDS);
            for (KeywordTy k : keywords) {
                k.accept(this);
                collector.appendItem();
            }
            collector.finishCollection();
        } else if (keywords.length == 1) {
            // Just one splat, no need for merging
            keywords[0].value.accept(this);
            addOp(COLLECTION_FROM_COLLECTION, CollectionBits.KWORDS);
        } else {
            /*
             * We need to emit bytecodes for proper keywords merging with checking for duplicate
             * keys. We accumulate them in an intermediate dict.
             */
            Collector collector = new KwargsMergingDictCollector();
            for (KeywordTy k : keywords) {
                if (k.arg == null) {
                    // splat
                    collector.flushStackIfNecessary();
                    k.value.accept(this);
                    collector.appendCollection();
                } else {
                    addOp(LOAD_CONST, addObject(unit.constants, k.arg));
                    k.value.accept(this);
                    collector.appendItem();
                }
            }
            collector.finishCollection();
            addOp(COLLECTION_FROM_COLLECTION, CollectionBits.KWORDS);
        }
    }

    private void makeClosure(CodeUnit code) {
        if (code.freevars.length > 0) {
            // add the closure
            for (String fv : code.freevars) {
                // special case for class scopes
                int arg;
                if (unit.scopeType == CompilationScope.Class && "__class__".equals(fv) || unit.scope.getUseOfName(fv).contains(Scope.DefUse.Cell)) {
                    arg = unit.cellvars.get(fv);
                } else {
                    arg = unit.freevars.get(fv);
                }
                addOp(LOAD_CLOSURE, arg);
            }
            addOp(CLOSURE_FROM_STACK, code.freevars.length);
        }
        addOp(LOAD_CONST, addObject(unit.constants, code));
        int flags = code.flags & (CodeUnit.HAS_DEFAULTS | CodeUnit.HAS_KWONLY_DEFAULTS | CodeUnit.HAS_ANNOTATIONS | CodeUnit.HAS_CLOSURE);
        addOp(MAKE_FUNCTION, flags);
    }

    // visiting

    private void visitBody(StmtTy[] stmts) {
        if (unit.scope.isModule() && stmts.length > 0) {
            /*
             * Set current line number to the line number of first statement. This way line number
             * for SETUP_ANNOTATIONS will always coincide with the line number of first "real"
             * statement in module. If body is empty, then lineno will be set later in assemble.
             */
            setLocation(stmts[0]);
        }
        if (containsAnnotations(stmts)) {
            // addOp(SETUP_ANNOTATIONS);
        }
        int i = 0;
        String docstring = getDocstring(stmts);
        if (docstring != null) {
            i++;
            stmts[0].accept(this);
            addNameOp("__doc__", ExprContext.Store);
        }
        for (; i < stmts.length; i++) {
            stmts[i].accept(this);
        }
    }

    @Override
    public Void visit(AliasTy node) {
        addOp(LOAD_BYTE, 0);
        addOp(LOAD_CONST, addObject(unit.constants, PythonUtils.EMPTY_STRING_ARRAY));
        addOpName(IMPORT_NAME, unit.names, node.name);
        if (node.asName != null) {
            int dotIdx = node.name.indexOf('.');
            if (dotIdx >= 0) {
                while (true) {
                    int pos = dotIdx + 1;
                    dotIdx = node.name.indexOf('.', pos);
                    int end = dotIdx >= 0 ? dotIdx : node.name.length();
                    String attr = node.name.substring(pos, end);
                    addOpObject(IMPORT_FROM, unit.names, attr);
                    if (dotIdx < 0) {
                        break;
                    }
                    addOp(ROT_TWO);
                    addOp(POP_TOP);
                }
                addNameOp(node.asName, ExprContext.Store);
                addOp(POP_TOP);
            } else {
                addNameOp(node.asName, ExprContext.Store);
            }
        } else {
            int dotIdx = node.name.indexOf('.');
            if (dotIdx >= 0) {
                addNameOp(node.name.substring(0, dotIdx), ExprContext.Store);
            } else {
                addNameOp(node.name, ExprContext.Store);
            }
        }
        return null;
    }

    @Override
    public Void visit(ArgTy node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ArgumentsTy node) {
        throw new IllegalStateException("Should not be visited");
    }

    @Override
    public Void visit(ComprehensionTy node) {
        throw new IllegalStateException("Should not be visited");
    }

    @Override
    public Void visit(ExprTy.Attribute node) {
        int savedOffset = setLocation(node);
        try {
            node.value.accept(this);
            int idx = addObject(unit.names, node.attr);
            switch (node.context) {
                case Store:
                    return addOp(STORE_ATTR, idx);
                case Delete:
                    return addOp(DELETE_ATTR, idx);
                case Load:
                default:
                    return addOp(LOAD_ATTR, idx);
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Await node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.BinOp node) {
        int savedOffset = setLocation(node);
        try {
            node.left.accept(this);
            node.right.accept(this);
            switch (node.op) {
                case ADD:
                    addOp(BINARY_OP, BinaryOps.ADD.ordinal());
                    break;
                case SUB:
                    addOp(BINARY_OP, BinaryOps.SUB.ordinal());
                    break;
                case MULT:
                    addOp(BINARY_OP, BinaryOps.MUL.ordinal());
                    break;
                case MATMULT:
                    addOp(BINARY_OP, BinaryOps.MATMUL.ordinal());
                    break;
                case DIV:
                    addOp(BINARY_OP, BinaryOps.TRUEDIV.ordinal());
                    break;
                case MOD:
                    addOp(BINARY_OP, BinaryOps.MOD.ordinal());
                    break;
                case POW:
                    addOp(BINARY_OP, BinaryOps.POW.ordinal());
                    break;
                case LSHIFT:
                    addOp(BINARY_OP, BinaryOps.LSHIFT.ordinal());
                    break;
                case RSHIFT:
                    addOp(BINARY_OP, BinaryOps.RSHIFT.ordinal());
                    break;
                case BITOR:
                    addOp(BINARY_OP, BinaryOps.OR.ordinal());
                    break;
                case BITXOR:
                    addOp(BINARY_OP, BinaryOps.XOR.ordinal());
                    break;
                case BITAND:
                    addOp(BINARY_OP, BinaryOps.AND.ordinal());
                    break;
                case FLOORDIV:
                    addOp(BINARY_OP, BinaryOps.FLOORDIV.ordinal());
                    break;
                default:
                    throw new UnsupportedOperationException("Not supported yet.");
            }
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.BoolOp node) {
        int savedOffset = setLocation(node);
        try {
            Block end = new Block();
            for (ExprTy v : node.values) {
                v.accept(this);
                switch (node.op) {
                    case And:
                        addOp(JUMP_IF_FALSE_OR_POP, end);
                        break;
                    case Or:
                    default:
                        addOp(JUMP_IF_TRUE_OR_POP, end);
                }
            }
            switch (node.op) {
                case And:
                    addOp(LOAD_TRUE);
                    break;
                case Or:
                default:
                    addOp(LOAD_FALSE);
            }
            unit.useNextBlock(end);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    private boolean isAttributeLoad(ExprTy node) {
        return node instanceof ExprTy.Attribute && ((ExprTy.Attribute) node).context == ExprContext.Load;
    }

    private boolean hasOnlyPlainArgs(ExprTy.Call node) {
        if (node.keywords.length > 0) {
            return false;
        }
        for (ExprTy arg : node.args) {
            if (arg instanceof ExprTy.Starred) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Void visit(ExprTy.Call node) {
        int savedOffset = setLocation(node);
        try {
            // n.b.: we do things completely different from python for calls
            ExprTy func = node.func;
            OpCodes op = CALL_FUNCTION_VARARGS;
            int oparg = 0;
            boolean shortCall = false;
            if (isAttributeLoad(func) && node.keywords.length == 0) {
                ((ExprTy.Attribute) func).value.accept(this);
                op = CALL_METHOD_VARARGS;
                oparg = addObject(unit.names, ((ExprTy.Attribute) func).attr);
                shortCall = node.args.length <= 3;
            } else {
                func.accept(this);
                shortCall = node.args.length <= 4;
            }
            if (hasOnlyPlainArgs(node) && shortCall) {
                if (op == CALL_METHOD_VARARGS) {
                    oparg = (node.args.length << 8) | oparg;
                    op = CALL_METHOD;
                } else {
                    oparg = node.args.length;
                    op = CALL_FUNCTION;
                }
                // fast calls without extra arguments array
                visitSequence(node.args);
                return addOp(op, oparg);
            } else {
                collectIntoArray(node.args, CollectionBits.OBJECT, op == CALL_METHOD_VARARGS ? 1 : 0);
                if (node.keywords.length > 0) {
                    assert op == CALL_FUNCTION_VARARGS;
                    collectKeywords(node.keywords);
                    return addOp(CALL_FUNCTION_KW);
                } else {
                    return addOp(op, oparg);
                }
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    private void addCompareOp(ExprTy.Compare.Operator op) {
        switch (op) {
            case EQ:
                addOp(BINARY_OP, BinaryOps.EQ.ordinal());
                break;
            case NOTEQ:
                addOp(BINARY_OP, BinaryOps.NE.ordinal());
                break;
            case LT:
                addOp(BINARY_OP, BinaryOps.LT.ordinal());
                break;
            case LTE:
                addOp(BINARY_OP, BinaryOps.LE.ordinal());
                break;
            case GT:
                addOp(BINARY_OP, BinaryOps.GT.ordinal());
                break;
            case GTE:
                addOp(BINARY_OP, BinaryOps.GE.ordinal());
                break;
            case IS:
                addOp(BINARY_OP, BinaryOps.IS.ordinal());
                break;
            case ISNOT:
                addOp(BINARY_OP, BinaryOps.IS.ordinal());
                addOp(UNARY_OP, UnaryOps.NOT.ordinal());
                break;
            case IN:
                addOp(BINARY_OP, BinaryOps.IN.ordinal());
                break;
            case NOTIN:
                addOp(BINARY_OP, BinaryOps.IN.ordinal());
                addOp(UNARY_OP, UnaryOps.NOT.ordinal());
                break;
        }
    }

    @Override
    public Void visit(ExprTy.Compare node) {
        int savedOffset = setLocation(node);
        try {
            node.left.accept(this);
            if (node.comparators.length == 1) {
                visitSequence(node.comparators);
                addCompareOp(node.ops[0]);
            } else {
                Block cleanup = new Block();
                int i;
                for (i = 0; i < node.comparators.length - 1; i++) {
                    node.comparators[i].accept(this);
                    addOp(DUP_TOP);
                    addOp(ROT_THREE);
                    addCompareOp(node.ops[i]);
                    addOp(JUMP_IF_FALSE_OR_POP, cleanup);
                }
                node.comparators[i].accept(this);
                addCompareOp(node.ops[i]);
                Block end = new Block();
                addOp(JUMP_FORWARD, end);
                unit.useNextBlock(cleanup);
                addOp(ROT_TWO);
                addOp(POP_TOP);
                unit.useNextBlock(end);
            }
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Constant node) {
        int savedOffset = setLocation(node);
        try {
            switch (node.kind) {
                case OBJECT:
                    return addOp(LOAD_CONST, addObject(unit.constants, node.value));
                case NONE:
                    return addOp(LOAD_NONE);
                case ELLIPSIS:
                    return addOp(LOAD_ELLIPSIS);
                case BOOLEAN:
                    return addOp(node.value == Boolean.TRUE ? LOAD_TRUE : LOAD_FALSE);
                case LONG:
                    return addLoadLong(node.longValue);
                case DOUBLE:
                    return addOp(LOAD_DOUBLE, addObject(unit.primitiveConstants, node.longValue));
                case COMPLEX:
                    addOp(LOAD_DOUBLE, addObject(unit.primitiveConstants, node.getReal()));
                    addOp(LOAD_DOUBLE, addObject(unit.primitiveConstants, node.getImaginary()));
                    return addOp(MAKE_COMPLEX);
                case BIGINTEGER:
                    return addOp(LOAD_BIGINT, addObject(unit.constants, node.value));
                case RAW:
                    return addOp(LOAD_STRING, addObject(unit.constants, node.value));
                case BYTES:
                    return addOp(LOAD_BYTES, addObject(unit.constants, node.value));
                default:
                    throw new UnsupportedOperationException("Not supported yet.");
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    private Void addLoadLong(long value) {
        if (value == (byte) value) {
            return addOp(LOAD_BYTE, (byte) value);
        } else {
            return addOp(LOAD_LONG, addObject(unit.primitiveConstants, value));
        }
    }

    @Override
    public Void visit(ExprTy.Dict node) {
        int savedOffset = setLocation(node);
        try {
            collectIntoDict(node.keys, node.values);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        return visitComprehension(node, "<dictcomp>", node.generators, node.key, node.value, ComprehensionType.DICT);
    }

    @Override
    public Void visit(ExprTy.FormattedValue node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.IfExp node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.JoinedStr node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        int savedOffset = setLocation(node);
        try {
            checkForbiddenArgs(node.args);
            int flags = collectDefaults(node.args);
            enterScope("<lambda>", CompilationScope.Lambda, node, node.args);
            CodeUnit code;
            try {
                node.body.accept(this);
                addOp(RETURN_VALUE);
                code = unit.assemble(filename, flags);
            } finally {
                exitScope();
            }
            makeClosure(code);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    private Void unpackInto(ExprTy[] elements) {
        boolean unpack = false;
        for (int i = 0; i < elements.length; i++) {
            ExprTy e = elements[i];
            if (e instanceof ExprTy.Starred) {
                if (unpack) {
                    // TODO: raise "multiple starred expressions in assignment"
                }
                unpack = true;
                if (elements.length < 0xf) {
                    // single byte arg is enough
                    addOp(UNPACK_EX, (i << 4) | (elements.length - i - 1));
                } else if (elements.length <= 0xff) {
                    // short arg is enough
                    addOp(UNPACK_EX_LARGE, i << 8 | (elements.length - i - 1));
                } else {
                    // don't support this, it's silly
                    // TODO: raise too many expressions in star-unpacking assignment
                }
            }
        }
        if (!unpack) {
            if (elements.length <= 0xff) {
                addOp(UNPACK_SEQUENCE, elements.length);
            } else if (elements.length <= 0xffff) {
                addOp(UNPACK_SEQUENCE_LARGE, elements.length);
            } else {
                // don't support this, it's silly
                // TODO: raise too many expressions in star-unpacking assignment
                return null;
            }
        }
        for (ExprTy e : elements) {
            if (e instanceof ExprTy.Starred) {
                ((ExprTy.Starred) e).value.accept(this);
            } else if (e != null) {
                e.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visit(ExprTy.List node) {
        int savedOffset = setLocation(node);
        try {
            switch (node.context) {
                case Store:
                    return unpackInto(node.elements);
                case Load:
                    collectIntoArray(node.elements, CollectionBits.LIST);
                    return null;
                case Delete:
                default:
                    return visitSequence(node.elements);
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    private enum ComprehensionType {
        LIST(CollectionBits.LIST),
        SET(CollectionBits.SET),
        DICT(CollectionBits.DICT),
        GENEXPR(-1);

        public final int typeBits;

        ComprehensionType(int typeBits) {
            this.typeBits = typeBits;
        }
    }

    @Override
    public Void visit(ExprTy.ListComp node) {
        return visitComprehension(node, "<listcomp>", node.generators, node.element, null, ComprehensionType.LIST);
    }

    private Void visitComprehension(ExprTy node, String name, ComprehensionTy[] generators, ExprTy element, ExprTy value, ComprehensionType type) {
        /*
         * Create an inner anonymous function to run the comprehension. It takes the outermost
         * iterator as an argument and returns the accumulated sequence
         */
        int savedOffset = setLocation(node);
        try {
            enterScope(name, CompilationScope.Comprehension, node, 1, 0, 0, false, false);
            if (type != ComprehensionType.GENEXPR) {
                // The result accumulator, empty at the beginning
                addOp(COLLECTION_FROM_STACK, type.typeBits);
            }
            visitComprehensionGenerator(generators, 0, element, value, type);
            if (type != ComprehensionType.GENEXPR) {
                addOp(RETURN_VALUE);
            }
            CodeUnit code = unit.assemble(filename, 0);
            exitScope();
            makeClosure(code);
            generators[0].iter.accept(this);
            addOp(GET_ITER);
            addOp(CALL_FUNCTION, 1);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    private void visitComprehensionGenerator(ComprehensionTy[] generators, int i, ExprTy element, ExprTy value, ComprehensionType type) {
        ComprehensionTy gen = generators[i];
        if (i == 0) {
            /* The iterator is the function argument for the outermost generator */
            addOp(LOAD_FAST, 0);
        } else {
            /* Create the iterator for nested iteration */
            gen.iter.accept(this);
            addOp(GET_ITER);
        }
        Block start = new Block();
        Block ifCleanup = new Block();
        Block anchor = new Block();
        unit.useNextBlock(start);
        addOp(FOR_ITER, anchor);
        gen.target.accept(this);
        for (ExprTy ifExpr : gen.ifs) {
            jumpIf(ifExpr, ifCleanup, false);
        }
        if (i + 1 < generators.length) {
            visitComprehensionGenerator(generators, i + 1, element, value, type);
        }
        if (i == generators.length - 1) {
            /* The last generator produces the resulting element to be appended/yielded */
            element.accept(this);
            int collectionStackDepth = generators.length + 1;
            if (value != null) {
                value.accept(this);
                collectionStackDepth++;
            }
            if (type == ComprehensionType.GENEXPR) {
                // TODO yield and pop
                throw new IllegalStateException("Not supported yet.");
            } else {
                /*
                 * There is an iterator for every generator on the stack. We need to append to the
                 * collection that's below them
                 */
                // TODO turn this into an exeption
                assert collectionStackDepth <= CollectionBits.MAX_STACK_ELEMENT_COUNT;
                addOp(ADD_TO_COLLECTION, collectionStackDepth | type.typeBits);
            }
        }
        unit.useNextBlock(ifCleanup);
        addOp(JUMP_BACKWARD, start);
        unit.useNextBlock(anchor);
    }

    @Override
    public Void visit(ExprTy.Name node) {
        int savedOffset = setLocation(node);
        try {
            addNameOp(node.id, node.context);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.NamedExpr node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Set node) {
        int savedOffset = setLocation(node);
        try {
            collectIntoArray(node.elements, CollectionBits.SET);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        return visitComprehension(node, "<setcomp>", node.generators, node.element, null, ComprehensionType.SET);
    }

    @Override
    public Void visit(ExprTy.Slice node) {
        int savedOffset = setLocation(node);
        try {
            int n = 2;
            if (node.lower != null) {
                node.lower.accept(this);
            } else {
                addOp(LOAD_NONE);
            }
            if (node.upper != null) {
                node.upper.accept(this);
            } else {
                addOp(LOAD_NONE);
            }
            if (node.step != null) {
                node.step.accept(this);
                n++;
            }
            addOp(BUILD_SLICE, n);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Starred node) {
        int savedOffset = setLocation(node);
        try {
            // TODO context?
            node.value.accept(this);
            return null;
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Subscript node) {
        int savedOffset = setLocation(node);
        try {
            node.value.accept(this);
            node.slice.accept(this);
            switch (node.context) {
                case Load:
                    return addOp(BINARY_SUBSCR);
                case Store:
                    return addOp(STORE_SUBSCR);
                case Delete:
                default:
                    return addOp(DELETE_SUBSCR);
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Tuple node) {
        int savedOffset = setLocation(node);
        try {
            switch (node.context) {
                case Store:
                    return unpackInto(node.elements);
                case Load:
                    /*
                     * We don't have mutation operations for tuples, so if we cannot construct the
                     * tuple within a single instruction, we construct a list and convert it to a
                     * tuple.
                     */
                    boolean useList = false;
                    if (node.elements != null) {
                        if (node.elements.length > CollectionBits.MAX_STACK_ELEMENT_COUNT) {
                            useList = true;
                        } else {
                            for (ExprTy e : node.elements) {
                                if (e instanceof ExprTy.Starred) {
                                    useList = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!useList) {
                        collectIntoArray(node.elements, CollectionBits.TUPLE);
                    } else {
                        collectIntoArray(node.elements, CollectionBits.LIST);
                        // FIXME this operation copies the underlying storage, we should make a
                        // separate instruction for shallow conversion
                        addOp(COLLECTION_FROM_COLLECTION, CollectionBits.TUPLE);
                    }
                    return null;
                case Delete:
                default:
                    return visitSequence(node.elements);
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.UnaryOp node) {
        int savedOffset = setLocation(node);
        try {
            node.operand.accept(this);
            switch (node.op) {
                case ADD:
                    return addOp(UNARY_OP, UnaryOps.POSITIVE.ordinal());
                case INVERT:
                    return addOp(UNARY_OP, UnaryOps.INVERT.ordinal());
                case NOT:
                    return addOp(UNARY_OP, UnaryOps.NOT.ordinal());
                case SUB:
                    return addOp(UNARY_OP, UnaryOps.NEGATIVE.ordinal());
                default:
                    throw new IllegalStateException("Unknown unary operation");
            }
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.Yield node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(ExprTy.YieldFrom node) {
        int savedOffset = setLocation(node);
        try {
            throw new UnsupportedOperationException("Not supported yet.");
        } finally {
            setLocation(savedOffset);
        }
    }

    @Override
    public Void visit(KeywordTy node) {
        node.value.accept(this);
        setLocation(node);
        return addOp(MAKE_KEYWORD, addObject(unit.constants, node.arg));
    }

    @Override
    public Void visit(ModTy.Expression node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ModTy.FunctionType node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ModTy.Interactive node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ModTy.Module node) {
        visitBody(node.body);
        return null;
    }

    @Override
    public Void visit(ModTy.TypeIgnore node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Assert node) {
        setLocation(node);
        Block end = new Block();
        jumpIf(node.test, end, true);
        addOp(LOAD_ASSERTION_ERROR);
        if (node.msg != null) {
            node.msg.accept(this);
            addOp(CALL_FUNCTION, 1);
        }
        addOp(RAISE_VARARGS, 1);
        unit.useNextBlock(end);
        return null;
    }

    @Override
    public Void visit(StmtTy.Assign node) {
        setLocation(node);
        node.value.accept(this);
        for (int i = 0; i < node.targets.length; i++) {
            if (i != node.targets.length - 1) {
                addOp(DUP_TOP);
            }
            node.targets[i].accept(this);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFor node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AsyncWith node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AugAssign node) {
        int savedOffset = setLocation(node);
        node.target.copyWithContext(ExprContext.Load).accept(this);
        setLocation(savedOffset);
        node.value.accept(this);
        setLocation(node);
        switch (node.op) {
            case ADD:
                addOp(BINARY_OP, BinaryOps.INPLACE_ADD.ordinal());
                break;
            case SUB:
                addOp(BINARY_OP, BinaryOps.INPLACE_SUB.ordinal());
                break;
            case MULT:
                addOp(BINARY_OP, BinaryOps.INPLACE_MUL.ordinal());
                break;
            case MATMULT:
                addOp(BINARY_OP, BinaryOps.INPLACE_MATMUL.ordinal());
                break;
            case DIV:
                addOp(BINARY_OP, BinaryOps.INPLACE_TRUEDIV.ordinal());
                break;
            case MOD:
                addOp(BINARY_OP, BinaryOps.INPLACE_MOD.ordinal());
                break;
            case POW:
                addOp(BINARY_OP, BinaryOps.INPLACE_POW.ordinal());
                break;
            case LSHIFT:
                addOp(BINARY_OP, BinaryOps.INPLACE_LSHIFT.ordinal());
                break;
            case RSHIFT:
                addOp(BINARY_OP, BinaryOps.INPLACE_RSHIFT.ordinal());
                break;
            case BITOR:
                addOp(BINARY_OP, BinaryOps.INPLACE_OR.ordinal());
                break;
            case BITXOR:
                addOp(BINARY_OP, BinaryOps.INPLACE_XOR.ordinal());
                break;
            case BITAND:
                addOp(BINARY_OP, BinaryOps.INPLACE_AND.ordinal());
                break;
            case FLOORDIV:
                addOp(BINARY_OP, BinaryOps.INPLACE_FLOORDIV.ordinal());
                break;
            default:
                throw new UnsupportedOperationException("Not supported yet.");
        }
        return node.target.accept(this);
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        setLocation(node);
        visitSequence(node.decoratorList);

        enterScope(node.name, CompilationScope.Class, node, 0, 0, 0, false, false);
        addNameOp("__name__", ExprContext.Load);
        addNameOp("__module__", ExprContext.Store);
        addOp(LOAD_CONST, addObject(unit.constants, unit.qualName));
        addNameOp("__qualname__", ExprContext.Store);

        visitBody(node.body);

        if (unit.scope.needsClassClosure()) {
            int idx = unit.cellvars.get("__class__");
            addOp(LOAD_CLOSURE, idx);
            addOp(DUP_TOP);
            addNameOp("__classcell__", ExprContext.Store);
        } else {
            addOp(LOAD_NONE);
        }
        addOp(RETURN_VALUE);
        CodeUnit co = unit.assemble(filename, 0);
        exitScope();

        addOp(LOAD_BUILD_CLASS);
        makeClosure(co);
        addOp(LOAD_CONST, addObject(unit.constants, node.name));

        if ((node.bases.length < 3) && node.keywords.length == 0) {
            visitSequence(node.bases);
            addOp(CALL_FUNCTION, 2 + node.bases.length);
        } else if (node.keywords.length == 0) {
            collectIntoArray(node.bases, CollectionBits.OBJECT, 2);
            addOp(CALL_FUNCTION_VARARGS);
        } else {
            collectIntoArray(node.bases, CollectionBits.OBJECT, 2);
            collectKeywords(node.keywords);
            addOp(CALL_FUNCTION_KW);
        }

        if (node.decoratorList != null) {
            for (ExprTy decorator : node.decoratorList) {
                addOp(CALL_FUNCTION, 1);
            }
        }

        addNameOp(node.name, ExprContext.Store);

        return null;
    }

    @Override
    public Void visit(StmtTy.Delete node) {
        setLocation(node);
        visitSequence(node.targets);
        return null;
    }

    @Override
    public Void visit(StmtTy.Expr node) {
        setLocation(node);
        if (!(node.value instanceof ExprTy.Constant)) {
            node.value.accept(this);
            addOp(POP_TOP);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.For node) {
        setLocation(node);
        Block head = new Block();
        Block body = new Block();
        Block end = new Block();
        Block orelse = node.orElse != null ? new Block() : end;

        node.iter.accept(this);
        addOp(GET_ITER);

        unit.useNextBlock(head);
        addOp(FOR_ITER, orelse);

        unit.useNextBlock(body);
        unit.blockInfoStack.push(new BlockInfo(head, end, BlockInfo.Type.FOR_LOOP));
        try {
            node.target.accept(this);
            visitSequence(node.body);
            addOp(JUMP_BACKWARD, head);
        } finally {
            unit.blockInfoStack.pop();
        }

        if (node.orElse != null) {
            unit.useNextBlock(orelse);
            visitSequence(node.orElse);
        }

        unit.useNextBlock(end);
        return null;
    }

    @Override
    public Void visit(StmtTy.FunctionDef node) {
        setLocation(node);
        checkForbiddenArgs(node.args);

        // visit decorators
        visitSequence(node.decoratorList);

        // visit defaults outside the function scope
        int flags = collectDefaults(node.args);

        // TODO: visit annotations

        enterScope(node.name, CompilationScope.Function, node, node.args);

        CodeUnit code;
        try {
            String docString = getDocstring(node.body);
            addObject(unit.constants, docString);
            visitSequence(node.body);
            code = unit.assemble(filename, flags);
        } finally {
            exitScope();
        }

        makeClosure(code);

        if (node.decoratorList != null) {
            for (ExprTy decorator : node.decoratorList) {
                addOp(CALL_FUNCTION, 1);
            }
        }

        addNameOp(node.name, ExprContext.Store);
        return null;
    }

    private int collectDefaults(ArgumentsTy args) {
        int flags = 0;
        if (args != null) {
            if (args.defaults != null && args.defaults.length > 0) {
                collectIntoArray(args.defaults, CollectionBits.OBJECT);
                flags |= CodeUnit.HAS_DEFAULTS;
            }
            if (args.kwDefaults != null && args.kwDefaults.length > 0) {
                ArrayList<KeywordTy> defs = new ArrayList<>();
                for (int i = 0; i < args.kwOnlyArgs.length; i++) {
                    ArgTy arg = args.kwOnlyArgs[i];
                    ExprTy def = args.kwDefaults[i];
                    if (def != null) {
                        String mangled = ScopeEnvironment.mangle(unit.privateName, arg.arg);
                        defs.add(new KeywordTy(mangled, def, arg.getStartOffset(), def.getEndOffset()));
                    }
                }
                collectKeywords(defs.toArray(KeywordTy[]::new));
                return CodeUnit.HAS_KWONLY_DEFAULTS;
            }
        }
        return flags;
    }

    private void checkForbiddenArgs(ArgumentsTy args) {
        if (args != null) {
            if (args.posOnlyArgs != null) {
                for (ArgTy arg : args.posOnlyArgs) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (args.args != null) {
                for (ArgTy arg : args.args) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (args.kwOnlyArgs != null) {
                for (ArgTy arg : args.kwOnlyArgs) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (args.varArg != null) {
                checkForbiddenName(args.varArg.arg, ExprContext.Store);
            }
            if (args.kwArg != null) {
                checkForbiddenName(args.kwArg.arg, ExprContext.Store);
            }
        }
    }

    @Override
    public Void visit(StmtTy.Global node) {
        setLocation(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.If node) {
        setLocation(node);
        Block then = new Block();
        Block end = new Block();
        Block alt = node.orElse != null && node.orElse.length > 0 ? new Block() : end;
        jumpIf(node.test, alt, false);
        unit.useNextBlock(then);
        visitSequence(node.body);
        if (alt != end) {
            addOp(JUMP_FORWARD, end);
            unit.useNextBlock(alt);
            visitSequence(node.orElse);
        }
        unit.useNextBlock(end);
        return null;
    }

    private void jumpIf(ExprTy test, Block next, boolean jumpIfTrue) {
        // TODO Optimize for various test types, such as short-circuit operators
        // See compiler_jump_if in CPython
        test.accept(this);
        if (jumpIfTrue) {
            addOp(POP_AND_JUMP_IF_TRUE, next);
        } else {
            addOp(POP_AND_JUMP_IF_FALSE, next);
        }
    }

    @Override
    public Void visit(StmtTy.Import node) {
        setLocation(node);
        return visitSequence(node.names);
    }

    @Override
    public Void visit(StmtTy.ImportFrom node) {
        setLocation(node);
        addLoadLong(node.level);
        String[] names = new String[node.names.length];
        for (int i = 0; i < node.names.length; i++) {
            names[i] = node.names[i].name;
        }
        addOp(LOAD_CONST, addObject(unit.constants, names));
        addOpName(IMPORT_NAME, unit.names, node.module != null ? node.module : "");
        for (AliasTy alias : node.names) {
            if ("*".equals(alias.name)) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
            addOpName(IMPORT_FROM, unit.names, alias.name);
            String storeName = alias.asName != null ? alias.asName : alias.name;
            addNameOp(storeName, ExprContext.Store);
        }
        addOp(POP_TOP);
        return null;
    }

    @Override
    public Void visit(StmtTy.Match node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Case node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchAs node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchClass node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchMapping node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchOr node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchSequence node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchSingleton node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchStar node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match.Pattern.MatchValue node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.NonLocal node) {
        setLocation(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.Raise node) {
        setLocation(node);
        int argc = 0;
        if (node.exc != null) {
            argc++;
            node.exc.accept(this);
            if (node.cause != null) {
                argc++;
                node.cause.accept(this);
            }
        }
        addOp(RAISE_VARARGS, argc);
        unit.useNextBlock(new Block());
        return null;
    }

    @Override
    public Void visit(StmtTy.Return node) {
        setLocation(node);
        node.value.accept(this);
        return addOp(RETURN_VALUE);
    }

    private void jumpToFinally(Block finalBlock, Block end) {
        if (finalBlock != null) {
            addOp(JUMP_FORWARD, finalBlock);
        } else {
            addOp(JUMP_FORWARD, end);
        }
    }

    @Override
    public Void visit(StmtTy.Try node) {
        setLocation(node);
        Block tryBody = new Block();
        Block end = new Block();
        boolean hasFinally = node.finalBody != null;
        boolean hasHandlers = node.handlers != null && node.handlers.length > 0;
        Block finallyBlockNormal = null;
        Block finallyBlockExcept = null;
        Block finallyBlockExceptWithSavedExc = null;
        if (hasFinally) {
            finallyBlockNormal = new Block();
            finallyBlockExcept = Block.createFinallyHandler(tryBody);
            finallyBlockExceptWithSavedExc = new Block();
        }
        Block elseBlock = node.orElse != null ? new Block() : null;
        assert hasFinally || hasHandlers;

        // try block
        unit.useNextBlock(tryBody);
        visitSequence(node.body);
        if (elseBlock != null) {
            addOp(JUMP_FORWARD, elseBlock);
        } else {
            jumpToFinally(finallyBlockNormal, end);
        }

        // except clauses
        if (hasHandlers) {
            unit.useNextBlock(Block.createExceptionHandler(tryBody));
            /* This puts saved exception under the current exception */
            addOp(PUSH_EXC_INFO);
            /* The stack is now [*, savedException, currentException] */
            boolean hasBareExcept = false;
            Block nextHandler = new Block();
            /*
             * We need to save and restore the outer exception state. In order to restore it even in
             * case of an exception, we need an internal finally handler for this.
             */
            Block cleanupHandler = Block.createFinallyHandler(nextHandler);
            /*
             * We use this offset to unwind the exception from the except block, we don't need it on
             * the stack
             */
            cleanupHandler.unwindOffset = -1;
            for (int i = 0; i < node.handlers.length; i++) {
                assert !hasBareExcept;
                setLocation(node.handlers[i]);
                unit.useNextBlock(nextHandler);

                if (i < node.handlers.length - 1) {
                    nextHandler = new Block();
                } else {
                    if (hasFinally) {
                        // Keep the save state, it's the same
                        nextHandler = finallyBlockExceptWithSavedExc;
                    } else {
                        nextHandler = cleanupHandler;
                    }
                }

                if (node.handlers[i].type != null) {
                    node.handlers[i].type.accept(this);
                    addOp(MATCH_EXC_OR_JUMP, nextHandler);
                    if (node.handlers[i].name != null) {
                        addOp(UNWRAP_EXC);
                        addNameOp(node.handlers[i].name, ExprContext.Store);
                    } else {
                        addOp(POP_TOP);
                    }
                } else {
                    hasBareExcept = true;
                    addOp(POP_TOP);
                }
                visitSequence(node.handlers[i].body);
                addOp(POP_EXCEPT);
                jumpToFinally(finallyBlockNormal, end);
            }
            unit.useNextBlock(cleanupHandler);
            if (hasFinally) {
                addOp(ROT_TWO);
                /*
                 * POP the saved exception state, the finally block needs to push a new one. The
                 * saved state will be the same, but PUSH_EXC_INFO also updates the current
                 * exception info which is now different.
                 */
                addOp(POP_EXCEPT);
                addOp(JUMP_FORWARD, finallyBlockExcept);
            } else {
                /*
                 * We can also reach this code by falling off the except handlers if no types
                 * matched and there is no finally
                 */
                addOp(END_EXC_HANDLER);
            }
        }

        if (elseBlock != null) {
            unit.useNextBlock(elseBlock);
            visitSequence(node.orElse);
            jumpToFinally(finallyBlockNormal, end);
        }

        /*
         * We emit two copies of the finally block, one that is executed when there is no exception
         * and one that is executed when an exception occurs. The latter needs to deal with the
         * exception state. We start with the latter.
         */
        if (hasFinally) {
            unit.useNextBlock(finallyBlockExcept);
            /* This puts saved exception under the current exception */
            addOp(PUSH_EXC_INFO);
            Block cleanupHandler = Block.createFinallyHandler(finallyBlockExceptWithSavedExc);
            cleanupHandler.unwindOffset = -1;
            /* The stack is [*, savedException, currentException] */
            unit.useNextBlock(finallyBlockExceptWithSavedExc);
            visitSequence(node.finalBody);
            unit.useNextBlock(cleanupHandler);
            addOp(END_EXC_HANDLER);

            /* Now emit the finally for the no-exception case */
            unit.useNextBlock(finallyBlockNormal);
            visitSequence(node.finalBody);
        }

        unit.useNextBlock(end);

        return null;
    }

    @Override
    public Void visit(StmtTy.Try.ExceptHandler node) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public Void visit(StmtTy.While node) {
        setLocation(node);
        Block test = new Block();
        Block body = new Block();
        Block end = new Block();
        Block orelse = node.orElse != null ? new Block() : end;
        unit.useNextBlock(test);
        jumpIf(node.test, orelse, false);
        unit.useNextBlock(body);
        unit.blockInfoStack.push(new BlockInfo(test, end, BlockInfo.Type.WHILE_LOOP));
        try {
            visitSequence(node.body);
            addOp(JUMP_BACKWARD, test);
        } finally {
            unit.blockInfoStack.pop();
        }
        if (node.orElse != null) {
            unit.useNextBlock(orelse);
            visitSequence(node.orElse);
        }
        unit.useNextBlock(end);
        return null;
    }

    @Override
    public Void visit(StmtTy.With node) {
        setLocation(node);
        visitWith(node, 0);
        unit.useNextBlock(new Block());
        return null;
    }

    private void visitWith(StmtTy.With node, int itemIndex) {
        Block body = new Block();
        Block handler = Block.createFinallyHandler(body);

        StmtTy.With.Item item = node.items[itemIndex];
        item.contextExpr.accept(this);
        addOp(SETUP_WITH);

        unit.useNextBlock(body);
        /*
         * Unwind one more stack item than it normally would to get rid of the context manager that
         * is not needed in the finally block
         */
        handler.unwindOffset = -1;
        if (item.optionalVars != null) {
            item.optionalVars.accept(this);
        } else {
            addOp(POP_TOP);
        }
        if (itemIndex < node.items.length - 1) {
            visitWith(node, itemIndex + 1);
        } else {
            visitSequence(node.body);
        }
        addOp(LOAD_NONE);

        unit.useNextBlock(handler);
        setLocation(node);
        addOp(EXIT_WITH);
    }

    @Override
    public Void visit(StmtTy.With.Item node) {
        throw new UnsupportedOperationException("should not reach here");
    }

    @Override
    public Void visit(StmtTy.Break node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Continue node) {
        setLocation(node);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Pass aThis) {
        return null;
    }
}
