/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictGetItemNodeGen;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetAttrNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.AbstractImportNodeFactory.PyModuleIsInitializingNodeGen;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.strings.TruffleString;

public class ImportFromNode extends AbstractImportNode {
    private static final TruffleString T_UNKNOWN_LOCATION = tsLiteral("unknown location");
    private static final TruffleString T_UNKNOWN_MODULE_NAME = tsLiteral("<unknown module name>");

    @Children private final WriteNode[] aslist;
    @Child private PyDictGetItem getItem;
    @Child private PyModuleIsInitializing isInitNode;
    @Child private CastToTruffleStringNode castToStringNode;
    @Child private PConstructAndRaiseNode constructAndRaiseNode;
    @Child private PyObjectGetAttr getattr;

    private final TruffleString importee;
    private final int level;

    @Child private IsBuiltinClassProfile getAttrErrorProfile = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile getFileErrorProfile = IsBuiltinClassProfile.create();
    @CompilationFinal(dimensions = 1) private final TruffleString[] fromlist;

    private PException raiseImportError(VirtualFrame frame, Object name, Object path, TruffleString format, Object... formatArgs) {
        if (constructAndRaiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            constructAndRaiseNode = insert(PConstructAndRaiseNode.create());
        }
        throw constructAndRaiseNode.raiseImportError(frame, name, path, format, formatArgs);
    }

    public static ImportFromNode create(TruffleString importee, TruffleString[] fromlist, WriteNode[] readNodes, int level) {
        return new ImportFromNode(importee, fromlist, readNodes, level);
    }

    public TruffleString getImportee() {
        return importee;
    }

    public int getLevel() {
        return level;
    }

    public TruffleString[] getFromlist() {
        return fromlist;
    }

    protected ImportFromNode(TruffleString importee, TruffleString[] fromlist, WriteNode[] readNodes, int level) {
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
        PDict sysModules = getContext().getSysModules();

        for (int i = 0; i < fromlist.length; i++) {
            TruffleString attr = fromlist[i];
            WriteNode writeNode = aslist[i];
            if (getattr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getattr = insert(PyObjectGetAttrNodeGen.create());
            }
            try {
                writeNode.executeObject(frame, getattr.execute(frame, importedModule, attr));
            } catch (PException pe) {
                pe.expectAttributeError(getAttrErrorProfile);
                Object moduleName = T_UNKNOWN_MODULE_NAME;
                Object modulePath = T_UNKNOWN_LOCATION;
                TruffleString pkgname = null;
                boolean readFile = true;
                try {
                    moduleName = getattr.execute(frame, importedModule, T___NAME__);
                    try {
                        pkgname = ensureCastToStringNode().execute(moduleName);
                    } catch (CannotCastException cce) {
                        readFile = false;
                    }
                } catch (PException pe2) {
                    if (getAttrErrorProfile.profileException(pe2, PythonBuiltinClassType.AttributeError)) {
                        readFile = false;
                    }
                }
                if (pkgname != null) {
                    TruffleString fullname = StringUtils.cat(pkgname, T_DOT, attr);
                    Object resolvedFullname = ensureGetItemNode().execute(frame, sysModules, fullname);
                    if (resolvedFullname != null) {
                        writeNode.executeObject(frame, resolvedFullname);
                        continue;
                    }
                }
                if (readFile) {
                    try {
                        modulePath = getattr.execute(frame, importedModule, T___FILE__);
                    } catch (PException pe3) {
                        pe3.expectAttributeError(getFileErrorProfile);
                    }
                }
                if (isModuleInitialising(frame, importedModule)) {
                    throw raiseImportError(frame, moduleName, modulePath, ErrorMessages.CANNOT_IMPORT_NAME_CIRCULAR, attr, moduleName);
                } else {
                    throw raiseImportError(frame, moduleName, modulePath, ErrorMessages.CANNOT_IMPORT_NAME, attr, moduleName, modulePath);
                }
            }
        }
    }

    private boolean isModuleInitialising(VirtualFrame frame, Object importedModule) {
        if (isInitNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isInitNode = insert(PyModuleIsInitializingNodeGen.create());
        }
        return isInitNode.execute(frame, importedModule);
    }

    private PyDictGetItem ensureGetItemNode() {
        if (getItem == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItem = insert(PyDictGetItemNodeGen.create());
        }
        return getItem;
    }

    private CastToTruffleStringNode ensureCastToStringNode() {
        if (castToStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castToStringNode = insert(CastToTruffleStringNode.create());
        }
        return castToStringNode;
    }
}
