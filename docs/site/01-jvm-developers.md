---
layout: base
permalink: jvm-developers/
audience_identifier: jvm
---

<section class="content-section language-benefits">
  <div class="wrapper">
    <div class="langbenefits">
      <div class="container">
        <h3 class="langpage__title-02">Embed Python in JVM Applications with GraalPy</h3>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/container-icon.svg" | relative_url }}' alt="access icon">
            </div>
            <div class="langbenefits__title">
              <h4>Python Packages in Java</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Use <a target="_blank" href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-javase-guide/">Python packages directly in Java</a>, Kotlin, or Scala</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/speed-icon.svg" | relative_url }}' alt="speed icon">
            </div>
            <div class="langbenefits__title">
              <h4>Jython Upgrade Path</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a target="_blank" href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-jython-guide/">Upgrade Jython projects</a> to Python 3</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/upgrade.svg" | relative_url }}' alt="upgrade icon">
            </div>
            <div class="langbenefits__title">
              <h4>JVM Scripting with Python</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Script JVM applications with Python</h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="content-section languages__back language-get-started language-get-started--centered language-get-started--no-top-overlay">
  <div class="wrapper">
    <div class="languages__example">
      <div class="container">
        <h3 id="getting-started" class="langstarter__title">How to Get Started</h3>
        <div class="langpage__benefits-text">
          <h5>Add GraalPy as a dependency to your JVM application, or go straight to the <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-starter/" target="_blank">starter project</a>.</h5>
        </div>
        <div class="get-started-groups">
          <div class="get-started-tabs languages__example-card" data-jvm-get-started-group>
            <div class="get-started-tabs__heading">
              <h4>Without Python Dependencies</h4>
            </div>
            <div class="compatibility_page-filter" role="tablist" aria-label="Without Python Dependencies build tool options">
              <div class="get-started-tabs__controls">
                <button type="button" class="compatibility_page-item" data-target="jvm-get-started-without-python-dependencies-maven" role="tab" aria-label="Maven" title="Maven" aria-selected="false" aria-controls="jvm-get-started-without-python-dependencies-maven" tabindex="-1" id="jvm-get-started-tab-without-python-dependencies-maven">
                  <img src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="get-started-tabs__tab-logo">
                </button>
                <button type="button" class="compatibility_page-item compatibility_page-active" data-target="jvm-get-started-without-python-dependencies-gradle-java" role="tab" aria-label="Gradle" title="Gradle" aria-selected="true" aria-controls="jvm-get-started-without-python-dependencies-gradle-java" tabindex="0" id="jvm-get-started-tab-without-python-dependencies-gradle-java">
                  <img src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="get-started-tabs__tab-logo get-started-tabs__tab-logo--gradle">
                </button>
              </div>
            </div>
            <div class="get-started-panel" id="jvm-get-started-without-python-dependencies-maven" role="tabpanel" aria-labelledby="jvm-get-started-tab-without-python-dependencies-maven" hidden>
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight xml -%}
<dependencies>
  <dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>{{ site.language_version }}</version>
  </dependency>
  <dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>python</artifactId>
    <version>{{ site.language_version }}</version>
    <type>pom</type>
  </dependency>
</dependencies>
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Maven snippet">
                    <img src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo">
                  </div>
                </div>
              </div>
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight java -%}
import org.graalvm.polyglot.Context;

