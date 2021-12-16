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
package com.oracle.graal.python.pegparser;

// TODO this class has to be moved to impl package and from this package we need to do api.

import com.oracle.graal.python.pegparser.sst.AnnAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AnnotationSSTNode;
import com.oracle.graal.python.pegparser.sst.ArgDefListBuilder;
import com.oracle.graal.python.pegparser.sst.AssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AugAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.BinaryArithmeticSSTNode;
import com.oracle.graal.python.pegparser.sst.BlockSSTNode;
import com.oracle.graal.python.pegparser.sst.BooleanLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.CallSSTNode;
import com.oracle.graal.python.pegparser.sst.CollectionSSTNode;
import com.oracle.graal.python.pegparser.sst.ComprehensionSSTNode;
import com.oracle.graal.python.pegparser.sst.ForComprehensionSSTNode;
import com.oracle.graal.python.pegparser.sst.FunctionDefSSTNode;
import com.oracle.graal.python.pegparser.sst.GetAttributeSSTNode;
import com.oracle.graal.python.pegparser.sst.KeyValueSSTNode;
import com.oracle.graal.python.pegparser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SimpleSSTNode;
import com.oracle.graal.python.pegparser.sst.StarSSTNode;
import com.oracle.graal.python.pegparser.sst.StringLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SubscriptSSTNode;
import com.oracle.graal.python.pegparser.sst.UnarySSTNode;
import com.oracle.graal.python.pegparser.sst.UntypedSSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;
import com.oracle.graal.python.pegparser.sst.YieldExpressionSSTNode;


public class NodeFactoryImp implements NodeFactory{

    @Override
    public AnnAssignmentSSTNode createAnnAssignment(AnnotationSSTNode annotation, SSTNode rhs, int startOffset, int endOffset) {
        return new AnnAssignmentSSTNode(annotation, rhs, startOffset, endOffset);
    }

    @Override
    public AnnotationSSTNode createAnnotation(SSTNode lhs, SSTNode type, int startOffset, int endOffset) {
        return new AnnotationSSTNode(lhs, type, startOffset, endOffset);
    }

