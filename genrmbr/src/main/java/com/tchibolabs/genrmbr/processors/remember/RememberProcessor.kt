package com.tchibolabs.genrmbr.processors.remember

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.tchibolabs.genrmbr.annotations.Remember
import com.tchibolabs.genrmbr.processors.ANNOTATION_REMEMBER
import com.tchibolabs.genrmbr.processors.getClassesImports
import com.tchibolabs.genrmbr.processors.getConstructorArgs
import com.tchibolabs.genrmbr.processors.getFunctionParams
import com.tchibolabs.genrmbr.processors.getInjectorParameter
import com.tchibolabs.genrmbr.processors.hasRememberCoroutineScope
import com.tchibolabs.genrmbr.processors.usesKoinInjection

class RememberProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val useKoinInjection: Boolean = usesKoinInjection(options)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Remember::class.qualifiedName!!)
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

        val injectorFn = getInjectorParameter(ANNOTATION_REMEMBER, classDeclaration)
        val hasInjectorFn = injectorFn.isNotEmpty()

        val functionParams: List<String> = getFunctionParams(classDeclaration, hasInjectorFn, injectorFn, useKoinInjection)
        val invalidateParams: List<String> = getInvalidateRememberParams(classDeclaration)
        val constructorArgs: List<String> = getConstructorArgs(classDeclaration)
        val classesImports: List<String> = getClassesImports(classDeclaration)

        val hasRememberCoroutineScope = hasRememberCoroutineScope(classDeclaration)

        val additionalImports: List<String> = buildList {
            if (hasRememberCoroutineScope) {
                add("import androidx.compose.runtime.rememberCoroutineScope")
            }
            if (useKoinInjection) {
                add("import org.koin.compose.koinInject")
                add("import org.koin.core.parameter.parametersOf")
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
                    return remember${if (invalidateParams.isNotEmpty()) "(${invalidateParams.joinToString(",")})" else ""} { $className(${constructorArgs.joinToString()}) }
                }
                """.trimIndent()
            )
        }
    }

    private fun getInvalidateRememberParams(
        classDeclaration: KSClassDeclaration,
    ): List<String> = classDeclaration.primaryConstructor?.parameters?.filter { param ->
        param.annotations.any { it.shortName.asString() == "InvalidateRemember" }
    }?.mapNotNull { param ->
        param.name?.asString() ?: return@mapNotNull null
    } ?: emptyList()
}