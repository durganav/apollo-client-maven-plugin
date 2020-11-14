package com.lahzouz.java.graphql.client.maven.plugin

import com.apollographql.apollo.api.ApolloExperimental
import com.apollographql.apollo.compiler.GraphQLCompiler
import com.apollographql.apollo.compiler.NullableValueType
import com.apollographql.apollo.compiler.OperationIdGenerator
import com.apollographql.apollo.compiler.OperationOutputGenerator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import org.apache.tools.ant.DirectoryScanner
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import kotlin.reflect.full.createInstance

/**
 * Generate queries classes for a GraphQl API
 */
@Mojo(
    name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true
)
class GraphQLClientMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "graph")
    private val graph: String = "http://localhost/graphql"

    @Parameter(property = "key")
    private val key: String = ""

    @Parameter(property = "variant")
    private val variant: String = ""

    @Parameter(property = "schemaUrl")
    private val schemaUrl: String = "http://localhost/graphql"

    @Parameter(property = "schemaUrlHeaders")
    private val customHeaders: Map<String, String> = emptyMap()

    @Parameter(property = "connectTimeoutSeconds")
    private val connectTimeoutSeconds: Long = 10L

    @Parameter(property = "readTimeoutSeconds")
    private val readTimeoutSeconds: Long = 10L

    @Parameter(property = "useSelfSignedCertificat")
    private val useSelfSignedCertificat: Boolean = false

    @Parameter(property = "useGzip")
    private val useGzip: Boolean = false

    @Parameter(property = "enableNetworkLogging")
    private val enableNetworkLogging: Boolean = false

    /**
     * the OperationOutputGenerator class used to generate operation Ids
     */
    @Parameter(property = "operationIdGeneratorClass")
    private val operationIdGeneratorClass: String = ""

    @Parameter(property = "generateIntrospectionFile")
    private val generateIntrospectionFile: Boolean = false

    @Parameter(property = "skip")
    private val skip: Boolean = false

    @Parameter(property = "addSourceRoot")
    private val addSourceRoot: Boolean = true

    /**
     * The rootFolders where the graphqlFiles are located. The package name of each individual graphql query
     * will be the relative path to the root folders
     */
    @Parameter(property = "rootFolders", defaultValue = "\${project.basedir}/src/main/graphql")
    private val rootFolders: List<File> = emptyList()

    /**
     * The folders where the graphql queries/fragments are located
     */
    @Parameter(property = "sourceFolder", defaultValue = "\${project.basedir}/src/main/graphql")
    private lateinit var sourceFolder: File

    /**
     * Files to include from source set directory as in [java.nio.file.PathMatcher]
     */
    @Parameter(property = "includes", defaultValue = "**/*.graphql,**/*.gql,**/*.json,**/*.sdl")
    private val includes: Set<String> = emptySet()

    /**
     * Files to exclude from source set directory as in [java.nio.file.PathMatcher]
     */
    @Parameter(property = "excludes")
    private val excludes: Set<String> = emptySet()

    /**
     * The schema. Can be either a SDL schema or an introspection schema.
     * If null, the schema, metedata must not be empty
     */
    @Parameter(property = "schemaFile")
    private val schemaFile: File? = null

    /**
     * The folder where to generate the sources
     */
    @Parameter(property = "outputDir", defaultValue = "\${project.build.directory}/generated-sources/graphql-client")
    private lateinit var outputDirectory: File

    // ========== multi-module ============

    /**
     * A list of files containing metadata from previous compilations
     */
    @ApolloExperimental
    @Parameter(property = "metadata")
    private val metadata: List<File> = emptyList()

    /**
     * The moduleName for this metadata. Used for debugging purposes
     */
    @Parameter(property = "moduleName")
    private val moduleName: String = "?"

    /**
     * Optional rootProjectDir. If it exists:
     * - when writing metadata the compiler will output relative path to rootProjectDir
     * - when reading metadata the compiler will lookup the actual file
     * This allows to lookup the real fragment file if all compilation units belong to the same project
     * and output nicer error messages
     */
    @Parameter(property = "rootProjectDir")
    private val rootProjectDir: File? = null

    /**
     * The file where to write the metadata
     */
    @Parameter(
        property = "metadataOutputFile",
        defaultValue = "\${project.build.directory}/generated-sources/graphql-client/metadata.json"
    )
    private lateinit var metadataOutputFile: File

    @Parameter(property = "generateMetadata")
    private val generateMetadata: Boolean = false

    /**
     * Additional types to generate. This will generate this type and all types this type depends on.
     */
    @ApolloExperimental
    @Parameter(property = "alwaysGenerateTypesMatching")
    private val alwaysGenerateTypesMatching: Set<String>? = null

    // ========== operation-output ============

    /**
     * the file where to write the operationOutput
     * if null no operationOutput is written
     */
    @Parameter(property = "operationOutputFile")
    private val operationOutputFile: File? = null

    // ========== global codegen options ============
    @Parameter(property = "rootPackageName")
    private val rootPackageName: String = ""

    @Parameter(property = "generateKotlinModels")
    private val generateKotlinModels: Boolean = false

    @Parameter(property = "customTypeMap")
    private val customTypeMap: Map<String, String> = emptyMap()

    @Parameter(property = "useSemanticNaming")
    private val useSemanticNaming: Boolean = true

    @Parameter(property = "generateAsInternal")
    private val generateAsInternal: Boolean = false

    @Parameter(property = "warnOnDeprecatedUsages")
    private val warnOnDeprecatedUsages: Boolean = true

    @Parameter(property = "failOnWarnings")
    private val failOnWarnings: Boolean = false

    // ========== Kotlin codegen options ============
    @Parameter(property = "kotlinMultiPlatformProject")
    private val kotlinMultiPlatformProject: Boolean = false

    @Parameter(property = "enumAsSealedClassPatternFilters")
    private val enumAsSealedClassPatternFilters: Set<String> = emptySet()

    // ========== Java codegen options ============
    @Parameter(property = "NullableValueType")
    private val nullableValueType: NullableValueType = NullableValueType.ANNOTATED

    @Parameter(property = "generateModelBuilder")
    private val generateModelBuilder: Boolean = false

    @Parameter(property = "useJavaBeansSemanticNaming")
    private val useJavaBeansSemanticNaming: Boolean = false

    @Parameter(property = "suppressRawTypesWarning")
    private val suppressRawTypesWarning: Boolean = false

    @Parameter(property = "generateVisitorForPolymorphicDatatypes")
    private val generateVisitorForPolymorphicDatatypes: Boolean = false

    @Throws(MojoExecutionException::class)
    override fun execute() {

        if (skip) {
            log.info("Skipping execution because skip option is true")
            return
        }

        log.info("Apollo GraphQL Client Code Generation task started")

        if (generateIntrospectionFile) {
            log.info("Automatically generating introspection file from $schemaUrl")
            schemaFile?.let { schema ->
                val okHttpClient = SchemaDownloader.newOkHttpClient(
                    readTimeoutSeconds = readTimeoutSeconds,
                    connectTimeoutSeconds = connectTimeoutSeconds,
                    useSelfSignedCertificat = useSelfSignedCertificat,
                    useGzip = useGzip,
                    enableNetworkLogging = enableNetworkLogging
                )
                if (schemaUrl.isNotEmpty()) {
                    SchemaDownloader.downloadIntrospection(
                        schema = schema,
                        endpoint = schemaUrl,
                        headers = customHeaders,
                        okHttpClient = okHttpClient
                    )
                } else if (graph.isNotEmpty()) {
                    SchemaDownloader.downloadRegistry(
                        schema = schema,
                        graph = graph,
                        key = key,
                        variant = variant,
                        okHttpClient = okHttpClient
                    )
                }
            }
        }

        log.info("Read schema file")
        val sourceSetFiles = getSourceSetFiles(sourceFolder = sourceFolder, includes = includes, excludes = excludes)
        val schemaMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{json,sdl}")
        val schemas = findFilesByMatcher(sourceSetFiles, schemaMatcher)
        val schema = schemas.takeIf { it.isNotEmpty() && it.size == 1 }?.first()
            ?: throw MojoExecutionException(
                "duplicate Schema : ${
                schemas.map { it }.joinToString(
                    ","
                )
                }"
            )
        log.info("Read querie(s)/fragment(s) files")
        val graphqlMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{graphql,gql}")
        val graphqlFiles = findFilesByMatcher(sourceSetFiles, graphqlMatcher).takeIf { it.isNotEmpty() }
            ?: throw MojoExecutionException("No querie(s)/fragment(s) found")

        val operationOutputGenerator = if (operationIdGeneratorClass.isEmpty()) {
            OperationOutputGenerator.DefaultOperationOuputGenerator(OperationIdGenerator.Sha256())
        } else {
            val operationIdGenerator =
                Class.forName(operationIdGeneratorClass).kotlin.createInstance() as OperationIdGenerator
            OperationOutputGenerator.DefaultOperationOuputGenerator(operationIdGenerator)
        }

        MetadataCheckDuplicates.check(metadataFiles = metadata.toSet(), metadataOutputFile)

        val compiler = GraphQLCompiler()
        compiler.write(
            GraphQLCompiler.Arguments(
                rootFolders = rootFolders,
                graphqlFiles = graphqlFiles,
                schemaFile = schema,
                outputDir = outputDirectory,
                metadata = metadata,
                moduleName = moduleName,
                rootProjectDir = rootProjectDir,
                metadataOutputFile = metadataOutputFile,
                generateMetadata = generateMetadata,
                alwaysGenerateTypesMatching = alwaysGenerateTypesMatching,
                operationOutputFile = operationOutputFile,
                operationOutputGenerator = operationOutputGenerator,
                rootPackageName = rootPackageName,
                generateKotlinModels = generateKotlinModels,
                customTypeMap = customTypeMap,
                useSemanticNaming = useSemanticNaming,
                generateAsInternal = generateAsInternal,
                warnOnDeprecatedUsages = warnOnDeprecatedUsages,
                failOnWarnings = failOnWarnings,
                kotlinMultiPlatformProject = kotlinMultiPlatformProject,
                enumAsSealedClassPatternFilters = enumAsSealedClassPatternFilters,
                nullableValueType = nullableValueType,
                generateModelBuilder = generateModelBuilder,
                useJavaBeansSemanticNaming = useJavaBeansSemanticNaming,
                suppressRawTypesWarning = suppressRawTypesWarning,
                generateVisitorForPolymorphicDatatypes = generateVisitorForPolymorphicDatatypes
            )
        )

        if (addSourceRoot) {
            log.info("Add the compiled sources to project root")
            project.addCompileSourceRoot(outputDirectory.absolutePath)
        }
        log.info("Apollo GraphQL Client Code Generation task finished")
    }

    private fun findFilesByMatcher(files: Set<File>, matcher: PathMatcher): Set<File> {
        return files.asSequence()
            .filter { file -> matcher.matches(file.toPath()) }
            .toSet()
    }

    private fun getSourceSetFiles(sourceFolder: File, includes: Set<String>, excludes: Set<String>): Set<File> {
        val scanner = DirectoryScanner().apply {
            basedir = sourceFolder
            isCaseSensitive = false
            setIncludes(includes.toTypedArray())
            addExcludes(excludes.toTypedArray())
            scan()
        }
        return scanner.includedFiles.asSequence()
            .map { path -> Paths.get(sourceFolder.path, path).toFile() }
            .toSet()
    }
}
