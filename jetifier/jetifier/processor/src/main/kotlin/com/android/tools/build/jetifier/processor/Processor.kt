/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.jetifier.processor

import com.android.tools.build.jetifier.core.config.Config
import com.android.tools.build.jetifier.core.pom.DependencyVersions
import com.android.tools.build.jetifier.core.pom.PomDependency
import com.android.tools.build.jetifier.core.utils.Log
import com.android.tools.build.jetifier.processor.archive.Archive
import com.android.tools.build.jetifier.processor.archive.ArchiveFile
import com.android.tools.build.jetifier.processor.archive.ArchiveItemVisitor
import com.android.tools.build.jetifier.processor.archive.FileSearchResult
import com.android.tools.build.jetifier.processor.com.android.tools.build.jetifier.processor.transform.java.JavaTransformer
import com.android.tools.build.jetifier.processor.transform.TransformationContext
import com.android.tools.build.jetifier.processor.transform.Transformer
import com.android.tools.build.jetifier.processor.transform.bytecode.ByteCodeTransformer
import com.android.tools.build.jetifier.processor.transform.metainf.MetaInfTransformer
import com.android.tools.build.jetifier.processor.transform.pom.PomDocument
import com.android.tools.build.jetifier.processor.transform.pom.PomScanner
import com.android.tools.build.jetifier.processor.transform.proguard.ProGuardTransformer
import com.android.tools.build.jetifier.processor.transform.resource.XmlResourcesTransformer
import java.io.File
import java.io.FileNotFoundException
import java.lang.StringBuilder

/**
 * The main entry point to the library. Extracts any given archive recursively and runs all
 * the registered [Transformer]s over the set and creates new archives that will contain the
 * transformed files.
 */
