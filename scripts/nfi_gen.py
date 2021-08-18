# Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import re
import os

LICENSE = """/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */"""

class_template = LICENSE + """
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;

/*-
 * Generated using:
{cmd}
 */
public class NFI{lib_name}Support {{

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFI{lib_name}Support.class);

{var_defs}{enum_def}
{class_init}
{static_functions}
{functions}
}}
"""
var_def_template = "    public static final int {name} = {value};"

class_init_template = """    private static final String SUPPORTING_NATIVE_LIB_NAME = "{sys_lib}";

    private final PythonContext pythonContext;
    private final NativeLibrary.TypedNativeLibrary<{lib_name}NativeFunctions> typedNativeLib;

    @CompilerDirectives.CompilationFinal private boolean available;

    private NFI{lib_name}Support(PythonContext context, NativeLibrary.NFIBackend backend, String noNativeAccessHelp) {{
        if (context.isNativeAccessAllowed()) {{
            this.pythonContext = context;
            this.typedNativeLib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME, {lib_name}NativeFunctions.values(),
                            backend, noNativeAccessHelp, true);
            this.available = true;
        }} else {{
            this.pythonContext = null;
            this.typedNativeLib = null;
            this.available = false;
        }}
    }}

    public static NFI{lib_name}Support createNative(PythonContext context, String noNativeAccessHelp) {{
        return new NFI{lib_name}Support(context, NativeLibrary.NFIBackend.NATIVE, noNativeAccessHelp);
    }}

    public static NFI{lib_name}Support createLLVM(PythonContext context, String noNativeAccessHelp) {{
        return new NFI{lib_name}Support(context, NativeLibrary.NFIBackend.LLVM, noNativeAccessHelp);
    }}
{finalizer_helpers}
    public void notAvailable() {{
        if (available) {{
            CompilerAsserts.neverPartOfCompilation("Checking NFI{lib_name}Support availability should only be done during initialization.");
            available = false;
        }}
    }}

    public boolean isAvailable() {{
        return available;
    }}

    public PythonContext getContext() {{
        return pythonContext;
    }}
"""

gc_finalizer_template = """
    static class PointerReleaseCallback implements AsyncHandler.AsyncAction {{
        private final Pointer pointer;

        public PointerReleaseCallback(Pointer pointer) {{
            this.pointer = pointer;
        }}

        @Override
        public void execute(PythonContext context) {{
            synchronized (pointer) {{
                if (pointer.isReleased()) {{
                    return;
                }}
                try {{
                    pointer.doRelease();
                    pointer.markReleased();
                    LOGGER.finest("NFI{lib_name}Support pointer has been freed");
                }} catch (Exception e) {{
                    LOGGER.severe("Error while trying to free NFI{lib_name}Support pointer: " + e.getMessage());
                }}
            }}
        }}
    }}

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {{

        private final NFI{lib_name}Support lib;

        public Pointer(Object referent, Object ptr, NFI{lib_name}Support lib) {{
            super(referent, ptr, lib.pythonContext.getSharedFinalizer());
            this.lib = lib;
        }}

        protected void doRelease() {{
            lib.{release_function}(getReference());
        }}

        @Override
        public AsyncHandler.AsyncAction release() {{
            if (!isReleased()) {{
                return new PointerReleaseCallback(this);
            }}
            return null;
        }}
    }}
"""

enum_def_template = """    enum {lib_name}NativeFunctions implements NativeLibrary.NativeFunction {{
{enums};

        private final String signature;

        {lib_name}NativeFunctions(String signature) {{
            this.signature = signature;
        }}

        @Override
        public String signature() {{
            return signature;
        }}
    }}
"""
enum_value_template = """
        /*-
          {comments}
        */
        {native_func_name}("{native_func_sign}"),"""

doc_star = "     *"
doc_java_param_template = doc_star + " @param {param_name} {param_desc}"
doc_java_return_template = doc_star + " @return %s"
java_function_return = "        return "
java_function_no_return = "        "
java_function = """    /**
     * 
{params_doc}
{return_doc}
     */
    public {return_type} {function_name}({params_list}
                    NativeLibrary.InvokeNativeFunction invokeNode) {{
{do_return}invokeNode.{call_type}(typedNativeLib, {lib_name}NativeFunctions.{native_func_name}{args_list});
    }}
"""

java_static_function = """    /**
     *
{params_doc}
{return_doc}
     */
    public Object {function_name}({params_list}) {{
        return typedNativeLib.callUncached(pythonContext, {lib_name}NativeFunctions.{native_func_name}{args_list});
    }}
"""

