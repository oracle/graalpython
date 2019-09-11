def find_on_path(importer, path_item, only=False):
    if _is_unpacked_egg(path_item):
        yield Distribution.from_filename(
            path_item, metadata=PathMetadata(
                path_item, os.path.join(path_item, 'EGG-INFO')
            )
        )
        return

    entries = safe_listdir(path_item)

    filtered = (
        entry
        for entry in entries
        if dist_factory(path_item, entry, only)
    )

    path_item_entries = _by_version_descending(filtered)
    for entry in path_item_entries:
        fullpath = os.path.join(path_item, entry)
        factory = dist_factory(path_item, entry, only)
        for dist in factory(fullpath):
            yield dist
