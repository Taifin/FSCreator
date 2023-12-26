package fs

import java.nio.file.InvalidPathException
import java.nio.file.Paths

object FSUtils {
    fun isValidPathString(string: String) =
        try {
            Paths.get(string)
            true
        } catch (e: InvalidPathException) {
            false
        }
}