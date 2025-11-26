---
layout: base
---
<section>
  <div>
    <div class="hi">
      <div class="container">
        <div class="hi__row">
          <div class="hi__body">
            <h4 class="hi__title">A high-performance embeddable Python 3 runtime for Java</h4>
            <div class="hi__buttons">
              <a href="#getting-started" class="btn btn-primary">Quickstart</a>
              <a href="#guides" class="btn btn-primary">Guides</a>
            </div>
          </div>
          <div class="hi__image">
            <img src="{{ '/assets/img/home/python-logo.svg' | relative_url }}" alt="Python icon">
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<!-- Benefits -->
<section class="content-section">
  <div class="wrapper">
    <div class="langbenefits">
      <div class="container">
        <h3 class="langpage__title-02">Benefits</h3>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/container-icon.svg" | relative_url }}' alt="access icon">
            </div>
            <div class="langbenefits__title">
              <h4>Python for Java</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Load and use <a target="_blank" href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-javase-guide/">Python packages directly in Java</a></h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/compatibility.svg" | relative_url }}' alt="compatibility icon">
            </div>
            <div class="langbenefits__title">
              <h4>Python 3 Compatible</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="{{ '/compatibility/' | relative_url }}">Compatible with many Python packages</a>, including popular AI and Data Science libraries</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/speed-icon.svg" | relative_url }}' alt="speed icon">
            </div>
            <div class="langbenefits__title">
              <h4>Fastest Python on the JVM</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="https://www.graalvm.org/latest/reference-manual/java/compiler/">Graal JIT</a> compiles Python for native code speed</h5>
            </div>
          </div>
        </div>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/upgrade.svg" | relative_url }}' alt="upgrade icon">
            </div>
            <div class="langbenefits__title">
              <h4>Modern Python for the JVM</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>GraalPy provides an <a target="_blank" href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-jython-guide/">upgrade path for Jython users</a></h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/home-code.svg" | relative_url }}' alt="code icon">
            </div>
            <div class="langbenefits__title">
              <h4>Script Java with Python</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Extend applications with Python scripts that <a href="{{ '/docs/#interoperability' | relative_url }}">interact with Java</a></h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon">
              <img src='{{ "/assets/img/icon-set-general/binary-icon.svg" | relative_url }}' alt="binary icon">
            </div>
            <div class="langbenefits__title">
              <h4>Simple distribution</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Package Python applications as a <a href="{{ '/docs/#python-standalone-applications' | relative_url }}">single binary</a> with GraalVM Native Image</h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>

<!-- Get Started -->
<section class="content-section languages__back">
  <div class="wrapper">
    <div class="languages__example">
      <div class="container">
        <h3 id="getting-started" class="langstarter__title">How to Get Started</h3>
        <div class="langpage__benefits-text">
          <h5>You have the option to extend your Java application with Python, or go straight to the <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-starter/" target="_blank">starter project</a></h5>
        </div>
        <div class="languages__example-card">
          <div class="language__example-subtitle-mobile">
            <h4>1. Add GraalPy as a dependency from <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/python" target="_blank">Maven Central</a></h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__example-subtitle">
            <h4>1. Add GraalPy as a dependency from <a href="https://central.sonatype.com/artifact/org.graalvm.polyglot/python" target="_blank">Maven Central</a></h4>
          </div>
              {%- highlight xml -%}
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
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Maven icon" src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__text-secondary">
            <h4>or</h4>
          </div>
              {%- highlight groovy -%}
implementation("org.graalvm.polyglot:polyglot:{{ site.language_version }}")
implementation("org.graalvm.polyglot:python:{{ site.language_version }}")
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Gradle icon" src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="language__example-subtitle-mobile">
            <h4>2. Embed Python code in Java</h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__example-subtitle">
            <h4>2. Embed Python code in Java</h4>
          </div>
              {%- highlight java -%}
import org.graalvm.polyglot.Context;

try (Context context = Context.create()) {
    context.eval("python", "print('Hello from GraalPy!')");
}
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Java icon" src='{{ "/assets/img/logos/java-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="language__example-subtitle-mobile">
            <h4>3. Add GraalPy plugins for additional Python packages (optional)</h4>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__example-subtitle">
            <h4>3. Add GraalPy plugins for additional Python packages (optional)</h4>
          </div>
              {%- highlight xml -%}
