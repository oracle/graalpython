/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

public class NumberLiteralSSTNode extends SSTNode {
    protected final String value;
    protected final int start;
    protected final int base;

    public NumberLiteralSSTNode(String value, int start, int base, int startIndex, int endIndex) {
        super(startIndex, endIndex);
        this.value = value;
        this.start = start;
        this.base = base;
    }

//    @Override
//    PNode createPythonNode(ScopeEnvironment scopeEnvironment) {
//        int i = start;
//        long result = 0;
//        while (i < value.length()) {
//            long next = result * base + digitValue(value.charAt(i));
//            if (next < 0) {
//                // overflow
//                BigInteger bigResult = BigInteger.valueOf(result);
//                BigInteger bigBase = BigInteger.valueOf(base);
//                while (i < value.length()) {
//                    bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(value.charAt(i))));
//                    i++;
//                }
//                PIntLiteralNode intLiteral = new PIntLiteralNode(bigResult);
//                return intLiteral;
//            }
//            result = next;
//            i++;
//        }
//        
//        ExpressionNode intLiteral = result <= Integer.MAX_VALUE ? new IntegerLiteralNode((int) result) : new LongLiteralNode(result);
//        return intLiteral;
//    }
    
    
    @Override
    public <T>T accept(SSTreeVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
}
