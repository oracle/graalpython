---
layout: base
permalink: python-developers/
audience_identifier: python
---

<section class="content-section language-benefits">
  <div class="wrapper">
    <div class="langbenefits">
      <div class="container">
        <h3 class="langpage__title-02">Build and Run Python Applications with GraalPy</h3>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/compatibility.svg" | relative_url }}' alt="compatibility icon">
            </div>
            <div class="langbenefits__title">
              <h4>High-Performance Python</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Speed up Python applications with the Graal JIT compiler</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/binary-icon.svg" | relative_url }}' alt="binary icon">
            </div>
            <div class="langbenefits__title">
              <h4>Single-Binary Packaging</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Package Python applications as a <a href="/python/python-developers/docs/#python-standalone-applications">single binary</a></h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/home-code.svg" | relative_url }}' alt="code icon">
            </div>
            <div class="langbenefits__title">
              <h4>Java Interoperability</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Use Java libraries in Python applications</h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="content-section language-downloads" id="install-download">
  <script>
    async function resolveEarlyAccessDownload(event, urlFile) {
      event.preventDefault();
      try {
        const response = await fetch(urlFile);
        if (!response.ok) {
          throw new Error(`Failed to fetch ${urlFile}: ${response.status}`);
        }
        const artifactUrl = (await response.text()).trim();
        if (!artifactUrl) {
          throw new Error(`Empty artifact URL in ${urlFile}`);
        }
        window.open(artifactUrl, '_blank', 'noopener');
      } catch (error) {
        window.open(urlFile, '_blank', 'noopener');
      }
    }
  </script>
  <div class="wrapper">
    <div class="langstarter">
      <div class="container">
        <h3 class="langstarter__title">Install or Download</h3>
        <div>
          <h5 class="download-text">
          GraalPy is available for multiple platforms in two variants: <strong class="language-downloads__variant-native">Native</strong> (for a compact download size and smaller footprint) and <strong class="language-downloads__variant-jvm">JVM</strong> (for full Java interoperability). Distributions based on Oracle GraalVM provide the best performance and advanced features and are released under the <a href="https://www.oracle.com/downloads/licenses/graal-free-license.html">GFTC license</a>. Distributions based on GraalVM Community Edition, released under the OSI-approved <a href="https://opensource.org/licenses/UPL">UPL license</a>, are available on <a href="https://github.com/oracle/graalpython/releases">GitHub</a>.
          See <a href="/python/python-developers/docs/#choosing-a-graalpy-distribution">Choosing a GraalPy Distribution</a> for guidance on selecting the appropriate runtime.
          </h5>
        </div>
        <div class="languages__example-card">
          <div class="languages__example-box">
            <div class="languages__snippet">
{%- highlight bash -%}
# Latest GraalPy release
pyenv install graalpy-{{ site.language_version }}
pyenv shell graalpy-{{ site.language_version }}

# On Windows (pyenv-win), provide platform-specific names
pyenv install graalpy-{{ site.language_version }}-windows-amd64
pyenv shell graalpy-{{ site.language_version }}-windows-amd64

# Latest development build of GraalPy
pyenv install graalpy-dev
pyenv shell graalpy-dev
{%- endhighlight -%}
            </div>
            <div class="example-logo-box example-logo-box--install-badge">
              <div>
                <a href="https://github.com/pyenv/pyenv" target="_blank" rel="noopener noreferrer">
                  <img src="{{ '/assets/img/python/pyenv-badge.svg' | relative_url }}" class="language-install-badge" alt="pyenv badge">
                </a>
              </div>
            </div>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
{%- highlight bash -%}
# Install GraalPy with uv (uv selects GraalPy by Python language version)
uv python install graalpy-3.12

# Create a virtual environment with GraalPy
uv venv --python graalpy-3.12
{%- endhighlight -%}
            </div>
            <div class="example-logo-box example-logo-box--install-badge">
              <div>
                <a href="https://docs.astral.sh/uv" target="_blank" rel="noopener noreferrer">
                  <img src="{{ '/assets/img/python/uv-badge.svg' | relative_url }}" class="language-install-badge" alt="uv badge">
                </a>
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
             <img src="{{ '/assets/img/downloads/github-icon.svg' | relative_url }}" class="" alt="github icon">
          </div>
              </a>
            </div>
          </div>
          <!-- <div class="languages__example-box">
            <div class="languages__snippet">
              {%- highlight bash -%} -->
<!-- # Container images: latest GraalPy release -->
<!-- docker pull ghcr.io/graalvm/graalpy-community:{{ site.language_version }}
docker run --rm ghcr.io/graalvm/graalpy-community:{{ site.language_version }} python --version
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box example-logo-box--install-badge">
              <div>
                <img src="{{ '/assets/img/downloads/container-badge.svg' | relative_url }}" class="language-install-badge" alt="container images badge">
              </div>
            </div>
          </div> -->
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
                {{ site.language_version }}
                </td>
                <td>Native</td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-linux-aarch64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-linux-amd64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-macos-aarch64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-{{ site.language_version }}-windows-amd64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
              </tr>
              <tr>
                <td>JVM</td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-linux-aarch64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-linux-amd64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-macos-aarch64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://github.com/oracle/graalpython/releases/download/graal-{{ site.language_version }}/graalpy-jvm-{{ site.language_version }}-windows-amd64.tar.gz">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
              </a></td>
              </tr>
              <tr>
                <td class="border-correct-1" rowspan="2">Latest early access build</td>
                <td>Native</td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-linux-aarch64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-linux-amd64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-darwin-aarch64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-native-windows-amd64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
              </a></td>
              </tr>
              <tr>
                <td>JVM</td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-linux-aarch64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-linux-amd64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-darwin-aarch64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
                </a></td>
                <td class="border-correct-2"><a target="_blank" href="https://raw.githubusercontent.com/graalvm/graal-languages-ea-builds/refs/heads/main/graalpy/versions/latest-jvm-windows-amd64.url" onclick="resolveEarlyAccessDownload(event, this.href)">
                <img src="{{ '/assets/img/download-icon.svg' | relative_url }}" class="" alt="download icon">
              </a></td>
              </tr>
            </table>
          </div>
          <div class="example-logo-box">
             <img src="{{ '/assets/img/downloads/arrow-icon.svg' | relative_url }}" class="" alt="download icon">
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<!-- Guides -->
<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 id="guides" class="truffle__subtitle">Guides</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="{{ '/python-developers/docs/' | relative_url }}">
                <div class="guides__topics">Choose the Right GraalPy Runtime Distribution</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="{{ '/python-developers/docs/#python-standalone-applications' | relative_url }}">
                <div class="guides__topics">Package Python as Standalone Applications</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="{{ '/python-developers/docs/#using-graalpy-as-a-standalone-python-runtime' | relative_url }}">
                <div class="guides__topics">Use GraalPy as a Standalone Python Runtime</div>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
