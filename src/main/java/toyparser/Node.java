/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package toyparser;

import java.util.List;

/**
 *
 * @author petr
 */
public class Node {
    
    public final String type;
    public final List <Node> children;

    public Node(String type, List <Node> children) {
        this.type = type;
        this.children = children;
    }

}
