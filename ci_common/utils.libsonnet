{
    // return a download specific configuration
    download(name, version, platformSpecific = true)::
          {name: name, version: version, platformspecific: platformSpecific},

    // extract value from object
    getValue(object, field)::
        if (!std.objectHas(object, field)) then
            error "unknown field: "+field+" in "+object+", valid choices are: "+std.objectFields(object)
        else
            object[field],

    // add a graal option
    graalOption(name, value)::
        ["--Ja", "@-Dgraal."+name+"="+value],

    // utility for array contains
    contains(array, value)::
        std.count(array, value) > 0,

    // validation
    isGraalVmVm(name)::
        std.startsWith(name, "graalvm_"),

    isGraalPythonVm(name)::
        std.startsWith(name, "graalpython"),

    isBaselineVm(name)::
        std.startsWith(name, "cpython") || std.startsWith(name, "pypy"),

    local isBuilder = function(builder)
        std.objectHas(builder, "run"),
    isBuilder:: isBuilder,

    // make a builder run with a given primary suite
    withPrimarySuite(suite, builder)::
        if (std.type(builder) == 'array') then [
                b + (if isBuilder(b) then {environment +: {'MX_PRIMARY_SUITE_PATH': suite}} else {})
                for b in builder
            ]
        else
            builder + (if (isBuilder(builder)) then {environment +: {'MX_PRIMARY_SUITE_PATH': suite}} else {}),
}