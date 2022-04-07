package com.ikovalyov.scrapper

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

object ScrapperApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val parser = ArgParser("example")
            val fromRegex by parser.option(
                ArgType.String,
                shortName = "f",
                description = "regex used to replace urls"
            )
            val to by parser.option(
                ArgType.String,
                shortName = "t",
                description = "destination url"
            )
            val destinationPath by parser.option(
                ArgType.String,
                shortName = "d",
                description = "destinationPath"
            ).required()
            val entryPoint by parser.option(
                ArgType.String,
                shortName = "e",
                description = "Url used as base for scrapping"
            ).required()
            val service = Service()
            service.startWorker(entryPoint, destinationPath)
            if (fromRegex != null && to != null) {
                service.replaceUrl(fromRegex!!.toRegex(), to!!, destinationPath, destinationPath)
            }
        }
    }
}