    @Override
    public AssignmentSSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, SSTNode typeComment, int startOffset, int endOffset) {
        return new AssignmentSSTNode(lhs, rhs, typeComment, startOffset, endOffset);
    }

    @Override
    public AugAssignmentSSTNode createAugAssignment(SSTNode lhs, BinaryArithmeticSSTNode.Type operation, SSTNode rhs, int startOffset, int endOffset) {
        return new AugAssignmentSSTNode(lhs, operation, rhs, startOffset, endOffset);
    }

    @Override
    public BinaryArithmeticSSTNode createBinaryOp(BinaryArithmeticSSTNode.Type op, SSTNode left, SSTNode right, int startOffset, int endOffset) {
        return new BinaryArithmeticSSTNode(op, left, right, startOffset, endOffset);
    }

    @Override
    public BlockSSTNode createBlock(SSTNode[] statements, int startOffset, int endOffset) {
        return new BlockSSTNode(statements, startOffset, endOffset);
    }

    @Override
    public BooleanLiteralSSTNode createBooleanLiteral(boolean value, int startOffset, int endOffset) {
        return new BooleanLiteralSSTNode(value, startOffset, endOffset);
    }

    @Override
    public SSTNode createNone(int startOffset, int endOffset) {
        return new SimpleSSTNode(SimpleSSTNode.Type.NONE, startOffset, endOffset);
    }

    @Override
    public SSTNode createEllipsis(int startOffset, int endOffset) {
        return new SimpleSSTNode(SimpleSSTNode.Type.ELLIPSIS, startOffset, endOffset);
    }

    @Override
    public GetAttributeSSTNode createGetAttribute(SSTNode receiver, String name, int startOffset, int endOffset) {
        return new GetAttributeSSTNode(receiver, name, startOffset, endOffset);
    }

    @Override
    public SSTNode createPass(int startOffset, int endOffset) {
        return new SimpleSSTNode(SimpleSSTNode.Type.PASS, startOffset, endOffset);
    }

    @Override
    public SSTNode createBreak(int startOffset, int endOffset) {
        return new SimpleSSTNode(SimpleSSTNode.Type.BREAK, startOffset, endOffset);
    }
    
    @Override
    public CallSSTNode createCall(SSTNode target, SSTNode[] args, SSTNode[] kwargs, int startOffset, int endOffset) {
        return new CallSSTNode(target, args, kwargs, startOffset, endOffset);
    }

    @Override
    public SSTNode createContinue(int startOffset, int endOffset) {
        return new SimpleSSTNode(SimpleSSTNode.Type.CONTINUE, startOffset, endOffset);
    }

    @Override
    public SSTNode createYield(SSTNode value, boolean isFrom, int startOffset, int endOffset) {
        return new YieldExpressionSSTNode(value, isFrom, startOffset, endOffset);
    }

    @Override
    public NumberLiteralSSTNode createNumber(String number, int startOffset, int endOffset) {
        // TODO handle all kind of numbers here.
        return NumberLiteralSSTNode.create(number, 0, 10, startOffset, endOffset);
    }

    @Override
    public StringLiteralSSTNode createString(String[] values, int startOffset, int endOffset, FExprParser exprParser, ParserErrorCallback errorCb) {
        return StringLiteralSSTNode.create(values, startOffset, endOffset, this, exprParser, errorCb);
    }

    @Override
    public UnarySSTNode createUnaryOp(UnarySSTNode.Type op, SSTNode value, int startOffset, int endOffset) {
        return new UnarySSTNode(op, value, startOffset, endOffset);
    }

    @Override
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset, ExprContext context) {
        return new VarLookupSSTNode(name, startOffset, endOffset, context);
    }

    @Override
    public UntypedSSTNode createUntyped(int tokenPosition) {
        return new UntypedSSTNode(tokenPosition);
    }

    @Override
    public StarSSTNode createStarred(SSTNode value, int startOffset, int endOffset) {
        return new StarSSTNode(value, startOffset, endOffset);
    }
    
    @Override
    public SubscriptSSTNode createSubscript(SSTNode receiver, SSTNode subscript, int startOffset, int endOffset) {
        return new SubscriptSSTNode(receiver, subscript, startOffset, endOffset);
    }

    @Override
    public SSTNode createTuple(SSTNode[] values, int startOffset, int endOffset) {
        return CollectionSSTNode.createTuple(values, startOffset, endOffset);
    }

    @Override
    public SSTNode createList(SSTNode[] values, int startOffset, int endOffset) {
        return CollectionSSTNode.createList(values, startOffset, endOffset);
    }

    @Override
    public SSTNode createKeyValuePair(SSTNode key, SSTNode value) {
        return KeyValueSSTNode.create(key, value);
    }

    @Override
    public SSTNode createDict(SSTNode[] keyValuePairs, int startOffset, int endOffset) {
        return CollectionSSTNode.createDict(keyValuePairs, startOffset, endOffset);
    }

    @Override
    public SSTNode createSet(SSTNode[] values, int startOffset, int endOffset) {
        return CollectionSSTNode.createSet(values, startOffset, endOffset);
    }

    @Override
    public ForComprehensionSSTNode createComprehension(SSTNode target, SSTNode iter, SSTNode[] ifs, boolean isAsync, int startOffset, int endOffset) {
        return ForComprehensionSSTNode.create(target, iter, ifs, isAsync, startOffset, endOffset);
    }

    @Override
    public SSTNode createListComprehension(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset) {
        return ComprehensionSSTNode.createList(name, generators, startOffset, endOffset);
    }

    @Override
    public SSTNode createDictComprehension(KeyValueSSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset) {
        return ComprehensionSSTNode.createDict(name, generators, startOffset, endOffset);
    }

    @Override
    public SSTNode createSetComprehension(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset) {
        return ComprehensionSSTNode.createSet(name, generators, startOffset, endOffset);
    }

    @Override
    public SSTNode createGenerator(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset) {
        return ComprehensionSSTNode.createGenerator(name, generators, startOffset, endOffset);
    }

    @Override
    public SSTNode createFunctionDef(String name, ArgDefListBuilder args, SSTNode[] body, SSTNode[] decorators, SSTNode returns, SSTNode typeComment, int startOffset, int endOffset) {
        return new FunctionDefSSTNode(name, args, body, decorators, returns, typeComment, startOffset, endOffset);
    }

    @Override
    public SSTNode createTypeComment(String typeComment, int startOffset, int ednOffset) {
        // FIXME: see comment in AbstractParser#newTypeComment
        return new VarLookupSSTNode(typeComment, startOffset, ednOffset, null);
    }
}
