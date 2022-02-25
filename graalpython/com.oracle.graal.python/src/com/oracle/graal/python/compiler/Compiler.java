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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Stack;

import com.oracle.graal.python.compiler.OpCodes.CollectionBits;
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

public class Compiler implements SSTreeVisitor<Void> {
    String filename;
    ScopeEnvironment env;
    EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
    int optimizationLevel = 0;
    boolean isInteractive = false;
    int nestingLevel = 0;
    CompilationUnit unit;
    Stack<CompilationUnit> stack = new Stack<>();
    ByteBuffer buffer;

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
            setOffset(mod.getEndOffset());
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

    private void enterScope(String name, CompilationScope scopeType, SSTNode node, int argc, int pargc, int kwargc,
                    boolean hasSplat, boolean hasKwSplat) {
        if (unit != null) {
            stack.add(unit);
        }
        unit = new CompilationUnit(scopeType, env.lookupScope(node), name, unit, argc, pargc, kwargc,
                        hasSplat, hasKwSplat, node.getStartOffset(), node.getEndOffset());
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
        addOp(code, unit.startOffset, target);
    }

    private Void addOp(OpCodes code, int argOrSrcOffset) {
        if (code.hasArg()) {
            addOp(code, argOrSrcOffset, unit.startOffset);
        } else {
            addOp(code, 0, unit.startOffset);
        }
        return null;
    }

