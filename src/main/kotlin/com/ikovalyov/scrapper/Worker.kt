package com.ikovalyov.scrapper

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.toURI
import io.ktor.util.normalizeAndRelativize
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import org.jsoup.Jsoup
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

class Worker {
    private val htmlQueries = listOf("a[href]", "link[href]", "script[src]", "img[src]")

    suspend fun getWebPage(url: String): HttpResponse? {
        return supervisorScope {
            try {
                println("Getting $url")
                retry(5) {
                    val client = HttpClient(CIO) {
                        install(ContentEncoding) {
                            deflate(1.0F)
                            gzip(0.9F)
                        }
                    }
                    val response = try {
                        client.get<HttpResponse>(url)
                    } catch (t: ServerResponseException) {
                        if (t.response.status == HttpStatusCode.BadGateway) {
                            delay(Random.nextLong(5000, 10000))
                            throw t
                        }
                        println("$url resulted in error ${t.response.status.value}")
                        null
                    } catch (t: ClientRequestException) {
                        println("$url resulted in error ${t.response.status.value}")
                        null
                    } finally {
                        client.close()
                    }
                    response
                }
            } catch (t: Throwable) {
                println("Can't get $url")
                throw t
            }
        }
    }

    fun getLinksFromWebPage(html: String, baseHostName: String): List<String> {
        val document = Jsoup.parse(html)
        val links = htmlQueries.map { query ->
            document.select(query).map { element ->
                val href = element.attr("href")
                if (href == "") {
                    element.attr("src")
                } else {
                    href
                }
            }
        }
        return links.flatten().mapNotNull {
            it.substringBefore("?").substringBefore("#")
        }.filter {
            it.isNotEmpty()
        }.filter {
            try {
                val host = Url(it).toURI().host
                host == baseHostName && it.indexOf("wp-json/oembed/1.0/embed") == -1 &&
                        it.indexOf("wp-json/wp/v2/pages") == -1 &&
                        it.indexOf("wp-json/wp/v2/posts") == -1 &&
                        it.indexOf("wp-json/wp/v2/users") == -1 &&
                        it.indexOf("wp-json/wp/v2/tags") == -1
            } catch (t: Throwable) {
                throw t
            }
        }.distinct()
    }

    fun getObjectsUrls(urlsList: List<String>): List<String> {
        return urlsList.filter { url ->
            val extension = FilenameUtils.getExtension(url)
            !extension.isNullOrEmpty()
        }
    }

    fun getPagesUrls(urlsList: List<String>): List<String> {
        return urlsList.filter { url ->
            val extension = FilenameUtils.getExtension(url)
            extension.isNullOrEmpty()
        }
    }

    fun storeObject(url: String, body: ByteArray, destinationPath: String) {
        val parsedUrl = Url(url).toURI()
        val name = if (parsedUrl.path.isNullOrEmpty()) {
             "${parsedUrl.host}/index.html"
        } else {
            var roughName = "${parsedUrl.host}/${parsedUrl.path}"
            val extention = FilenameUtils.getExtension(roughName)
            if (roughName.endsWith(")")) {
                roughName = roughName.substring(0, roughName.length - 1)
            }
            val finalName: String = if (extention.isEmpty()) {
                "$roughName/index.html"
            } else {
                roughName
            }
            finalName
        }
        val file = Path.of("$destinationPath/$name").normalizeAndRelativize().toFile()
        try {
            file.mkdirs()
            file.writeBytes(body)
        } catch (t: FileNotFoundException) {
            println(t.message + " " + file.name)
        } catch (t: Throwable) {
            println(file.name)
            throw t
        }
    }

    private suspend fun <T> retry(numOfRetries: Int, block: suspend () -> T): T {
        var throwable: Throwable? = null
        (1..numOfRetries).forEach { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                throwable = e
                println("Failed attempt $attempt / $numOfRetries")
                delay(1000)
            }
        }
        throw throwable!!
    }
}