class Processor private constructor(
    private val context: TransformationContext,
    private val transformers: List<Transformer>,
    private val stripSignatureFiles: Boolean = false
) : ArchiveItemVisitor {

    companion object {
        private const val TAG = "Processor"

        /**
         * Transformers to be used when refactoring general libraries.
         */
        private fun createTransformers(context: TransformationContext) = listOf(
            // Register your transformers here
            ByteCodeTransformer(context),
            XmlResourcesTransformer(context),
            ProGuardTransformer(context),
            JavaTransformer(context)
        )

        /**
         * Transformers to be used when refactoring the support library itself.
         */
        private fun createSLTransformers(context: TransformationContext) = listOf(
            // Register your transformers here
            ByteCodeTransformer(context),
            XmlResourcesTransformer(context),
            ProGuardTransformer(context),
            MetaInfTransformer(context)
        )

        /**
         * Creates a new instance of the [Processor].
         *
         * @param config Transformation configuration
         * @param reversedMode Whether the processor should run in reversed mode
         * @param rewritingSupportLib Whether we are rewriting the support library itself
         * @param useFallbackIfTypeIsMissing Use fallback for types resolving instead of crashing
         * @param allowAmbiguousPackages Whether Jetifier should not crash when it attempts to
         * rewrite ambiguous package reference such as android.support.v4.
         * @param stripSignatures Don't throw an error when jetifying a signed library and strip
         * the signature files instead.
         * @param dataBindingVersion The versions to be used for data binding otherwise undefined.
         */
        fun createProcessor3(
            config: Config,
            reversedMode: Boolean = false,
            rewritingSupportLib: Boolean = false,
            useFallbackIfTypeIsMissing: Boolean = true,
            allowAmbiguousPackages: Boolean = false,
            stripSignatures: Boolean = false,
            dataBindingVersion: String? = null
        ): Processor {
            var newConfig = config

            val versionsMap = DependencyVersions
                .parseFromVersionSetTypeId(
                    versionsMap = config.versionsMap
                )
                .replaceVersionIfAny(
                    forVariable = DependencyVersions.DATA_BINDING_VAR_NAME,
                    newVersion = dataBindingVersion
                )

            if (reversedMode) {
                newConfig = Config(
                    restrictToPackagePrefixes = config.reversedRestrictToPackagePrefixes,
                    reversedRestrictToPackagePrefixes = config.restrictToPackagePrefixes,
                    rulesMap = config.rulesMap.reverse().appendRules(config.slRules),
                    slRules = config.slRules,
                    pomRewriteRules = config.pomRewriteRules
                        // Remove uiautomator-v18 from the reversed version
                        .filterNot { it.from.artifactId == "uiautomator-v18" }
                        .map { it.getReversed() }
                        .toSet(),
                    typesMap = config.typesMap.reverseMapOrDie(),
                    proGuardMap = config.proGuardMap.reverseMap(),
                    versionsMap = config.versionsMap,
                    packageMap = config.packageMap.reverse()
                )
            }

            val context = TransformationContext(
                config = newConfig,
                rewritingSupportLib = rewritingSupportLib,
                isInReversedMode = reversedMode,
                useFallbackIfTypeIsMissing = useFallbackIfTypeIsMissing,
                allowAmbiguousPackages = allowAmbiguousPackages,
                versions = versionsMap)
            val transformers = if (rewritingSupportLib) {
                createSLTransformers(context)
            } else {
                createTransformers(context)
            }

            return Processor(context, transformers, stripSignatures)
        }

        /**
         * Creates a new instance of the [Processor].
         *
         * @param config Transformation configuration
         * @param reversedMode Whether the processor should run in reversed mode
         * @param rewritingSupportLib Whether we are rewriting the support library itself
         * @param useFallbackIfTypeIsMissing Use fallback for types resolving instead of crashing
         * @param allowAmbiguousPackages Whether Jetifier should not crash when it attempts to
         * rewrite ambiguous package reference such as android.support.v4.
         * @param dataBindingVersion The versions to be used for data binding otherwise undefined.
         */
        @Deprecated(
            message = "Legacy method that is missing 'throwErrorIsSignatureDetected' attribute",
            replaceWith = ReplaceWith(expression = "Processor.createProcessor3"))
        fun createProcessor2(
            config: Config,
            reversedMode: Boolean = false,
            rewritingSupportLib: Boolean = false,
            useFallbackIfTypeIsMissing: Boolean = true,
            allowAmbiguousPackages: Boolean = false,
            dataBindingVersion: String? = null
        ): Processor {
            return createProcessor3(
                config = config,
                reversedMode = reversedMode,
                rewritingSupportLib = rewritingSupportLib,
                useFallbackIfTypeIsMissing = useFallbackIfTypeIsMissing,
                allowAmbiguousPackages = allowAmbiguousPackages,
                stripSignatures = false,
                dataBindingVersion = dataBindingVersion
            )
        }

        /**
         * Creates a new instance of the [Processor].
         *
         * @param config Transformation configuration
         * @param reversedMode Whether the processor should run in reversed mode
         * @param rewritingSupportLib Whether we are rewriting the support library itself
         * @param useFallbackIfTypeIsMissing Use fallback for types resolving instead of crashing
         * @param versionSetName Versions map for dependencies rewriting
         * @param dataBindingVersion The versions to be used for data binding otherwise undefined.
         */
        @Deprecated(
            message = "Legacy method that is missing 'allowAmbiguousPackages' attribute and " +
                    "'versionSetName' attribute is not used anymore.",
            replaceWith = ReplaceWith(expression = "Processor.createProcessor3"))
        fun createProcessor(
            config: Config,
            reversedMode: Boolean = false,
            rewritingSupportLib: Boolean = false,
            useFallbackIfTypeIsMissing: Boolean = true,
            versionSetName: String? = null,
            dataBindingVersion: String? = null
        ): Processor {
            return createProcessor2(
                config = config,
                reversedMode = reversedMode,
                rewritingSupportLib = rewritingSupportLib,
                useFallbackIfTypeIsMissing = useFallbackIfTypeIsMissing,
                allowAmbiguousPackages = false,
                dataBindingVersion = dataBindingVersion
            )
        }
    }

    private val oldDependenciesRegex: List<Regex> = context.config.pomRewriteRules.map {
        Regex(".*" +
            it.from.groupId!!.replace(".", "[./\\\\]") +
            "[./\\\\]" +
            it.from.artifactId +
            "[./\\\\].*")
    }

    private val newDependenciesRegex: List<Regex> = context.config.pomRewriteRules.map {
        Regex(".*" +
            it.to.groupId!!.replace(".", "[./\\\\]") +
            "[./\\\\]" +
            it.to.artifactId +
            "[./\\\\].*")
    }

    /**
     * Transforms the input libraries given in [inputLibraries] using all the registered
     * [Transformer]s and returns a list of replacement libraries (the newly created libraries are
     * get stored into [outputPath]). Also supports transforming single source files (java and xml.)
     *
     * Currently we have the following transformers:
     * - [ByteCodeTransformer] for java native code
     * - [XmlResourcesTransformer] for java native code and xml resource files
     * - [ProGuardTransformer] for PorGuard files
     * - [JavaTransformer] for java source code
     *
     * @param input Files to process together with a path where they should be saved to.
     * @param copyUnmodifiedLibsAlso Whether archives that were not modified should be also copied
     * to their target path.
     * @return list of files (existing and generated) that should replace the given [input] files.
     */
    fun transform(input: Set<FileMapping>, copyUnmodifiedLibsAlso: Boolean = true): Set<File> {
        val nonSingleFiles = HashSet<FileMapping>(input)
        for (fileMapping in nonSingleFiles) {
            // Treat all files as single files and check if they are transformable.
            val file = ArchiveFile(fileMapping.from.toPath(), fileMapping.from.readBytes())
            file.setIsSingleFile(true)
            val transformer = transformers.firstOrNull { it.canTransform(file) }
            if (transformer != null) {
                // Single file is transformable, set relativePath to the output path.
                file.updateRelativePath(fileMapping.to.toPath())
                transformer.runTransform(file)
                nonSingleFiles.remove(fileMapping)
            }
        }
        if (nonSingleFiles.isEmpty()) {
            // all files were single files, we're done.
            return emptySet()
        }

        val inputLibraries = nonSingleFiles.map { it.from }.toSet()
        if (inputLibraries.size != input.size) {
            throw IllegalArgumentException("Input files are duplicated!")
        }

        // 1) Extract and load all libraries
        val libraries = loadLibraries(input)

        // 2) Search for POM files
        val pomFiles = scanPomFiles(libraries)

        // 3) Transform all the libraries
        libraries.forEach { transformLibrary(it) }

        if (context.errorsTotal() > 0) {
            if (context.isInReversedMode && context.rewritingSupportLib) {
                throw IllegalArgumentException("There were ${context.errorsTotal()} errors found " +
                    "during the de-jetification. You have probably added new androidx types " +
                    "into support library and dejetifier doesn't know where to move them. " +
                    "Please update default.config and regenerate default.generated.config via " +
                    "jetifier/jetifier/preprocessor/scripts/processDefaultConfig.sh")
            }

            throw IllegalArgumentException("There were ${context.errorsTotal()}" +
                " errors found during the remapping. Check the logs for more details.")
        }

        // TODO: Here we might need to modify the POM files if they point at a library that we have
        // just refactored.

        // 4) Transform the previously discovered POM files
        transformPomFiles(pomFiles)

        // 5) Find signature files and report them if needed
        runSignatureDetectionFor(libraries)

        // 6) Repackage the libraries back to archive files
        val generatedLibraries = libraries
            .filter { copyUnmodifiedLibsAlso || it.wasChanged }
            .map {
                it.writeSelf()
            }
            .toSet()

        if (copyUnmodifiedLibsAlso) {
            return generatedLibraries
        }

        // 7) Create a set of files that should be removed (because they've been changed).
        val filesToRemove = libraries
            .filter { it.wasChanged }
            .map { it.relativePath.toFile() }
            .toSet()

        return inputLibraries.minus(filesToRemove).plus(generatedLibraries)
    }

    private fun runSignatureDetectionFor(libraries: List<Archive>) {
        var wereSignaturesDetected = false
        val sb = StringBuilder()

        libraries
            .filter { it.wasChanged }
            .forEach { library ->
                val foundSignatures = FileSearchResult()
                library.findAllFiles({ isSignatureFile(it) }, foundSignatures)
                if (foundSignatures.all.isNotEmpty()) {
                    wereSignaturesDetected = true
                    sb.appendln()
                    sb.appendln("Found following signature files for '${library.relativePath}':")
                    foundSignatures.all
                        .sortedBy { it.relativePath.toString() }
                        .forEach { file ->
                            sb.appendln("- ${file.relativePath}")
                            file.markedForRemoval = true
                    }
                }
            }

        if (wereSignaturesDetected && !stripSignatureFiles) {
            throw SignatureFilesFoundJetifierException(
                "Jetifier found signature in at least one of the archives that need to be " +
                "modified. However doing so would break the signatures. Please ask the library " +
                "owner to provide jetpack compatible signed library. If you don't need " +
                "the signatures you can re-run jetifier with 'stripSignatures' option on. " +
                "Jetifier will then remove all affected signature files. Below is list of all " +
                "the signature that were discovered: $sb}"
            )
        }
    }

    /**
     * Maps the given dependency (in form of groupId:artifactId:version) to a new set of
     * dependencies. Used for mapping of old support library artifacts to jetpack ones.
     *
     * @return set of new dependencies. Can be empty which means the given dependency should be
     * removed without replacement. Returns null in case a mapping was not found which means that
     * the given artifact was unknown.
     */
    fun mapDependency(depNotation: String): String? {
        val parts = depNotation.split(":")
        val inputDependency = PomDependency(
            groupId = parts[0],
            artifactId = parts[1],
            version = parts[2])

        // TODO: We ignore version check for now
        val resultRule = context.config.pomRewriteRules
            .firstOrNull { it.matches(inputDependency) } ?: return null

        return resultRule.to
            .rewrite(inputDependency, context.versions)
            .toStringNotation()
    }

    /**
     * Returns map of all rewritten dependencies in format "groupId:artifactId"
     * to "groupId:artifactId:version".
     *
     * Don't forget to pass dataBinding version to the constructor to get correct versions.
     *
     * @param filterOutBaseLibrary Set true to filter out "baseLibrary" artifact of data binding.
     */
    fun getDependenciesMap(filterOutBaseLibrary: Boolean = true): Map<String, String> {
        return context.config.pomRewriteRules
            .filter { !filterOutBaseLibrary || !(it.from.artifactId == "baseLibrary" &&
                    it.from.groupId == "com.android.databinding") }
            .map {
                (context.versions.applyOnConfigPomDep(it.from).toStringNotationWithoutVersion()
                    to context.versions.applyOnConfigPomDep(it.to).toStringNotation()) }
            .toMap()
    }

    /**
     * Returns whether the given artifact file is from the old list of dependencies and should be
     * replaced by a new one.
     */
    fun isOldDependencyFile(aarOrJarFile: File): Boolean {
        return oldDependenciesRegex.any { it.matches(aarOrJarFile.absolutePath) }
    }

    /**
     * Return whether the given artifact file is a new artifact from the new set of dependencies
     * and should be kept.
     */
    fun isNewDependencyFile(aarOrJarFile: File): Boolean {
        return newDependenciesRegex.any { it.matches(aarOrJarFile.absolutePath) }
    }

    private fun loadLibraries(inputLibraries: Iterable<FileMapping>): List<Archive> {
        val libraries = mutableListOf<Archive>()
        for (library in inputLibraries) {
            if (!library.from.canRead()) {
                throw FileNotFoundException("Cannot open a library at '$library'")
            }

            val archive = Archive.Builder.extract(library.from)
            archive.setTargetPath(library.to.toPath())
            libraries.add(archive)
        }
        return libraries.toList()
    }

    private fun scanPomFiles(libraries: List<Archive>): List<PomDocument> {
        val scanner = PomScanner(context)

        libraries.forEach { scanner.scanArchiveForPomFile(it) }
        if (scanner.wasErrorFound()) {
            throw IllegalArgumentException("At least one of the libraries depends on an older" +
                " version of support library. Check the logs for more details.")
        }

        return scanner.pomFiles
    }

    private fun transformPomFiles(files: List<PomDocument>) {
        files.forEach {
            it.applyRules(context)
            it.saveBackToFileIfNeeded()
        }
    }

    private fun transformLibrary(archive: Archive) {
        Log.i(TAG, "Started new transformation")
        Log.i(TAG, "- Input file: %s", archive.relativePath)

        archive.accept(this)
    }

    override fun visit(archive: Archive) {
        archive.files.forEach { it.accept(this) }

        // This is an ugly workaround to merge annotations files due to having old and new
        // namespaces at the same time
        if (context.isInReversedMode) {
            AnnotationFilesMerger.tryMergeFilesInArchive(archive)
        }
    }

    override fun visit(archiveFile: ArchiveFile) {
        val transformer = transformers.firstOrNull { it.canTransform(archiveFile) }

        if (transformer == null) {
            Log.v(TAG, "[Skipped] %s", archiveFile.relativePath)
            return
        }

        Log.v(TAG, "[Applied: %s] %s", transformer.javaClass.simpleName, archiveFile.relativePath)
        transformer.runTransform(archiveFile)
    }
}