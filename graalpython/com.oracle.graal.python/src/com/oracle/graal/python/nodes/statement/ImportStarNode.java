/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ImportStarNode extends AbstractImportNode {
    private final String moduleName;
    private final int level;

    @Child private SetItemNode dictWriteNode = null;
    @Child private SetAttributeNode.Dynamic setAttributeNode = null;

    // TODO: remove once we removed PythonModule globals

    private void writeAttribute(VirtualFrame frame, PythonObject globals, String name, Object value) {
        if (globals instanceof PDict || globals instanceof PMappingproxy) {
            if (dictWriteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dictWriteNode = insert(SetItemNode.create());
            }
            dictWriteNode.executeWith(frame, globals, name, value);
        } else {
            if (setAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setAttributeNode = insert(new SetAttributeNode.Dynamic());
            }
            setAttributeNode.execute(frame, globals, name, value);
        }
    }

    public ImportStarNode(String moduleName, int level) {
        this.moduleName = moduleName;
        this.level = level;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object importedModule = importModule(frame, moduleName, PArguments.getGlobals(frame), new String[]{"*"}, level);
        PythonObject globals = PArguments.getGlobals(frame);
        assert importedModule instanceof PythonModule;
        for (String name : getModuleAttrs(importedModule)) {
            if (name.startsWith("__")) {
                continue;
            }
            Object attr = ((PythonModule) importedModule).getAttribute(name);
            writeAttribute(frame, globals, name, attr);
        }
    }

    @TruffleBoundary
    private static String[] getModuleAttrs(Object importedModule) {
        return ((PythonModule) importedModule).getAttributeNames().toArray(new String[0]);
    }
}