    private void addOp(OpCodes code, int srcOffset, Block target) {
        Block b = unit.currentBlock;
        b.instr.add(new Instruction(code, 0, target, srcOffset));
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

    private void setOffset(SSTNode node) {
        setOffset(node.getStartOffset());
    }

    private void setOffset(int offset) {
        unit.startOffset = offset;
    }

    private void collectIntoArray(SSTNode[] nodes, int bits, int alreadyOnStack) {
        boolean collectionOnStack = false;
        int cnt = alreadyOnStack;
        for (SSTNode e : nodes) {
            assert e instanceof ExprTy || e instanceof KeywordTy || e instanceof ExprTy.Starred;
            if (e instanceof ExprTy.Starred || (e instanceof KeywordTy && ((KeywordTy) e).arg == null)) {
                // splat
                if (!collectionOnStack && cnt > 0) {
                    addOp(COLLECTION_FROM_STACK, bits | cnt);
                    e.accept(this);
                    setOffset(e.getEndOffset());
                    addOp(COLLECTION_ADD_COLLECTION, bits);
                } else {
                    e.accept(this);
                    setOffset(e.getEndOffset());
                    addOp(COLLECTION_FROM_COLLECTION, bits);
                }
                collectionOnStack = true;
                cnt = 0;
            } else {
                e.accept(this);
                setOffset(e.getEndOffset());
            }
            if (cnt > CollectionBits.MAX_STACK_ELEMENT_COUNT) {
                if (!collectionOnStack) {
                    addOp(COLLECTION_FROM_STACK, bits | cnt);
                } else {
                    addOp(COLLECTION_ADD_STACK, bits | cnt);
                }
                collectionOnStack = true;
                cnt = 0;
            } else {
                cnt++;
            }
        }
        if (!collectionOnStack) {
            addOp(COLLECTION_FROM_STACK, bits | cnt);
        } else {
            addOp(COLLECTION_ADD_STACK, bits | cnt);
        }
    }

    private void collectIntoArray(SSTNode[] nodes, int bits) {
        collectIntoArray(nodes, bits, 0);
    }

    private void makeClosure(CodeUnit code, int hasDefaults, int hasKwDefaults) {
        if (code.freevars.length > 0) {
            // add the closure
            for (String fv : code.freevars) {
                // special case for class scopes
                int arg;
                if (unit.scopeType == CompilationScope.Class && "__class__".equals(fv)) {
                    arg = unit.cellvars.get(fv);
                } else {
                    arg = unit.freevars.get(fv);
                }
                addOp(LOAD_CLOSURE, arg);
            }
            addOp(CLOSURE_FROM_STACK, code.freevars.length);
        }
        addOp(LOAD_CONST, addObject(unit.constants, code));
        addOp(MAKE_FUNCTION, hasDefaults + hasKwDefaults + (code.freevars.length > 0 ? 2 : 1));
    }

    // visiting

    private void visitBody(StmtTy[] stmts) {
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
        addOp(LOAD_NONE);
        addOpName(IMPORT_NAME, unit.names, node.name);
        if (node.asName != null) {
            int dotIdx = node.asName.indexOf('.');
            if (dotIdx >= 0) {
                while (true) {
                    int pos = dotIdx + 1;
                    dotIdx = node.asName.indexOf('.', pos);
                    int end = dotIdx >= 0 ? dotIdx : node.asName.length();
                    String attr = node.asName.substring(pos, end);
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
    public Void visit(ComprehensionTy aThis) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Attribute node) {
        setOffset(node);
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
    }

    @Override
    public Void visit(ExprTy.Await node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.BinOp node) {
        node.left.accept(this);
        node.right.accept(this);
        setOffset(node.left.getEndOffset());
        switch (node.op) {
            case ADD:
                return addOp(BINARY_ADD);
            case SUB:
                return addOp(BINARY_SUBTRACT);
            case MULT:
                return addOp(BINARY_MULTIPLY);
            case MATMULT:
                return addOp(BINARY_MATRIX_MULTIPLY);
            case DIV:
                return addOp(BINARY_TRUE_DIVIDE);
            case MOD:
                return addOp(BINARY_MODULO);
            case POW:
                return addOp(BINARY_POWER);
            case LSHIFT:
                return addOp(BINARY_LSHIFT);
            case RSHIFT:
                return addOp(BINARY_RSHIFT);
            case BITOR:
                return addOp(BINARY_OR);
            case BITXOR:
                return addOp(BINARY_XOR);
            case BITAND:
                return addOp(BINARY_AND);
            case FLOORDIV:
                return addOp(BINARY_FLOOR_DIVIDE);
            default:
                throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    @Override
    public Void visit(ExprTy.BoolOp node) {
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
        setOffset(func.getEndOffset());
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
            setOffset(node.getEndOffset());
            return addOp(op, oparg);
        } else {
            collectIntoArray(node.args, CollectionBits.OBJECT, op == CALL_METHOD_VARARGS ? 1 : 0);
            if (node.keywords.length > 0) {
                assert op == CALL_FUNCTION_VARARGS;
                collectIntoArray(node.keywords, CollectionBits.KWORDS);
                setOffset(node.getEndOffset());
                return addOp(CALL_FUNCTION_KW);
            } else {
                setOffset(node.getEndOffset());
                return addOp(op, oparg);
            }
        }
    }

    private void addCompareOp(ExprTy.Compare.Operator op) {
        switch (op) {
            case EQ:
            case NOTEQ:
            case LT:
            case LTE:
            case GT:
            case GTE:
                addOp(COMPARE_OP, op.ordinal());
                break;
            case IS:
                addOp(IS_OP, 0);
                break;
            case ISNOT:
                addOp(IS_OP, 1);
                break;
            case IN:
                addOp(CONTAINS_OP, 0);
                break;
            case NOTIN:
                addOp(CONTAINS_OP, 1);
                break;
        }
    }

    @Override
    public Void visit(ExprTy.Compare node) {
        setOffset(node);
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
    }

    @Override
    public Void visit(ExprTy.Constant node) {
        setOffset(node);
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
                if (node.longValue == (byte) node.longValue) {
                    return addOp(LOAD_BYTE, (byte) node.longValue);
                } else {
                    return addOp(LOAD_LONG, addObject(unit.primitiveConstants, node.longValue));
                }
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
    }

    @Override
    public Void visit(ExprTy.Dict node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.FormattedValue node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.IfExp node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.JoinedStr node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        throw new UnsupportedOperationException("Not supported yet.");
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
        setOffset(node);
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
    }

    @Override
    public Void visit(ExprTy.ListComp node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Name node) {
        setOffset(node);
        addNameOp(node.id, node.context);
        return null;
    }

    @Override
    public Void visit(ExprTy.NamedExpr node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Set node) {
        setOffset(node);
        collectIntoArray(node.elements, CollectionBits.SET);
        return null;
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Slice node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Starred node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.Subscript node) {
        node.value.accept(this);
        node.slice.accept(this);
        setOffset(node);
        switch (node.context) {
            case Load:
                return addOp(BINARY_SUBSCR);
            case Store:
                return addOp(STORE_SUBSCR);
            case Delete:
            default:
                return addOp(DELETE_SUBSCR);
        }
    }

    @Override
    public Void visit(ExprTy.Tuple node) {
        setOffset(node);
        switch (node.context) {
            case Store:
                return unpackInto(node.elements);
            case Load:
                collectIntoArray(node.elements, CollectionBits.TUPLE);
                return null;
            case Delete:
            default:
                return visitSequence(node.elements);
        }
    }

    @Override
    public Void visit(ExprTy.UnaryOp node) {
        node.operand.accept(this);
        setOffset(node);
        switch (node.op) {
            case ADD:
                return addOp(UNARY_POSITIVE);
            case INVERT:
                return addOp(UNARY_INVERT);
            case NOT:
                return addOp(UNARY_NOT);
            case SUB:
            default:
                return addOp(UNARY_NEGATIVE);
        }
    }

    @Override
    public Void visit(ExprTy.Yield node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(ExprTy.YieldFrom node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(KeywordTy node) {
        node.value.accept(this);
        setOffset(node);
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
        setOffset(node);
        visitBody(node.body);
        return null;
    }

    @Override
    public Void visit(ModTy.TypeIgnore node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Assert node) {
        Block end = new Block();
        node.test.accept(this);
        setOffset(node);
        addOp(POP_AND_JUMP_IF_TRUE, end);
        addOp(LOAD_ASSERTION_ERROR);
        if (node.msg != null) {
            node.msg.accept(this);
            addOp(CALL_FUNCTION, 1);
        }
        setOffset(node.getEndOffset());
        addOp(RAISE_VARARGS, 1);
        unit.useNextBlock(end);
        return null;
    }

    @Override
    public Void visit(StmtTy.Assign node) {
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
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AsyncWith node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.AugAssign node) {
        node.target.copyWithContext(ExprContext.Load).accept(this);
        node.value.accept(this);
        setOffset(node.target.getEndOffset());
        switch (node.op) {
            case ADD:
                addOp(INPLACE_ADD);
                break;
            case SUB:
                addOp(INPLACE_SUBTRACT);
                break;
            case MULT:
                addOp(INPLACE_MULTIPLY);
                break;
            case MATMULT:
                addOp(INPLACE_MATRIX_MULTIPLY);
                break;
            case DIV:
                addOp(INPLACE_TRUE_DIVIDE);
                break;
            case MOD:
                addOp(INPLACE_MODULO);
                break;
            case POW:
                addOp(INPLACE_POWER);
                break;
            case LSHIFT:
                addOp(INPLACE_LSHIFT);
                break;
            case RSHIFT:
                addOp(INPLACE_RSHIFT);
                break;
            case BITOR:
                addOp(INPLACE_OR);
                break;
            case BITXOR:
                addOp(INPLACE_XOR);
                break;
            case BITAND:
                addOp(INPLACE_AND);
                break;
            case FLOORDIV:
                addOp(INPLACE_FLOOR_DIVIDE);
                break;
            default:
                throw new UnsupportedOperationException("Not supported yet.");
        }
        return node.target.accept(this);
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        visitSequence(node.decoratorList);

        enterScope(node.name, CompilationScope.Class, node, 0, 0, 0, false, false);
        setOffset(node.body[0]);
        addNameOp("__name__", ExprContext.Load);
        addNameOp("__module__", ExprContext.Store);
        addOp(LOAD_CONST, addObject(unit.constants, unit.qualName));
        addNameOp("__qualname__", ExprContext.Store);

        visitBody(node.body);

        setOffset(node.getEndOffset());
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

        setOffset(node);
        addOp(LOAD_BUILD_CLASS);
        makeClosure(co, 0, 0);
        addOp(LOAD_CONST, addObject(unit.constants, node.name));

        if ((node.bases.length < 3) && node.keywords.length == 0) {
            visitSequence(node.bases);
            addOp(CALL_FUNCTION, 2 + node.bases.length);
        } else if (node.keywords.length == 0) {
            collectIntoArray(node.bases, CollectionBits.OBJECT, 2);
            addOp(CALL_FUNCTION_VARARGS);
        } else {
            collectIntoArray(node.bases, CollectionBits.OBJECT, 2);
            collectIntoArray(node.keywords, CollectionBits.KWORDS);
            addOp(CALL_FUNCTION_KW);
        }

        if (node.decoratorList != null) {
            for (ExprTy decorator : node.decoratorList) {
                setOffset(decorator.getEndOffset());
                addOp(CALL_FUNCTION, 1);
            }
        }

        setOffset(node);
        addNameOp(node.name, ExprContext.Store);

        return null;
    }

    @Override
    public Void visit(StmtTy.Delete node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Expr node) {
        if (!(node.value instanceof ExprTy.Constant)) {
            node.value.accept(this);
            setOffset(node.value.getEndOffset());
            addOp(POP_TOP);
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.For node) {
        Block head = new Block();
        Block body = new Block();
        Block end = new Block();
        Block orelse = node.orElse != null ? new Block() : end;

        node.iter.accept(this);
        addOp(GET_ITER);

        unit.useNextBlock(head);
        setOffset(node);
        addOp(FOR_ITER, orelse);

        unit.useNextBlock(body);
        unit.blockInfoStack.push(new BlockInfo(head, end, BlockInfo.Type.FOR_LOOP));
        try {
            node.target.accept(this);
            visitSequence(node.body);
            setOffset(node.body[node.body.length - 1].getEndOffset());
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
        if (node.args != null) {
            if (node.args.posOnlyArgs != null) {
                for (ArgTy arg : node.args.posOnlyArgs) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (node.args.args != null) {
                for (ArgTy arg : node.args.args) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (node.args.kwOnlyArgs != null) {
                for (ArgTy arg : node.args.kwOnlyArgs) {
                    checkForbiddenName(arg.arg, ExprContext.Store);
                }
            }
            if (node.args.varArg != null) {
                checkForbiddenName(node.args.varArg.arg, ExprContext.Store);
            }
            if (node.args.kwArg != null) {
                checkForbiddenName(node.args.kwArg.arg, ExprContext.Store);
            }
        }

        // visit decorators
        visitSequence(node.decoratorList);

        // visit defaults outside the function scope
        int hasDefaults = 0;
        int hasKwDefaults = 0;
        if (node.args != null) {
            if (node.args.defaults != null && node.args.defaults.length > 0) {
                collectIntoArray(node.args.defaults, CollectionBits.OBJECT);
                hasDefaults = CodeUnit.HAS_DEFAULTS;
            }
            if (node.args.kwDefaults != null && node.args.kwDefaults.length > 0) {
                ArrayList<KeywordTy> defs = new ArrayList<>();
                for (int i = 0; i < node.args.kwOnlyArgs.length; i++) {
                    ArgTy arg = node.args.kwOnlyArgs[i];
                    ExprTy def = node.args.kwDefaults[i];
                    if (def != null) {
                        String mangled = ScopeEnvironment.mangle(unit.privateName, arg.arg);
                        defs.add(new KeywordTy(mangled, def, arg.getStartOffset(), def.getEndOffset()));
                    }
                }
                collectIntoArray(defs.toArray(KeywordTy[]::new), CollectionBits.KWORDS);
                hasKwDefaults = CodeUnit.HAS_KWONLY_DEFAULTS;
            }
        }

        // TODO: visit annotations

        int argc, pargc, kwargc;
        boolean splat, kwSplat;
        if (node.args == null) {
            argc = pargc = kwargc = 0;
            splat = kwSplat = false;
        } else {
            argc = node.args.args == null ? 0 : node.args.args.length;
            pargc = node.args.posOnlyArgs == null ? 0 : node.args.posOnlyArgs.length;
            kwargc = node.args.kwOnlyArgs == null ? 0 : node.args.kwOnlyArgs.length;
            splat = node.args.varArg != null;
            kwSplat = node.args.kwArg != null;
        }

        enterScope(node.name, CompilationScope.Function, node, argc, pargc, kwargc, splat, kwSplat);

        CodeUnit code;
        try {
            String docString = getDocstring(node.body);
            addObject(unit.constants, docString);
            visitSequence(node.body);
            code = unit.assemble(filename, hasDefaults | hasKwDefaults);
        } finally {
            exitScope();
        }

        setOffset(node);
        makeClosure(code, hasDefaults > 0 ? 1 : 0, hasKwDefaults > 0 ? 1 : 0);

        if (node.decoratorList != null) {
            for (ExprTy decorator : node.decoratorList) {
                setOffset(decorator.getEndOffset());
                addOp(CALL_FUNCTION, 1);
            }
        }

        addNameOp(node.name, ExprContext.Store);
        return null;
    }

    @Override
    public Void visit(StmtTy.Global node) {
        return null;
    }

    @Override
    public Void visit(StmtTy.If node) {
        node.test.accept(this);
        Block then = new Block();
        Block end = new Block();
        Block alt = node.orElse != null && node.orElse.length > 0 ? new Block() : end;

        setOffset(node.test.getEndOffset());
        addOp(POP_AND_JUMP_IF_FALSE, alt);
        unit.useNextBlock(then);
        visitSequence(node.body);
        if (alt != end) {
            setOffset(node.body[node.body.length - 1].getEndOffset());
            addOp(JUMP_FORWARD, end);
            unit.useNextBlock(alt);
            visitSequence(node.orElse);
        }
        unit.useNextBlock(end);
        return null;
    }

    @Override
    public Void visit(StmtTy.Import node) {
        return visitSequence(node.names);
    }

    @Override
    public Void visit(StmtTy.ImportFrom node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Match node) {
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
        return null;
    }

    @Override
    public Void visit(StmtTy.Raise node) {
        int argc = 0;
        if (node.exc != null) {
            argc++;
            node.exc.accept(this);
            if (node.cause != null) {
                argc++;
                node.cause.accept(this);
            }
        }
        setOffset(node);
        addOp(RAISE_VARARGS, argc);
        unit.useNextBlock(new Block());
        return null;
    }

    @Override
    public Void visit(StmtTy.Return node) {
        node.value.accept(this);
        setOffset(node);
        return addOp(RETURN_VALUE);
    }

    private void jumpToFinally(Block finalBlock, Block end) {
        if (finalBlock != null) {
            addOp(LOAD_NONE);
            addOp(JUMP_FORWARD, finalBlock);
        } else {
            addOp(JUMP_FORWARD, end);
        }
    }

    @Override
    public Void visit(StmtTy.Try node) {
        Block tryBody = new Block();
        Block end = new Block();
        Block finalBlock = node.finalBody != null ? Block.createFinallyHandler(tryBody) : null;
        Block elseBlock = node.orElse != null ? new Block() : null;
        boolean hasHandlers = node.handlers != null && node.handlers.length > 0;
        assert finalBlock != null || hasHandlers;

        // try block
        unit.useNextBlock(tryBody);
        visitSequence(node.body);
        if (elseBlock != null) {
            addOp(JUMP_FORWARD, elseBlock);
        } else {
            setOffset(node.body[node.body.length - 1].getEndOffset());
            jumpToFinally(finalBlock, end);
        }

        // except clauses
        if (hasHandlers) {
            boolean hasBareExcept = false;
            Block nextHandler = Block.createExceptionHandler(tryBody);
            for (int i = 0; i < node.handlers.length; i++) {
                assert !hasBareExcept;
                unit.useNextBlock(nextHandler);

                if (i < node.handlers.length - 1) {
                    nextHandler = new Block();
                } else {
                    if (finalBlock != null) {
                        nextHandler = finalBlock;
                    } else {
                        nextHandler = new Block();
                    }
                }

                if (node.handlers[i].type != null) {
                    node.handlers[i].type.accept(this);
                    addOp(MATCH_EXC_OR_JUMP, nextHandler);
                    if (node.handlers[i].name != null) {
                        addOp(UNWRAP_EXC);
                        setOffset(node.handlers[i].type.getEndOffset() + 1);
                        addNameOp(node.handlers[i].name, ExprContext.Store);
                    } else {
                        addOp(POP_TOP);
                    }
                } else {
                    hasBareExcept = true;
                    addOp(POP_TOP);
                }
                visitSequence(node.handlers[i].body);

                setOffset(node.handlers[i].getEndOffset());
                jumpToFinally(finalBlock, end);
            }
            if (nextHandler != finalBlock && !hasBareExcept) {
                // there's no finally block, so when we fall off the except clauses, we re-raise
                unit.useNextBlock(nextHandler);
                addOp(END_FINALLY);
            }
        }

        if (elseBlock != null) {
            unit.useNextBlock(elseBlock);
            visitSequence(node.orElse);
            setOffset(node.orElse[node.orElse.length - 1].getEndOffset());
            jumpToFinally(finalBlock, end);
        }

        if (finalBlock != null) {
            unit.useNextBlock(finalBlock);
            visitSequence(node.finalBody);
            setOffset(node.finalBody[node.finalBody.length - 1].getEndOffset());
            addOp(END_FINALLY);
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
        Block test = new Block();
        Block body = new Block();
        Block end = new Block();
        Block orelse = node.orElse != null ? new Block() : end;
        unit.useNextBlock(test);
        node.test.accept(this);
        addOp(POP_AND_JUMP_IF_FALSE, orelse);
        unit.useNextBlock(body);
        unit.blockInfoStack.push(new BlockInfo(test, end, BlockInfo.Type.WHILE_LOOP));
        try {
            visitSequence(node.body);
            setOffset(node.body[node.body.length - 1].getEndOffset());
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
        addOp(EXIT_WITH);
    }

    @Override
    public Void visit(StmtTy.With.Item node) {
        throw new UnsupportedOperationException("should not reach here");
    }

    @Override
    public Void visit(StmtTy.Break aThis) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Continue aThis) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Void visit(StmtTy.Pass aThis) {
        return null;
    }
}
