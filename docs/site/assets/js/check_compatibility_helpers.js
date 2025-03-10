class RequestedModule {
    constructor(name, version) {
        this.name = name;
        this.version = Utilities.normalize_version(version);
    }
}

class ModuleListing {
    constructor(name, contents) {
        this.name = name;
        this.contents = contents;
    }
}

class DBEntry {
    constructor([library_name, library_version, test_status, pass_percentage]) {
        this.name = library_name;
        this.version = Utilities.normalize_version(library_version);
        this.test_status = parseInt(test_status);
        this.pass_percentage = pass_percentage;
    }
}

class DB {
    constructor(language, db_contents) {
        this.db = {};
        this.language = language;

        const lines = db_contents.split('\n');

        for (let l in lines) {
            const entry = new DBEntry(lines[l].split(','));

            if (!(entry.name in this.db)) {
                this.db[entry.name] = {};
            }

            this.db[entry.name][entry.version] = entry;
            this.db[entry.name][Utilities.approximate_recommendation(entry.version)] = entry;
        }
    }

    lookup(requested_name, requested_version, print_missing) {
        const ret = [];

        if (requested_name in this.db) {
            if (requested_version === undefined) {
                const versions = this.db[requested_name];
                for (let version in versions) {
                    if (!version.startsWith('~')) {
                        const entry = versions[version];
                        ret.push([entry.name, version, entry.test_status, entry.pass_percentage]);
                    }
                }
            } else {
                if (requested_version in this.db[requested_name]) {
                    const entry = this.db[requested_name][requested_version];
                    ret.push([entry.name, entry.version, entry.test_status, entry.pass_percentage]);
                } else {
                    const semver_match = Utilities.approximate_recommendation(requested_version);
                    if (semver_match in this.db[requested_name]) {
                        const entry = this.db[requested_name][semver_match];
                        ret.push([entry.name, entry.version, entry.test_status, entry.pass_percentage]);
                    } else {
                        ret.push([requested_name, requested_version, 'unknown', undefined]);
                    }
                }
            }
        } else {
            if (print_missing) {
                ret.push([requested_name, '*', 'library not yet tested', undefined]);
            }
        }

        // In the event of multiple versions for a module, sort the results from highest version to lowest.
        ret.sort(function(a, b) {
            return b[1].localeCompare(a[1]);
        });

        return ret;
    }

    lookup_module(module, print_missing) {
        return this.lookup(module.name, module.version, print_missing);
    }

}

class Utilities {
    static pretty_name(language) {
        switch(language) {
            case 'js': return 'GraalVM JavaScript';
            case 'r': return 'GraalVM R';
            case 'ruby': return 'GraalVM Ruby';
            case 'python': return 'GraalVM Python';
        }
    }

    static normalize_version(version) {
        if (version === undefined) {
            return undefined;
        } else {
            return version.replace(/^v(\d.*)/, '$1');
        }
    }

    static version_segments(version) {
        if (version === undefined) {
            return undefined;
        }

        const string_parts = version.match(/[0-9]+|[a-z]+/ig);

        // The result of the regexp match will be an array of strings. We would like to be
        // able to perform numeric comparisons on the parts corresponding to integer values,
        // so convert them here.
        return string_parts.map(function(e) {
            let x = parseInt(e);

            if (isNaN(x)) {
                return e;
            } else {
                return x;
            }
        });
    }

    static approximate_recommendation(version) {
        if (version === undefined) {
            return undefined;
        }

        const segments = this.version_segments(version);

        while (segments.some(function(e) { typeof(e) === 'string' })) {
            segments.pop();
        }

        while (segments.length > 2) {
            segments.pop();
        }

        while (segments.length < 2) {
            segments.push(0);
        }

        return `~> ${segments.join('.')}`
    }
}

class DependencyFileProcessor {
    static handle_gemfile(db, contents) {
        if (db.language !== 'ruby') {
            return [];
        }

        const r = /\n    (\S+?) \((.+?)\)/g;
        let match;
        const queries = [];

        while (match = r.exec(contents)) {
            queries.push(match.slice(1, 3));
        }

        queries.sort(function(a, b) {
            return a[0].localeCompare(b[0]);
        });

        let results = [];
        for (let [name, version] of queries) {
            results = results.concat(db.lookup(name, version, true));
        }

        return results;
    }

    static handle_package_json(db, contents) {
        if (db.language !== 'js') {
            return [];
        }

        const json = JSON.parse(contents);
        let queries = [];

        for (let name in json['dependencies']) {
            queries.push([name, undefined]);
        }

        let results = [];
        for (let [name, version] of queries) {
            results = results.concat(db.lookup(name, version, true));
        }

        return results;
    }

    static handle_package_lock(db, contents) {
        if (db.language !== 'js') {
            return [];
        }

        const json = JSON.parse(contents);
        let queries = [[json['name'], json['version']]];

        for (let name in json['dependencies']) {
            let metadata = json['dependencies'][name];

            if (metadata['dev'] || metadata['optional']) {
                continue;
            }

            queries.push([name, metadata['version']]);
        }

        let results = [];
        for (let [name, version] of queries) {
            results = results.concat(db.lookup(name, version, true));
        }

        return results;
    }

    static handle_yarn_lock(db, contents) {
        if (db.language != 'js') {
            return [];
        }

        const r = /\n(\w.*?)@.+?:\s+?version "(.+?)"/g;
        let match;
        const queries = [];

        while (match = r.exec(contents)) {
            queries.push(match.slice(1, 3));
        }

        queries.sort(function(a, b) {
            return a[0].localeCompare(b[0]);
        });

        let results = [];
        for (let [name, version] of queries) {
            results = results.concat(db.lookup(name, version, true));
        }

        return results;
    }

    static handle_packrat_lock(db, contents) {
        if (db.language != 'r') {
            return [];
        }

        const r = /\nPackage: (\w+)\nSource: (\w+)\nVersion: (.+?)\n/g;
        let match;
        const queries = [];

        while (match = r.exec(contents)) {
            queries.push(match.slice(1, 4));
        }

        queries.sort(function(a, b) {
            return a[0].localeCompare(b[0]);
        });

        let results = [];
        for (let [name, repository, version] of queries) {
            results = results.concat(db.lookup(name, version, true));
        }

        return results;
    }
}

if (typeof module !== 'undefined') {
    module.exports.DB = DB;
    module.exports.DependencyFileProcessor = DependencyFileProcessor;
    module.exports.ModuleListing = ModuleListing;
    module.exports.RequestedModule = RequestedModule;
    module.exports.Utilities = Utilities;
}