nfi_type_dict = {
    'int' : 'SINT32',
    'uInt' : 'UINT32',
    'int*' : '[SINT32]',
    'uint32_t' : 'UINT32',
    'uint32_t*' : '[UINT32]',

    'long' : 'SINT64',
    'int64_t' : 'SINT64',
    'ssize_t' : 'SINT64',
    'long*' : '[SINT64]',
    'int64_t*' : '[SINT64]',
    'ssize_t*' : '[SINT64]',
    'uLong' : 'UINT64',
    'uint64_t' : 'UINT64',
    'size_t' : 'UINT64',
    'uint64_t*' : '[UINT64]',
    'size_t*' : '[UINT64]',

    'double' : 'DOUBLE',
    'double*' : '[DOUBLE]',

    'Byte' : 'UINT8',
    'Byte*' : '[UINT8]',

    'char*' : 'STRING',

    'void' : 'VOID',
    'void*': 'POINTER'
}

java_type_dict = {
    'int' : 'int',
    'uInt' : 'int',

    'uint32_t' : 'long',

    'long' : 'long',
    'uLong' : 'long',
    'int64_t' : 'long',
    'uint64_t' : 'long',
    'ssize_t' : 'long',
    'size_t' : 'long',
    
    'double' : 'double',

    'Byte' : 'byte',

    'char*' : 'String',

    'void' : 'void',
}

java_ret_type_dict = {
    'int' :   ('int', 'callInt'),
    'uInt' :  ('int', 'callInt'),
    'size_t' :  ('long', 'callLong'),
    'ssize_t' :  ('long', 'callLong'),
    'long' :  ('long', 'callLong'),
    'uLong' : ('long', 'callLong'),
    'char*' : ('String', 'callString'),
    'void' : ('void', 'call'),
}

def find_nfi_type(t, local_dict, obj='OBJECT', ref='POINTER'):
    t = t.replace(' ','')
    if t in local_dict:
        return local_dict[t]
    elif t in nfi_type_dict:
        return nfi_type_dict[t]
    else:
        return ref if '*' in t else obj

def find_java_type(t, obj='Object'):
    t = t.replace(' ','')
    if t in java_type_dict:
        return java_type_dict[t]
    else:
        return obj

ignore_list = ['{', 'static ', 'const ', 'struct ']
annotation_regex_name = "(name\(\'(.*?)\'\))"
annotation_regex_map = "(map\(\'(.*?)\', \'(.*?)\'\))"
annotation_regex_static = "(static\(true\))"
funcdef_regex = '([\w\s\*]+) ([\s\*\w]+)'
var_regex = '#define\s+(\w+)\s+([0-9]+)'

annotation_regex_gc = "(release\(true\))"

