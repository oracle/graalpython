class DBEntry {
    constructor(library_name, library_version, test_status, notes) {
        this.name = library_name;
        this.version = library_version.replace(/^v(\d.*)/, '$1').replace(/ and /g, ", ");
        this.test_status = parseInt(test_status);
        this._notes = notes;
    }

    is_test_percentage() {
        return !!this._notes.match(/^\d+\.\d+$/)
    }

    has_no_test_results() {
        return !!this._notes.match(/^0+\.0+$/)
    }

    has_good_test_results() {
        return this.is_test_percentage() && this.test_status == 0 && parseFloat(this._notes.match(/^\d+\.\d+$/)[0]) > 90;
    }

    set notes(value) {
        this._notes = value;
    }

    get notes() {
        let notes = this._notes;
        if (this.is_test_percentage()) {
            if (this.has_no_test_results()) {
                if (this.test_status < 2) {
                    notes = DB.INSTALLS_BUT_FAILS_TESTS;
                } else if (this.test_status == 2) {
                    notes = DB.FAILS_TO_INSTALL;
                } else {
                    notes = DB.UNSUPPORTED;
                }
            } else {
                notes = DB.PERCENT_PASSING(notes);
            }
        }
        if (!notes.endsWith(".")) {
            notes += ".";
        }
        return notes;
    }

    set highlight(value) {
        this._highlight = value;
    }

    get highlight() {
        if (this._highlight) {
            return this._highlight;
        } else if (this.has_good_test_results()) {
            return 1;
        } else if (!this.is_test_percentage() && this.test_status == 0) {
            return 1;
        } else {
            return 0;
        }
    }
}

class DB {
    constructor(language, db_contents) {
        this.db = {};
        this.language = language;

        const lines = db_contents.split('\n');
        let any_versions = {}

        for (const line of lines) {
            if (!line) {
                continue;
            }
            let [name, version, test_status, ...notes] = line.split(',');
            const entry = new DBEntry(name, version, test_status, notes.join(','));

            this.db[entry.name] ||= {};
            this.db[entry.name][entry.version] = merge_entries(entry, this.db[entry.name][entry.version]);

            if (entry.version == DB.ANY_VERSION) {
                any_versions[entry.name] = this.db[entry.name][entry.version];
            }
        }

        for (const name in any_versions) {
            for (const version in this.db[name]) {
                this.db[name][version] = merge_entries(any_versions[name], this.db[name][version]);
            }
        }

        function merge_entries(entry, previous_entry) {
            if (previous_entry) {
                if (!notes_overlap(previous_entry.notes, entry.notes)) {
                    previous_entry.highlight = Math.max(entry.highlight, previous_entry.highlight);
                    if (previous_entry.is_test_percentage() && previous_entry.has_no_test_results()) {
                        previous_entry.notes = entry.notes;
                    } else {
                        previous_entry.notes = entry.notes + " " + previous_entry.notes;
                    }
                }
                return previous_entry;
            } else {
                return entry;
            }
        }

        function notes_overlap(notes1, notes2) {
            return notes1.replace(/<[^>]+>/, "").includes(notes2.replace(/<[^>]+>/, ""));
        }
    }
}
