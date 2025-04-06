# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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


# Simplistic fuzzer testing interactions between slots, magic methods and builtin functions
#
# How to use:
#
# Run the fuzzer as follows:
#
# python3 slot_fuzzer.py --graalpy /path/to/bin/graalpy --cpython /path/to/bin/python3 --iterations 100
#
# It is recommended to use debug build of CPython, so that wrong usages of the C API fail the CPython run,
# and we do not even run such bogus tests on GraalPy.
#
# Triaging a failed test, for example, test number 42:
#   cpython42.out is the CPython output
#   graalpy42.out is GraaPy output -> compare the two using some diff tool
#   test42.py is the self-contained test, running it again is as simple as:
#           `/path/to/bin/graalpy test42.py` or `/path/to/bin/python3 test42.py`
#           the test itself compiles and loads the native extension
#   test42.c is the native extension code
#   backup test42.py and manually reduce the code in it to get a smaller reproducer suitable for debugging
#
# Adding a new slot:
#   - add example implementations to "SLOTS" and "MAGIC"
#       - single line implementation is assumed to be an expression and will be transformed to a proper function body
#       - multiline implementation is taken as-is
#   - add tests to 'slots_tester' function


import os.path
import sys
import dataclasses
import random
import itertools
import subprocess
import textwrap
import time
from argparse import ArgumentParser
from collections import defaultdict

# language=python
SLOTS_TESTER = '''
import sys
import re
import traceback
import operator
def slots_tester(Klass, other_klasses):
    def normalize_output(text):
        return re.sub(r'object at 0x[0-9a-fA-F]+', '', text)

    def test(fun, name):
        try:
            print(f'{name}:', end='')
            result = repr(fun())
            result = normalize_output(result)
            print(result)
        except Exception as e:
            if '--verbose' in sys.argv or '-v' in sys.argv:
                traceback.print_exc()
            else:
                print(type(e))

    def test_dunder(obj, fun_name, *args):
        # avoid going through tp_getattr/o, which may be overridden to something funky
        args_str = normalize_output(','.join([repr(x) for x in [obj, *args]]))
        test(lambda: type(obj).__dict__[fun_name](obj, *args), f"{fun_name} via class dict for {args_str}")
        test(lambda: getattr(obj, fun_name)(*args), f"{fun_name} for {args_str}")

    def write_attr(obj, attr, value):
        if attr == 'foo':
            obj.foo = value
        elif attr == 'bar':
            obj.bar = value

    obj = Klass()
    test(lambda: bool(obj), "bool(obj)")
    test(lambda: len(obj), "len(obj)")
    test_dunder(obj, '__bool__')
    test_dunder(obj, '__len__')
    test(lambda: obj.foo, "obj.foo")
    test(lambda: obj.bar, "obj.bar")

    test(lambda: write_attr(obj, 'foo', 42), "obj.foo = 42")
    test(lambda: obj.foo, "obj.foo")
    test(lambda: write_attr(obj, 'foo', 'hello'), "obj.foo = 'hello'")
    test(lambda: obj.foo, "obj.foo")

    test(lambda: write_attr(obj, 'bar', 42), "obj.bar = 42")
    test(lambda: obj.foo, "obj.foo")
    test(lambda: obj.bar, "obj.bar")

    test_dunder(obj, '__bool__')
    test_dunder(obj, '__len__')
    test_dunder(obj, '__getattr__', 'bar')
    test_dunder(obj, '__getattribute__', 'bar')
    test_dunder(obj, '__setattr__', 'foo', 11)
    test_dunder(obj, '__getattr__', 'foo')
    test_dunder(obj, '__delattr__', 'foo')
    test_dunder(obj, '__getattr__', 'foo')
    test(lambda: obj.foo, "obj.foo")

    class Dummy1:
        pass

    obj = Klass()
    owner = Dummy1()
    test_dunder(obj, '__get__', owner)
    test_dunder(obj, '__set__', owner, 42)
    test_dunder(obj, '__get__', owner)
    test_dunder(obj, '__delete__', owner)
    test_dunder(obj, '__get__', owner)

    class WithDescr:
        descr = Klass()
        def assign(self): self.descr = 42
        def del_descr(self): del self.descr

    obj = WithDescr()
    test(lambda: obj.descr, "obj.descr")
    test(lambda: obj.assign(), "obj.assign()")
    test(lambda: obj.descr, "obj.descr")
    test(lambda: obj.del_descr(), "obj.del_descr()")
    test(lambda: obj.descr, "obj.descr")

    obj = Klass()
    test(lambda: obj[42], "obj[42]")
    test(lambda: obj['hello'], "obj['hello']")
    test(lambda: obj[-1], "obj[-1]")
    test_dunder(obj, '__getitem__', 42)
    test_dunder(obj, '__getitem__', -1)
    test_dunder(obj, '__getitem__', 'hello')

    other_objs = [K() for K in other_klasses] + [42, 3.14, 'string', (1,2,3)]
    for obj2 in other_objs:
        obj1 = Klass()
        for op in [operator.add, operator.mul, operator.lt, operator.le, operator.eq,
                   operator.ne, operator.gt, operator.ge, operator.concat]:
            test(lambda: op(obj1, obj2), f"{op.__name__}: {Klass}, {type(obj2)}")
            test(lambda: op(obj2, obj1), f"{op.__name__}: {type(obj2)}, {Klass}")

        for dunder in ["__add__", "__radd__", "__mul__", "__rmul__", "__lt__", "__le__", "__eq__", "__ne__", "__gt__", "__ge__"]:
            test_dunder(obj1, dunder, obj2)
            test_dunder(obj2, dunder, obj1)

    def safe_hashattr(x, name):
        try:
            return hasattr(x, name)
        except:
            return False

    if safe_hashattr(Klass(), '__hash__'):
        if Klass().__hash__ is None:
            print("Klass().__hash__ is None")
        else:
            # just check presence/absence of exception
            def test_hash():
                hash(Klass())
                return 'dummy result'
            test(test_hash, '__hash__')
    else:
        print("Klass().__hash__ does not exist")
'''

