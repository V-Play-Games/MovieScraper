import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

suspend fun <T, R> List<T>.executeTask(
    taskIdentifier: String,
    threadCount: Int = Runtime.getRuntime().availableProcessors(),
    progressBarWidth: Int = 50,
    taskProcessor: suspend (T) -> R
): List<TaskResult<T, R>> = coroutineScope {
    val tasks = this@executeTask
    Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher().use { dispatcher ->
        val startTime = System.currentTimeMillis()
        val totalTasks = tasks.size
        val header = "Task: $taskIdentifier | Count: $totalTasks | Threads: $threadCount"

        val progressChannel = Channel<Int>(Channel.BUFFERED)
        val completedCounter = AtomicInteger(0)
        // Launch progress tracking coroutine if needed
        val progressJob = launch {
            for (update in progressChannel) {
                val completed = completedCounter.incrementAndGet()
                val percent = completed * 100 / totalTasks
                val completeWidth = progressBarWidth * percent / 100
                val remainingWidth = progressBarWidth - completeWidth
                val bar = "[" + "=".repeat(completeWidth) + " ".repeat(remainingWidth) + "]"

                print("\r$header | Progress: $bar $percent%")
            }
        }


        // Execute tasks
        val results = withContext(dispatcher) {
            tasks.map { task ->
                async {
                    try {
                        val result = taskProcessor(task)
                        progressChannel.send(1)
                        TaskResult.Success(task, result)
                    } catch (e: Exception) {
                        progressChannel.send(1)
                        TaskResult.Failure(task, e)
                    }
                }
            }.awaitAll()
        }
        progressChannel.close()
        progressJob.join()

        val endTime = System.currentTimeMillis()

        val successful = results.count { it is TaskResult.Success }
        val failed = results.count { it is TaskResult.Failure }
        println(
            "\r$header" +
                    " | Completed in ${(endTime - startTime) / 1000.0} seconds" +
                    " | Results: \u001B[32m$successful successful\u001B[0m, \u001B[31m$failed failed\u001B[0m"
        )

        return@coroutineScope results
    }
}

suspend fun <T> List<T>.executeScrapeTask(
    taskIdentifier: String,
    cacheFolderName: String,
    threadCount: Int = Runtime.getRuntime().availableProcessors(),
    progressBarWidth: Int = 50,
    urlFileProcessor: suspend (T) -> Pair<String, String>
): List<TaskResult<T, File>> {
    val cacheFolder = File("scrape-cache", cacheFolderName).apply {
        if (!exists()) mkdirs()
    }
    return this.executeTask(taskIdentifier, threadCount, progressBarWidth) { task ->
        val (url, fileName) = urlFileProcessor(task)
        val file = File(cacheFolder, fileName)
        if (!file.exists()) {
            file.writeText(URI(url).toURL().readText())
        }
        return@executeTask file
    }
}

fun <T, R> List<TaskResult<T, R>>.filterSuccessful() = filterIsInstance<TaskResult.Success<T, R>>()

sealed class TaskResult<T, R> {
    data class Success<T, R>(val task: T, val result: R) : TaskResult<T, R>()

    data class Failure<T, R>(val task: T, val error: Exception) : TaskResult<T, R>()
}