package com.tchibolabs.genrmbr.remembered

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

class RememberedProcessor(
    private val codeGenerator: CodeGenerator
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Remembered::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDeclaration ->
            generateRememberFun(classDeclaration)
        }

        return emptyList()
    }

    private fun generateRememberFun(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = packageName,
            fileName = "Remember$className"
        )

        // Get the injector function name from the Remembered annotation if present
        val injectorFn = classDeclaration.annotations
            .find { it.shortName.asString() == "Remembered" }
            ?.arguments
            ?.find { it.name?.asString() == "injector" }
            ?.value as? String
            ?: ""

        val hasInjectorFn = injectorFn.isNotEmpty()

        val functionParams: List<String> =
            getFunctionParams(classDeclaration, hasInjectorFn, injectorFn)
        val constructorArgs: List<String> = getConstructorArgs(classDeclaration)
        val classesImports: List<String> = getClassesImports(classDeclaration)

        val hasRememberCoroutineScope = classDeclaration.primaryConstructor?.parameters?.any {
            it.annotations.any { it.shortName.asString() == "DefaultCoroutineScope"  }
        } == true

        val additionalImports: List<String> = buildList {
            if (hasRememberCoroutineScope) {
                add("import androidx.compose.runtime.rememberCoroutineScope")
            }
        }

        file.writer().use { writer ->
            writer.write(
                """
                package $packageName
                
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.remember
                ${classesImports.joinToString("\n")}
                ${additionalImports.joinToString("\n")}
                
                @Composable
                fun remember$className(${functionParams.joinToString()}): $className {
                    return remember { $className(${constructorArgs.joinToString()}) }
                }
                """.trimIndent()
            )
        }
    }

    private fun getFunctionParams(
        classDeclaration: KSClassDeclaration,
        hasInjectorFn: Boolean,
        injectorFn: String
    ): List<String> {
        return classDeclaration.primaryConstructor?.parameters?.map { param ->
            val name = param.name?.asString() ?: return@map null
            val type = param.type.resolve().declaration.qualifiedName?.asString() ?: return@map null

            val defaultValue = when {
                param.annotations.any { it.shortName.asString() == "DefaultInt" } -> {
                    val annotation =
                        param.annotations.first { it.shortName.asString() == "DefaultInt" }
                    val value = annotation.arguments.firstOrNull()?.value as? Int ?: 0
                    "$name: $type = $value"
                }

                param.annotations.any { it.shortName.asString() == "DefaultString" } -> {
                    val annotation =
                        param.annotations.first { it.shortName.asString() == "DefaultString" }
                    val value = annotation.arguments.firstOrNull()?.value as? String ?: ""
                    "$name: $type = \"$value\""
                }

                param.annotations.any { it.shortName.asString() == "DefaultBoolean" } -> {
                    val annotation =
                        param.annotations.first { it.shortName.asString() == "DefaultBoolean" }
                    val value = annotation.arguments.firstOrNull()?.value as? Boolean == true
                    "$name: $type = $value"
                }

                param.annotations.any { it.shortName.asString() == "DefaultCoroutineScope" } -> {
                    "$name: $type = rememberCoroutineScope()"
                }

                hasInjectorFn && param.annotations.any { it.shortName.asString() == "DefaultInject" } -> {
                    val type = param.type.resolve().declaration.qualifiedName?.asString()
                        ?: "error(\"No provider function specified\")"
                    "$name: $type = $injectorFn<$type>()"
                }

                param.annotations.any { it.shortName.asString() == "DefaultCustom" } -> {
                    val annotation =
                        param.annotations.first { it.shortName.asString() == "DefaultCustom" }
                    val providerFunction = annotation.arguments.firstOrNull()?.value as? String
                        ?: "error(\"No provider function specified\")"
                    "$name: $type = $providerFunction()"
                }

                else -> "$name: $type"
            }

            defaultValue
        }?.filterNotNull() ?: emptyList()
    }

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