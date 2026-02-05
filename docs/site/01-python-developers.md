---
layout: docs
title: Python Developers
permalink: docs/python-developers/
docs_index: python-developers
nav_order: 1
---

# GraalPy for Python Developers

**You want to use GraalPy instead of the standard Python from python.org.**

Install GraalPy on your machine and use it like any Python interpreter.
You get better performance, the ability to compile to native binaries, and access to the GraalVM ecosystem.

{% gfm_docs ../user/Version-Compatibility.md %}

{% gfm_docs ../user/Platform-Support.md %}

These guides cover everything you need to know:

{% gfm_docs ../user/Standalone-Getting-Started.md %}
{% gfm_docs ../user/Python-Runtime.md %}
{% gfm_docs ../user/Python-Standalone-Applications.md %}
{% gfm_docs ../user/Native-Extensions.md %}
{% gfm_docs ../user/Performance.md %}
{% gfm_docs ../user/Tooling.md %}

<h3 id="python-context-options">
<a href="#python-context-options" class="anchor-link">Python Context Options</a>
</h3>
Below are the options you can set on contexts for GraalPy.
{% python_options ../../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonOptions.java %}

{% gfm_docs ../user/Test-Tiers.md %}
{% gfm_docs ../user/Troubleshooting.md %}

{% copy_assets ../user/assets %}