package com.tchibolabs.genrmbr.processors

import com.google.devtools.ksp.symbol.KSClassDeclaration

internal const val ANNOTATION_VALUE = "Value"
internal const val ANNOTATION_INJECT = "Inject"
internal const val ANNOTATION_INJECT_CUSTOM = "InjectCustom"
internal const val ANNOTATION_SAVEABLE = "Saveable"
internal const val ANNOTATION_REMEMBERED = "Remembered"
internal const val ANNOTATION_REMEMBER_SAVEABLE = "RememberSaveable"
internal const val ANNOTATION_KEY = "Key"

// Get the injector function name from the Remembered/RememberSaveable annotation if present
fun getInjectorParameter(
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
        val type = param.type.resolve().declaration.qualifiedName?.asString() ?: return@mapNotNull null

        val defaultValue = when {
            param.annotations.any { it.shortName.asString() == ANNOTATION_VALUE } -> {
                val annotation = param.annotations.first { it.shortName.asString() == "Value" }
                val value = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String

                if (value != null) {
                    "$name: $type = $value"
                } else {
                    "$name: $type"
                }
            }

            param.annotations.any { it.shortName.asString() == ANNOTATION_INJECT } -> when {
                hasInjectorFn -> {
                    val type = param.type.resolve().declaration.qualifiedName?.asString()
                        ?: "error(\"No provider function specified\")"
                    "$name: $type = $injectorFn<$type>()"
                }

                useKoinInjection -> {
                    "$name: $type = koinInject()"
                }

                else -> throw Exception("Cannot determine inject function")
            }

            param.annotations.any { it.shortName.asString() == ANNOTATION_INJECT_CUSTOM } -> {
                val annotation =
                    param.annotations.first { it.shortName.asString() == ANNOTATION_INJECT_CUSTOM }
                val providerFunction = annotation.arguments.firstOrNull()?.value as? String
                    ?: "error(\"No provider function specified\")"
                "$name: $type = $providerFunction()"
            }

            param.type.resolve().declaration.qualifiedName?.asString() == "kotlinx.coroutines.CoroutineScope" -> {
                "$name: $type = rememberCoroutineScope()"
            }

            else -> "$name: $type"
        }

        defaultValue
    } ?: emptyList()