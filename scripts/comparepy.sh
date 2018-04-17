function info {
	local msg=$1
	echo "-----------------------------------------------------------------------------------------"
	echo $msg
	echo "-----------------------------------------------------------------------------------------"
}

function graalpython {
    info "GRAAL Python"
    //mx --dynamicimports /tools-enterprise python -c "$1"
    mx python -c "$1"
}

function cpython {
    info "CPython"
    python3 -c "$1"
}

args="$1"

cpython "${args}"
echo
graalpython "${args}"
