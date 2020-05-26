This document is about parsing python files in GraalPython implementation. 
It describes way how we obtain Truffle tree from a source.

Creating Truffle tree for a python source has two phases. The first one creates  
simple syntax tree (SST) and scope tree, the second phase transforms the SST to 
the Truffle tree and for the transformation we need scope tree. The scope tree 
contains scope locations for variable and function definitions and information 
about scopes. The simple syntax tree contains nodes mirroring the source. 
Comparing SST and Truffle tree, the SST is much smaller. It contains just the nodes 
representing the source in a simple way. One SST node is usually translated 
to many Truffle nodes. 

The simple syntax tree can be created in two ways. With ANTLR parsing 
or deserialization from appropriate `*.pyc` file. In both cases together with 
scope tree. If there is no appropriate `.pyc` file for a source, then the source 
is parsed with ANTLR and result SST and scope tree is serialized to the `.pyc` file.  
The next time, we don't have to use ANTLR parser, because the result is already 
serialized in the `.pyc` file. So instead of parsing source file with ANTLR, 
we just deserialized SST and scope tree from the `.pyc` file.  The deserialization 
is much faster then source parsing with ANTLR. The deserialization needs ruffly 
just 30% of the time  that needs ANTLR parser. Of course the first run is little 
bit slower (we need to SST and scope tree save to the `.pyc` file). 

In the folder structure it looks like this:

```
top_folder
    __pycache__
         sourceA.graalpython.pyc
         sourceB.graalpython.pyc
    sourceA.py
    sourceB.py
    sub_folder
        __pycache__
            sourceX.graalpython.pyc
        sourceX.py
```

On the same directory level of a source code file, the `__pycache__` directory 
is created and in this directory are stored all `.*pyc` files from the same 
directory. There can be also files created with CPython, so user can see there 
also files with extension `*.cpython3-6.pyc` for example. 

The current implementation includes also copy of the original text into `.pyc' file. 
The reason is that we create from this Truffle Source object with path to the 
original source file, but we do not need to read the original `*.py` file, which 
speed up the process obtaining Truffle tree (we read just one file). 

The structure of a `.graalpython.pyc` file is this:

```
MAGIC_NUMBER
source text
binary data - scope tree
binary data - simple syntax tree
```

The serialized SST and scope tree is stored in Code object as well, attribute `code`

For example:
```
>>> def add(x, y):
... print('Running x+y')
... return x+y
...
>>> co = add.__code__
>>> co.co_code
b'\x01\x00\x00\x02[]K\xbf\xd1\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00 ...'
```

The creating `*.pyc` files can be canceled / allowed  in the same ways like in CPython:

 * evironment variable: PYTHONDONTWRITEBYTECODE - If this is set to a non-empty string, 
Python won’t try to write .pyc files on the import of source modules. 
 * command line option: -B, If given, Python won’t try to write .pyc files on 
the import of source modules.
 * in a code: setting attribute `dont_write_bytecode` of `sys` built in module


## Security
The serialization of SST and scope tree is hand written and during deserialization 
is not possible to load other classes then SSTNodes. It doesn't use Java serialization 
or other framework to serialize Java object. The main reason was performance. 
The performance can be maximize in this way. The next reason was the security.