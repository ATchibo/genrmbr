package com.tchibolabs.forgetmenot.processors.remembersaveable

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tchibolabs.forgetmenot.annotations.RememberSaveable
import com.tchibolabs.forgetmenot.processors.ANNOTATION_REMEMBER_SAVEABLE
import com.tchibolabs.forgetmenot.processors.ANNOTATION_SAVEABLE
import com.tchibolabs.forgetmenot.processors.composableAnnotation
import com.tchibolabs.forgetmenot.processors.getAnnotation
import com.tchibolabs.forgetmenot.processors.getConstructorArgs
import com.tchibolabs.forgetmenot.processors.getFunctionParamSpecs
import com.tchibolabs.forgetmenot.processors.getInjectorParameter
import com.tchibolabs.forgetmenot.processors.getInvalidateRememberParams
import com.tchibolabs.forgetmenot.processors.hasRememberCoroutineScope
import com.tchibolabs.forgetmenot.processors.koinInjectClassName
import com.tchibolabs.forgetmenot.processors.mapSaverClassName
import com.tchibolabs.forgetmenot.processors.parametersOfClassName
import com.tchibolabs.forgetmenot.processors.prependTabs
import com.tchibolabs.forgetmenot.processors.rememberCoroutineScopeClassName
import com.tchibolabs.forgetmenot.processors.rememberSaveableClassName
import com.tchibolabs.forgetmenot.processors.saverClassName
import com.tchibolabs.forgetmenot.processors.usesKoinInjection
import java.time.LocalDateTime

class RememberSaveableProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val useKoinInjection: Boolean = usesKoinInjection(options)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(RememberSaveable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDeclaration ->
            generateRememberSaveableFun(classDeclaration)
        }

        return emptyList()
    }

    private fun generateRememberSaveableFun(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.toClassName()

        val injectorFn = getInjectorParameter(ANNOTATION_REMEMBER_SAVEABLE, classDeclaration)
        val hasInjectorFn = injectorFn.isNotEmpty()

        val functionParams: List<ParameterSpec> =
            getFunctionParamSpecs(classDeclaration, hasInjectorFn, injectorFn, useKoinInjection)
        val invalidateParams: List<String> = getInvalidateRememberParams(classDeclaration)
        val constructorArgs: List<String> = getConstructorArgs(classDeclaration)

        val hasRememberCoroutineScope = hasRememberCoroutineScope(classDeclaration)

        val saveableParameters = getSaveableParameters(classDeclaration)
        val saveableFields = getSaveableFields(classDeclaration)

        try {
            assert(saveableFields.size == saveableParameters.size)
            val fieldsKeys = saveableFields.map { it.key }.sorted()
            val paramsKeys = saveableFields.map { it.key }.sorted()
            fieldsKeys.zip(paramsKeys).forEach {
                assert(it.component1() == it.component2())
            }
        } catch (_: AssertionError) {
            throw Exception("Not all @Saveable class parameters have a matching property!")
        }

        val saverParams = classDeclaration.primaryConstructor?.parameters?.filter { param ->
            getAnnotation(param, ANNOTATION_SAVEABLE) == null
        }?.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null
            val type = param.type.toTypeName()
            ParameterSpec(name, type)
        } ?: emptyList()

        val restoreParams = classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null

            val saveableItem = saveableParameters.find { it.name == name }
            if (saveableItem != null) {
                val type = param.type.toTypeName()
                "$name = it[\"${saveableItem.key}\"] as $type"
            } else {
                "$name = $name"
            }
        } ?: emptyList()

        val imports: List<ClassName> = buildList {
            add(composableAnnotation)
            add(rememberSaveableClassName)
            add(saverClassName)
            add(mapSaverClassName)

            if (hasRememberCoroutineScope) {
                add(rememberCoroutineScopeClassName)
            }
            if (useKoinInjection) {
                add(koinInjectClassName)
                add(parametersOfClassName)
            }
        }

        val saverFunctionContent: CodeBlock = generateSaverFunctionContent(saveableFields, className, restoreParams)

        val saverFunction = FunSpec.builder("get${className.simpleName}Saver")
            .addModifiers(KModifier.INTERNAL)
            .apply { saverParams.forEach { addParameter(it) } }
            .addStatement(saverFunctionContent.toString())
            .returns(saverClassName.parameterizedBy(className, STAR))
            .build()

        val rememberSaveableFunctionContent: CodeBlock =
            generateRememberSaveableFunctionContent(invalidateParams, className, constructorArgs, saverParams)

        val rememberSaveableFunction = FunSpec.builder("remember${className.simpleName}")
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(composableAnnotation)
            .apply { functionParams.forEach { addParameter(it) } }
            .addStatement(rememberSaveableFunctionContent.toString())
            .returns(className)
            .build()

        FileSpec.builder(className.packageName, "RememberSaveable${className.simpleName}")
            .apply { imports.forEach { addImport(it.packageName, it.simpleName) } }
            .addFileComment("This file was auto-generated on ${LocalDateTime.now()}. Do not modify.")
            .addFunction(saverFunction)
            .addFunction(rememberSaveableFunction)
            .build()
            .writeTo(codeGenerator, Dependencies(true))
    }

    private fun generateSaverFunctionContent(
        saveableFields: List<SaveableItemInfo>,
        className: ClassName,
        restoreParams: List<String>
    ): CodeBlock = run {
        val blockBuilder = CodeBlock.builder()

        blockBuilder.add("return mapSaver(")
        blockBuilder.add("\nsave = { mapOf(".prependTabs())
        saveableFields.forEach { item ->
            blockBuilder.add("\n%S to it.${item.name},".prependTabs(2), item.key)
        }
        blockBuilder.add("\n) },".prependTabs())
        blockBuilder.add("\nrestore = {".prependTabs())
        blockBuilder.add("\n${className.simpleName}(".prependTabs(2))
        restoreParams.forEach { param ->
            blockBuilder.add("\n$param,".prependTabs(3))
        }
        blockBuilder.add("\n)".prependTabs(2))
        blockBuilder.add("\n}".prependTabs())
        blockBuilder.add("\n)")
        blockBuilder.build()
    }

    private fun generateRememberSaveableFunctionContent(
        invalidateParams: List<String>,
        className: ClassName,
        constructorArgs: List<String>,
        saverParams: List<ParameterSpec>
    ): CodeBlock = run {
        val hasInvalidateParams = invalidateParams.isNotEmpty()
        val blockBuilder = CodeBlock.builder()

        blockBuilder.add("return rememberSaveable(")

        if (hasInvalidateParams) {
            invalidateParams.forEach { param ->
                blockBuilder.add("\n$param,".prependTabs())
            }
        }

        blockBuilder.add("\nsaver = get${className.simpleName}Saver(".prependTabs())
        saverParams.forEach {
            blockBuilder.add("\n${it.name},".prependTabs(2))
        }
        blockBuilder.add("\n)".prependTabs())
        if (saverParams.isNotEmpty()) blockBuilder.add("\n")
        blockBuilder.add(") {\n")

        blockBuilder.add("${className.simpleName.prependTabs()}(\n")
        blockBuilder.add(constructorArgs.joinToString(",\n") { it.prependTabs(2) })
        blockBuilder.add("\n)".prependTabs())
        blockBuilder.add("\n}")

        blockBuilder.build()
    }

    private fun getSaveableFields(classDeclaration: KSClassDeclaration): List<SaveableItemInfo> {
        return classDeclaration.getAllProperties()
            .filter { prop -> getAnnotation(prop.annotations, ANNOTATION_SAVEABLE) != null }
            .map { prop ->
                val name = prop.simpleName.asString()
                val key = getSaveableKey(prop.annotations)

                SaveableItemInfo(name, key, isProperty = true)
            }
            .toList()
    }

    private fun getSaveableParameters(classDeclaration: KSClassDeclaration): List<SaveableItemInfo> {
        return classDeclaration.primaryConstructor?.parameters
            ?.filter { param -> getAnnotation(param, ANNOTATION_SAVEABLE) != null }
            ?.map { param ->
                val name = param.name?.asString() ?: ""
                val key = getSaveableKey(param.annotations)

                SaveableItemInfo(name, key, isProperty = false)
            }
            ?.toList() ?: emptyList()
    }

    private fun getSaveableKey(annotations: Sequence<KSAnnotation>) =
        annotations
            .find { it.shortName.asString() == ANNOTATION_SAVEABLE }
            ?.arguments
            ?.find { it.name?.asString() == "key" }
            ?.value as String
}

// Helper class to store information about saveable items (fields or parameters)
private data class SaveableItemInfo(
    val name: String,
    val key: String,
    val isProperty: Boolean
)