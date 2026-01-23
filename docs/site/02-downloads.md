---
layout: base
title: Downloads
permalink: downloads/
published: false
---

<section class="content-section download-homescreen">
  <div class="wrapper">
    <div>
      <div class="container">
        <h3 class="truffle__subtitle">Download GraalPy from Maven Central</h3>
        <div>
          <h5 class="download-text">
            Have a Java application?
          </h5>
          <h5 class="download-text">
You can extend it with Python code or leverage packages from the Python ecosystem. GraalPy is available on <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/python" target="_blank">Maven Central</a> and can be added as a dependency to your Maven or Gradle project as — see <a href="#" target="_blank">setup instructions</a>.
          </h5>
        </div>
      </div>
    </div>
  </div>
</section>
<!-- GraalPy Standalone Distributions -->
<section class="content-section">
  <div class="wrapper">
    <div class="langstarter">
      <div class="container">
        <h3 class="langstarter__title">Download Standalone Distributions of GraalPy</h3>
          <div>
          <h5 class="download-text">
            Do you want to test your Python application or package on GraalPy?
          </h5>
          <h5 class="download-text">
            To test Python code on GraalPy, a standalone distribution is available for different platforms and in two different kinds: <a href="{{ '/docs/#graalpy-distributions' | relative_url }}">Native</a> (for compact download and footprint) and <a href="{{ '/docs/#graalpy-distributions' | relative_url }}">JVM</a> (for full Java interoperability). We recommend the distributions based on Oracle GraalVM for best performance and advanced features (released under the <a target="_blank" href="https://www.oracle.com/downloads/licenses/graal-free-license.html">GFTC license</a>). Distributions based on GraalVM Community Edition (released under the OSI-approved <a target="_blank" href="https://github.com/oracle/graalpython/blob/master/LICENSE.txt">UPL license</a>) are available on <a href="https://github.com/oracle/graalpython/releases" target="_blank">GitHub</a>. Standalone distributions are also available via <a target="_blank" href="{{ 'docs#installing-graalpy' | relative_url }}">pyenv</a>, <a target="_blank" href="{{ 'docs#installing-graalpy' | relative_url }}">pyenv-win</a>, and <a target="_blank" href="https://github.com/actions/setup-python?tab=readme-ov-file#basic-usage">setup-python</a>:
          </h5>
        </div>
        <div class="languages__example-card">
          <div class="languages__example-box">
            <div class="languages__snippet">
              {%- highlight bash -%}
# Latest GraalPy release
pyenv install graalpy-{{ site.language_version }}
pyenv shell graalpy-{{ site.language_version }}

# Latest EA build of GraalPy
pyenv install graalpy-dev
pyenv shell graalpy-dev
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <div>
                <a target="_blank" href="https://github.com/pyenv/pyenv">pyenv</a> and <a target="_blank" href="https://pyenv-win.github.io/pyenv-win/">pyenv-win</a>
              </div>
            </div>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
              {%- highlight yml -%}
steps:
- uses: actions/checkout@v4
- uses: actions/setup-python@v5 
  with:
    python-version: 'graalpy-{{ site.language_version }}' 
- run: python my_script.py
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <a target="_blank" href="https://github.com/actions/setup-python?tab=readme-ov-file#basic-usage">
          <div>
             <img src="{{ '/assets/img/downloads-new/github-icon.svg' | relative_url }}" class="" alt="github icon">
          </div>
              </a>
            </div>
          </div>
        </div>
        <div class="languages__example-box">
          <div class="languages__snippet">
            <table class="centered">
              <tr>
                <th class="border-correct-3">Version</th>
                <th>Kind</th>
                <th>Linux (aarch64)</th>
                <th>Linux (amd64)</th>
                <th>macOS (aarch64)</th>
                <th class="border-correct-4">Windows (amd64)</th>
              </tr>
              <tr>
                <td rowspan="2">
                {{ site.language_version }}<br>
                <i><a href="https://github.com/oracle/graalpython/releases" target="_blank">other versions</a></i>
                </td>
                <td>Native</td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-linux-aarch64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-linux-amd64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-macos-aarch64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-windows-amd64.tar.gz"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
              </a></td>
              </tr>
              <tr>
                <td>JVM</td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-linux-aarch64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-linux-amd64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-macos-aarch64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-windows-amd64.tar.gz">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
              </a></td>
              </tr>
              <tr>
                <td class="border-correct-1" rowspan="2">Latest early access build</td>
                <td>Native</td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-linux-aarch64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-linux-amd64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-macos-aarch64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-windows-amd64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
              </a></td>
              </tr>
              <tr>
                <td>JVM</td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-linux-aarch64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-linux-amd64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-macos-aarch64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
                </a></td>
                <td class="border-correct-2"><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-windows-amd64.url">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_261_486)">
  <path d="M12 0C5.383 0 0 5.383 0 12C0 18.617 5.383 24 12 24C18.617 24 24 18.617 24 12C24 5.383 18.617 0 12 0Z" fill="#F29111"/>
  <path d="M7 19H17C17.553 19 18 18.552 18 18C18 17.448 17.553 17 17 17H7C6.447 17 6 17.448 6 18C6 18.552 6.447 19 7 19Z" fill="#09222E"/>
  <path d="M11.4693 14.78C11.6153 14.927 11.8073 15 11.9993 15C12.1913 15 12.3833 14.927 12.5293 14.78L15.7793 11.53C16.2513 11.06 15.9183 10.25 15.2493 10.25H12.9993V5.75C12.9993 5.198 12.5523 4.75 11.9993 4.75C11.4463 4.75 10.9993 5.198 10.9993 5.75V10.25H8.74927C8.08027 10.25 7.74727 11.06 8.21927 11.53L11.4693 14.78Z" fill="#09222E"/>
  </g>
  <defs>
  <clipPath id="clip0_261_486">
  <rect width="24" height="24" fill="white"/>
  </clipPath>
  </defs>
</svg>
              </a></td>
              </tr>
            </table>
          </div>
          <div class="example-logo-box">
             <img src="{{ '/assets/img/downloads-new/binary-icon.svg' | relative_url }}" class="" alt="binary icon">
          </div>
        </div>
        <div class="license-note centered"><i>GraalPy on Oracle GraalVM is free to use in production and free to redistribute, at no cost, under the <a target="_blank" href="https://www.oracle.com/downloads/licenses/graal-free-license.html">GraalVM Free Terms and Conditions</a>.</i></div>
      </div>
    </div>
  </div>
</section>
