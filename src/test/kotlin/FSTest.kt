import fs.FSCreator
import fs.FSDirectory
import fs.FSEntry
import fs.FSFile
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FSTest {

    private val testDirectory = Paths.get("test")
    private val creator = FSCreator()

    inner class FileList {
        private val files = mutableListOf<Path>()

        fun file(path: Path) {
            path.createFile()
            files.add(path)
        }

        fun clear() {
            files.forEach {
                it.deleteExisting()
            }
        }
    }

    private fun withFiles(block: FileList.() -> Unit) {
        val list = FileList()
        list.block()
        list.clear()
    }

    @BeforeEach
    fun initDirectory() {
        testDirectory.createDirectory()
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun clearDirectory() {
        testDirectory.deleteRecursively()
    }

    private fun checkFile(entry: FSFile, file: Path) {
        assertEquals(Paths.get(entry.name).fileName, file.fileName)
        assertTrue { file.isRegularFile() }
        assertEquals(entry.content, file.readText())
    }

    private fun checkDirectory(entry: FSDirectory, directory: Path) {
        assertEquals(Paths.get(entry.name).fileName, directory.fileName)
        assertTrue { directory.isDirectory() }

        val entries = directory.listDirectoryEntries().sortedBy { it.name }
        val content = entry.content.sortedBy { it.name }

        assertEquals(content.size, entries.size)
        content.forEachIndexed { index, child ->
            assertEquals(child.name, entries[index].name)
            when (child) {
                is FSDirectory -> {
                    checkDirectory(child, entries[index])
                }

                is FSFile -> {
                    checkFile(child, entries[index])
                }
            }
        }
    }

    private fun checkSucceeded(root: FSEntry, errors: List<Pair<FSEntry, String>>, entryName: String = "") {
        assertTrue { errors.isEmpty() }

        val entries = testDirectory.listDirectoryEntries()
        val entryInd = if (entryName.isEmpty()) 0 else entries.indexOf(testDirectory.resolve(entryName))

        assertTrue { entryInd >= 0 }
        assertTrue { entries.size >= entryInd }

        val entry = entries[entryInd]

        when (root) {
            is FSDirectory -> {
                checkDirectory(root, entry)
            }

            is FSFile -> {
                checkFile(root, entry)
            }
        }
    }

    private fun checkFailed(errors: List<Pair<FSEntry, String>>) {
        assertTrue { errors.isNotEmpty() }
        assertTrue { testDirectory.listDirectoryEntries().isEmpty() }
    }

    @Test
    fun `single file is created`() {
        val root = FSFile("foo", "bar")
        checkSucceeded(root, creator.create(root, testDirectory.name))
    }

    @Test
    fun `single file is created with absolute directory path`() {
        val root = FSFile("foo", "bar")
        checkSucceeded(root, creator.create(root, testDirectory.absolutePathString()))
    }

    @Test
    fun `single file is created with absolute path and relative directory`() {
        val testAbsolutePath = testDirectory.resolve("foo").absolutePathString()
        val root = FSFile(testAbsolutePath, "bar")
        checkSucceeded(root, creator.create(root, testDirectory.name))
    }

    @Test
    fun `empty directory is created`() {
        val root = FSDirectory("foo", listOf())
        checkSucceeded(root, creator.create(root, testDirectory.name))
    }

    @Test
    fun `non-empty directory is created`() {
        val root = FSDirectory("foo", listOf(FSFile("bar", "bar"), FSFile("baz", "baz")))
        checkSucceeded(root, creator.create(root, testDirectory.name))
    }

    @Test
    fun `arbitrary complex directory`() {
        val root = FSDirectory(
            "foo",
            listOf(
                FSFile("bar1", "1"),
                FSDirectory(
                    "baz1", listOf(
                        FSFile("botva1", "1"),
                        FSFile("botva2", "2"),
                        FSDirectory("fuz", listOf())
                    )
                ),
                FSFile("bar2", "2"),
                FSFile("bar3", "3"),
                FSDirectory(
                    "dir1", listOf(
                        FSDirectory(
                            "dir2", listOf(
                                FSDirectory(
                                    "dir3", listOf(
                                        FSDirectory(
                                            "dir4", listOf(
                                                FSDirectory(
                                                    "dir4", listOf(
                                                        FSDirectory(
                                                            "dir3", listOf(
                                                                FSDirectory(
                                                                    "dir2", listOf(
                                                                        FSDirectory(
                                                                            "dir1", listOf(
                                                                                FSFile(
                                                                                    "nested",
                                                                                    "really-really nested file"
                                                                                )
                                                                            )
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        checkSucceeded(root, creator.create(root, testDirectory.name))
    }

    @Test
    fun `create entries inside file fails`() {
        val root = FSFile("foo", "foo")
        var errors: List<Pair<FSEntry, String>> = listOf()
        withFiles {
            file(testDirectory.resolve("foo"))
            errors = creator.create(root, testDirectory.resolve("foo").name)
        }
        checkFailed(errors)
    }

    @Test
    fun `create existing files fails`() {
        val root = FSFile("foo", "foo")
        var errors: List<Pair<FSEntry, String>> = listOf()
        withFiles {
            file(testDirectory.resolve("foo"))
            errors = creator.create(root, testDirectory.name)
        }
        checkFailed(errors)
    }

    @Test
    fun `create file with blank name fails`() {
        val root = FSFile("", "")
        checkFailed(creator.create(root, testDirectory.name))
    }

    @Test
    fun `create in blank target dir fails`() {
        val root = FSFile("foo", "foo")
        checkFailed(creator.create(root, ""))
    }

    @Test
    fun `nested incorrect entry does not create anything`() {
        val root = FSDirectory("foo", listOf(
            FSDirectory("bar", listOf(
                FSFile("", "baz")
            ))
        ))
        checkFailed(creator.create(root, testDirectory.name))
    }

    @Test
    fun `duplicate entries in same dir fails`() {
        val root = FSDirectory(
            "foo", listOf(
                FSFile("bar", "baz"),
                FSFile("bar", "bar")
            )
        )
        checkFailed(creator.create(root, testDirectory.name))
    }

    @Test
    fun `second create of same entry fails`() {
        val root = FSFile("foo", "foo")
        checkSucceeded(root, creator.create(root, testDirectory.name))
        assertTrue { root in creator.create(root, testDirectory.name).map { it.first } }
    }

    @Test
    fun `multiple create of different entries has no side effects`() {
        val root1 = FSFile("foo", "foo")
        val root2 = FSFile("bar", "bar")
        checkSucceeded(root1, creator.create(root1, testDirectory.name))
        checkSucceeded(root2, creator.create(root2, testDirectory.name))
    }

    @Test
    fun `async execution is possible`() = runBlocking(Dispatchers.IO) {
        val rootEntries = mutableListOf<FSEntry>()

        repeat(10000) {
            rootEntries.add(FSFile(it.toString(), it.toString()))
        }

        val d1 = FSDirectory("d1", rootEntries)
        val d2 = FSDirectory("d2", rootEntries)

        var errors1: List<Pair<FSEntry, String>> = listOf(Pair(d1, ""))
        var errors2: List<Pair<FSEntry, String>> = listOf(Pair(d2, ""))

        val j1 = launch {
            errors1 = creator.create(d1, testDirectory.name)
        }
        val j2 = launch {
            errors2 = creator.create(d2, testDirectory.name)
        }

        j1.join()
        j2.join()
        checkSucceeded(d1, errors1, "d1")
        checkSucceeded(d2, errors2, "d2")
    }

    @Test
    fun `cannot create files without permission`() {
        val homeEntries = Paths.get("/").listDirectoryEntries()
        val root = FSFile("foo", "foo")
        val errors = creator.create(root, "/")

        assertTrue { homeEntries == Paths.get("/").listDirectoryEntries() }
        assertTrue { errors.size == 1 }
        assertTrue { errors.last().first == root }
    }

    @Test
    fun `no attempt to create children if parent directory fails at runtime`() {
        val root = FSDirectory("foo", listOf(
            FSFile("bar1", "1"),
            FSDirectory("baz", listOf(
                FSFile("baz1", "1")
            )),
            FSFile("bar2", "2"),
            FSFile("bar3", "3")
        ))
        val errors = creator.create(root, "/")
        assertTrue { errors.size == 1 }
        assertTrue { errors.last().first == root }
    }

    @Test
    fun `no attempt to create children if parent directory is incorrect`() {
        val root = FSDirectory("", listOf(
            FSFile("bar1", "1"),
            FSFile("bar2", "2")
        ))
        val errors = creator.create(root, testDirectory.name)
        assertTrue { errors.size == 1 }
        assertTrue { errors.last().first == root }
    }
}