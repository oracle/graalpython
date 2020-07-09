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

@__graalpython__.builtin
def warn_explicit(message, category, filename, lineno, module=None, registry=None, module_globals=None, source=None):
    """
    Low-level interface to warnings functionality.
    """
    lineno = int(lineno)
    source_line = None
    if module_globals is not None:
        if not isinstance(module_globals, dict):
            raise TypeError("module_globals must be a dict, not %s" % type(module_globals))
        # get_source_line
        loader = module_globals.get("__loader__")
        if loader:
            module_name = module_globals.get("__name__")
            if module_name:
                src = loader.get_source(module_name)
                if src is not None:
                    source_line = src.splitlines(False)[lineno - 1]
    # warn_explicit
    if not (registry is None or isinstance(registry, dict)):
        raise TypeError("'registry' must be a dict or None")

    if module is None:
        if len(filename) == 0:
            module = "<unknown>"
        elif filename.endswith(".py"):
            module = filename[:-3]
        else:
            module = filename

    if isinstance(message, Warning):
        text = str(message)
        category = type(message)
    else:
        text = message
        message = category(message)

    key = (text, category, lineno)
    if registry is not None:
        # already warned?
        if registry.get("version") != _filters_version():
            registry.clear()
            registry["version"] = _filters_version()
        elif registry.get(key):
            return None

    # get_filter
    if not isinstance(filters, list):
        raise ValueError("_warnings.filters must be a list")

    action = None
    for idx, f in enumerate(filters):
        if not (isinstance(f, tuple) and len(f) == 5):
            raise ValueError("_warnings.filters item %d isn't a 5-tuple" % idx)
        act, msg, cat, mod, ln = f
        if not isinstance(act, str):
            raise TypeError("action must be a string, not '%s'" % type(act))
        if msg is None:
            good_msg = True
        elif isinstance(msg, str):
            good_msg = msg.__eq__(message)
        else:
            good_msg = msg.match(message)
        if mod is None:
            good_mod = True
        elif isinstance(mod, str):
            good_mod = mod.__eq__(module)
        else:
            good_mod = mod.match(module)
        is_subclass = isinstance(category, cat)
        ln = int(ln)
        if good_msg and good_mod and is_subclass and (ln == 0 or lineno == ln):
            action = act
            break
    if not action:
        action = _defaultaction

    if action == "error":
        raise category(message)
    elif action == "ignore":
        return

    already_warned = False
    if action != "always":
        if registry:
            registry[key] = True
        if action == "once":
            if _onceregistry.get("version") != _filters_version():
                _onceregistry.clear()
                _onceregistry["version"] = _filters_version()
            elif _onceregistry.get((text, category)):
                already_warned = True
            _onceregistry[(text, category)] = 1
        elif action == "module" and registry:
            if registry.get("version") != _filters_version():
                registry.clear()
                registry["version"] = _filters_version()
            elif registry.get((text, category, 0)):
                already_warned = True
            registry[(text, category, 0)] = 1
        elif action != "default":
            raise RuntimeError("Unrecognized action (%r) in warnings.filters" % action)

    if already_warned:
        return
    else:
        # call_show_warning
        import warnings
        show_fn = getattr(warnings, "_showwarnmsg", None)
        if not show_fn:
            import sys
            sys.stderr.write(filename)
            sys.stderr.write(":")
            sys.stderr.write(str(lineno))
            sys.stderr.write(": ")
            sys.stderr.write(category.__name__)
            sys.stderr.write(": ")
            sys.stderr.write(text)
            sys.stderr.write("\n")
            sys.stderr.flush()
            if source_line:
                print(source_line.strip(), file=sys.stderr, flush=True)
            else:
                try:
                    with open(filename, "r") as f:
                        for i in range(lineno):
                            line = f.readline()
                        print("  ", line.strip(), file=sys.stderr, flush=True)
                except:
                    pass # errors are ignored
            return
        if not callable(show_fn):
            raise TypeError("warnings._showwarnmsg() must be set to a callable")
        WarningMessage = getattr(warnings, "WarningMessage", None)
        if not WarningMessage:
            raise RuntimeError("unable to get warnings.WarningMessage")
        return show_fn(WarningMessage(message, category, filename, lineno, None, None, source))


@__graalpython__.builtin
def warn(message, category=None, stacklevel=1, source=None):
    """
    warn($module, /, message, category=None, stacklevel=1, source=None)

    Issue a warning, or maybe ignore it or raise an exception.
    """
    if isinstance(stacklevel, float):
        raise TypeError("integer argument expected, got float")
    stacklevel = int(stacklevel)
    # get_category
    if isinstance(message, Warning):
        category = type(message)
    elif category is None:
        category = UserWarning
    if not issubclass(category, Warning):
        raise TypeError("category must be a Warning subclass, not '%s'", type(category))
    # do_warn
    import sys
    try:
        f = sys._getframe(stacklevel)
        globals = f.f_globals
        filename = f.f_code.co_filename
        lineno = f.f_lineno
    except ValueError:
        globals = sys.__dict__
        filename = "sys"
        lineno = 1
    module = globals.get("__name__", "<string>")
    registry = globals.get("__warningregistry__")
    if registry is None:
        globals["__warningregistry__"] = {}
    return warn_explicit(message, category, filename, lineno, module, registry, None, source)
