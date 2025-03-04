/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false) // used in BCI root node
public abstract class ImportFromNode extends PNodeWithContext {
    private static final TruffleString T_UNKNOWN_LOCATION = tsLiteral("unknown location");
    private static final TruffleString T_UNKNOWN_MODULE_NAME = tsLiteral("<unknown module name>");

    public abstract Object execute(Frame frame, Object module, TruffleString name);

    @Specialization
    Object doImport(VirtualFrame frame, Object module, TruffleString name,
                    @Bind("this") Node inliningTarget,
                    @Cached PyObjectLookupAttr lookupAttr,
                    @Cached InlinedBranchProfile maybeCircularProfile) {
        Object result = lookupAttr.execute(frame, inliningTarget, module, name);
        if (result != PNone.NO_VALUE) {
            return result;
        }
        maybeCircularProfile.enter(inliningTarget);
        return tryResolveCircularImport(module, name);
    }

    @TruffleBoundary
    private Object tryResolveCircularImport(Object module, TruffleString name) {
        Object pkgnameObj;
        Object pkgpathObj = null;
        TruffleString pkgname = T_UNKNOWN_MODULE_NAME;
        TruffleString pkgpath = T_UNKNOWN_LOCATION;
        try {
            pkgnameObj = PyObjectGetAttr.executeUncached(module, T___NAME__);
            pkgname = CastToTruffleStringNode.executeUncached(pkgnameObj);
        } catch (PException | CannotCastException e) {
            pkgnameObj = null;
        }
        if (pkgnameObj != null) {
            TruffleString fullname = cat(pkgname, T_DOT, name);
            Object imported = PyDictGetItem.executeUncached(getContext().getSysModules(), fullname);
            if (imported != null) {
                return imported;
            }
            try {
                pkgpathObj = PyObjectGetAttr.executeUncached(module, T___FILE__);
                pkgpath = CastToTruffleStringNode.executeUncached(pkgpathObj);
            } catch (PException | CannotCastException e) {
                pkgpathObj = null;
            }
        }
        if (pkgnameObj == null) {
            pkgnameObj = PNone.NONE;
        }
        if (pkgpathObj != null && AbstractImportNode.PyModuleIsInitializing.getUncached().execute(null, module)) {
            throw PConstructAndRaiseNode.getUncached().raiseImportErrorWithModule(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME_CIRCULAR, name, pkgname, pkgpath);
        } else {
            if (pkgpathObj == null) {
                pkgpathObj = PNone.NONE;
            }
            throw PConstructAndRaiseNode.getUncached().raiseImportErrorWithModule(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME, name, pkgname, pkgpath);
        }
    }

    @NeverDefault
    public static ImportFromNode create() {
        return ImportFromNodeGen.create();
    }

    public static ImportFromNode getUncached() {
        return ImportFromNodeGen.getUncached();
    }
}
