/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_COL_OFFSET;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_END_COL_OFFSET;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_END_LINENO;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_LINENO;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Utility methods that simplify the generated code in {@link Sst2ObjVisitor}.
 */
abstract class Sst2ObjVisitorBase implements SSTreeVisitor<Object> {

    final PythonObjectFactory factory;

    Sst2ObjVisitorBase() {
        factory = PythonObjectFactory.getUncached();
    }

    static int visitNullable(int i) {
        return i;
    }

    static Object visitNullable(String str) {
        return str == null ? PNone.NONE : toTruffleStringUncached(str);
    }

    Object visitNullableStringOrByteArray(Object o) {
        return o == null ? PNone.NONE : visitNonNullStringOrByteArray(o);
    }

    final Object visitNullable(CmpOpTy op) {
        return op == null ? PNone.NONE : visitNonNull(op);
    }

    final Object visitNullable(SSTNode node) {
        return node == null ? PNone.NONE : node.accept(this);
    }

    static int visitNonNull(boolean i) {
        return i ? 1 : 0;
    }

    static int visitNonNull(int i) {
        return i;
    }

    static TruffleString visitNonNull(String str) {
        return toTruffleStringUncached(str);
    }

    Object visitNonNullStringOrByteArray(Object o) {
        if (o instanceof String) {
            return toTruffleStringUncached((String) o);
        }
        assert o instanceof byte[];
        return factory.createBytes((byte[]) o);
    }

    final Object visitNonNull(ConstantValue v) {
        return PythonUtils.pythonObjectFromConstantValue(v, factory);
    }

    abstract Object visitNonNull(CmpOpTy op);

    final Object visitNonNull(SSTNode node) {
        return node.accept(this);
    }

    final PList seq2List(String[] seq) {
        if (seq == null || seq.length == 0) {
            return factory.createList();
        }
        Object[] objs = new Object[seq.length];
        for (int i = 0; i < objs.length; ++i) {
            objs[i] = visitNullable(seq[i]);
        }
        return factory.createList(objs);
    }

    final PList seq2List(CmpOpTy[] seq) {
        if (seq == null || seq.length == 0) {
            return factory.createList();
        }
        Object[] objs = new Object[seq.length];
        for (int i = 0; i < objs.length; ++i) {
            objs[i] = visitNullable(seq[i]);
        }
        return factory.createList(objs);
    }

    final PList seq2List(SSTNode[] seq) {
        if (seq == null || seq.length == 0) {
            return factory.createList();
        }
        Object[] objs = new Object[seq.length];
        for (int i = 0; i < objs.length; ++i) {
            objs[i] = visitNullable(seq[i]);
        }
        return factory.createList(objs);
    }

    final void fillSourceRangeAttributes(PythonObject o, SourceRange sourceRange) {
        o.setAttribute(T_F_LINENO, sourceRange.startLine);
        o.setAttribute(T_F_COL_OFFSET, sourceRange.startColumn);
        o.setAttribute(T_F_END_LINENO, sourceRange.endLine);
        o.setAttribute(T_F_END_COL_OFFSET, sourceRange.endColumn);
    }
}
