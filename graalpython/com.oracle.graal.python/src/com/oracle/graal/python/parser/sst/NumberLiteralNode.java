/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.parser.ScopeEnvironment;
import java.math.BigInteger;

/**
 *
 * @author petr
 */
public class NumberLiteralNode extends SSTNode {
    protected final String value;
    protected final int start;
    protected final int base;

    public NumberLiteralNode(String value, int start, int base, int startIndex, int endIndex) {
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
