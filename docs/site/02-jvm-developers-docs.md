---
layout: docs
permalink: jvm-developers/docs/
audience_identifier: jvm
title: Documentation
---

# Documentation for JVM Developers

**For JVM developers who need to use Python libraries from their JVM applications or migrate from legacy Jython code.**

You do not need to install GraalPy separately - you can use GraalPy directly in Java with Maven or Gradle.
This lets you call Python libraries like NumPy, pandas, or any PyPI package from your Java application.
GraalPy also provides a migration path from Jython 2.x to Python 3.x with better performance and maintained Java integration capabilities.

{% gfm_docs ../user/Version-Compatibility.md %}

{% gfm_docs ../user/Platform-Support.md %}

These guides cover everything you need to know:

{% gfm_docs ../user/Embedding-Getting-Started.md %}
{% gfm_docs ../user/Embedding-Build-Tools.md %}
{% gfm_docs ../user/Embedding-Permissions.md %}
{% gfm_docs ../user/Embedding-Native-Extensions.md %}
{% gfm_docs ../user/Interoperability.md %}
{% gfm_docs ../user/Native-Images-with-Python.md %}
{% gfm_docs ../user/Python-on-JVM.md %}

<h3 id="python-context-options">
<a href="#python-context-options" class="anchor-link">Python Context Options</a>
</h3>
Below are the options you can set on contexts for GraalPy.
{% python_options ../../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonOptions.java %}

{% gfm_docs ../user/Test-Tiers.md %}
{% gfm_docs ../user/Troubleshooting.md %}

{% copy_assets ../user/assets %}
