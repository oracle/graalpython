# Python GC Graph Generator:

```sh
python scripts/GC_Graph/generate_gc_graph.py -h                                                                                                                                             
usage: generate_gc_graph.py [-h] [-r] [-t] [-n TYPENAME] [--extra-info EXTRA_INFO] [-l LIMIT] FILENAME

Generate GC Graphs on Cytoscape.

positional arguments:
  FILENAME              Path of the json file to Generate GC Graphs

optional arguments:
  -h, --help            show this help message and exit
  -r, --reverse         Reverse order of the Generated GC Graphs
  -t, --traverse-only   Generate GC Graphs without internal routines
  -n TYPENAME, --typename TYPENAME
                        Select only nodes with the provided type name [Exact] and their parent and childs
  --extra-info EXTRA_INFO
                        Add extra info from additional json files
  -l LIMIT, --limit LIMIT
                        Maximum number of nodes per graph
```

## Apply `gcmodule.c` patch:


* Add helpers (GraalPy or CPython):

```c
static inline int
call_traverse(traverseproc traverse, PyObject *op, visitproc visit, void *arg) {
        return traverse(op, (visitproc)visit, arg);
}

#ifndef GRAALVM_PYTHON
    #define CALL_TRAVERSE(traverse, op, visit_fun, ctx) ((void) call_traverse((traverse), (op), (visitproc)(visit_fun), (ctx)))
    #define UNTAG(gc) gc
    #define is_managed(gc) 0
    #define IMPL_NAME "cpy_"
#else
    #define IMPL_NAME "graalpy_"
#endif


static const char* graph_legend = "\"legend\": {" \ 
                                "\"n\": \"name\", " \
                                "\"gc\": \"gc address\", " \
                                "\"t\": \"type name\", " \
                                "\"ntv\": \"is native\", " \
                                "\"rc\": \"ref count\", " \
                                "\"gr\": \"gc refcount\", " \
                                "\"nr\": \"is unreachable\", " \
                                "\"ct\": \"is collecting\"" \
                                "},\n";


static long log_time_stamp = NULL;
static int log_enabled = -1;
static FILE *gc_graph_file = NULL;

static int is_gc_graph_enabled() {
    if (log_enabled == -1) {
      log_enabled = getenv("GC_GRAPH") == NULL ? 0 : 1;
    }
    return log_enabled;
}

static FILE *
get_gc_graph_file() {
    if(is_gc_graph_enabled() && !gc_graph_file) {
        if (!log_time_stamp) {
            log_time_stamp = time(NULL);
        }
        char *fname;
        asprintf(&fname, IMPL_NAME "gc_graph_%ld.json", log_time_stamp);
        gc_graph_file = fopen(fname, "a");
        fprintf(gc_graph_file, "{");
        fprintf(gc_graph_file, graph_legend);
        fprintf(gc_graph_file, "\"runs\": [[\"gc graphs\"]\n");

    }
    return gc_graph_file;
}

static void close_log() {
    if(is_gc_graph_enabled()) {
        fclose(gc_graph_file);
        gc_graph_file = NULL;
    }
}

#define BASIC_INFO 1
#define REFCOUNT_INFO 2
#define GC_REFCOUNT_INFO 4
#define IS_REACHABLE_INFO 8
#define IS_COLLECTING_INFO 16

static void
write_item_ptr(FILE *fp, int add_comma, const char* key, void* val) {
    fprintf(fp, "%s\"%s\": %p ", add_comma ? "" : ",", key, val);
}

static void
write_item_bool(FILE *fp, int add_comma, const char* key, int val) {
    fprintf(fp, "%s\"%s\": %d ", add_comma ? "" : ",", key, val);
}

static void
write_item_str(FILE *fp, int add_comma, const char* key, const char* val) {
    fprintf(fp, "%s\"%s\": %s ", add_comma ? "" : ",", key, val);
}

static void
write_item_size_t(FILE *fp, int add_comma, const char* key, Py_ssize_t val) {
    fprintf(fp, "%s\"%s\": %ld ", add_comma ? "" : ",", key, val);
}

static void
print_node(PyGC_Head *gc, int is_first_node, int opt) {
    FILE *fp = get_gc_graph_file();
    PyObject *op = FROM_GC(gc);
    fprintf(fp, "%s\"%p\": { ", is_first_node ? "" : ",", op);
    int is_first_info = 1;
    if ((opt & BASIC_INFO) != 0) {
        write_item_ptr(fp, is_first_info, "n", op);
        is_first_info = 0;
        write_item_ptr(fp, is_first_info, "gc", gc);
        write_item_str(fp, is_first_info, "t", Py_TYPE(op)->tp_name);
        write_item_bool(fp, is_first_info, "ntv", !is_managed(gc) ? 1 : 0);
    }
    if ((opt & REFCOUNT_INFO) != 0) {
        write_item_size_t(fp, is_first_info, "rc", Py_REFCNT(op));
        is_first_info = 0;
    }
    if ((opt & GC_REFCOUNT_INFO) != 0) {
        write_item_size_t(fp, is_first_info, "gr", gc_get_refs(gc));
        is_first_info = 0;
    }
    if ((opt & IS_REACHABLE_INFO) != 0) {
        int is_unreachable = (UNTAG(gc)->_gc_next & NEXT_MASK_UNREACHABLE) != 0 ? 1 : 0;
        write_item_bool(fp, is_first_info, "nr", is_unreachable);
        is_first_info = 0;
    }
    if ((opt & IS_COLLECTING_INFO) != 0) {
        write_item_bool(fp, is_first_info, "ct", gc_is_collecting(gc) ? 1 : 0);
        is_first_info = 0;
    }
    fprintf(fp, "}\n");
    fflush(fp);
}

static void
print_edge(int is_first, PyObject *src, PyObject *dst) {
    fprintf(get_gc_graph_file(), "%s[\"%p\", \"%p\"]\n", is_first ? "" : ",", src, dst);
    fflush(get_gc_graph_file());
}

static void print_section(const char* section, const char* name, int new_gc_collect, int is_start, int is_array) {
    FILE *fp = get_gc_graph_file();
    if(is_gc_graph_enabled()) {
        if (is_start) {
            if (new_gc_collect) {
                fprintf(fp, ",[\"%s\", \"%s\"\n", section, name);
            } else {
                fprintf(fp, ",[\"%s\", \"%s\", %s\n", section, name, is_array ? "[" : "{");
            }
        } else {
            if (new_gc_collect) {
                fprintf(fp, "]\n");
            } else {
                fprintf(fp, "%s\n]\n", is_array ? "]" : "}");
            }
        }
        fflush(fp);
    }
}

static int
visit_discover_edges(PyObject *op, void *parent)
{
    if (_PyObject_IS_GC(op)) {
        print_edge(0, (PyObject *)parent, op);
    }
    return 0;
}

static void
traverse_gc_objects(PyGC_Head *containers, const char* name) {
    if(is_gc_graph_enabled() && containers && GC_NEXT(containers)) {
        traverseproc traverse;
        PyGC_Head *gc;
        print_section("nodes", name, 0, 1, 0);
        int is_first_node = 1;
        for (gc = GC_NEXT(containers); gc != containers; gc = GC_NEXT(gc)) {
            PyObject *op = FROM_GC(gc);
            print_node(gc, is_first_node, BASIC_INFO | REFCOUNT_INFO);
            is_first_node = 0;
        }
        print_section("nodes", name, 0, 0, 0);

        print_section("edges", name, 0, 1, 1);
        print_edge(1, NULL, NULL);
        for (gc = GC_NEXT(containers); gc != containers; gc = GC_NEXT(gc)) {
            PyObject *op = FROM_GC(gc);
            traverse = Py_TYPE(op)->tp_traverse;
            CALL_TRAVERSE(traverse, op, visit_discover_edges, (void *)op);
        }
        print_section("edges", name, 0, 0, 1);
    }
}

#define GC_NEXT_UNMASK_UNREACHABLE(container) (PyGC_Head*)(UNTAG(container)->_gc_next & ~NEXT_MASK_UNREACHABLE);

static void
query_gc_objects(PyGC_Head *containers, int do_unmask_unreachable, const char* location, const char* name, int info_opt) {
    if(is_gc_graph_enabled() && containers) {
        print_section(location, name, 0, 1, 0);
        if (!gc_list_is_empty(containers)) {
            PyGC_Head *next, *gc = !do_unmask_unreachable? GC_NEXT(containers) : GC_NEXT_UNMASK_UNREACHABLE(containers);
            int is_first_node = 1;
            while (gc != containers) {
                next = !do_unmask_unreachable? GC_NEXT(gc) : GC_NEXT_UNMASK_UNREACHABLE(gc);
                print_node(gc, is_first_node, info_opt);
                gc = next;
                is_first_node = 0;
            }
        }
        print_section(location, name, 0, 0, 0);
    }
}

```

