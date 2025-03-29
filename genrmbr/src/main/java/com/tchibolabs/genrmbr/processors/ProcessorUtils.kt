package com.tchibolabs.genrmbr.processors

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ANNOTATION
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toTypeName

internal const val ANNOTATION_VALUE = "Value"
internal const val ANNOTATION_DEFAULT_INJECT = "DefaultInject"
internal const val ANNOTATION_PROVIDE = "Provide"
internal const val ANNOTATION_SAVEABLE = "Saveable"
internal const val ANNOTATION_REMEMBER = "Remember"
internal const val ANNOTATION_REMEMBER_SAVEABLE = "RememberSaveable"
internal const val ANNOTATION_KEY = "Key"

internal val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
internal val rememberClassName = ClassName("androidx.compose.runtime", "remember")
internal val rememberSaveableClassName = ClassName("androidx.compose.runtime.saveable", "rememberSaveable")
internal val rememberCoroutineScopeClassName = ClassName("androidx.compose.runtime", "rememberCoroutineScope")
internal val koinInjectClassName = ClassName("org.koin.compose", "koinInject")
internal val parametersOfClassName = ClassName("org.koin.core.parameter", "parametersOf")
internal val saverClassName = ClassName("androidx.compose.runtime.saveable", "Saver")
internal val mapSaverClassName = ClassName("androidx.compose.runtime.saveable", "mapSaver")

private const val TAB = "  "

internal fun usesKoinInjection(options: Map<String, String>) =
    options["genrmbr.injectionType"] == "koin"

// Get the injector function name from the Remembered/RememberSaveable annotation if present
internal fun getInjectorParameter(
    annotationName: String,
    classDeclaration: KSClassDeclaration,
) = classDeclaration.annotations
    .find { it.shortName.asString() == annotationName }
    ?.arguments
    ?.find { it.name?.asString() == "injector" }
    ?.value as? String
    ?: ""

internal fun getFunctionParamSpecs(
    classDeclaration: KSClassDeclaration,
    hasInjectorFn: Boolean,
    injectorFn: String,
    useKoinInjection: Boolean
): List<ParameterSpec> =
    classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
        val name = param.name?.asString() ?: return@mapNotNull null
        val type = param.type.toTypeName()

        val paramBuilder = ParameterSpec.builder(name, type)

        val valueAnnotation = getAnnotation(param, ANNOTATION_VALUE)
        val defaultInjectAnnotation = getAnnotation(param, ANNOTATION_DEFAULT_INJECT)
        val provideAnnotation = getAnnotation(param, ANNOTATION_PROVIDE)
        val isCoroutineScope = param.type.resolve().declaration.simpleName.asString() == "CoroutineScope"

        when {
            valueAnnotation != null -> {
                val value = getAnnotationArgument<String>(valueAnnotation, "value")
                paramBuilder.apply { if (value != null) defaultValue(value) }
                    .build()
            }

            defaultInjectAnnotation != null -> {
                val parameters = getAnnotationArgument<List<*>>(defaultInjectAnnotation, "params")

                val parametersString = if (parameters?.isEmpty() == true) ""
                else parameters?.joinToString(", ")

                when {
                    hasInjectorFn -> {
                        paramBuilder.defaultValue("$injectorFn<$type>($parametersString)")
                            .build()
                    }

                    useKoinInjection -> {
                        val codeBlock = if (parametersString.isNullOrEmpty()) {
                            CodeBlock.builder()
                                .add("koinInject()")
                                .build()
                        } else {
                            CodeBlock.builder()
                                .add("koinInject { parametersOf($parametersString) }")
                                .build()
                        }
                        paramBuilder.defaultValue(codeBlock).build()
                    }

                    else -> throw Exception("Cannot determine inject function")
                }
            }

            provideAnnotation != null -> {
                val providerFunction = getAnnotationArgument<String>(provideAnnotation, "providerFunction") ?:
                    throw Exception("Cannot determine provider function")

                paramBuilder.defaultValue("$providerFunction()").build()
            }

            isCoroutineScope -> {
                paramBuilder.defaultValue("rememberCoroutineScope()").build()
            }

            else -> paramBuilder.build()
        }
    } ?: emptyList()

internal fun getConstructorArgs(classDeclaration: KSClassDeclaration): List<String> {
    return classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
        param.name?.asString()
    } ?: emptyList()
}

internal fun hasRememberCoroutineScope(classDeclaration: KSClassDeclaration) =
    classDeclaration.primaryConstructor?.parameters?.any {
        it.type.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.CoroutineScope"
    } == true

internal fun getInvalidateRememberParams(
    classDeclaration: KSClassDeclaration,
): List<String> = classDeclaration.primaryConstructor?.parameters?.filter { param ->
    getAnnotation(param, ANNOTATION_KEY) != null
}?.mapNotNull { param ->
    param.name?.asString()
} ?: emptyList()

internal fun String.prependTabs(tabsNr: Int = 1) =
    this.prependIndent(TAB.repeat(tabsNr))

internal fun getAnnotation(param: KSValueParameter, name: String) =
    getAnnotation(param.annotations, name)

internal fun getAnnotation(annotations: Sequence<KSAnnotation>, name: String) =
    annotations.find { it.shortName.asString() == name }

@Suppress("UNCHECKED_CAST")
private fun <T> getAnnotationArgument(annotation: KSAnnotation?, paramName: String): T? =
    annotation?.arguments?.find { it.name?.asString() == paramName }?.value as T