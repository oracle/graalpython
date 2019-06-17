/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.PNode;

public interface ParserNode {
    public PNode createPythonNode(PythonNodeFactory nodeFactory);
}
