package io.github.pelletier197.fixkture.domain.generator.java

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import io.github.pelletier197.fixkture.domain.*
import io.github.pelletier197.fixkture.domain.generator.CallbackClassInstantiationFieldBuilder
import io.github.pelletier197.fixkture.domain.generator.LanguageCallbackValueGenerator
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass

class NullInstantiationField : CallbackClassInstantiationFieldBuilder(
        LanguageCallbackValueGenerator(
                java = { "null" },
                kotlin = { "null" }
        )
)

class ClassParameterInstantiationField(
        private val parameter: PsiParameter,
        private val instantiationField: InstantiationFieldBuilder
) : InstantiationFieldBuilder {
    override fun asJavaConstructorArgument(context: FieldConstructionContext): String {
        return instantiationField.asJavaConstructorArgument(modifyContext(context))
    }

    override fun asKotlinConstructorArgument(context: FieldConstructionContext): String {
        return instantiationField.asKotlinConstructorArgument(modifyContext(context))
    }

    override fun asJavaFlatValue(context: FieldConstructionContext): String {
        return instantiationField.asJavaFlatValue(modifyContext(context))
    }

    override fun asKotlinFlatValue(context: FieldConstructionContext): String {
        return instantiationField.asKotlinFlatValue(modifyContext(context))
    }

    private fun modifyContext(context: FieldConstructionContext): FieldConstructionContext {
        return context.copy(
                fieldName = parameter.name,
                targetElement = TargetElement.of(parameter),
        )
    }
}

data class ClassInstantiationContext(
        val targetClass: PsiClass,
        val constructorSelector: ConstructorSelectionFunction,
) {
    fun asClassInstantiationStatementBuilderContext(element: PsiElement): PsiElementInstantiationStatementBuilderContext {
        return PsiElementInstantiationStatementBuilderContext(
                targetElement = TargetElement.of(element),
                constructorSelector = this.constructorSelector
        )
    }
}

class ClassInstantiationField(
        val targetClass: PsiClass,
        val argumentsFields: List<InstantiationFieldBuilder>
) : CallbackClassInstantiationFieldBuilder(
        LanguageCallbackValueGenerator(
                java = { generateJavaClass(targetClass = targetClass, arguments = argumentsFields, context = it) },
                kotlin = { generateKotlinClass(targetClass = targetClass, arguments = argumentsFields, context = it) }
        )
)

private fun generateJavaClass(targetClass: PsiClass, arguments: List<InstantiationFieldBuilder>, context: FieldConstructionContext): String {
    val parameters = arguments.joinToString(separator = ", ") { it.asJavaConstructorArgument(context) }
    return "new ${targetClass.qualifiedName}($parameters)"
}

private fun generateKotlinClass(targetClass: PsiClass, arguments: List<InstantiationFieldBuilder>, context: FieldConstructionContext): String {
    val parameters = getKotlinParametersString(targetClass, arguments, context)
    return "${targetClass.qualifiedName}($parameters)"
}

private fun getKotlinParametersString(targetClass: PsiClass, arguments: List<InstantiationFieldBuilder>, context: FieldConstructionContext): String {
    // The target class is also a kotlin class
    if (targetClass is KtUltraLightClass) {
        return arguments.joinToString(separator = ", ") { it.asKotlinConstructorArgument(context) }
    }

    // The target class is a Java class
    return arguments.joinToString(separator = ", ") { it.asKotlinFlatValue(context) }
}

object ClassGenerator {
    fun generateClass(context: ClassInstantiationContext): InstantiationFieldBuilder {
        val targetClass = context.targetClass
        val targetConstructor = context.constructorSelector(targetClass) ?: return NullInstantiationField()

        val arguments = targetConstructor.parameterList.parameters.toList()
        val instantiationFields = arguments.map {
            convertClassArgumentToInstantiationField(psiParameter = it, context = context)
        }

        return ClassInstantiationField(
                targetClass = targetClass,
                argumentsFields = instantiationFields
        )
    }

    private fun convertClassArgumentToInstantiationField(psiParameter: PsiParameter,
                                                         context: ClassInstantiationContext
    ): InstantiationFieldBuilder {
        return ClassParameterInstantiationField(
                parameter = psiParameter,
                instantiationField = createInstantiationField(
                        context = context.asClassInstantiationStatementBuilderContext(psiParameter)
                )
        )
    }
}