* GraalPy patch

```diff
@@ -1037,6 +1214,8 @@ move_legacy_finalizers(PyGC_Head *unreachable, PyGC_Head *finalizers)
             gc_list_move(gc, finalizers);
         }
     }
+    query_gc_objects(unreachable, 0, "move_legacy_finalizers", "unreachable", GC_REFCOUNT_INFO);
+    query_gc_objects(finalizers, 0, "move_legacy_finalizers", "finalizers", GC_REFCOUNT_INFO);
 }
 
 static inline void
@@ -1243,6 +1422,7 @@ handle_weakrefs(PyGC_Head *unreachable, PyGC_Head *old)
          * dict, leaving no other references to the weakref (excepting
          * ours).
          */
+        printf("[%p][%ld] %s\n", op, Py_REFCNT(op), Py_TYPE(op)->tp_name);fflush(stdout);
         Py_DECREF(op);
         if (wrcb_to_call._gc_next == (uintptr_t)gc) {
             /* object is still alive -- move it */
@@ -1448,7 +1628,9 @@ deduce_unreachable(PyGC_Head *base, PyGC_Head *unreachable) {
      * set are taken into account).
      */
     update_refs(base);  // gc_prev is used for gc_refs
+    query_gc_objects(base, 0, "update_refs", "base", GC_REFCOUNT_INFO | IS_COLLECTING_INFO);
     subtract_refs(base);
+    query_gc_objects(base, 0, "subtract_refs", "base", GC_REFCOUNT_INFO);
 
     /* Leave everything reachable from outside base in base, and move
      * everything else (in base) to unreachable.
@@ -1488,6 +1670,9 @@ deduce_unreachable(PyGC_Head *base, PyGC_Head *unreachable) {
     gc_list_init(unreachable);
     gc_list_init(&weak_candidates);
     move_unreachable(base, unreachable, &weak_candidates);  // gc_prev is pointer again
+    query_gc_objects(base, 0, "move_unreachable", "base", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
+    query_gc_objects(unreachable, 1, "move_unreachable", "unreachable", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
+    query_gc_objects(&weak_candidates, 1, "move_unreachable", "weak_candidates", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
     validate_list(base, collecting_clear_unreachable_clear);
     validate_list(&weak_candidates, collecting_clear_unreachable_clear);
     validate_list(unreachable, collecting_set_unreachable_set);
@@ -1511,6 +1696,7 @@ deduce_unreachable(PyGC_Head *base, PyGC_Head *unreachable) {
         update_refs(base);  // gc_prev is used for gc_refs
         subtract_refs(base);
         move_weak_reachable(base, &weak_candidates);
+        query_gc_objects(&weak_candidates, 1, "move_weak_reachable", "weak_candidates", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
         commit_weak_candidate(&weak_candidates);
     }
 }
@@ -1543,6 +1729,8 @@ handle_resurrected_objects(PyGC_Head *unreachable, PyGC_Head* still_unreachable,
     deduce_unreachable(resurrected, still_unreachable);
     clear_unreachable_mask(still_unreachable);
 
+    query_gc_objects(resurrected, 0, "handle_resurrected_objects", "resurrected", GC_REFCOUNT_INFO);
+    query_gc_objects(still_unreachable, 0, "handle_resurrected_objects", "still_unreachable", GC_REFCOUNT_INFO);
     // Move the resurrected objects to the old generation for future collection.
     gc_list_merge(resurrected, old_generation);
 }
@@ -1554,6 +1742,7 @@ gc_collect_main(PyThreadState *tstate, int generation,
                 Py_ssize_t *n_collected, Py_ssize_t *n_uncollectable,
                 int nofail)
 {
+    is_gc_graph_enabled();
     int i;
     Py_ssize_t m = 0; /* # objects collected */
     Py_ssize_t n = 0; /* # unreachable objects that couldn't be collected */
@@ -1570,6 +1759,8 @@ gc_collect_main(PyThreadState *tstate, int generation,
         return m + n;
     }
 
+    print_section("gc_collect_main", "", 1, 1, 0);
+
     // gc_collect_main() must not be called before _PyGC_Init
     // or after _PyGC_Fini()
     assert(gcstate->garbage != NULL);
@@ -1604,6 +1795,7 @@ gc_collect_main(PyThreadState *tstate, int generation,
         old = young;
     validate_list(old, collecting_clear_unreachable_clear);
 
+    traverse_gc_objects(young, "young");
     deduce_unreachable(young, &unreachable);
 
     untrack_tuples(young);
@@ -1689,6 +1881,8 @@ gc_collect_main(PyThreadState *tstate, int generation,
      */
     handle_legacy_finalizers(tstate, gcstate, &finalizers, old);
     validate_list(old, collecting_clear_unreachable_clear);
+    print_section("gc_collect_main", "gc_collect_main", 1, 0, 0);
+    log_enabled = -1;
 
     /* Clear free list only during the collection of the highest
      * generation */

```

