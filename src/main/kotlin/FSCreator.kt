package fs

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedList
import java.util.Queue
import kotlin.io.path.*

class FSCreator {
    /**
     * Helper class to encapsulate state during creation of one entry and perform actual validation and creation.
     * Its main purpose is to support trivial concurrent creation of entries in different directories.
     */
    inner class FSVisitor {
        private val creationQueue: Queue<Pair<FSEntry, Path>> = LinkedList()
        private val visitedDirectories: MutableSet<FSDirectory> = mutableSetOf()
        val errors: MutableList<Pair<FSEntry, String>> = mutableListOf()

        private fun FSEntry.error(error: String) = errors.add(Pair(this, error))
        private fun FSEntry.create(path: Path) = creationQueue.add(Pair(this, path))

        fun create() {
            require(visitedDirectories.isEmpty())
            require(creationQueue.isNotEmpty())
            require(errors.isEmpty())

            while (creationQueue.isNotEmpty()) {
                val (entry, path) = creationQueue.remove()
                create(entry, path)
            }
        }

        fun validate(root: FSEntry, destination: String): List<Pair<FSEntry, String>> {
            if (destination.isBlank()) {
                root.error("Destination path string is blank.")
                return errors
            }

            if (!FSUtils.isValidPathString(destination)) {
                root.error("Destination path is not a valid directory name.")
                return errors
            }

            val path = Paths.get(destination)

            if (!path.isDirectory()) {
                root.error("Destination does not correspond to a directory.")
                return errors
            }

            val rootFiles = mutableSetOf<String>()

            validate(root, path, rootFiles)

            visitedDirectories.clear()

            return errors
        }

        private fun validate(
            entry: FSEntry,
            destination: Path,
            existingFiles: MutableSet<String>,
        ) {
            if (entry.name.isBlank()) {
                entry.error("Entry name is blank.")
                return
            }

            if (!FSUtils.isValidPathString(entry.name)) {
                entry.error("Entry name is not a valid file name.")
                return
            }

            when (entry) {
                is FSDirectory -> validateDirectory(entry, destination, existingFiles)
                is FSFile -> validateFile(entry, destination, existingFiles)
            }
        }

        private fun create(entryToCreate: FSEntry, destination: Path) {
            when (entryToCreate) {
                is FSDirectory -> createDirectory(entryToCreate, destination)
                is FSFile -> createFile(entryToCreate, destination)
            }
        }

        private fun validateFile(
            fsFile: FSFile,
            destination: Path,
            existingFiles: MutableSet<String>,
        ) {
            val filePath = destination.resolve(fsFile.name)

            if (filePath.exists()) {
                fsFile.error("File with the same name already exists on disk.")
                return
            }

            if (fsFile.name in existingFiles) {
                fsFile.error("File with the same name was already declared in the parent directory.")
                return
            }

            existingFiles.add(fsFile.name)
            fsFile.create(filePath)
        }

        private fun validateDirectory(
            fsDirectory: FSDirectory,
            destination: Path,
            existingFiles: MutableSet<String>,
        ) {
            if (fsDirectory in visitedDirectories) {
                fsDirectory.error("Detected circular dependency with the directory.")
                return
            }

            visitedDirectories.add(fsDirectory)
            val directoryPath = destination.resolve(fsDirectory.name)

            val errorsSize = errors.size

            if (directoryPath.exists()) {
                fsDirectory.error("Directory with the same name already exists on disk.")
            }

            if (fsDirectory.name in existingFiles) {
                fsDirectory.error("Directory with the same name was already declared in the parent directory.")
            }

            existingFiles.add(fsDirectory.name)

            if (errorsSize == errors.size) {
                fsDirectory.create(directoryPath)
            }

            val directoryFiles = mutableSetOf<String>()

            fsDirectory.content.forEach {
                validate(it, directoryPath, directoryFiles)
            }
        }

        private fun createFile(fsFile: FSFile, filePath: Path) {
            val errorsSize = errors.size

            try {
                filePath.createFile()
            } catch (e: FileAlreadyExistsException) {
                fsFile.error("File with the same name exists on the disk.")
            } catch (e: UnsupportedOperationException) {
                fsFile.error("Could not create file with default attributes.")
            } catch (e: SecurityException) {
                fsFile.error("Insufficient permissions to create the file.")
            } catch (e: IOException) {
                fsFile.error("An unexpected error occurred while creating the file.")
            }

            if (errorsSize == errors.size) {
                try {
                    filePath.writeBytes(fsFile.content.encodeToByteArray())
                } catch (e: IOException) {
                    fsFile.error("An unexpected error occurred while writing content to the file.")
                }
            }
        }

        private fun createDirectory(fsDirectory: FSDirectory, directoryPath: Path) {
            val errorsSize = errors.size

            try {
                directoryPath.createDirectory()
            } catch (e: FileAlreadyExistsException) {
                fsDirectory.error("Directory with the same name exists on the disk.")
            } catch (e: UnsupportedOperationException) {
                fsDirectory.error("Could not create directory with default attributes")
            } catch (e: SecurityException) {
                fsDirectory.error("Insufficient permissions to create the directory.")
            } catch (e: IOException) {
                fsDirectory.error("An unexpected error occurred while creating the directory.")
            }

            if (errorsSize != errors.size) {
                while (creationQueue.isNotEmpty() && creationQueue.peek().second.startsWith(directoryPath)) {
                    creationQueue.remove()
                }
            }
        }
    }

    /**
     * Recursively create an entry specified by [entryToCreate] in the filesystem directory specified by [destination] string.
     * This process is performed in two steps:
     * - "Static" validation: creator will ensure that all given entries are correct, i.e. have valid names, do not have
     * duplicates in the [destination] or duplicates in the same [FSDirectory]. If any of those are found, no files will be created and a list of errors will be returned.
     * - Creation: given a correct description of entries, they will be created in order of their specification in [entryToCreate].
     * If any "runtime" errors occur, they will be reported after the creation finished in the same format.

     * @param entryToCreate an [FSEntry] describing desired files and directories to be created
     * @param destination a string specifying path to existing directory where [entryToCreate] should be created
     * @return a list of errors found during validation stage or happened during creation stage in format (erroneousEntry, errorDescription)
     **/
    fun create(entryToCreate: FSEntry, destination: String): List<Pair<FSEntry, String>> {
        val visitor = FSVisitor()

        val staticErrors = visitor.validate(entryToCreate, destination)
        if (staticErrors.isNotEmpty()) {
            return staticErrors
        }

        visitor.create()
        return visitor.errors
    }
}