package com.ikovalyov.scrapper

import kotlinx.cli.ArgParser
import kotlinx.coroutines.runBlocking

object RenamerApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val service = Service()
            service.replaceUrl("(http(s?)://)?magic-forum.club/?".toRegex(), "/", "./magic-forum.club", "./magic-forum.club.processed")
        }
    }
}