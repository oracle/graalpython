---
layout: base
title: Compatibility
permalink: compatibility/
---

<style>
img.pylogo {
  width: 80px;
  height: 80px;
}

.langbenefits__icon_pylogo {
  width: 80px;
}

.dataTable-version {
  min-width: 120px;
}

.highlight-package::before
{
  float: right;
  border-radius: 50%;
  margin-top: 0.5ex;
  width: 15px;
  height: 15px;
  display: inline-block;
  margin-right: 5px;
}

.legend-item-1::before
{
  font-size: 12px;
  font-weight: bold;
  text-align: center;
  content: "✓";
}

.legend-item-2::before
{
  font-size: 12px;
  font-weight: bold;
  text-align: center;
  content: "?";
}

.legend-item-3::before
{
  font-size: 12px;
  font-weight: bold;
  text-align: center;
  content: "✗";
}

#compatibility-stats-compatible,
#compatibility-stats-untested,
#compatibility-stats-incompatible,
#compatibility-stats-not-supported {
  padding-left: 0.5em;
}
</style>


<script src="{{ '/assets/js/check_compatibility_helpers.js' | relative_url }}"></script>
<script>
    DB.ANY_VERSION = "any";
    DB.INSTALLS_BUT_FAILS_TESTS = "The package installs, but the test suite was not set up for GraalPy.";
    DB.FAILS_TO_INSTALL = "The package fails to build or install.";
    DB.UNSUPPORTED = "The package is unsupported.";
    DB.PERCENT_PASSING = (pct) => `${pct}% of the tests are passing on GraalPy.`;
    const PATCH_AVAILABLE = "GraalPy will automatically apply a patch when installing this package to improve compatibility.";
    const LOWER_PRIORITY = "This version works, but there is no reason to prefer it over more recent versions.";
    const BUILD_SCRIPT_AVAILABLE = (url) => `If you have trouble building this package, there is a <a href='${url}' target='_blank'>script</a>.`

    const default_version = 'v242';
    const show_percentages = true;
    const dbs = {};
    var module_query = '';
    const load_db = function (graalpyVersion) {
        var graalvmVersion = graalpyVersion.replace(/^v/, "").replace(/^(\d\d)/, "$1.");
        var wheelbuilder_scripts = new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            const url = `https://api.github.com/repos/oracle/graalpython/contents/scripts/wheelbuilder/linux?ref=release/graal-vm/${graalvmVersion}`;
            xhr.open('GET', url);
            xhr.overrideMimeType('text/plain');
            xhr.onload = function () {
                if (this.status === 200) {
                    const contents = JSON.parse(this.responseText);
                    const packages = [];
                    for (const item of contents) {
                        const parts = item.name.split('.');
                        const package_name = parts[0];
                        const version = parts.slice(1, -1).join('.') || DB.ANY_VERSION;
                        packages.push(`${package_name},${version},0,${BUILD_SCRIPT_AVAILABLE(item.html_url)}`);
                    }
                    resolve(packages.join("\n"));
                } else {
                    reject(this.statusText);
                }
            };
            xhr.onerror = function () {
                reject(url);
            };
            xhr.send();
        });
        var patch_metadata = new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            const url = `https://raw.githubusercontent.com/oracle/graalpython/refs/heads/release/graal-vm/${graalvmVersion}/graalpython/lib-graalpython/patches/metadata.toml`;
            xhr.open('GET', url);
            xhr.overrideMimeType('text/plain');
            xhr.onload = function () {
                if (this.status === 200) {
                    const patches = [];
                    const lines = this.responseText.split('\n');
                    var currentPatch = null;
                    for (let i = 0; i < lines.length; i++) {
                        const line = lines[i].trim();
                        if (line.startsWith('[[')) {
                            if (currentPatch != null) {
                                patches.push(
                                    [currentPatch.name,
                                     currentPatch.version,
                                     0,
                                     currentPatch.comment || PATCH_AVAILABLE].join(",")
                                )
                            }
                            let pkgName = line.substring(2, line.indexOf(".")).trim();
                            currentPatch = {name: pkgName, version: DB.ANY_VERSION};
                        } else if (line.startsWith('note =')) {
                            currentPatch.comment = line.substring('note ='.length).trim();
                        } else if (line.startsWith('version =')) {
                            currentPatch.version = line.substring('version ='.length).trim().replace(/'|"/g, '').replace(/^== ?/, "").replace(/, ?/g, " and ");
                        } else if (line.startsWith('install-priority =')) {
                            if (parseInt(line.substring('install-priority ='.length).trim(), 10) <= 0) {
                                if (currentPatch.comment) {
                                    if (!currentPatch.comment.endsWith(".")) {
                                        currentPatch.comment += ".";
                                    }
                                    currentPatch.comment += " " + LOWER_PRIORITY;
                                } else {
                                    currentPatch.comment = LOWER_PRIORITY;
                                }
                            }
                        }
                    }
                    if (currentPatch != null) {
                        patches.push(
                            [currentPatch.name,
                             currentPatch.version,
                             0,
                             currentPatch.comment || PATCH_AVAILABLE].join(",")
                        )
                    }
                    resolve(patches.join("\n"));
                } else {
                    reject(this.statusText);
                }
            };
            xhr.onerror = function () {
                reject(url);
            };
            xhr.send();
        });
        var module_testing_csv = new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            const url = `/python/module_results/python-module-testing-${graalpyVersion}.csv`;
            xhr.open('GET', url);
            xhr.overrideMimeType('text/plain');
            xhr.onload = function () {
                if (this.status === 200) {
                    resolve(this.responseText);
                } else {
                    reject(this.statusText);
                }
            };
            xhr.onerror = function () {
                reject(url);
            };
            xhr.send();
        });
        var wheels_csv = new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            const url = `/python/wheels/${graalpyVersion}.csv`;
            xhr.open('GET', url);
            xhr.overrideMimeType('text/plain');
            xhr.onload = function () {
                if (this.status === 200) {
                    resolve(this.responseText);
                } else {
                    reject(this.statusText);
                }
            };
            xhr.onerror = function () {
                reject(url);
            };
            xhr.send();
        });
        return new Promise(function (resolve, reject) {
            Promise.allSettled([module_testing_csv, patch_metadata, wheelbuilder_scripts, wheels_csv]).then(function (results) {
                resolve(results.map(function (result) {
                    if (result.status === 'fulfilled') {
                        return result.value;
                    } else {
                        return null;
                    }
                }).filter((entry) => !!entry).join("\n"));
            }).catch(function (err) {
                reject(err);
            });
        });
    };
    let pageNumber = 0;
    let database;
    const getRowsPerPage = function () {
        return parseInt($('#rowsPerPage').val());
    }
    const updatePagination = function (reset) {
        if (reset) {
            pageNumber = 0;
        }
        $('#pagination-previous').attr('disabled', pageNumber == 0);
        const rowsPerPage = getRowsPerPage();
        const count = $('#dataTable tbody tr:not(.dataTable-filtered-out)').length;
        let pageText;
        if (count < rowsPerPage) {
            pageText = `1-${count}`;
            pageNumber = 0;
        } else {
            const start = pageNumber * rowsPerPage;
            const end = start + rowsPerPage;
            $('#pagination-next').attr('disabled', end >= count);
            pageText = `${start}-${Math.min(end, count)}`
            if (!reset) {
                let numberOfRowsToSkip = start
                let numberOfVisibleRows = 0;
                const rows = $('#dataTable tbody tr');
                rows.each(function () {
                    if (!$(this).hasClass('dataTable-filtered-out')) {
                        if (numberOfRowsToSkip > 0) {
                            $(this).hide();
                            numberOfRowsToSkip--;
                        } else {
                            if (numberOfVisibleRows < rowsPerPage) {
                                $(this).show();
                                numberOfVisibleRows++;
                            } else {
                                $(this).hide();
                            }
                        }
                    }
                });
            }
        }
        $('#pagination-label').text(`${pageText} of ${count}`)
    }
    const toStatisticsText = function (part, total) {
        return `${part} (${Math.round(part / total * 100 * 100) / 100}%)`
    }
    const updateStatistics = function (count, countCompatible, countUntested, countIncompatible, countNotSupported) {
        var offset = 0;
        var svg = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160">
          <g transform="rotate(-90)" transform-origin="80 80">`;
        const drawOne = function(name, size, color) {
          svg += `<circle r="64" cx="80" cy="80"
            fill="none" pathLength="${count}"
            stroke="${color}" stroke-width="32"
            stroke-dasharray="0 ${offset} ${size} ${count - offset - size}">
              <title>${name}: ${toStatisticsText(size, count)}</title>
            </circle>
          `;
          offset += size;
        }
        drawOne('Compatible', countCompatible, '#13A97C');
        drawOne('Untested', countUntested, '#76D1FF');
        drawOne('Incompatible', countIncompatible, '#C84D4D');
        drawOne('Unsupported', countNotSupported, '#D7D7D7');
        svg += `</g></svg>`;
        var chart = document.getElementById('pie-chart');
        chart.innerHTML = svg;

        $('#compatibility-stats-compatible').text(toStatisticsText(countCompatible, count));
        $('#compatibility-stats-untested').text(toStatisticsText(countUntested, count));
        $('#compatibility-stats-incompatible').text(toStatisticsText(countIncompatible, count));
        $('#compatibility-stats-not-supported').text(toStatisticsText(countNotSupported, count));
    }
    const updatePageData = function () {
        const params = new URLSearchParams(window.location.search);
        const graalpyModuleValue = params.get('version') || default_version;
        load_db(graalpyModuleValue).then(function (db_contents) {
            database = new DB("python", db_contents);
            const rowsPerPage = getRowsPerPage();
            let count = 0;
            let countCompatible = 0;
            let countUntested = 0;
            let countIncompatible = 0;
            let countNotSupported = 0;
            $('#dataTable tbody').empty();
            for (let package in database.db) {
                const versions = database.db[package];
                const version_keys = Object.keys(versions).sort((a, b) => {
                    const versionA = a.replace(/[~><=! ]/g, '');
                    const versionB = b.replace(/[~><=! ]/g, '');
                    if (versionA < versionB) return -1;
                    if (versionA > versionB) return 1;
                    return 0;
                });
                versions_loop: for (const version of version_keys) {
                    if (version.startsWith('~')) {
                        continue;
                    }
                    const info = versions[version];
                    switch (info.test_status) {
                        case 0:
                            countCompatible++;
                            break;
                        case 1:
                            countUntested++;
                            break;
                        case 2:
                            countIncompatible++;
                            break;
                        case 3:
                            countNotSupported++;
                            break;
                        default:
                            continue versions_loop;
                    }
                    const styling = count++ < rowsPerPage ? '' : ' style="display: none;"';
                    const highlight = '<span class="highlight-package legend-item-1"></span>'.repeat(Math.ceil(info.highlight));
                    $('#dataTable tbody').append(`
                            <tr${styling}>
                                <td class="dataTable-name"><a href="https://pypi.org/project/${info.name}" target="_blank">${info.name}<a/></td>
                                <td class="dataTable-version">${info.version}${highlight}</td>
                                <td>${info.notes}</td>
                            </tr>`);
                }
            }
            $('#compatibility_page__search-field').trigger("input");
            updateStatistics(count, countCompatible, countUntested, countIncompatible, countNotSupported);
            updatePagination(true);
        });
    }
    $(document).ready(function () {
        updatePageData();
        $('#compatibility_page__search-field').on('input', function () {
            const searchTerms = this.value.split(',');
            let numberOfVisibleRows = 0;
            const rowsPerPage = getRowsPerPage();
            const rows = $('#dataTable tbody tr');
            rows.each(function () {
                if (searchTerms.some(term => (searchTerms.length <= 1 || term !== '') && $(this).find('.dataTable-name').first().text().includes(term))) {
                    $(this).removeClass('dataTable-filtered-out');
                    if (numberOfVisibleRows < rowsPerPage) {
                        $(this).show();
                        numberOfVisibleRows++;
                    } else {
                        $(this).hide();
                    }
                } else {
                    $(this).addClass('dataTable-filtered-out');
                    $(this).hide();
                }
            });
            updatePagination(true);
        });
        $('#add-requirements-btn').on('change', function (e) {
            e.stopPropagation();
            e.preventDefault();
            const file = this.files[0];
            const fileReader = new FileReader();
            fileReader.onloadend = function (e) {
                const contents = e.target.result;
                const searchTerms = [];
                for (const line of contents.split('\n')) {
                    const trimmedLine = line.trim();
                    const pythonPackageName = trimmedLine.match(/^[a-zA-Z0-9]+/);
                    if (pythonPackageName !== null) {
                        searchTerms.push(pythonPackageName);
                    }
                }
                const searchField = $('#compatibility_page__search-field');
                searchField.val(searchTerms.join(','));
                searchField.trigger('input').change();
            }
            fileReader.readAsText(file);
        });
        $('#rowsPerPage').on('input', function () {
            const rowsPerPage = parseInt(this.value);
            let numberOfVisibleRows = 0;
            const rows = $('#dataTable tbody tr');
            rows.each(function () {
                if (numberOfVisibleRows < rowsPerPage && !$(this).hasClass('dataTable-filtered-out')) {
                    $(this).show();
                    numberOfVisibleRows++;
                } else {
                    $(this).hide();
                }
            });
            updatePagination(true);
        });
        $('#pagination-previous').on('click', function () {
            pageNumber--;
            updatePagination(false);
        });
        $('#pagination-next').on('click', function () {
            pageNumber++;
            updatePagination(false);
        });
        /* top-level version switcher */
        $(".compatibility_page-item").click(function () {
            $(this).addClass("compatibility_page-active").siblings().removeClass("compatibility_page-active");
            const graalpyModuleValue = $(".compatibility_page-item.compatibility_page-module.compatibility_page-active").attr("data-filter");
            let search = window.location.search;
            if (search) {
                search = search.replace(/version=[^&]+&?/, "");
                if (search != "?" && !search.endsWith("&")) {
                    search += "&";
                }
            } else {
                search = "?";
            }
            search += `version=${graalpyModuleValue}`;
            window.history.pushState("", window.location.title, search);
            updatePageData();
        });
        function setFilters() {
            const params = new URLSearchParams(window.location.search);
            const graalpyModuleValue = params.get('version') || default_version;
            const moduleFilterElement = $(`.compatibility_page-module[data-filter=${graalpyModuleValue}]`);
            moduleFilterElement.addClass("compatibility_page-active").siblings().removeClass("compatibility_page-active");
            const packages = params.get('packages') || "";
            $('#compatibility_page__search-field').val(packages);
        }
        setFilters();
    });
</script>

<section class="content-section compatibility-homescreen versions">
    <div class="wrapper">
        <div>
            <div class="container">
                <h3 class="truffle__subtitle">GraalPy: Package Compatibility</h3>

                GraalPy is compatible with many packages for Data Science and Machine Learning, including the popular PyTorch, NumPy, and Huggingface Transformers.
                To try a package, pick any version and only if you run into problems, consult our table below to see if there is a version that may work better.

                <div class="compatibility_page-filter">
                    <div class="compatibility__row">
                        <p class="compatibility_page-item compatibility_page-module compatibility_page-active" data-filter="v242">GraalPy 24.2</p>
                        <p class="compatibility_page-item compatibility_page-module" data-filter="v241">GraalPy 24.1</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
<!-- <section class="content-section download-homescreen">
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
</section> -->
<!-- Benefits -->

<section class="content-section">
  <div class="wrapper">
    <div class="langbenefits">
      <div class="container">
        <h3 class="langpage__title-02"></h3>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/numpy.svg" | relative_url }}' alt="numpy icon">
            </div>
            <div class="langbenefits__title">
              <h4>Numeric Computing</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>We test <a href="?packages=numpy#compattable-container">NumPy</a> across multiple versions and know of multiple deployments where it brings numeric computing to Java.</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/scipy.svg" | relative_url }}' alt="scipy icon">
            </div>
            <div class="langbenefits__title">
              <h4>Scientific Computing</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5><a href="?packages=scipy#compattable-container">SciPy</a>'s rich library for scientific computing is just a package download away.</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/pandas.svg" | relative_url }}' alt="pandas icon">
            </div>
            <div class="langbenefits__title">
              <h4>Data Processing</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Thanks to Arrow, <a href="?packages=pandas,arrow#compattable-container">Pandas</a> on GraalPy can run multi-threaded while avoiding unneccessary data copies.</h5>
            </div>
          </div>
        </div>
        <div class="langbenefits__row">
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/huggingface.svg" | relative_url }}' alt="huggingface icon">
            </div>
            <div class="langbenefits__title">
              <h4>Models for any Task</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>The <a href="?packages=huggingface,transformers#compattable-container">Huggingface</a> transformers library works on GraalPy with its huge library of language, vision, and audio models.</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/pytorch.svg" | relative_url }}' alt="pytorch icon">
            </div>
            <div class="langbenefits__title">
              <h4>Training and Inference</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>Train models and run inference on GraalPy with <a href="?packages=torch#compattable-container">PyTorch</a>, taking full advantage of the latest techniques and accellerator hardware.</h5>
            </div>
          </div>
          <div class="langbenefits__card">
            <div class="langbenefits__icon langbenefits__icon_pylogo">
              <img class="pylogo" src='{{ "/assets/img/python/autogen.svg" | relative_url }}' alt="pyautogen icon">
            </div>
            <div class="langbenefits__title">
              <h4>Agentic Workflows</h4>
            </div>
            <div class="langpage__benefits-text">
              <h5>With <a href="?packages=autogen#compattable-container">Autogen</a> and GraalPy you can write agentic workflows and use Java code to create tools for AI Agents.</h5>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</section>
<section class="content-section">
    <div class="wrapper">
        <div class="compatibility">
            <div class="container">
                <h5 class="compatibility-text">To ensure GraalPy is compatible with common Python packages,
                    the GraalPy team conducts compatibility testing and creates scripts to build and patch many
                    of the top packages on PyPI plus some more that are of special interest to us, including
                  	libraries and frameworks such as NumPy, Pandas, and Django.</h5>
                <h5 class="compatibility-text">Compatibility testing ensures that
                    developers can leverage GraalPy's powerful capabilities in their existing applications.
                    It also enables developers to use GraalPy to create more efficient and productive applications in the areas of
                    machine learning, data analysis, and web development using their familiar Python
                    toolsets.</h5>
                <h5 class="compatibility-text">Many more Python packages than are on this list work on GraalPy.
                    If there is a package you are interested in that you cannot find here, chances are that it
                    might just work. If it does not, please reach out to us on <a href="https://github.com/oracle/graalpython/issues" target="_blank">GitHub</a></h5>
            </div>
        </div>
    </div>
</section>
<section class="boxes">
    <div class="wrapper">
        <div class="compatibility">
            <div class="container compatibility_page-box v231 all">
                <div class="compatibility__mid">
                    <div class="container">
                        <div class="compatibility__chart">
                            <div class="compatibility__chart-row">
                                <div class="chart" id="pie-chart">
                                </div>
                                <div class="legend">
                                    <div>
                                        <div class="legend-item legend-item-1"
                                            title="More than 90% of the package's tests run successfully on GraalPy">Compatible: <span id="compatibility-stats-compatible">loading...</span><sup class="info-tooltip"></sup></div>
                                        <div class="legend-item legend-item-2"
                                            title="The package either does not install on GraalPy or is not currently tested">
                                            Currently Untested: <span id="compatibility-stats-untested">loading...</span><sup class="info-tooltip"></sup></div>
                                    </div>
                                    <div>
                                        <div class="legend-item legend-item-3"
                                            title="Fewer than 90% of the package's tests run successfully on GraalPy">Currently
                                            Incompatible: <span id="compatibility-stats-incompatible">loading...</span><sup class="info-tooltip"></sup></div>
                                        <div class="legend-item legend-item-4"
                                            title="We have no plans to test the package">Not
                                            Supported: <span id="compatibility-stats-not-supported">loading...</span><sup class="info-tooltip"></sup></div>
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
<section class="content-section module-table">
    <div class="wrapper">
        <div class="compatibility">
            <div class="container">
                <h3 id="compattable-container" class="langpage__title-02">Python Packages</h3>
                <div class="package__row">
                    <div class="package__search">
                        <input type="text" id="compatibility_page__search-field" placeholder="Comma-separated list of packages">
                    </div>
                    <div class="package__btn">
                        <div class='input-file gp-requirement'>
                            <label for="add-requirements-btn" class="btn-comp-link">
                                Filter by requirements.txt
                            </label>
                            <input type="file" id="add-requirements-btn" accept=".txt">
                        </div>
                    </div>
                </div>
                <div class="compattable-container">
                    <table class="compattable" id="dataTable">
                        <caption class="visually-hidden">Python Packages</caption>
                        <thead>
                            <tr class="add-radius">
                                <th scope="col" title="Name">Name</th>
                                <th scope="col" title="Version">Version</th>
                                <th scope="col" title="Notes">Notes</th>
                            </tr>
                        </thead>
                        <tbody></tbody>
                    </table>
                </div>
                <div class="tablebot">
                    <div class="tablebot__elements">
                        <label for="rowsPerPage">Rows per page:</label>
                        <select id="rowsPerPage">
                            <option value="25" selected>25</option>
                            <option value="50">50</option>
                            <option value="100">100</option>
                        </select>
                        <div class="pagination-group">
                            <div class="pagination-lab" id="pagination-label"></div>
                            <button class="pagination-dis" id="pagination-previous" disabled>&#60;</button>
                            <button class="pagination-act" id="pagination-next">&#62;</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