try (Context context = Context.newBuilder()
        .allowAllAccess(true) // See documentation for options
        .build()) {
    context.eval("python", "print('Hello from GraalPy!')");
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Java snippet">
                    <img src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--java">
                  </div>
             </div>
           </div>
             </div>
            <div class="get-started-panel" id="jvm-get-started-without-python-dependencies-gradle-java" role="tabpanel" aria-labelledby="jvm-get-started-tab-without-python-dependencies-gradle-java">
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight groovy -%}
dependencies {
    implementation("org.graalvm.polyglot:polyglot:{{ site.language_version }}")
    implementation("org.graalvm.polyglot:python:{{ site.language_version }}")
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Gradle snippet">
                    <img src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--gradle">
                  </div>
              </div>
            </div>
            <div class="languages__example-box">
              <div class="languages__snippet">
{%- highlight java -%}
import org.graalvm.polyglot.Context;

try (Context context = Context.newBuilder()
        .allowAllAccess(true) // See documentation for options
        .build()) {
    context.eval("python", "print('Hello from GraalPy!')");
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Java snippet">
                    <img src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--java">
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="get-started-tabs languages__example-card" data-jvm-get-started-group>
            <div class="get-started-tabs__heading">
              <h4>With Python Dependencies</h4>
            </div>
            <div class="compatibility_page-filter" role="tablist" aria-label="With Python Dependencies build tool options">
              <div class="get-started-tabs__controls">
                <button type="button" class="compatibility_page-item" data-target="jvm-get-started-with-python-dependencies-maven" role="tab" aria-label="Maven" title="Maven" aria-selected="false" aria-controls="jvm-get-started-with-python-dependencies-maven" tabindex="-1" id="jvm-get-started-tab-with-python-dependencies-maven">
                  <img src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="get-started-tabs__tab-logo">
                </button>
                <button type="button" class="compatibility_page-item compatibility_page-active" data-target="jvm-get-started-with-python-dependencies-gradle-java" role="tab" aria-label="Gradle" title="Gradle" aria-selected="true" aria-controls="jvm-get-started-with-python-dependencies-gradle-java" tabindex="0" id="jvm-get-started-tab-with-python-dependencies-gradle-java">
                  <img src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="get-started-tabs__tab-logo get-started-tabs__tab-logo--gradle">
                </button>
              </div>
            </div>
            <div class="get-started-panel" id="jvm-get-started-with-python-dependencies-maven" role="tabpanel" aria-labelledby="jvm-get-started-tab-with-python-dependencies-maven" hidden>
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight xml -%}
<dependencies>
  <dependency>
    <groupId>org.graalvm.python</groupId>
    <artifactId>python-embedding</artifactId>
    <version>{{ site.language_version }}</version>
  </dependency>
</dependencies>
<build>
  <plugins>
    <plugin>
      <groupId>org.graalvm.python</groupId>
      <artifactId>graalpy-maven-plugin</artifactId>
      <version>{{ site.language_version }}</version>
      <executions>
        <execution>
          <configuration>
            <packages>
              <package>pyfiglet==1.0.2</package>
            </packages>
          </configuration>
          <goals>
            <goal>process-graalpy-resources</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Maven snippet">
                    <img src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo">
                  </div>
                </div>
              </div>
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight java -%}
import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;

