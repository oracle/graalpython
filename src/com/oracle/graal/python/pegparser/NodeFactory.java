/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.*;

public interface NodeFactory {

    public AnnAssignmentSSTNode createAnnAssignment(AnnotationSSTNode annotation, SSTNode rhs, int startOffset, int endOffset);
    public AnnotationSSTNode createAnnotation(SSTNode lhs, SSTNode type, int startOffset, int endOffset);
    public AssignmentSSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, int startOffset, int endOffset);
    public BlockSSTNode createBlock(SSTNode[] statements, int startOffset, int endOffset);
    public SSTNode createNumber(String number, int startOffset, int endOffset);
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset);

}
