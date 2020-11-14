package com.lahzouz.java.graphql.client.maven.plugin

import com.apollographql.apollo.api.internal.json.JsonWriter
import com.apollographql.apollo.compiler.fromJson
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.apollographql.apollo.compiler.parser.introspection.toSDL
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object SchemaDownloader {

    fun newOkHttpClient(
        readTimeoutSeconds: Long,
        connectTimeoutSeconds: Long,
        useSelfSignedCertificat: Boolean,
        useGzip: Boolean,
        enableNetworkLogging: Boolean
    ): OkHttpClient {
        val okhttpClientBuilder = if (useSelfSignedCertificat) {
            UnsafeOkHttpClient.getUnsafeOkHttpClient()
        } else {
            OkHttpClient.Builder()
        }

        if (useGzip) {
            okhttpClientBuilder.addInterceptor(GzipRequestInterceptor())
        }

        if (enableNetworkLogging) {
            okhttpClientBuilder.addNetworkInterceptor(HttpLoggingInterceptor())
        }

        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    private fun executeQuery(
        query: String,
        variables: String? = null,
        url: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient
    ): Response {
        val byteArrayOutputStream = ByteArrayOutputStream()
        JsonWriter.of(byteArrayOutputStream.sink().buffer())
            .apply {
                beginObject()
                name("query")
                value(query)
                if (variables != null) {
                    name("variables")
                    value(variables)
                }
                endObject()
                flush()
            }

        val body = byteArrayOutputStream.toByteArray().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .post(body)
            .apply {
                addHeader("User-Agent", "ApolloMavenPlugin")
                headers.entries.forEach {
                    addHeader(it.key, it.value)
                }
            }
            .header("apollographql-client-name", "apollo-gradle-plugin")
            .header("apollographql-client-version", com.apollographql.apollo.compiler.VERSION)
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()

        check(response.isSuccessful) {
            "cannot get schema from $url: ${response.code}:\n${response.body?.string()}"
        }

        return response
    }

    fun downloadIntrospection(
        endpoint: String,
        schema: File,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient
    ) {

        val response = executeQuery(introspectionQuery, null, endpoint, headers, okHttpClient)

        writeResponse(schema, response)
    }

    fun downloadRegistry(
        graph: String,
        schema: File,
        key: String,
        variant: String,
        okHttpClient: OkHttpClient
    ) {
        val query =
            """
    query DownloadSchema(${'$'}graphID: ID!, ${'$'}variant: String!) {
      service(id: ${'$'}graphID) {
        variant(name: ${'$'}variant) {
          activeSchemaPublish {
            schema {
              document
            }
          }
        }
      }
    }
            """.trimIndent()
        val variables =
            """
      {
        "graphID": "$graph",
        "variant": "$variant"
      }
            """.trimIndent()

        val response = executeQuery(query, variables, "https://graphql.api.apollographql.com/api/graphql", mapOf("x-api-key" to key), okHttpClient)

        val responseString = response.body.use { it?.string() }

        val document = responseString
            ?.fromJson<Map<String, *>>()
            ?.get("data").cast<Map<String, *>>()
            ?.get("service").cast<Map<String, *>>()
            ?.get("variant").cast<Map<String, *>>()
            ?.get("activeSchemaPublish").cast<Map<String, *>>()
            ?.get("schema").cast<Map<String, *>>()
            ?.get("document").cast<String>()

        check(document != null) {
            "Cannot retrieve document from $responseString\nCheck graph id and variant"
        }

        writeResponse(schema, document)
    }

    inline fun <reified T> Any?.cast() = this as? T

    private fun writeResponse(schema: File, response: Response) {
        schema.parentFile?.mkdirs()
        response.body.use { responseBody ->
            if (schema.extension.toLowerCase() == "json") {
                schema.writeText(responseBody!!.string())
            } else {
                IntrospectionSchema(responseBody!!.byteStream()).toSDL(schema)
            }
        }
    }

    private fun writeResponse(schema: File, document: String?) {
        schema.parentFile?.mkdirs()
        if (schema.extension.toLowerCase() == "json") {
            schema.writeText(document!!)
        } else {
            IntrospectionSchema(document!!.byteInputStream()).toSDL(schema)
        }
    }

    val introspectionQuery =
        """
    query IntrospectionQuery {
      __schema {
        queryType { name }
        mutationType { name }
        subscriptionType { name }
        types {
          ...FullType
        }
        directives {
          name
          description
          locations
          args {
            ...InputValue
          }
        }
      }
    }

    fragment FullType on __Type {
      kind
      name
      description
      fields(includeDeprecated: true) {
        name
        description
        args {
          ...InputValue
        }
        type {
          ...TypeRef
        }
        isDeprecated
        deprecationReason
      }
      inputFields {
        ...InputValue
      }
      interfaces {
        ...TypeRef
      }
      enumValues(includeDeprecated: true) {
        name
        description
        isDeprecated
        deprecationReason
      }
      possibleTypes {
        ...TypeRef
      }
    }

    fragment InputValue on __InputValue {
      name
      description
      type { ...TypeRef }
      defaultValue
    }

    fragment TypeRef on __Type {
      kind
      name
      ofType {
        kind
        name
        ofType {
          kind
          name
          ofType {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                  }
                }
              }
            }
          }
        }
      }
    }
        """.trimIndent()
}