* CPython 3.11.7 patch

```diff
@@ -690,6 +873,8 @@ move_legacy_finalizers(PyGC_Head *unreachable, PyGC_Head *finalizers)
             gc_list_move(gc, finalizers);
         }
     }
+    query_gc_objects(unreachable, 0, "move_legacy_finalizers", "unreachable", GC_REFCOUNT_INFO);
+    query_gc_objects(finalizers, 0, "move_legacy_finalizers", "finalizers", GC_REFCOUNT_INFO);
 }
 
 static inline void
@@ -1097,7 +1282,9 @@ deduce_unreachable(PyGC_Head *base, PyGC_Head *unreachable) {
      * set are taken into account).
      */
     update_refs(base);  // gc_prev is used for gc_refs
+    query_gc_objects(base, 0, "update_refs", "base", GC_REFCOUNT_INFO | IS_COLLECTING_INFO);
     subtract_refs(base);
+    query_gc_objects(base, 0, "subtract_refs", "base", GC_REFCOUNT_INFO);
 
     /* Leave everything reachable from outside base in base, and move
      * everything else (in base) to unreachable.
@@ -1136,6 +1323,8 @@ deduce_unreachable(PyGC_Head *base, PyGC_Head *unreachable) {
      */
     gc_list_init(unreachable);
     move_unreachable(base, unreachable);  // gc_prev is pointer again
+    query_gc_objects(base, 0, "move_unreachable", "base", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
+    query_gc_objects(unreachable, 1, "move_unreachable", "unreachable", GC_REFCOUNT_INFO | IS_REACHABLE_INFO);
     validate_list(base, collecting_clear_unreachable_clear);
     validate_list(unreachable, collecting_set_unreachable_set);
 }
@@ -1168,6 +1357,8 @@ handle_resurrected_objects(PyGC_Head *unreachable, PyGC_Head* still_unreachable,
     deduce_unreachable(resurrected, still_unreachable);
     clear_unreachable_mask(still_unreachable);
 
+    query_gc_objects(resurrected, 0, "handle_resurrected_objects", "resurrected", GC_REFCOUNT_INFO);
+    query_gc_objects(still_unreachable, 0, "handle_resurrected_objects", "still_unreachable", GC_REFCOUNT_INFO);
     // Move the resurrected objects to the old generation for future collection.
     gc_list_merge(resurrected, old_generation);
 }
@@ -1179,6 +1370,7 @@ gc_collect_main(PyThreadState *tstate, int generation,
                 Py_ssize_t *n_collected, Py_ssize_t *n_uncollectable,
                 int nofail)
 {
+    is_gc_graph_enabled();
     int i;
     Py_ssize_t m = 0; /* # objects collected */
     Py_ssize_t n = 0; /* # unreachable objects that couldn't be collected */
@@ -1190,6 +1382,7 @@ gc_collect_main(PyThreadState *tstate, int generation,
     _PyTime_t t1 = 0;   /* initialize to prevent a compiler warning */
     GCState *gcstate = &tstate->interp->gc;
 
+    print_section("gc_collect_main", "", 1, 1, 0);
     // gc_collect_main() must not be called before _PyGC_Init
     // or after _PyGC_Fini()
     assert(gcstate->garbage != NULL);
@@ -1223,6 +1416,7 @@ gc_collect_main(PyThreadState *tstate, int generation,
         old = young;
     validate_list(old, collecting_clear_unreachable_clear);
 
+    traverse_gc_objects(young, "young");
     deduce_unreachable(young, &unreachable);
 
     untrack_tuples(young);
@@ -1306,6 +1500,8 @@ gc_collect_main(PyThreadState *tstate, int generation,
      */
     handle_legacy_finalizers(tstate, gcstate, &finalizers, old);
     validate_list(old, collecting_clear_unreachable_clear);
+    print_section("gc_collect_main", "gc_collect_main", 1, 0, 0);
+    log_enabled = -1;
 
     /* Clear free list only during the collection of the highest
      * generation */
```

