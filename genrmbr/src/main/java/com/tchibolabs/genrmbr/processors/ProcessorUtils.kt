package com.tchibolabs.genrmbr.processors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

internal const val ANNOTATION_VALUE = "Value"
internal const val ANNOTATION_DEFAULT_INJECT = "DefaultInject"
internal const val ANNOTATION_PROVIDE = "Provide"
internal const val ANNOTATION_SAVEABLE = "Saveable"
internal const val ANNOTATION_REMEMBER = "Remember"
internal const val ANNOTATION_REMEMBER_SAVEABLE = "RememberSaveable"
internal const val ANNOTATION_KEY = "Key"

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

internal fun getFunctionParams(
    classDeclaration: KSClassDeclaration,
    hasInjectorFn: Boolean,
    injectorFn: String,
    useKoinInjection: Boolean
): List<String> =
    classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
        val name = param.name?.asString() ?: return@mapNotNull null
        val typeString = getParameterTypeString(param)

        val defaultValue = when {
            param.annotations.any { it.shortName.asString() == ANNOTATION_VALUE } -> {
                val annotation =
                    param.annotations.first { it.shortName.asString() == ANNOTATION_VALUE }
                val value =
                    annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String

                if (value != null) {
                    "$name: $typeString = $value"
                } else {
                    "$name: $typeString"
                }
            }

            param.annotations.any { it.shortName.asString() == ANNOTATION_DEFAULT_INJECT } -> {
                val annotation =
                    param.annotations.first { it.shortName.asString() == ANNOTATION_DEFAULT_INJECT }
                val parameters =
                    annotation.arguments.firstOrNull { it.name?.asString() == "params" }?.value as? List<*>

                val parametersString = if (parameters?.isEmpty() == true) ""
                else parameters?.joinToString(",")

                when {
                    hasInjectorFn -> {
                        "$name: $typeString = $injectorFn<$typeString>($parametersString)"
                    }

                    useKoinInjection -> {
                        if (parametersString.isNullOrEmpty()) {
                            "$name: $typeString = koinInject()"
                        } else {
                            "$name: $typeString = koinInject { parametersOf($parametersString) }"
                        }
                    }

                    else -> throw Exception("Cannot determine inject function")
                }
            }

            param.annotations.any { it.shortName.asString() == ANNOTATION_PROVIDE } -> {
                val annotation =
                    param.annotations.first { it.shortName.asString() == ANNOTATION_PROVIDE }
                val providerFunction = annotation.arguments.firstOrNull()?.value as? String
                    ?: "error(\"No provider function specified\")"
                "$name: $typeString = $providerFunction()"
            }

            param.type.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.CoroutineScope" -> {
                "$name: $typeString = rememberCoroutineScope()"
            }

            else -> "$name: $typeString"
        }

        defaultValue
    } ?: emptyList()

internal fun getParameterTypeString(param: KSValueParameter): String? {
    val type =
        param.type.resolve().declaration.qualifiedName?.asString() ?: return null
    val superTypes = param.type.resolve().arguments.map {
        it.type?.resolve()?.declaration?.qualifiedName?.asString()
    }

    return buildString {
        if (superTypes.isEmpty()) append(type)
        else append("$type<${superTypes.joinToString(",")}>")

        if (param.type.resolve().isMarkedNullable) {
            append("?")
        }
    }
}

internal fun getClassesImports(classDeclaration: KSClassDeclaration): List<String> {
    return classDeclaration.getAllProperties()
        .mapNotNull { property ->
            val type = property.type.resolve()
            val typeName = type.declaration.qualifiedName?.asString()
            if (typeName != null && !typeName.startsWith("kotlin.")) typeName else null
        }
        .toSet() // Ensure unique imports
        .map { "import $it" }
}

internal fun getConstructorArgs(classDeclaration: KSClassDeclaration): List<String> {
    return classDeclaration.primaryConstructor?.parameters?.mapNotNull { param ->
        param.name?.asString()
    } ?: emptyList()
}

internal fun hasRememberCoroutineScope(classDeclaration: KSClassDeclaration) =
    classDeclaration.primaryConstructor?.parameters?.any {
        it.type.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.CoroutineScope"
    } == true