def generate_nfi_support_class(cmd, lib_name, c_source_path, sys_lib, to_path):
    text = None
    with open(c_source_path, 'r') as fp:
        text = fp.read()
    text_s = text.split('\n')
    class nfi_function:
        def __init__(self, java_name, name, ret_type, orig_ret_type, arg_orig_list, arg_name_list, arg_type_list, arg_java_list, comments, is_static, is_release):
            self.java_name = java_name
            self.name = name
            self.orig_ret_type = orig_ret_type
            self.ret_type = ret_type
            self.arg_orig_list = arg_orig_list
            self.arg_name_list = arg_name_list
            self.arg_type_list = arg_type_list
            self.arg_java_list = arg_java_list
            self.comments = comments
            self.is_static = is_static
            self.is_release = is_release

    nfi_vars = []
    nfi_functions = []
    for i, l in enumerate(text_s):
        if 'nfi_var' in l:
            var_def = re.findall(var_regex, l)
            nfi_vars.append(var_def[0])
            pass
        elif 'nfi_function:' in l:
            java_name = re.findall(annotation_regex_name, l)
            java_name = java_name[0][1]
            mapping = re.findall(annotation_regex_map, l)
            local_dict = {}
            for n, k, v in mapping:
                local_dict[k] = v

            is_static = True if re.findall(annotation_regex_static, l) else False
            is_release = True if re.findall(annotation_regex_gc, l) else False

            func_def = text_s[i+1]
            j = 2
            while '{' not in func_def:
                func_def += text_s[i+j].strip()
                j += 1
            for ignore in ignore_list:
                func_def = func_def.replace(ignore, '')

            sign_list = re.findall(funcdef_regex, func_def)
            name = sign_list[0][1]
            ret_type = sign_list[0][0]
            if '*' in name:
                ret_type += '*'
                name = name.replace('*', '')
            orig_ret_type = ret_type
            ret_type = find_nfi_type(ret_type, local_dict)
            arg_orig_list = []
            arg_name_list = []
            arg_type_list = []
            arg_java_list = []
            for t, v in sign_list[1:]:
                arg_orig_list.append(t + ' ' + v)
                if '*' in v:
                    t += '*'
                arg_name_list.append(v.replace('*', ''))
                arg_java_list.append(find_java_type(t))
                arg_type_list.append(find_nfi_type(t, local_dict))
            comments = [l.strip().replace('// ', ''), func_def.strip()]
            nfi_functions.append(nfi_function(java_name, name, ret_type, orig_ret_type, arg_orig_list, arg_name_list, arg_type_list, arg_java_list, comments, is_static, is_release))

    enum_vals = []
    java_funcs = []
    java_static_funcs = []
    release_func_name = None
    for f in nfi_functions:
        comments = '\n          '.join(f.comments)
        function_name = f.java_name
        native_func_name = f.name
        native_func_sign = '(' + ', '.join(f.arg_type_list) + '): ' + f.ret_type
        enum_val = enum_value_template.format(
            comments=comments,
            native_func_name=native_func_name,
            native_func_sign=native_func_sign
            )
        enum_vals.append(enum_val)

        args_list = ', '.join(f.arg_name_list)
        if args_list:
            args_list = ', ' + args_list
        def clean_str(s):
            return ' '.join(s.strip().split()) if s else s
        params_doc = '\n'.join([doc_java_param_template.format(param_name=clean_str(name), param_desc=clean_str(desc)) for name, desc in zip(f.arg_name_list, f.arg_orig_list)])
        if not params_doc:
            params_doc = doc_star
        return_type, call_type = java_ret_type_dict.get(f.orig_ret_type, ('Object', 'call'))
        return_doc = doc_star
        do_return = java_function_no_return
        if return_type != "void":
            return_doc = doc_java_return_template % clean_str(f.orig_ret_type)
            do_return = java_function_return
        params_list = ', '.join([' '.join(map(str, i)) for i in zip(f.arg_java_list, f.arg_name_list)])
        if f.is_static or f.is_release:
            java_static_func = java_static_function.format(
                params_doc=params_doc,
                return_doc=return_doc,
                params_list=params_list,
                args_list=args_list,
                lib_name=lib_name,
                native_func_name=native_func_name,
                function_name=function_name
                )
            java_static_funcs.append(java_static_func)
            if f.is_release:
                release_func_name = function_name
        else:
            if params_list:
                params_list += ','
            java_func = java_function.format(
                params_doc=params_doc,
                return_doc=return_doc,
                do_return=do_return,
                return_type=return_type,
                call_type=call_type,
                params_list=params_list,
                args_list=args_list,
                lib_name=lib_name,
                native_func_name=native_func_name,
                function_name=function_name
                )
            java_funcs.append(java_func)

    var_defs = ''
    if nfi_vars:
        var_defs = '\n'.join([var_def_template.format(name=name, value=value) for name, value in nfi_vars])
        var_defs += '\n\n'
    enum_vals_str = '\n'.join(enum_vals)
    enum_def = enum_def_template.format(
        lib_name=lib_name,
        enums=enum_vals_str[:-1]
    )

    java_lang_imports = ''
    other_imports = ''
    finalizer_helpers = ''
    if release_func_name:
        finalizer_helpers = gc_finalizer_template.format(
            lib_name=lib_name,
            release_function=release_func_name
            )

    class_init = class_init_template.format(
        sys_lib=sys_lib,
        lib_name=lib_name,
        finalizer_helpers=finalizer_helpers
    )
    java_func_str = '\n'.join(java_funcs)
    java_static_func_str = '\n'.join(java_static_funcs)
    cmd_str = ' * %s' % ' '.join(cmd)

    class_str = class_template.format(
        cmd=cmd_str,
        lib_name=lib_name,
        c_source_path=c_source_path,
        sys_lib=sys_lib,
        var_defs=var_defs,
        enum_def=enum_def,
        class_init=class_init,
        static_functions=java_static_func_str,
        functions=java_func_str
    )
    if not to_path:
        print(class_str)
    else:
        with open(to_path + os.path.sep + ("NFI%sSupport.java" % lib_name), 'w') as fp:
            fp.write(class_str)

if __name__ == "__main__":
    import sys
    from argparse import ArgumentParser
    default_rt_path = [
        '..',
        'graalpython', 
        'com.oracle.graal.python', 
        'src', 'com', 'oracle', 'graal', 'python', 
        'runtime']
    to_path = os.path.abspath(os.path.join(os.path.dirname(__file__), *default_rt_path))
    parser = ArgumentParser()
    parser.add_argument('-name', required=True, help="Name of the generated NFI<name>Support class")
    parser.add_argument('-cpath', required=True, help="Path to the c file that contains nfi tags")
    parser.add_argument('-lib', required=True, help="The name of the native library <lib>.so")
    parser.add_argument('-to', default=to_path, help="Path of the generated class <to>/NFI<name>Support.java")
    parsed_args = parser.parse_args(sys.argv[1:])

    generate_nfi_support_class(sys.argv, parsed_args.name, parsed_args.cpath, parsed_args.lib, parsed_args.to)
