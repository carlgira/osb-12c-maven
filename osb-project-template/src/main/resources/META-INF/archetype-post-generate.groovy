import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

Path projectPath = Paths.get(request.outputDirectory, request.artifactId)
// RENAME ROOT FOLDER INCLUDING A PREFIX FOR EXAMPLE
Files.move(projectPath, projectPath.resolveSibling(projectPath.toFile().getName() + "-app"))
