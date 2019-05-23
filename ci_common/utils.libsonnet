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
}