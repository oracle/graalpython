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

import static com.oracle.graal.python.nodes.frame.ReadLocalsNode.fastGetCustomLocalsOrGlobals;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class ImportStarNode extends AbstractImportNode {
    private final ConditionProfile javaImport = ConditionProfile.createBinaryProfile();
    private final ConditionProfile havePyFrame = ConditionProfile.createBinaryProfile();
    private final ConditionProfile haveCustomLocals = ConditionProfile.createBinaryProfile();

    @Child private SetItemNode dictWriteNode;
    @Child private SetAttributeNode.Dynamic setAttributeNode;
    @Child private GetItemNode getItemNode;
    @Child private PythonObjectLibrary pythonLibrary;
    @Child private CastToJavaStringNode castToStringNode;
    @Child private GetAnyAttributeNode readNode;
    @Child private PRaiseNode raiseNode;

    @Child private IsBuiltinClassProfile isAttributeErrorProfile;

    private final String moduleName;
    private final int level;

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

    private Object readAttribute(VirtualFrame frame, Object object, String name) {
        if (readNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readNode = insert(GetAttributeNode.GetAnyAttributeNode.create());
        }
        return readNode.executeObject(frame, object, name);
    }

    public ImportStarNode(String moduleName, int level) {
        this.moduleName = moduleName;
        this.level = level;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object importedModule = importModule(frame, moduleName, PArguments.getGlobals(frame), new String[]{"*"}, level);
        PythonObject locals = fastGetCustomLocalsOrGlobals(frame, havePyFrame, haveCustomLocals);

        if (javaImport.profile(emulateJython() && getContext().getEnv().isHostObject(importedModule))) {
            try {
                InteropLibrary interopLib = InteropLibrary.getFactory().getUncached();
                Object hostAttrs = interopLib.getMembers(importedModule, true);
                int len = (int) interopLib.getArraySize(hostAttrs);
                for (int i = 0; i < len; i++) {
                    // interop protocol guarantees these are Strings
                    String attrName = (String) interopLib.readArrayElement(hostAttrs, i);
                    Object attr = interopLib.readMember(importedModule, attrName);
                    writeAttribute(frame, locals, attrName, attr);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
        } else {
            try {
                Object attrAll = readAttribute(frame, importedModule, SpecialAttributeNames.__ALL__);
                int n = ensurePythonLibrary().lengthWithState(attrAll, PArguments.getThreadState(frame));
                for (int i = 0; i < n; i++) {
                    Object attrNameObj = ensureGetItemNode().executeWith(frame, attrAll, i);
                    String attrName;
                    try {
                        attrName = ensureCastToStringNode().execute(attrNameObj);
                    } catch (CannotCastException e) {
                        // TODO(fa): this error should be raised by the ReadAttributeFromObjectNode;
                        // but that needs some refactoring first.
                        throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, attrNameObj);
                    }
                    Object attr = readAttribute(frame, importedModule, attrName);
                    writeAttribute(frame, locals, attrName, attr);
                }
            } catch (PException e) {
                e.expectAttributeError(ensureIsAttributeErrorProfile());
                assert importedModule instanceof PythonModule;
                String[] exportedModuleAttrs = getModuleAttrs(importedModule);
                for (String name : exportedModuleAttrs) {
                    // skip attributes with leading '__' if there was no '__all__' attribute (see
                    // 'ceval.c: import_all_from')
                    if (!PString.startsWith(name, "__")) {
                        Object attr = readAttribute(frame, importedModule, name);
                        writeAttribute(frame, locals, name, attr);
                    }
                }
            }
        }
    }

    private PythonObjectLibrary ensurePythonLibrary() {
        if (pythonLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pythonLibrary = insert(PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth()));
        }
        return pythonLibrary;
    }

    private GetItemNode ensureGetItemNode() {
        if (getItemNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getItemNode = insert(GetItemNode.create());
        }
        return getItemNode;
    }

    private CastToJavaStringNode ensureCastToStringNode() {
        if (castToStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castToStringNode = insert(CastToJavaStringNodeGen.create());
        }
        return castToStringNode;
    }

    private IsBuiltinClassProfile ensureIsAttributeErrorProfile() {
        if (isAttributeErrorProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isAttributeErrorProfile = insert(IsBuiltinClassProfile.create());
        }
        return isAttributeErrorProfile;
    }

    private PException raise(PythonBuiltinClassType errType, String format, Object arg) {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        throw raiseNode.raise(errType, format, arg);
    }

    @TruffleBoundary
    private static String[] getModuleAttrs(Object importedModule) {
        return ((PythonModule) importedModule).getAttributeNames().toArray(new String[0]);
    }
}
