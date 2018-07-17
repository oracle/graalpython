# Graal/Truffle-based implementation of Python

GraalVM provides an early-stage experimental implementation of Python. A primary
goal is to support SciPy and its constituent libraries. This Python
implementation currently aims to be compatible with Python 3.7, but it is a long
way from there, and it is very likely that any Python program that requires any
imports at all will hit something unsupported. At this point, the Python
implementation is made available for experimentation and curious end-users.

### Trying it

To try it, you can use the bundled releases from
[www.graalvm.org](https://www.graalvm.org/downloads/). For more information and
some examples of what you can do with it, check out the
[reference](https://www.graalvm.org/docs/reference-manual/languages/python/).

### Licensing

This Graal/Truffle-based implementation of Python is copyright (c) 2017, 2018
Oracle and/or its affiliates and is made available to you under the terms the
Universal Permissive License v 1.0 as shown at
[http://oss.oracle.com/licenses/upl](http://oss.oracle.com/licenses/upl). This
implementation is in part derived from and contains additional code from 3rd
parties, the copyrights and licensing of which is detailed in the
[LICENSE](LICENSE) and [3rd_party_licenses.txt](3rd_party_licenses.txt) files.