# language=Python
EXT_COMPILER = '''
import sys
from pathlib import Path
import warnings
DIR = Path(__file__).parent.absolute()

with warnings.catch_warnings(action="ignore"):
    from distutils.core import setup, Extension

def compile_ext(name):
    source_file = name + '.c'
    verbosity = '--verbose' if '-v' in sys.argv else '--quiet'
    module = Extension(name, sources=[str(source_file)])
    args = [verbosity, 'build', 'install_lib', '-f', f'--install-dir={DIR}', 'clean']
    with warnings.catch_warnings(action="ignore"):
        setup(
            script_name='setup',
            script_args=args,
            name=name,
            version='1.0',
            description='',
            ext_modules=[module]
        )
'''


def write_all(filename, text):
    with open(filename, 'w+') as f:
        f.write(text)


@dataclasses.dataclass
class Slot:
    group: str | None
    name: str
    decl: str
    impls: list[str]

    def get_name(self, prefix) -> str:
        return prefix + '_' + self.name

    def impl(self, name_prefix, body):
        if body is None:
            return ''
        decl = self.decl.replace("$name$", self.get_name(name_prefix))
        if 'return' not in body:
            if 'Py_RETURN' in body:
                return f"{decl} {{ {body}; }}"
            else:
                return f"{decl} {{ return {body}; }}"
        else:
            b = '\n'.join(['  ' + line for line in body.split('\n')])
            return f"{decl} {{\n{b}\n}}"

    def tp_decl(self, name_prefix):
        return f"  {{ Py_{self.name}, {self.get_name(name_prefix)} }}"


NO_GROUP = 'top'

# language=C
C_SOURCE_HEADER = '''
#include <Python.h>

PyObject *global_stash1;
PyObject *global_stash2;
'''

