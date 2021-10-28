/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package toyparser;

/**
 *
 * @author petr
 */
public class PrintNodeVisitor {

    
    public static  String print(Node node) {
        StringBuffer sb = new StringBuffer();
        addNode(sb, node, 0);
        return sb.toString();
        
    }
    
    private static void addNode(StringBuffer sb, Node node, int indent) {
        addIndent(sb, indent);
        sb.append("Node \"").append(node.type).append("\"");
        if (node.children != null) {
            sb.append(" {");
            addNewLine(sb);
            for (Node child : node.children) {
                addNode(sb, child, indent + 2);
            }
            addIndent(sb, indent);
            sb.append("}");
        }
        addNewLine(sb);
    }
    
    private static void addIndent(StringBuffer sb, int indent) {
        for(int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }
    
    private static void addNewLine(StringBuffer sb) {
        sb.append('\n');
        
    }
}