try (Context context = GraalPyResources.contextBuilder().build()) {
    context.eval("python", """
        from pyfiglet import Figlet
        f = Figlet(font='slant')
        print(f.renderText('Hello from GraalPy!'))
        """);
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Java snippet">
                    <img src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--java">
                  </div>
             </div>
           </div>
             </div>
            <div class="get-started-panel" id="jvm-get-started-with-python-dependencies-gradle-java" role="tabpanel" aria-labelledby="jvm-get-started-tab-with-python-dependencies-gradle-java">
              <div class="languages__example-box">
                <div class="languages__snippet">
{%- highlight groovy -%}
plugins {
    id "org.graalvm.python" version "{{ site.language_version}}"
}

dependencies {
    implementation("org.graalvm.python:python-embedding:{{ site.language_version }}")
}

graalPy {
    packages = ["pyfiglet==1.0.2"]
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Gradle snippet">
                    <img src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--gradle">
                  </div>
              </div>
            </div>
            <div class="languages__example-box">
              <div class="languages__snippet">
{%- highlight java -%}
import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;

try (Context context = GraalPyResources.contextBuilder().build()) {
    context.eval("python", """
        from pyfiglet import Figlet
        f = Figlet(font='slant')
        print(f.renderText('Hello from GraalPy!'))
        """);
}
{%- endhighlight -%}
                  <div class="snippet-badge" role="img" aria-label="Java snippet">
                    <img src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' alt="" aria-hidden="true" class="snippet-badge__logo snippet-badge__logo--java">
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<script>
  $(document).ready(function () {
    $('[data-jvm-get-started-group]').each(function () {
      const getStartedGroup = $(this);
      const getStartedItems = getStartedGroup.find('.compatibility_page-item[role="tab"]');
      const getStartedPanels = getStartedGroup.find('.get-started-panel');

      function activateGetStartedTab(tab, options) {
        const settings = options || {};
        const target = tab.attr('data-target');

        getStartedItems
          .removeClass('compatibility_page-active')
          .attr('aria-selected', 'false')
          .attr('tabindex', '-1');

        tab
          .addClass('compatibility_page-active')
          .attr('aria-selected', 'true')
          .attr('tabindex', '0');

        getStartedPanels.attr('hidden', true);
        getStartedGroup.find(`#${target}`).removeAttr('hidden');

        const toolSuffix = target.includes('-maven') ? '-maven' : '-gradle-java';

        $('[data-jvm-get-started-group]').not(getStartedGroup).each(function () {
          const otherGroup = $(this);
          const otherItems = otherGroup.find('.compatibility_page-item[role="tab"]');
          const otherPanels = otherGroup.find('.get-started-panel');
          const matchingTab = otherItems.filter(`[data-target$="${toolSuffix}"]`);

          if (!matchingTab.length) {
            return;
          }

          otherItems
            .removeClass('compatibility_page-active')
            .attr('aria-selected', 'false')
            .attr('tabindex', '-1');

          matchingTab
            .addClass('compatibility_page-active')
            .attr('aria-selected', 'true')
            .attr('tabindex', '0');

          otherPanels.attr('hidden', true);
          otherGroup.find(`#${matchingTab.attr('data-target')}`).removeAttr('hidden');
        });

        if (settings.focus) {
          tab.trigger('focus');
        }
      }

      getStartedItems.on('click', function () {
        activateGetStartedTab($(this));
      });

      getStartedItems.on('keydown', function (event) {
        const currentIndex = getStartedItems.index(this);
        let nextIndex = null;

        if (event.key === 'ArrowRight') {
          nextIndex = (currentIndex + 1) % getStartedItems.length;
        } else if (event.key === 'ArrowLeft') {
          nextIndex = (currentIndex - 1 + getStartedItems.length) % getStartedItems.length;
        } else if (event.key === 'Home') {
          nextIndex = 0;
        } else if (event.key === 'End') {
          nextIndex = getStartedItems.length - 1;
        }

        if (nextIndex !== null) {
          event.preventDefault();
          activateGetStartedTab(getStartedItems.eq(nextIndex), { focus: true });
        }
      });
    });
  });
</script>


<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 id="guides" class="truffle__subtitle">Guides</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="{{ '/jvm-developers/docs/' | relative_url }}">
                <div class="guides__topics">Embed GraalPy in Java Applications</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-javase-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Maven or Gradle in a Java SE Application</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-freeze-dependencies-guide/" target="_blank">
                <div class="guides__topics">Freeze Transitive Python Dependencies for Reproducible Builds</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-spring-boot-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Spring Boot</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-micronaut-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Micronaut</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-custom-venv-guide/" target="_blank">
                <div class="guides__topics">Manually Install Python Packages</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-native-extensions-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy Maven Plugin to Install Python Native Extensions</div>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 class="truffle__subtitle">Demos</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-scripts-debug-guide" target="_blank">
                <div class="guides__topics">Debug Python Scripts in VS Code with GraalPy</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-openai-starter/" target="_blank">
                <div class="guides__topics">GraalPy OpenAI Starter</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-jbang-qrcode/" target="_blank">
                <div class="guides__topics">GraalPy JBang QRCode</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-spring-boot-pygal-charts/" target="_blank">
                <div class="guides__topics">SVG Charts with GraalPy and Spring Boot</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-micronaut-pygal-charts/" target="_blank">
                <div class="guides__topics">SVG Charts with GraalPy and Micronaut</div>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="content-section make-difference">
  <div class="wrapper">
    <div class="lang-video">
      <div class="container">
        <h3 class="truffle__subtitle">Videos</h3>
        <div class="lang-video__row">
          <div class="lang-video__card video-card">
            <a href="https://youtu.be/YqrEqXB59rA?si=3-PZGY2WUfltvCp0&t=1406" target="_blank" class="btn btn-primary--filledp">
              <img src="{{ '/assets/img/python/intellij2025-cover.png' | relative_url }}" class="video-cover" alt="IntelliJ 2025 thumbnail">
              <div class="play-button-container">
                <img src="{{ '/assets/img/play_button.svg' | relative_url }}" class="play-button" alt="play button">
              </div>
            </a>
          </div>
          <div class="lang-video__card">
            <div class="lang-video__title">
              <h4>Tips and Tricks for GraalVM and Graal Languages</h4>
            </div>
            <div class="lang-video__text">
              <h5>In this session, Fabio Niephaus from the GraalVM team shows his favourite tips and tricks for using GraalPy and other Graal Languages in IntelliJ IDEA. He also shows how to use IntelliJ IDEA as a multi-language IDE. Language injections and support for various debugging protocols make it easy to embed and debug code written in languages like Python in Java applications.
              </h5>
            </div>
          </div>
        </div>
        <div class="lang-video__row">
          <div class="lang-video__card video-card">
            <a href="https://www.youtube.com/watch?v=IdoFsS-mpVw" target="_blank" class="btn btn-primary--filledp">
              <img src="{{ '/assets/img/python/jfocus2025-cover.png' | relative_url }}" class="video-cover" alt="Jfokus 2025 thumbnail">
              <div class="play-button-container">
                <img src="{{ '/assets/img/play_button.svg' | relative_url }}" class="play-button" alt="play button">
              </div>
            </a>
          </div>
          <div class="lang-video__card">
            <div class="lang-video__title">
              <h4>Supercharge your Java Applications with Python!<br><a target="_blank" href="https://www.jfokus.se/talks/2305">Jfokus'25</a></h4>
            </div>
            <div class="lang-video__text">
              <h5>Projects such as LangChain4j, Spring AI, and llama3.java got the Java community very excited about AI in the last year.
                The Python ecosystem also provides many powerful packages for data science, machine learning, and more.
                Wouldn't it be cool if you, as a Java developer, could benefit from this, too?
                <br>
                In this talk, we show how you can get started with GraalPy and use packages from the Python ecosystem.
                We also show some live demos and preview upcoming features that will improve the interaction between Java and native extensions that ship with popular Python packages.
              </h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