SLOTS = [
    Slot('tp_as_number', 'nb_bool', 'int $name$(PyObject* self)', ['1', '0', None]),
    Slot('tp_as_number', 'nb_add', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(0)', None]),
    Slot('tp_as_number', 'nb_multiply', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(1)', None]),
    Slot('tp_as_sequence', 'sq_length', 'Py_ssize_t $name$(PyObject* self)', ['0', '1', '42', None]),
    Slot('tp_as_sequence', 'sq_item', 'PyObject* $name$(PyObject* self, Py_ssize_t index)', ['Py_NewRef(self)', 'PyLong_FromSsize_t(index + 1)', None]),
    Slot('tp_as_sequence', 'sq_concat', 'PyObject* $name$(PyObject* self, PyObject *other)', ['Py_NewRef(self)', 'PyLong_FromLong(10)', None]),
    Slot('tp_as_sequence', 'sq_repeat', 'PyObject* $name$(PyObject* self, Py_ssize_t count)', ['Py_NewRef(self)', 'PyLong_FromLong(count)', None]),
    Slot('tp_as_mapping', 'mp_length', 'Py_ssize_t $name$(PyObject* self)', ['0', '1', '42', None]),
    Slot('tp_as_mapping', 'mp_subscript', 'PyObject* $name$(PyObject* self, PyObject* key)', ['Py_RETURN_FALSE', 'Py_NewRef(key)', None]),
    Slot(NO_GROUP, 'tp_getattr', 'PyObject* $name$(PyObject* self, char *name)', ['Py_RETURN_NONE', 'Py_RETURN_FALSE', 'Py_NewRef(self)', None,
        '''
            if (global_stash1 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash1);
            return global_stash1;
        ''']),
    Slot(NO_GROUP, 'tp_getattro', 'PyObject* $name$(PyObject* self, PyObject *name)', ['Py_RETURN_NONE', 'Py_RETURN_TRUE', 'Py_NewRef(self)', 'Py_NewRef(name)', None,
       '''
            if (global_stash1 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash1);
            return global_stash1;
       ''']),
    Slot(NO_GROUP, 'tp_setattro', 'int $name$(PyObject* self, PyObject *name, PyObject *value)', ['0', None,
        '''
            Py_IncRef(value);
            Py_XDECREF(global_stash1);
            global_stash1 = value;
            return 0;
        ''']),
    # Disabled due to incompatibilities with Carlo Verre hack in some very specific corner cases
    # Slot(NO_GROUP, 'tp_setattr', 'int $name$(PyObject* self, char *name, PyObject *value)', ['0', None,
    #     '''
    #         Py_IncRef(value);
    #         Py_XDECREF(global_stash1);
    #         global_stash1 = value;
    #         return 0;
    #     ''']),
    Slot(NO_GROUP, 'tp_descr_get', 'PyObject* $name$(PyObject* self, PyObject* key, PyObject* type)', ['Py_RETURN_NONE', 'Py_NewRef(key)', None,
        '''
            if (global_stash2 == NULL) Py_RETURN_NONE;
            Py_IncRef(global_stash2);
            return global_stash2;
        ''']),
    Slot(NO_GROUP, 'tp_descr_set', 'int $name$(PyObject* self, PyObject* key, PyObject* value)', ['0', None,
        '''
            Py_IncRef(value);
            Py_XDECREF(global_stash2);
            global_stash2 = value;
            return 0;
        ''']),
    Slot(NO_GROUP, 'tp_hash', 'Py_hash_t $name$(PyObject* self)', ['0', None, '42', '-2', 'PY_SSIZE_T_MAX']),
    Slot(NO_GROUP, 'tp_richcompare', 'PyObject* $name$(PyObject* v, PyObject* w, int op)', [
        'Py_RETURN_FALSE', 'Py_RETURN_TRUE', None, 'Py_RETURN_NONE', 'Py_RETURN_NOTIMPLEMENTED',
        *[f'''
            if (op == {ops_true[0]} || op == {ops_true[1]}) Py_RETURN_TRUE;
            if (op == {ops_false[0]} || op == {ops_false[1]}) Py_RETURN_FALSE;
            Py_RETURN_NOTIMPLEMENTED;
          ''' for ops_true in itertools.combinations(range(0, 6), 2)
              for ops_false in itertools.combinations(set(range(0, 6)) - set(ops_true), 2)]]),
]

PY_GLOBALS = '''
global_dict1 = dict()
global_descr_val = None
'''

MAGIC = {
    '__bool__(self)': ['True', 'False', None],
    '__add__(self, other)': ['self', '"__add__result"', "NotImplemented", "str(other)", None],
    '__mul__(self, other)': ['self', '"__mul__result"', "NotImplemented", "repr(other)", None],
    '__radd__(self, other)': ['self', '"__radd__result"', "NotImplemented", "'radd' + str(other)", None],
    '__len__(self)': ['0', '1', '42', None],
    '__getattribute__(self,name)': ['name', '42', 'global_dict1[name]', None],
    '__getattr__(self,name)': ['name+"abc"', 'False', 'global_dict1[name]', None],
    '__get__(self,obj,objtype=None)': ['obj', 'True', 'global_descr_val', None],
    '__set__(self,obj,value)': [None,
                                '''
                                global global_descr_val
                                global_descr_val = value
                                return None
                                '''],
    '__setattr__(self,name,value)': [None,
                                    '''
                                    global global_dict1 # not using self._dict, because attr lookup can be something funky...
                                    global_dict1[name] = value
                                    return None
                                    '''],
    '__delattr__(self,name,value)': [None,
                                     '''
                                     global global_dict1
                                     del global_dict1[name]
                                     return None
                                     '''],
    '__getitem__(self, index)': [None, 'True', 'repr(index)'],
    '__hash__(self)': [None, '1', '-2', '44', '123456788901223442423234234'],
    **{name + '(self, other)': [None, 'True', 'False', 'NotImplemented']
       for name in ['__lt__', '__le__', '__eq__', '__ne__', '__gt__', '__ge__']}
}


def magic_impl(magic_key, magic_body):
    if magic_body is None:
        return ''
    if 'return' not in magic_body:
        return f" def {magic_key}: return {magic_body}"
    else:
        b = '\n'.join(['    ' + line for line in magic_body.split('\n')])
        return f" def {magic_key}:\n{b}"


def magic_combinations_choose_random():
    return list([magic_impl(key, impls[rand.randint(0, len(impls)-1)]) for (key, impls) in MAGIC.items()])


def managed_class_impl(name, bases, magic_impls):
    bases_tuple = '' if not bases else '(' + ','.join(bases) + ')'
    code = f"class {name}{bases_tuple}:\n"
    impls = [x for x in magic_impls if x]
    if impls:
        code += '\n'.join(impls)
    else:
        code += '  pass'
    return code


def native_heap_type_impl(name, mod_name, slots):
    slot_defs = '\n'.join([s.impl(name, body) for (s, body) in slots if body])
    slot_decls = ',\n'.join([s.tp_decl(name) for (s, body) in slots if body] + ["{0}"])
    return textwrap.dedent(f'''
        {slot_defs}
        PyType_Slot {name}_slots[] = {{
            {slot_decls}
        }};
        PyType_Spec {name}_spec = {{ "{mod_name}.{name}", sizeof(PyHeapTypeObject), 0, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, {name}_slots }};
        static PyObject* create_{name}(PyObject* module, PyObject* args) {{
            PyObject* bases;
            if (!PyArg_ParseTuple(args, "O", &bases))
                return NULL;
            PyObject* result = PyType_FromModuleAndSpec(module, &{name}_spec, bases);
            Py_XINCREF(result);
            return result;
        }}
    ''')


SLOT_GROUP_TYPES = {
    'tp_as_number': 'PyNumberMethods',
    'tp_as_sequence': 'PySequenceMethods',
    'tp_as_mapping': 'PyMappingMethods',
}


def native_static_type_impl(name, mod_name, slots):
    slot_defs = '\n'.join([s.impl(name, body) for (s, body) in slots if body])
    groups = defaultdict(lambda: list())
    for (s, body) in slots:
        if body:
            groups[s.group].append(s)

    slots_decl = ''
    slots_groups_decl = ''
    for group in groups.keys():
        group_decls = ''
        if group != NO_GROUP:
            slots_decl += f'.{group} = &{name}_{group},\n'
            group_decls = f'{SLOT_GROUP_TYPES[group]} {name}_{group} = {{\n'

        for s in groups[group]:
            group_decls += f'.{s.name} = &{s.get_name(name)},\n'

        if group != NO_GROUP:
            group_decls += '};\n'
            slots_groups_decl += group_decls
        else:
            slots_decl += group_decls

    slots_decl = textwrap.indent(slots_decl, '        ')
    slots_groups_decl = textwrap.indent(slots_groups_decl, '        ')

    return textwrap.dedent(f'''
        {slot_defs}

        {slots_groups_decl}

        static PyTypeObject CustomType_{name} = {{
            .ob_base = PyVarObject_HEAD_INIT(NULL, 0)
            .tp_name = "{mod_name}.{name}",
            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE,
            .tp_new = PyType_GenericNew,
            {slots_decl}
        }};

        static PyObject* create_{name}(PyObject* module, PyObject* args) {{
            if (PyType_Ready(&CustomType_{name}) < 0)
                return NULL;
            Py_INCREF(&CustomType_{name});
            return (PyObject*) &CustomType_{name};
        }}
    ''')


def slot_combinations_choose_random():
    return list([(slot, slot.impls[rand.randint(0, len(slot.impls) - 1)]) for slot in SLOTS])


def choose_random(l):
    return l[rand.randint(0, len(l)-1)]


parser = ArgumentParser()
parser.add_argument('--graalpy', dest='graalpy', required=True)
parser.add_argument('--cpython', dest='cpython', required=True)
parser.add_argument('--iterations', type=int, default=200)
parser.add_argument('--output-dir', dest='output_dir',
                    help='Where to store generated test cases and logs from test runs. If not provided the program uses "experiment-{timestamp}"')
parser.add_argument('--seed', type=int, default=0)
args, _ = parser.parse_known_args()

graalpy = args.graalpy
cpython = args.cpython
output_dir = args.output_dir if args.output_dir else f'./experiment-{int(time.time())}'

if os.path.exists(output_dir):
    if not os.path.isdir(output_dir):
        print(f"Error: output directory is existing file")
        sys.exit(1)
    elif os.listdir(output_dir):
        print(f"Error: output directory is not empty")
        sys.exit(1)
else:
    os.mkdir(output_dir)


log_file = open(os.path.join(output_dir, 'log'), 'w+')


def log(*args, **kwargs):
    print(*args, **kwargs)
    print(*args, **kwargs, file=log_file)


seed = args.seed if args.seed != 0 else random.randrange(sys.maxsize)
log(f"Using seed: {seed}")
log(f"Output directory: {output_dir}")
log(f"Iterations: {args.iterations}")
log()
log()
rand = random.Random(seed)

for test_case_idx in range(args.iterations):
    classes_count = max(3, rand.randint(1, 5))  # Make it more likely that it's 3...
    classes = []
    test_module_name = f"test{test_case_idx}"
    c_source = C_SOURCE_HEADER
    py_source = SLOTS_TESTER + PY_GLOBALS
    native_classes = []
    for i in range(classes_count):
        base = choose_random(classes) if classes else 'object'
        if rand.randint(0, 1) == 1:
            class_name = 'Native' + str(i)
            native_classes.append(class_name)
            if i == 0 and rand.randint(0, 2) < 2:
                c_source += native_static_type_impl(class_name, test_module_name, slot_combinations_choose_random())
            else:
                c_source += native_heap_type_impl(class_name, test_module_name, slot_combinations_choose_random())
            c_source += '\n'
            py_source += f"{class_name} = {test_module_name}.create_{class_name}(({base}, ))\n"
        else:
            class_name = 'Managed' + str(i)
            py_source += managed_class_impl(class_name, (base,), magic_combinations_choose_random())
            py_source += '\n'

        classes.append(class_name)

    if native_classes:
        compile_ext = EXT_COMPILER
        compile_ext += f"compile_ext('{test_module_name}')\n\n"
        py_source = compile_ext + f"import {test_module_name}\n\n" + py_source

        c_source += "static struct PyMethodDef test_module_methods[] = {\n"
        for clazz in native_classes:
            c_source += f'  {{"create_{clazz}", (PyCFunction)create_{clazz}, METH_VARARGS, ""}},\n'
        c_source += "  {NULL, NULL, 0, NULL}\n"
        c_source += "};"

        c_source += textwrap.dedent(f'''
            static PyModuleDef test_module = {{
                PyModuleDef_HEAD_INIT,
                "{test_module_name}",
                "",
                -1,
                test_module_methods,
                NULL, NULL, NULL, NULL
            }};

            PyMODINIT_FUNC
            PyInit_{test_module_name}(void) {{
                return PyModule_Create(&test_module);
            }}
        ''')

        c_filename = f"{test_module_name}.c"
        write_all(os.path.join(output_dir, c_filename), c_source)

    py_source += '\n\n# ===========\n'
    py_source += '# Tests:\n\n'
    py_source += 'all_classes = ' + ','.join(classes) + '\n\n'
    for klass in classes:
        py_source += f'print("\\n\\n\\nTesting {klass}\\n")\n'
        py_source += f'slots_tester({klass}, all_classes)\n'

    py_filename = f"test{test_case_idx}.py"
    write_all(os.path.join(output_dir, py_filename), py_source)

    log(f"Test case {test_case_idx:5}: ", end='')
    cpy_output_path = os.path.join(output_dir, f"cpython{test_case_idx}.out")
    try:
        cpython_out = subprocess.check_output([cpython, py_filename], stderr=subprocess.STDOUT, cwd=output_dir).decode()
        write_all(cpy_output_path, cpython_out)
    except subprocess.CalledProcessError as e:
        output = e.output.decode()
        log(f"CPython error;     ⚠️       {os.path.abspath(cpy_output_path)}")
        write_all(cpy_output_path, output)
        continue

    log("CPython succeeded; ", end='')
    gpy_output_path = os.path.join(output_dir, f"graalpy{test_case_idx}.out")
    try:
        graalpy_out = subprocess.check_output([graalpy, '--vm.ea', '--vm.esa', py_filename], stderr=subprocess.STDOUT, cwd=output_dir).decode()
        write_all(gpy_output_path, graalpy_out)
    except subprocess.CalledProcessError as e:
        output = e.output.decode()
        log(f"❌ fatal error in GraalPy    {os.path.abspath(gpy_output_path)}")
        write_all(gpy_output_path, output)
        continue

    if cpython_out != graalpy_out:
        log(f"❌ output does not match!    {os.path.abspath(cpy_output_path)} {os.path.abspath(gpy_output_path)}")
    else:
        log("✅ GraalPy succeeded")