<plugin>
  <groupId>org.graalvm.python</groupId>
  <artifactId>graalpy-maven-plugin</artifactId>
  <version>{{ site.language_version }}</version>
  <executions>
    <execution>
      <configuration>
        <packages>
          <!-- Select Python packages to install via pip. -->
          <package>pyfiglet==1.0.2</package>
        </packages>
      </configuration>
      <goals>
        <goal>process-graalpy-resources</goal>
      </goals>
    </execution>
  </executions>
</plugin>
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Maven icon" src='{{ "/assets/img/logos/maven-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
          </div>
          <div class="languages__example-box">
            <div class="languages__snippet">
                      <div class="language__text-secondary">
            <h4>or</h4>
          </div>
              {%- highlight groovy -%}
plugins {
    id("org.graalvm.python") version "{{ site.language_version }}"
}

graalPy {
    packages = setOf("pyfiglet==1.0.2")
}
              {%- endhighlight -%}
            </div>
            <div class="example-logo-box">
              <img alt="Gradle icon" src='{{ "/assets/img/logos/gradle-logo.svg" | relative_url }}' class="languages__example-logo">
            </div>
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
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-javase-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Maven or Gradle in a Java SE Application</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-spring-boot-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Spring Boot</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-micronaut-guide/" target="_blank">
                <div class="guides__topics">Use GraalPy with Micronaut</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-custom-venv-guide/" target="_blank">
                <div class="guides__topics">Manually Install Python Packages and Files</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-jython-guide/" target="_blank">
                <div class="guides__topics">Migrate from Jython to GraalPy</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/blob/main/graalpy/graalpy-freeze-dependencies-guide/" target="_blank">
                <div class="guides__topics">Freeze Transitive Python Dependencies for Reproducible Builds</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
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
<!-- Demos -->
<section class="boxes">
  <div class="wrapper">
    <div class="guides">
      <div class="container guides-box build all">
        <h3 class="truffle__subtitle">Demos</h3>
        <div class="guides__row">
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-openai-starter/" target="_blank">
                <div class="guides__topics">GraalPy OpenAI Starter</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-jbang-qrcode/" target="_blank">
                <div class="guides__topics">GraalPy JBang QRCode</div>
              </a>
            </div>
          </div>
          <div class="guides__column">
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
              <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-spring-boot-pygal-charts/" target="_blank">
                <div class="guides__topics">SVG Charts with GraalPy and Spring Boot</div>
              </a>
            </div>
            <div class="guides__card">
              <img src='{{ "/assets/img/downloads-new/miscellaneous-book.svg" | relative_url }}' alt="book icon">
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

<!-- Video -->
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
              <h5>In this session, Fabio Niephaus from the GraalVM team will show his favourite tips and tricks for using GraalPy and other Graal Languages in IntelliJ IDEA. He will also show how to use IntelliJ IDEA as a multi-language IDE. Language injections and support for various debugging protocols make it easy to embed and debug code written in languages like Python in Java applications.
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
        <div class="lang-video__row">
          <div class="lang-video__card video-card">
            <a href="https://www.youtube.com/watch?v=F8GoDqTtSOE" target="_blank" class="btn btn-primary--filledp">
              <img src="{{ '/assets/img/python/devoxx2024-cover.png' | relative_url }}" class="video-cover" alt="Devoxx 2024 thumbnail">
              <div class="play-button-container">
                <img src="{{ '/assets/img/play_button.svg' | relative_url }}" class="play-button" alt="play button">
              </div>
            </a>
          </div>
          <div class="lang-video__card">
            <div class="lang-video__title">
              <h4>Supercharge your Java Applications with Python!<br><a target="_target" href="https://devoxx.be/talk/?id=8173">Devoxx'24</a></h4>
            </div>
            <div class="lang-video__text">
              <h5>The Python ecosystem provides many powerful packages for data science, machine learning, and more, that you can now leverage in Java. 
                Get started by adding GraalPy as a dependency to your Java project.
                There are also Maven and Gradle plugins for GraalPy that help you install additional Python packages. 
                In this presentation, we also show live demos that illustrate different use cases, such as a Spring Boot application that visualizes data with Python, Python running on JBang!, a Java application scripted with Python, and more.
              </h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
