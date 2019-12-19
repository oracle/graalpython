package com.oracle.graal.python.util;

import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.LinkedList;
import java.util.List;

public class PFunctionArgsFinder {
    private final RootNode rootNode;

    public PFunctionArgsFinder(Node node) {
        this.rootNode = findRootNode(node);
        assert rootNode instanceof PRootNode;
    }

    private static RootNode findRootNode(Node node) {
        Node n = node;
        while (!(n instanceof RootNode) && (n != null)) {
            n = n.getParent();
        }
        return (RootNode) n;
    }

    public ArgumentListObject collectArgs() {
        List<String> arguments = new LinkedList<>();

        NodeUtil.findAllNodeInstances(rootNode, ReadArgumentNode.class).forEach(readArgumentNode -> {
            WriteIdentifierNode identifierNode = NodeUtil.findParent(readArgumentNode, WriteIdentifierNode.class);
            if (identifierNode != null) {
                arguments.add(identifierNode.getIdentifier().toString());
            }
        });

        return new ArgumentListObject(arguments.toArray(new String[0]));
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ArgumentListObject implements TruffleObject {
        final String[] args;

        private ArgumentListObject(String[] args) {
            this.args = args;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @TruffleBoundary
        Object getMembers(boolean includeInternal) {
            return new InteropArray(args);
        }
    }

}
