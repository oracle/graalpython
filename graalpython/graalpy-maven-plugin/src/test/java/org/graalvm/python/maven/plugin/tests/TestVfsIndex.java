package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestVfsIndex extends GraalPyPluginTests {
    @Test
    public void testVfsIndex() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("list_files_test");
        var vfsPath = Path.of(v.getBasedir(), "target", "classes", "vfs");
        v.addCliArguments("process-resources");
        v.execute();
        var vfsList = vfsPath.resolve("fileslist.txt");
        assertTrue(Files.exists(vfsList));
        var lines = new HashSet<String>(Files.readAllLines(vfsList));
        var linesStr = String.join("\n", lines);
        assertTrue(lines.contains("/vfs/"), linesStr);
        assertTrue(lines.contains("/vfs/home/"), linesStr);
        assertTrue(lines.contains("/vfs/home/dir_with_file/"), linesStr);
        assertTrue(lines.contains("/vfs/home/dir_with_file/file.txt"), linesStr);
        assertFalse(lines.contains("/vfs/home/dir_with_file/empty_dir/"), linesStr);
        assertFalse(lines.contains("/vfs/home/empty_dir/"), linesStr);
        assertFalse(lines.contains("/vfs/home/empty_dir/empty_dir/"), linesStr);
        assertEquals(4, lines.size(), linesStr);
    }
}
