package com.tchibolabs.forgetmenot.processors.remember

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tchibolabs.forgetmenot.annotations.Remember
import com.tchibolabs.forgetmenot.processors.ANNOTATION_REMEMBER
import com.tchibolabs.forgetmenot.processors.composableAnnotation
import com.tchibolabs.forgetmenot.processors.getConstructorArgs
import com.tchibolabs.forgetmenot.processors.getFunctionParamSpecs
import com.tchibolabs.forgetmenot.processors.getInjectorParameter
import com.tchibolabs.forgetmenot.processors.getInvalidateRememberParams
import com.tchibolabs.forgetmenot.processors.hasRememberCoroutineScope
import com.tchibolabs.forgetmenot.processors.koinInjectClassName
import com.tchibolabs.forgetmenot.processors.parametersOfClassName
import com.tchibolabs.forgetmenot.processors.prependTabs
import com.tchibolabs.forgetmenot.processors.rememberClassName
import com.tchibolabs.forgetmenot.processors.rememberCoroutineScopeClassName
import com.tchibolabs.forgetmenot.processors.usesKoinInjection
import java.time.LocalDateTime

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
        val className = classDeclaration.toClassName()

        val injectorFn = getInjectorParameter(ANNOTATION_REMEMBER, classDeclaration)
        val hasInjectorFn = injectorFn.isNotEmpty()

        val functionParams: List<ParameterSpec> =
            getFunctionParamSpecs(classDeclaration, hasInjectorFn, injectorFn, useKoinInjection)
        val invalidateParams: List<String> = getInvalidateRememberParams(classDeclaration)
        val constructorArgs: List<String> = getConstructorArgs(classDeclaration)

        val hasRememberCoroutineScope = hasRememberCoroutineScope(classDeclaration)

        val imports: List<ClassName> = buildList {
            add(composableAnnotation)
            add(rememberClassName)

            if (hasRememberCoroutineScope) {
                add(rememberCoroutineScopeClassName)
            }
            if (useKoinInjection) {
                add(koinInjectClassName)
                add(parametersOfClassName)
            }
        }

        val rememberFunctionContent: CodeBlock =
            generateRememberFunctionContent(invalidateParams, className, constructorArgs)

        val rememberFunction = FunSpec.builder("remember${className.simpleName}")
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(composableAnnotation)
            .apply { functionParams.forEach { addParameter(it) } }
            .returns(className)
            .addStatement(rememberFunctionContent.toString())
            .build()

        FileSpec.builder(className.packageName, "Remember${className.simpleName}")
            .apply { imports.forEach { addImport(it.packageName, it.simpleName) } }
            .addFileComment("This file was auto-generated on ${LocalDateTime.now()}. Do not modify.")
            .addFunction(rememberFunction)
            .build()
            .writeTo(codeGenerator, Dependencies(true))
    }

    private fun generateRememberFunctionContent(
        invalidateParams: List<String>,
        className: ClassName,
        constructorArgs: List<String>
    ): CodeBlock = run {
        val hasInvalidateParams = invalidateParams.isNotEmpty()
        val blockBuilder = CodeBlock.builder()

        if (hasInvalidateParams) {
            blockBuilder.add("return remember(")
            invalidateParams.forEach { param ->
                blockBuilder.add("\n$param,".prependTabs())
            }
            blockBuilder.add("\n) {")
        } else {
            blockBuilder.add("return remember {")
        }

        blockBuilder.add("\n${className.simpleName.prependTabs()}(\n")
        blockBuilder.add(constructorArgs.joinToString(",\n") { it.prependTabs(2) })
        blockBuilder.add("\n)".prependTabs())
        blockBuilder.add("\n}")

        blockBuilder.build()
    }
}