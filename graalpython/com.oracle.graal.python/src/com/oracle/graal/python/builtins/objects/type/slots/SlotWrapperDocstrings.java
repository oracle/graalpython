/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public enum SlotWrapperDocstrings {
    __repr__("Return repr(self)."),
    __hash__("Return hash(self)."),
    __call__("Call self as a function."),
    __str__("Return str(self)."),
    __getattribute__("Return getattr(self, name)."),
    __setattr__("Implement setattr(self, name, value)."),
    __delattr__("Implement delattr(self, name)."),
    __lt__("Return self<value."),
    __le__("Return self<=value."),
    __eq__("Return self==value."),
    __ne__("Return self!=value."),
    __gt__("Return self>value."),
    __ge__("Return self>=value."),
    __iter__("Implement iter(self)."),
    __next__("Implement next(self)."),
    __get__("Return an attribute of instance, which is of type owner."),
    __set__("Set an attribute of instance to value."),
    __delete__("Delete an attribute of instance."),
    __init__("Initialize self.  See help(type(self)) for accurate signature."),
    __new__("Create and return new object.  See help(type) for accurate signature."),
    __await__("Return an iterator to be used in await expression."),
    __aiter__("Return an awaitable, that resolves in asynchronous iterator."),
    __anext__("Return a value or raise StopAsyncIteration."),
    __add__("Return self+value."),
    __radd__("Return value+self."),
    __sub__("Return self-value."),
    __rsub__("Return value-self."),
    __mul__("Return self*value."),
    __rmul__("Return value*self."),
    __mod__("Return self%value."),
    __rmod__("Return value%self."),
    __divmod__("Return divmod(self, value)."),
    __rdivmod__("Return divmod(value, self)."),
    __pow__("Return pow(self, value, mod)."),
    __rpow__("Return pow(value, self, mod)."),
    __neg__("-self"),
    __pos__("+self"),
    __abs__("abs(self)"),
    __bool__("True if self else False"),
    __invert__("~self"),
    __lshift__("Return self<<value."),
    __rlshift__("Return value<<self."),
    __rshift__("Return self>>value."),
    __rrshift__("Return value>>self."),
    __and__("Return self&value."),
    __rand__("Return value&self."),
    __xor__("Return self^value."),
    __rxor__("Return value^self."),
    __or__("Return self|value."),
    __ror__("Return value|self."),
    __int__("int(self)"),
    __float__("float(self)"),
    __iadd__("Return self+=value."),
    __isub__("Return self-=value."),
    __imul__("Return self*=value."),
    __imod__("Return self%=value."),
    __ipow__("Return self**=value."),
    __ilshift__("Return self<<=value."),
    __irshift__("Return self>>=value."),
    __iand__("Return self&=value."),
    __ixor__("Return self^=value."),
    __ior__("Return self|=value."),
    __floordiv__("Return self//value."),
    __rfloordiv__("Return value//self."),
    __truediv__("Return self/value."),
    __rtruediv__("Return value/self."),
    __ifloordiv__("Return self//=value."),
    __itruediv__("Return self/=value."),
    __index__("Return self converted to an integer, if self is suitable for use as an index into a list."),
    __matmul__("Return self@value."),
    __rmatmul__("Return value@self."),
    __imatmul__("Return self@=value."),
    __len__("Return len(self)."),
    __getitem__("Return self[key]."),
    __setitem__("Set self[key] to value."),
    __delitem__("Delete self[key]."),
    __contains__("Return key in self.");

    public final TruffleString docstring;

    SlotWrapperDocstrings(String docstring) {
        this.docstring = tsLiteral(docstring);
    }

    public static TruffleString getDocstring(String name) {
        return valueOf(name).docstring;
    }
}
