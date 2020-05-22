/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.text.Normalizer;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "unicodedata")
public class UnicodeDataModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return UnicodeDataModuleBuiltinsFactory.getFactories();
    }

    public static String getUnicodeVersion() {

        // Preliminary Unicode 11 data obtained from
        // <https://www.unicode.org/Public/11.0.0/ucd/DerivedAge-11.0.0d13.txt>.
        if (Character.getType('\u0560') != Character.UNASSIGNED) {
            return "11.0.0";    // 11.0, June 2018.
        }

        if (Character.getType('\u0860') != Character.UNASSIGNED) {
            return "10.0.0";    // 10.0, June 2017.
        }

        if (Character.getType('\u08b6') != Character.UNASSIGNED) {
            return "9.0.0";     // 9.0, June 2016.
        }

        if (Character.getType('\u08b3') != Character.UNASSIGNED) {
            return "8.0.0";     // 8.0, June 2015.
        }

        if (Character.getType('\u037f') != Character.UNASSIGNED) {
            return "7.0.0";     // 7.0, June 2014.
        }

        if (Character.getType('\u061c') != Character.UNASSIGNED) {
            return "6.3.0";     // 6.3, September 2013.
        }

        if (Character.getType('\u20ba') != Character.UNASSIGNED) {
            return "6.2.0";     // 6.2, September 2012.
        }

        if (Character.getType('\u058f') != Character.UNASSIGNED) {
            return "6.1.0";     // 6.1, January 2012.
        }

        if (Character.getType('\u0526') != Character.UNASSIGNED) {
            return "6.0.0";     // 6.0, October 2010.
        }

        if (Character.getType('\u0524') != Character.UNASSIGNED) {
            return "5.2.0";     // 5.2, October 2009.
        }

        if (Character.getType('\u0370') != Character.UNASSIGNED) {
            return "5.1.0";     // 5.1, March 2008.
        }

        if (Character.getType('\u0242') != Character.UNASSIGNED) {
            return "5.0.0";     // 5.0, July 2006.
        }

        if (Character.getType('\u0237') != Character.UNASSIGNED) {
            return "4.1.0";     // 4.1, March 2005.
        }

        if (Character.getType('\u0221') != Character.UNASSIGNED) {
            return "4.0.0";     // 4.0, April 2003.
        }

        if (Character.getType('\u0220') != Character.UNASSIGNED) {
            return "3.2.0";     // 3.2, March 2002.
        }

        if (Character.getType('\u03f4') != Character.UNASSIGNED) {
            return "3.1.0";     // 3.1, March 2001.
        }

        if (Character.getType('\u01f6') != Character.UNASSIGNED) {
            return "3.0.0";     // 3.0, September 1999.
        }

        if (Character.getType('\u20ac') != Character.UNASSIGNED) {
            return "2.1.0";     // 2.1, May 1998.
        }

        if (Character.getType('\u0591') != Character.UNASSIGNED) {
            return "2.0.0";     // 2.0, July 1996.
        }

        if (Character.getType('\u0000') != Character.UNASSIGNED) {
            return "1.1.0";     // 1.1, June 1993.
        }

        return "1.0.0";         // 1.0
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("version", getUnicodeVersion());
        PythonBuiltinClass objectType = core.lookupType(PythonBuiltinClassType.PythonObject);
        PythonObject ucd_3_2_0 = core.factory().createPythonObject(objectType);
        ucd_3_2_0.setAttribute("unidata_version", "3.2.0");
        builtinConstants.put("ucd_3_2_0", ucd_3_2_0); // TODO this is a fake object, just satisfy
                                                      // pip installer import
    }

    // unicodedata.normalize(form, unistr)
    @Builtin(name = "normalize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBuiltinNode {
        @TruffleBoundary
        protected Normalizer.Form getForm(String form) {
            try {
                return Normalizer.Form.valueOf(form);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Specialization(guards = {"form.equals(cachedForm)"}, limit = "4")
        @TruffleBoundary
        public String normalize(@SuppressWarnings("unused") String form, String unistr,
                        @SuppressWarnings("unused") @Cached("form") String cachedForm,
                        @Cached("getForm(cachedForm)") Normalizer.Form cachedNormForm) {
            if (cachedNormForm == null) {
                throw raise(ValueError, ErrorMessages.INVALID_NORMALIZATION_FORM);
            }
            return Normalizer.normalize(unistr, cachedNormForm);
        }

        @Specialization(guards = {"form.equals(cachedForm)"}, limit = "4")
        public String normalize(String form, PString unistr,
                        @Cached("form") String cachedForm,
                        @Cached("getForm(cachedForm)") Normalizer.Form cachedNormForm) {
            return normalize(form, unistr.getValue(), cachedForm, cachedNormForm);
        }

    }
}
