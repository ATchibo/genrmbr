package com.tchibolabs.genrmbr.processors.rememberedsaveable

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.tchibolabs.genrmbr.annotations.RememberSaveable
import com.tchibolabs.genrmbr.processors.ANNOTATION_KEY
import com.tchibolabs.genrmbr.processors.ANNOTATION_REMEMBERED
import com.tchibolabs.genrmbr.processors.ANNOTATION_REMEMBER_SAVEABLE
import com.tchibolabs.genrmbr.processors.ANNOTATION_SAVEABLE
import com.tchibolabs.genrmbr.processors.getFunctionParams
import com.tchibolabs.genrmbr.processors.getInjectorParameter
import kotlin.text.toBoolean

class RememberSaveableProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val useKoinInjection: Boolean = options["genrmbr.useKoinInjection"]?.toBoolean() == true

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(RememberSaveable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDeclaration ->
            generateRememberSaveableFun(classDeclaration, resolver)
        }

        return emptyList()
    }

    private fun generateRememberSaveableFun(
        classDeclaration: KSClassDeclaration,
        resolver: Resolver
    ) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = packageName,
            fileName = "RememberSaveable$className"
        )

        val injectorFn = getInjectorParameter(ANNOTATION_REMEMBER_SAVEABLE, classDeclaration)
        val hasInjectorFn = injectorFn.isNotEmpty()

        // Collect saveable fields - both properties and constructor parameters
        val saveableFields = getSaveableFields(classDeclaration)
        val saveableParameters = getSaveableParameters(classDeclaration)

        // Get parameters needed for the function and saver
        val functionParams: List<String> = getFunctionParams(classDeclaration, hasInjectorFn, injectorFn, useKoinInjection)
        val invalidateParams: List<String> = getInvalidateRememberParams(classDeclaration)
        val constructorArgs: List<String> = getConstructorArgs(classDeclaration)
        val classesImports: List<String> = getClassesImports(classDeclaration)

        // Collect parameters needed for the saver (excluding ones with SaveableField annotation)
        val saverParams = classDeclaration.primaryConstructor?.parameters
            ?.filter { param ->
                param.name?.asString() ?: return@filter false
                // Skip parameters that are marked with @SaveableField
                !param.annotations.any { ann -> ann.shortName.asString() == ANNOTATION_SAVEABLE }
            }
            ?.mapNotNull { param -> param.name?.asString() }
            ?: emptyList()

        val hasRememberCoroutineScope = classDeclaration.primaryConstructor?.parameters?.any {
            it.type.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.CoroutineScope"
        } == true

        val additionalImports = buildList {
            add("import androidx.compose.runtime.saveable.Saver")
            add("import androidx.compose.runtime.saveable.mapSaver")

            if (hasRememberCoroutineScope) {
                add("import androidx.compose.runtime.rememberCoroutineScope")
            }
            if (useKoinInjection) {
                add("import org.koin.compose.koinInject")
            }
        }

        file.writer().use { writer ->
            writer.write(
                """
                package $packageName
                
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.saveable.rememberSaveable
                ${classesImports.joinToString("\n")}
                ${additionalImports.joinToString("\n")}
                
                @Composable
                fun remember$className(${functionParams.joinToString()}): $className {
                    return rememberSaveable(
                        ${if (invalidateParams.isNotEmpty()) "${invalidateParams.joinToString(",")}," else ""}
                        saver = get${className}Saver(
                            ${saverParams.joinToString { "$it = $it" }}
                        )
                    ) {
                        $className(${constructorArgs.joinToString()})
                    }
                }
                """.trimIndent()
            )
        }

        // Generate the companion object with getSaver method
        generateCompanionObject(classDeclaration, saveableFields, saveableParameters, saverParams)
    }

    private fun generateCompanionObject(
        classDeclaration: KSClassDeclaration,
        saveableItems: List<SaveableItemInfo>,
        saveableParameters: List<SaveableItemInfo>,
        saverParams: List<String>
    ) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = packageName,
            fileName = "${className}Companion"
        )

        // Generate parameter declarations for getSaver method
        val saverParamDeclarations = classDeclaration.primaryConstructor?.parameters
            ?.filter { param -> saverParams.contains(param.name?.asString()) }
            ?.mapNotNull { param ->
                val name = param.name?.asString() ?: return@mapNotNull null
                val type = param.type.resolve().declaration.qualifiedName?.asString()
                    ?: return@mapNotNull null
                "$name: $type"
            } ?: emptyList()

        // Generate save map entries using specified keys
        val saveEntries = saveableItems.map { item ->
            "            \"${item.key}\" to it.${item.name}"
        }

        // Generate restore parameters
        val restoreParams = classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null

            val saveableItem = saveableParameters.find { it.name == name }
            if (saveableItem != null) {
                val type = param.type.resolve().declaration.qualifiedName?.asString() ?: "Any"
                "$name = it[\"${saveableItem.key}\"] as $type"
            } else {
                "$name = $name"
            }
        } ?: emptyList()

        file.writer().use { writer ->
            writer.write(
                """
                package $packageName
                
                import androidx.compose.runtime.saveable.Saver
                import androidx.compose.runtime.saveable.mapSaver
                
                // This file is auto-generated. Do not modify.
                
                // Extension to provide companion object for $className
                fun get${className}Saver(
                    ${saverParamDeclarations.joinToString(",\n        ")}
                ): Saver<$className, *> = mapSaver(
                    save = { mapOf(
                        ${saveEntries.joinToString(",\n            ")}
                    )},
                    restore = {
                        $className(
                            ${restoreParams.joinToString(",\n                    ")}
                        )
                    }
                )
                """.trimIndent()
            )
        }
    }

    private fun getSaveableFields(classDeclaration: KSClassDeclaration): List<SaveableItemInfo> {
        return classDeclaration.getAllProperties()
            .filter { prop ->
                prop.annotations.any { ann -> ann.shortName.asString() == ANNOTATION_SAVEABLE }
            }
            .map { prop ->
                val name = prop.simpleName.asString()
                val key = prop.annotations
                    .find { it.shortName.asString() == ANNOTATION_SAVEABLE }
                    ?.arguments
                    ?.find { it.name?.asString() == "key" }
                    ?.value as String

                SaveableItemInfo(name, key, isProperty = true)
            }
            .toList()
    }

    private fun getSaveableParameters(classDeclaration: KSClassDeclaration): List<SaveableItemInfo> {
        return classDeclaration.primaryConstructor?.parameters
            ?.filter { param ->
                param.annotations.any { ann -> ann.shortName.asString() == ANNOTATION_SAVEABLE }
            }
            ?.map { param ->
                val name = param.name?.asString() ?: ""
                val key = param.annotations
                    .find { it.shortName.asString() == ANNOTATION_SAVEABLE }
                    ?.arguments
                    ?.find { it.name?.asString() == "key" }
                    ?.value as String

                SaveableItemInfo(name, key, isProperty = false)
            }
            ?.toList() ?: emptyList()
    }

    private fun getInvalidateRememberParams(
        classDeclaration: KSClassDeclaration,
    ): List<String> = classDeclaration.primaryConstructor?.parameters?.filter { param ->
        param.annotations.any { it.shortName.asString() == ANNOTATION_KEY }
    }?.mapNotNull { param ->
        param.name?.asString() ?: return@mapNotNull null
    } ?: emptyList()

    private fun getConstructorArgs(classDeclaration: KSClassDeclaration): List<String> {
        return classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
            param.name?.asString()
        } ?: emptyList()
    }

    private fun getClassesImports(classDeclaration: KSClassDeclaration): List<String> {
        return classDeclaration.getAllProperties()
            .mapNotNull { property ->
                val type = property.type.resolve()
                val typeName = type.declaration.qualifiedName?.asString()
                if (typeName != null && !typeName.startsWith("kotlin.")) typeName else null
            }
            .toSet() // Ensure unique imports
            .map { "import $it" }
    }
}

// Helper class to store information about saveable items (fields or parameters)
private data class SaveableItemInfo(
    val name: String,
    val key: String,
    val isProperty: Boolean
)