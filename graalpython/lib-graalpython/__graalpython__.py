# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys


@builtin
def import_current_as_named_module(name, owner_globals=None):
    """
    load a builtin anonymous module which does not have a Truffle land builtin module counter part

    :param name: the module name to load as
    :param owner_globals: the module globals (to update)
    :return: the loaded module
    """
    if owner_globals is None:
        owner_globals = {}

    module = sys.modules.get(name, None)
    if not module:
        module = type(sys)(name)
        sys.modules[name] = module
    new_globals = dict(**owner_globals)
    new_globals.update(**module.__dict__)
    module.__dict__.update(**new_globals)
    return module


@builtin
def lazy_attributes_from_delegate(delegate_name, attributes, owner_module):
    """
    used to lazily load attributes defined in another module via the __getattr__ mechanism.
    This will only cache the attributes in the caller module.

    :param delegate_name: the delegate module
    :param attributes: a list of attributes names to be loaded lazily from the delagate module
    :param owner_module: the owner module (where this is called from)
    :return:
    """
    attributes.append('__all__')

    def __getattr__(attr):
        if attr in attributes:
            delegate_module = __import__(delegate_name)

            new_globals = dict(**delegate_module.__dict__)
            new_globals.update(**owner_module.__dict__)
            owner_module.__dict__.update(**new_globals)

            if '__getattr__' in owner_module.__dict__:
                del owner_module.__dict__['__getattr__']

            return getattr(delegate_module, attr)
        raise AttributeError("module '{}' does not have '{}' attribute".format(delegate_name, attr))

    owner_module.__dict__['__getattr__'] = __getattr__


@builtin
def import_current_as_named_module_with_delegate(module_name, delegate_name, delegate_attributes=None,
                                                 owner_globals=None):
    owner_module = import_current_as_named_module(module_name, owner_globals=owner_globals)
    if delegate_attributes:
        lazy_attributes_from_delegate(delegate_name, delegate_attributes, owner_module)
