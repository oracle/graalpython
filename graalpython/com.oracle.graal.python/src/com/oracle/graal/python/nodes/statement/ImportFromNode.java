/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseImportErrorNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeGen.GetAnyAttributeNodeGen;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public class ImportFromNode extends AbstractImportNode {
    @Children private final WriteNode[] aslist;
    @Child private GetAttributeNode getName;
    @Child private GetAttributeNode getPath;
    @Child private GetItemNode getItem;
    @Child private ReadAttributeFromObjectNode readModules;
    @Child private GetAnyAttributeNode getAttributeNode = GetAnyAttributeNodeGen.create();
    @Child private PRaiseImportErrorNode raiseNode;

    private final String importee;
    private final int level;

    @Child private IsBuiltinClassProfile getAttrErrorProfile = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile getFileErrorProfile = IsBuiltinClassProfile.create();
    @CompilationFinal(dimensions = 1) private final String[] fromlist;

    public static ImportFromNode create(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        return new ImportFromNode(importee, fromlist, readNodes, level);
    }

    public String getImportee() {
        return importee;
    }

    public int getLevel() {
        return level;
    }

    public String[] getFromlist() {
        return fromlist;
    }

    protected ImportFromNode(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        this.importee = importee;
        this.fromlist = fromlist;
        this.aslist = readNodes;
        this.level = level;
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public void executeVoid(VirtualFrame frame) {
        Object globals = PArguments.getGlobals(frame);
        Object importedModule = importModule(frame, importee, globals, fromlist, level);
        for (int i = 0; i < fromlist.length; i++) {
            String attr = fromlist[i];
            WriteNode writeNode = aslist[i];
            try {
                writeNode.doWrite(frame, getAttributeNode.executeObject(frame, importedModule, attr));
            } catch (PException pe) {
                pe.expectAttributeError(getAttrErrorProfile);
                Object moduleName = "<unknown module name>";
                try {
                    moduleName = ensureGetNameNode().executeObject(frame, importedModule);
                    String pkgname;
                    if (moduleName instanceof PString) {
                        pkgname = ((PString) moduleName).getValue();
                    } else if (moduleName instanceof String) {
                        pkgname = (String) moduleName;
                    } else {
                        throw pe;
                    }
                    String fullname = PString.cat(pkgname, ".", attr);
                    Object sysModules = ensureReadModulesNode().execute(getContext().getCore().lookupBuiltinModule("sys"), "modules");
                    writeNode.doWrite(frame, ensureGetItemNode().execute(frame, sysModules, fullname));
                } catch (PException e2) {
                    Object modulePath = "unknown location";
                    if (!getAttrErrorProfile.profileException(e2, PythonBuiltinClassType.AttributeError)) {
                        try {
                            modulePath = ensureGetPathNode().executeObject(frame, importedModule);
                        } catch (PException e3) {
                            e3.expectAttributeError(getFileErrorProfile);
                        }
                    }
                    throw ensureRaiseNode().raiseImportError(frame, moduleName, modulePath, ErrorMessages.CANNOT_IMPORT_NAME, attr, moduleName, modulePath);
                }
            }
        }
    }

    private GetAttributeNode ensureGetNameNode() {
        if (getName == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getName = insert(GetAttributeNode.create(__NAME__));
        }
        return getName;
    }

    private GetAttributeNode ensureGetPathNode() {
        if (getPath == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getPath = insert(GetAttributeNode.create(__FILE__));
        }
        return getPath;
    }

    private PRaiseImportErrorNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseImportErrorNode.create());
        }
        return raiseNode;
    }

    private ReadAttributeFromObjectNode ensureReadModulesNode() {
        if (readModules == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItem = insert(GetItemNode.create());
            readModules = insert(ReadAttributeFromObjectNode.create());
        }
        return readModules;
    }

    private GetItemNode ensureGetItemNode() {
        if (getItem == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItem = insert(GetItemNode.create());
        }
        return getItem;
    }
}
