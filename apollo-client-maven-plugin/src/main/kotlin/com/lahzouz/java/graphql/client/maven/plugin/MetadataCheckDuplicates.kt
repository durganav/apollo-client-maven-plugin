package com.lahzouz.java.graphql.client.maven.plugin

import com.apollographql.apollo.compiler.ApolloMetadata
import org.apache.maven.plugin.MojoExecutionException
import java.io.File

object MetadataCheckDuplicates {
    fun check(
        metadataFiles: Set<File>,
        outputFile: File
    ) {
        val metadataList = metadataFiles.mapNotNull { ApolloMetadata.readFrom(it) }

        metadataList.flatMap { metadata ->
            metadata.fragments.map { it.fragmentName to metadata.moduleName }
        }
            .groupBy { it.first }
            .values
            .find { it.size > 1 }
            ?.run {
                throw MojoExecutionException(
                    "duplicate Fragment '${get(0).first}' generated in modules: ${
                    map { it.second }.joinToString(
                        ","
                    )
                    }"
                )
            }

        metadataList.flatMap { metadata ->
            metadata.types.map { it to metadata.moduleName }
        }
            .groupBy { it.first }
            .values
            .find { it.size > 1 }
            ?.run {
                throw MojoExecutionException(
                    "duplicate Type '${get(0).first}' generated in modules: ${map { it.second }.joinToString(",")}" +
                        "\nUse 'alwaysGenerateTypesMatching' in a parent module to generate the type only once"
                )
            }

        outputFile.run {
            parentFile.mkdirs()
            writeText("No duplicate found.")
        }
    }
}
