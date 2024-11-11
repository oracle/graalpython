---
layout: base
title: Compatibility
permalink: compatibility/
---
<script src="{{ '/assets/js/check_compatibility_helpers.js' | relative_url }}"></script>
<script>
    const show_percentages = true;
    const dbs = {};
    var module_query = '';
    const load_db = function (graalpyVersion) {
        return new Promise(function (resolve, reject) {
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
        const graalpyModuleValue = params.get('version') || 'v241';
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
                const versions = database.db[package]
                versions_loop: for (let version in versions) {
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
                            console.log(`Unknown test_status: ${info.test_status} for package ${info.name}`);
                            continue versions_loop;
                    }
                    const styling = count++ < rowsPerPage ? '' : ' style="display: none;"';
                    $('#dataTable tbody').append(`
                            <tr${styling}>
                                <td class="dataTable-name">${info.name}</td>
                                <td>${info.version}</td>
                                <td>${info.pass_percentage}</td>
                                <td><a href="https://pypi.org/project/${info.name}/" target="_blank">${info.name} on pypi.org</td>
                            </tr>`);
                }
            }
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
            window.history.pushState("", window.location.title, `?version=${graalpyModuleValue}`);
            updatePageData();
        });
        function setFilters() {
            const params = new URLSearchParams(window.location.search);
            const graalpyModuleValue = params.get('version') || 'v241';
            const moduleFilterElement = $(`.compatibility_page-module[data-filter=${graalpyModuleValue}]`);
            moduleFilterElement.addClass("compatibility_page-active").siblings().removeClass("compatibility_page-active");
        }
        setFilters();
    });
</script>

<section class="content-section compatibility-homescreen versions">
    <div class="wrapper">
        <div>
            <div class="container">
                    <h3 class="truffle__subtitle">GraalPy: Package Compatibility</h3>
                <div class="compatibility_page-filter">
                    <div class="compatibility__row">
                        <p class="compatibility_page-item compatibility_page-module compatibility_page-active" data-filter="v241">GraalPy 24.1</p>
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
<section class="content-section">
    <div class="wrapper">
        <div class="compatibility">
            <div class="container">
                <h5 class="compatibility-text">To ensure GraalPy is compatible with common Python packages, the GraalPy team conducts
                    compatibility testing to verify the presence and correct functioning of 
                  	the top 500 packages on PyPI plus some more that are of special interest to us, including
                  	libraries and frameworks such as NumPy, Pandas, and Django.</h5>
                <h5 class="compatibility-text">Compatibility testing ensures that
                    developers can leverage GraalPy's powerful capabilities in their existing applications.
                    It also enables developers to use GraalPy to create more efficient and productive applications in the areas of
                    machine learning, data analysis, and web development using their familiar Python
                    toolsets.</h5>
                <h5 class="compatibility-text">Many more Python packages than are on this list work on GraalPy.
                    If there is a package you are interested in that you cannot find here, chances are that it
                    might just work.</h5>
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
                <h3 class="pypage__title-02">Python Packages</h3>
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
                        <div>
                            <a href="{{ '/module_results/python-module-testing-v231.csv' | relative_url }}"
                                target="_blank"><button class="download-data-btn">Download data</button></a>
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
                                <th scope="col" title="Test Level">Test Level %</th>
                                <th scope="col" title="Package URL">Package URL</th>
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
