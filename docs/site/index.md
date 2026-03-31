---
layout: base
---
<div class="language-home-surface">
<section class="language-home-hero">
  <div>
    <div class="hi">
      <div class="container">
        <div class="hi__row">
          <div class="hi__body">
            <h4 class="hi__title">High-performance embeddable Python 3 runtime</h4>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section id="graalpy-choose-path" class="language-home content-section">
  <div class="wrapper">
    <div class="container">
      <div class="languages__example-row">
        <div class="languages__example-card languages__example-card-primary" tabindex="0">
          <a href="{{ '/jvm-developers/' | relative_url }}" title="Embed Python in JVM Applications" class="languages__example-card-link"></a>
          <p class="languages__example-card-title">Embed Python in JVM Applications</p>
          <div class="cta-code">
{%- highlight java -%}
import org.graalvm.polyglot.Context;

try (Context context = Context.newBuilder()
        .allowAllAccess(true) // See documentation for options
        .build()) {
    context.eval("python", "print('Hello from GraalPy!')");
}
{%- endhighlight -%}
          </div>
          <ul class="language__benefits-list">
            <li>Use Python packages directly in Java, Kotlin, or Scala</li>
            <li>Upgrade Jython projects to Python 3</li>
            <li>Control permissions for Python code from full host access to fully sandboxed</li>
            <li>Script JVM applications with Python</li>
          </ul>
        </div>
        <div class="languages__example-card" tabindex="0">
          <a href="{{ '/python-developers/' | relative_url }}" title="Build and Run Python Applications" class="languages__example-card-link"></a>
          <p class="languages__example-card-title">Build and Run Python Applications</p>
          <div class="cta-code">
{%- highlight bash -%}
$ pyenv install graalpy-{{ site.language_version }}
$ pyenv shell graalpy-{{ site.language_version }}

$ python3 -c "import sys; print(sys.implementation.name)"
graalpy
$ python3 -m timeit "'-'.join(str(n) for n in range(100))"
500000 loops, best of 5: 757 nsec per loop
{%- endhighlight -%}
          </div>
          <ul class="language__benefits-list">
            <li>Speed up Python applications with the Graal JIT</li>
            <li>Compatible with many Python AI and data science packages</li>
            <li>Package Python applications as a single binary</li>
            <li>Use Java libraries in Python applications</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</section>

<div class="hi__image" aria-hidden="true">
  <img src="{{ '/assets/img/home/python-logo.svg' | relative_url }}" alt="">
</div>

</div>