## Generating GC Graphs:

Enable GC graph generator around the area of interest in the code by setting environment `GC_GRAPH`. This will help reduce the size of the generated graph and provide a faster lookup for the objects that need to be investigated.

e.g.
```python
import os

...
os.environ['GC_GRAPH'] = '1'
# do something
os.environ.pop('GC_GRAPH')

```

After executing the program, a json file will be produced `*py_gc_graph_####.json` that can be processed using `generate_gc_graph.py`:

```sh
python scripts/GC_Graph/generate_gc_graph.py /path/to/*py_gc_graph_####.json
```

To add more information about objects, e.g. `size`, the target package need to also be modified/patched to generate json files that maps objects address with the additional information.

e.g.
```json
{
    "0x55e90cf22868": { "size": 3397386240 },
    ...
}
```

an example of patch that produce size information:

* PyTorch 2.2.1 patch

```diff
diff --git a/torch/csrc/autograd/python_variable.cpp b/torch/csrc/autograd/python_variable.cpp
index ba0e913896..e76a1a9ca0 100644
--- a/torch/csrc/autograd/python_variable.cpp
+++ b/torch/csrc/autograd/python_variable.cpp
@@ -1869,6 +1869,38 @@ void THPVariable_subclass_dealloc(PyObject* self) {
   Py_DECREF(type);
 }
 
+#if defined(GRAALVM_PYTHON)
+  #define IMPL_NAME "graalpy_"
+#else
+  #define IMPL_NAME "cpy_"
+#endif
+
+long log_time_stamp = 0;
+int log_enabled = -1;
+int obj_size = 1024 * 1024 * 512; // > 512 MB
+FILE *pytorch_file = NULL;
+static int is_gc_graph_enabled() {
+    if (log_enabled == -1) {
+      log_enabled = getenv("GC_GRAPH") == NULL ? 0 : 1;
+      obj_size = getenv("TENSOR_SIZE") == NULL ? obj_size : atoi(getenv("TENSOR_SIZE"));
+    }
+    return log_enabled;
+}
+
+static FILE *
+get_pytorch_file() {
+    if(log_enabled && !pytorch_file) {
+        if (!log_time_stamp) {
+            log_time_stamp = time(NULL);
+        }
+        char *fname;
+        asprintf(&fname, IMPL_NAME "pytorch_%ld.json", log_time_stamp);
+        pytorch_file = fopen(fname, "a");
+        fprintf(pytorch_file, "%s", "{\n");
+    }
+    return pytorch_file;
+}
+
 // Creates a new Python object for a Variable.  The status parameter
 // specifies what the interpreter tag status on the object is; for
 // example, if you ran check_pyobj, the return optional of this object
@@ -1978,6 +2010,13 @@ static PyObject* THPVariable_NewWithVar(
         var.unsafeGetTensorImpl()->set_python_dispatch(true);
       }
     }
+
+    size_t size = THPVariable_Unpack(v).nbytes();
+    if (is_gc_graph_enabled() && size > obj_size) {
+      fprintf(get_pytorch_file(), "\t\"%p\": { \"size\": \"%ld\" },\n", obj, size);
+      fflush(get_pytorch_file());
+    }
+
   }
   return obj;
 }
```

Then the produced json `*py_pytorch_#####.json` can then be passed using `--extra-info`:

```sh
python scripts/GC_Graph/generate_gc_graph.py --extra-info /path/to/*py_pytorch_#####.json /path/to/*py_gc_graph_####.json
```
