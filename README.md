# FSCreator

A simple library for declaring and creating files and directories.

### Usage

To declare a file, create a new instance of `FSFile` class and give it a `name` and some `content`:

```kotlin
val file = FSFile("my_file", "myContent")
```

To declare a directory, create a new instance of `FSDirectory` class with a `name` and list of child entries -- they can
be either files or directories as well:

```kotlin
val directory = FSDirectory(
    "my_directory", listOf(
        FSFile("nested_file", "in directory"),
        FSDirectory("nested_directory", listOf())
    )
)
```

Declared entries then can be created using `FSCreator` class:

```kotlin
val creator = FSCreator()
val destination = "/home/me/test"
creator.create(directory, destination)
```

Creator will check your hierarchy for errors and either report them to you in form of a list containing pairs
of `(errorneousEntry, errorDescription)` or try to create it if no errors are found. However, something may go wrong
during the creation process -- in this case, creator will also report entries that were not created along with the
errors occurred.

Possible errors that prevent creation:

* Entry name is empty or cannot be converted to filesystem path
* `destination` is empty, or cannot be converted to path, or is not a directory in the filesystem
* There are several entries with the same name in the same directory
* For some entries there are files with the same name in the `destination` directory
* Circular reference in the hierarchy (e.g. directory `foo` contains directory `bar`, which contains the same
  directory `foo` and hence itself)

Possible "runtime" errors:

* There happened to exist a file with the same name as the entry name
* Process does not have sufficient privileges to create files in the `destination` directory
* Some unexpected IO error occurred
