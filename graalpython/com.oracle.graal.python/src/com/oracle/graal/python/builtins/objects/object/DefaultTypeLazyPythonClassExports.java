/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonTypeLibrary.class, receiverType = LazyPythonClass.class)
final class DefaultTypeLazyPythonClassExports {
    @ExportMessage
    public static boolean isSequenceType(LazyPythonClass type,
                    @Cached LookupAttributeInMRONode.Dynamic hasGetItemNode,
                    @Cached LookupAttributeInMRONode.Dynamic hasLenNode,
                    @Cached("createBinaryProfile()") ConditionProfile lenProfile,
                    @Cached("createBinaryProfile()") ConditionProfile getItemProfile) {
        if (lenProfile.profile(hasLenNode.execute(type, __LEN__) != PNone.NO_VALUE)) {
            return getItemProfile.profile(hasGetItemNode.execute(type, __GETITEM__) != PNone.NO_VALUE);
        }
        return false;
    }

    @ExportMessage
    public static boolean isMappingType(LazyPythonClass type,
                    @Cached LookupAttributeInMRONode.Dynamic hasKeysNode,
                    @Cached LookupAttributeInMRONode.Dynamic hasItemsNode,
                    @Cached LookupAttributeInMRONode.Dynamic hasValuesNode,
                    @CachedLibrary(limit = "1") PythonTypeLibrary pythonTypeLibrary,
                    @Cached("createBinaryProfile()") ConditionProfile profile) {
        if (pythonTypeLibrary.isSequenceType(type)) {
            return profile.profile(hasKeysNode.execute(type, KEYS) != PNone.NO_VALUE &&
                            hasItemsNode.execute(type, ITEMS) != PNone.NO_VALUE &&
                            hasValuesNode.execute(type, VALUES) != PNone.NO_VALUE);
        }
        return false;
    }
}
