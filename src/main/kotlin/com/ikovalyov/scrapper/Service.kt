package com.ikovalyov.scrapper

import io.ktor.client.call.receive
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.toURI
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils

@Suppress("BlockingMethodInNonBlockingContext")
class Service {
    private val worker = Worker()
    private val processedUrls: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    private val backgroundDispatcher = newFixedThreadPoolContext(4, "App Background")

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun startWorker(entryPoint: String, destinationPath: String) {
        val entryHost = Url(entryPoint).toURI().host
        var urlList = listOf(entryPoint)
        var i = 0
        val chunk = 40
        withContext(backgroundDispatcher) {
            while (urlList.isNotEmpty()) {
                i = 0
                urlList = urlList.chunked(chunk).map {
                    println("${processedUrls.count()} processed, ${urlList.count() - i * chunk} in queue")
                    i++
                    it.map {
                        async {
                            processedUrls.add(it)
                            processPage(it, entryHost, destinationPath)
                        }
                    }.awaitAll()
                }.flatten().flatten().filter { url ->
                    processedUrls.indexOf(url) == -1
                }.distinct()
            }
        }
    }

    suspend fun processPage(url: String, baseHostName: String, destinationPath: String): List<String> {
        val uri = Url(url).toURI()
        val name = FilenameUtils.getName(url
        val response = worker.getWebPage(url) ?: return emptyList()
        val content =
            if (response.headers.contains("Content-Encoding") && response.headers["Content-Encoding"] == "gzip") {
                response.receive<String>().toByteArray()
            } else {
                response.receive()
            }
        val urls = if (response.contentType()?.contentType == "text") {
            worker.getLinksFromWebPage(content.decodeToString(), baseHostName)
        } else emptyList()
        worker.storeObject(url, content, destinationPath)
        return urls
    }

    fun replaceUrl(fromRegex: Regex, to: String, pathFrom: String, pathTo: String) {
        var i = 0
        val baseFile = File(pathFrom)
        val fileTo = File(pathTo)
        if (!fileTo.exists()) {
            fileTo.mkdirs()
        }
        baseFile.walk().forEach {
            if (i % 100 == 0) {
                println(i)
            }
            val relativePath = it.toRelativeString(baseFile)
            if (it.isDirectory) {
                if (!it.exists()) {
                    it.mkdirs()
                }
            } else {
                val fileTo = File("$pathTo/$relativePath")
                it.copyTo(fileTo)
                if (it.path.endsWith("html")) {
                    val content = String(it.readBytes())
                    val newContent = content.replace(fromRegex, to)
                    fileTo.writeBytes(newContent.toByteArray())
                }
            }
            i++
        }
    }
}