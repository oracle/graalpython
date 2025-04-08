# Configuration file for the Sphinx documentation builder.
#
# This file only contains a selection of the most common options. For a full
# list see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

# -- Path setup --------------------------------------------------------------

# If extensions (or modules to document with autodoc) are in another directory,
# add these directories to sys.path here. If the directory is relative to the
# documentation root, use os.path.abspath to make it absolute, like shown here.
#
# import os
# import sys
# sys.path.insert(0, os.path.abspath('.'))

import sys
import os
import re
import datetime

# -- Project information -----------------------------------------------------

project = "HPy"
copyright = "2019-{}, HPy Collective".format(datetime.date.today().year)
author = "HPy Collective"

# The full version, including alpha/beta/rc tags
release = "0.9"


# -- General configuration ---------------------------------------------------

# Add any Sphinx extension module names here, as strings. They can be
# extensions coming with Sphinx (named 'sphinx.ext.*') or your custom
# ones.
extensions = [
    "sphinx.ext.autosectionlabel",
    "sphinx_c_autodoc",
    "sphinx_c_autodoc.viewcode",
    "sphinx_rtd_theme",
]

autosectionlabel_prefix_document = True

# Add any paths that contain templates here, relative to this directory.
templates_path = ["_templates"]

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
# This pattern also affects html_static_path and html_extra_path.
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

# -- autodoc -----------------------------------------------------------------

autodoc_member_order = "bysource"

# -- sphinx_c_autodoc --------------------------------------------------------

c_autodoc_roots = [
    "../hpy/devel/include",
    "../hpy/devel/src",
    "../hpy/tools",
]


def pre_process(app, filename, contents, *args):
    # FIXME: the missing typedef for 'HPy' causes the file formatting to fail
    if filename.endswith('public_api.h'):
        contents[0] = '#include "../../devel/include/hpy.h"\n' + contents[0]
    if filename.endswith('autogen_ctx.h'):
        contents[0] = 'typedef int HPy;' + contents[0]

    # remove HPyAPI_HELPER so that the sphinx-c-autodoc and clang
    # find and render the API functions
    contents[:] = [
        re.sub(r"^(HPyAPI_HELPER|HPyAPI_INLINE_HELPER|HPy_ID\(\d+\))", r"", part, flags=re.MULTILINE)
        for part in contents
    ]


def setup(app):
    app.connect("c-autodoc-pre-process", pre_process)


def setup_clang():
    """
    Make sure clang is set up correctly for the sphinx_c_autodoc extension.

    The Python clang package requires a matching libclang*.so. Our
    ``doc/requirements.txt`` file specifies ``clang==10.0.1``, so we need
    ``libclang-10``.

    On Ubuntu 20.04 (and possibly later), this can be installed with
    ``apt install libclang-10-dev`` and the Python clang package finds the
    appropriate .so automatically.

    However, ReadTheDocs has an older Ubuntu that only packages libclang-6.0.
    The Python ``clang==10.0.1`` packages supports this older .so, but
    needs to be explicitly told where to find it.

    If you encounter issues with a local build, please start by checking that
    the ``libclang-10-dev`` system package or equivalent is installed.
    """
    from clang import cindex
    if 'READTHEDOCS' in os.environ:
        # TODO: Hopefully we can remove this setting of the libclang path once
        #       readthedocs updates its docker image to Ubuntu 20.04 which
        #       supports clang-10 and clang-11.
        cindex.Config.set_library_file(
            "/usr/lib/x86_64-linux-gnu/libclang-6.0.so.1"
        )
    elif sys.platform == "darwin":
        cindex.Config.set_library_file(
            "/Library/Developer/CommandLineTools/usr/lib/libclang.dylib"
        )


setup_clang()

# -- Options for HTML output -------------------------------------------------

# The theme to use for HTML and HTML Help pages.  See the documentation for
# a list of builtin themes.
#
html_theme = "sphinx_rtd_theme"

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
html_static_path = ["_static"]

# most the the code examples will be in C, let's make it the default
highlight_language = 'C'
