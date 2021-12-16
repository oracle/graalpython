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

import com.oracle.graal.python.pegparser.sst.*;

public interface NodeFactory {
    public AnnAssignmentSSTNode createAnnAssignment(AnnotationSSTNode annotation, SSTNode rhs, int startOffset, int endOffset);

    public AnnotationSSTNode createAnnotation(SSTNode lhs, SSTNode type, int startOffset, int endOffset);

    public AssignmentSSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, SSTNode typeComment, int startOffset, int endOffset);

    public AugAssignmentSSTNode createAugAssignment(SSTNode lhs, BinaryArithmeticSSTNode.Type operation, SSTNode rhs, int startOffset, int endOffset);

    public BinaryArithmeticSSTNode createBinaryOp(BinaryArithmeticSSTNode.Type op, SSTNode left, SSTNode right, int startOffset, int endOffset);

    public BlockSSTNode createBlock(SSTNode[] statements, int startOffset, int endOffset);

    public BooleanLiteralSSTNode createBooleanLiteral(boolean value, int startOffset, int endOffset);

    public SSTNode createNone(int startOffset, int endOffset);

    public SSTNode createEllipsis(int startOffset, int endOffset);

    public GetAttributeSSTNode createGetAttribute(SSTNode receiver, String name, int startOffset, int endOffset);

    public SSTNode createPass(int startOffset, int endOffset);

    public SSTNode createBreak(int startOffset, int endOffset);
    
    public CallSSTNode createCall(SSTNode target, SSTNode[] args, SSTNode[] kwargs, int startOffset, int endOffset);

    public SSTNode createContinue(int startOffset, int endOffset);

    public SSTNode createYield(SSTNode value, boolean isFrom, int startOffset, int endOffset);

    public NumberLiteralSSTNode createNumber(String number, int startOffset, int endOffset);

    public StringLiteralSSTNode createString(String[] values, int startOffset, int endOffset, FExprParser exprParser, ParserErrorCallback errorCb);

    public SubscriptSSTNode createSubscript(SSTNode receiver, SSTNode subscript, int startOffset, int endOffset);

    public UnarySSTNode createUnaryOp(UnarySSTNode.Type op, SSTNode value, int startOffset, int endOffset);

    default VarLookupSSTNode createVariable(String name, int startOffset, int endOffset) {
        return createVariable(name, startOffset, endOffset, ExprContext.Load);
    }

    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset, ExprContext context);

    public SSTNode createTuple(SSTNode[] values, int startOffset, int endOffset);

    public SSTNode createList(SSTNode[] values, int startOffset, int endOffset);

    public SSTNode createKeyValuePair(SSTNode key, SSTNode value);

    public SSTNode createDict(SSTNode[] keyValuePairs, int startOffset, int endOffset);

    public SSTNode createSet(SSTNode[] values, int startOffset, int endOffset);
    
    public StarSSTNode createStarred(SSTNode value, int startOffset, int endOffset);

    public UntypedSSTNode createUntyped(int tokenPosition);

    public ForComprehensionSSTNode createComprehension(SSTNode target, SSTNode iter, SSTNode[] ifs, boolean isAsync, int startOffset, int endOffset);

    public SSTNode createListComprehension(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset);

    public SSTNode createDictComprehension(KeyValueSSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset);

    public SSTNode createSetComprehension(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset);

    public SSTNode createGenerator(SSTNode name, ForComprehensionSSTNode[] generators, int startOffset, int endOffset);

    public SSTNode createFunctionDef(String name, ArgDefListBuilder args, SSTNode[] body, SSTNode[] decorators, SSTNode returns, SSTNode typeComment, int startOffset, int endOffset);

    public SSTNode createTypeComment(String typeComment, int startOffset, int ednOffset);
}
