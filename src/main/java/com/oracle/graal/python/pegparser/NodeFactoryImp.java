/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

// TODO this class has to be moved to impl package and from this package we need to do api.

import com.oracle.graal.python.pegparser.sst.AnnAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AnnotationSSTNode;
import com.oracle.graal.python.pegparser.sst.AssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.BlockSSTNode;
import com.oracle.graal.python.pegparser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;


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
    public AssignmentSSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, int startOffset, int endOffset) {
        return new AssignmentSSTNode(lhs, rhs, startOffset, endOffset);
    }

    @Override
    public BlockSSTNode createBlock(SSTNode[] statements, int startOffset, int endOffset) {
        return new BlockSSTNode(statements, startOffset, endOffset);
    }

    @Override
    public SSTNode createNumber(String number, int startOffset, int endOffset) {
        // TODO handle all kind of numbers here.
        return NumberLiteralSSTNode.create(number, 0, 10, startOffset, endOffset);
    }

    @Override
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset) {
        return new VarLookupSSTNode(name, startOffset, endOffset);
    }

}
