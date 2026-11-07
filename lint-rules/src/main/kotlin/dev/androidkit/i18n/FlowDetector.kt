package dev.androidkit.i18n

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class FlowDetector : Detector(), Detector.UastScanner {
    private val methods = mutableMapOf<String, MutableList<UMethod>>()
    private val pendingCalls = mutableListOf<() -> Unit>()

    override fun afterCheckEachProject(context: com.android.tools.lint.detector.api.Context) {
        pendingCalls.forEach { it() }
        pendingCalls.clear()
        methods.clear()
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java, UCallExpression::class.java, UBinaryExpression::class.java, UTryExpression::class.java)
    override fun createUastHandler(context: JavaContext): UElementHandler {
        val config = Configuration.load(context.project.dir)
        val localizers = config.domains.values.map { it.localizer }.toSet()
        fun matches(name: String, configured: Set<String>) = configured.any { it == name || it.substringAfterLast('.') == name }
        fun resourceLocalizer(call: UCallExpression): Boolean {
            if (call.methodName == "stringResource") return true
            if (call.methodName !in setOf("getString", "getQuantityString")) return false
            fun resourceOwner(owner: com.intellij.psi.PsiClass?): Boolean {
                if (owner == null) return false
                if (owner.qualifiedName in setOf("android.content.Context", "android.content.res.Resources", "androidx.fragment.app.Fragment")) return true
                return owner.supers.any { resourceOwner(it) }
            }
            return resourceOwner(call.resolve()?.containingClass)
        }
        fun annotation(owner: PsiModifierListOwner?, name: String) = owner?.modifierList?.annotations?.any { it.qualifiedName?.substringAfterLast('.') == name } == true
        fun sourceBody(call: UCallExpression): UExpression? {
            val resolved = call.resolve()
            val owner = resolved?.containingClass?.qualifiedName
            val candidates = methods[call.methodName ?: return null].orEmpty().filter {
                val candidateOwner = it.javaPsi.containingClass?.qualifiedName
                owner == null || candidateOwner == owner
            }
            val indexed = candidates.singleOrNull()
            return indexed?.uastBody ?: (resolved?.toUElementOfType<UMethod>()
                ?: resolved?.navigationElement?.toUElementOfType<UMethod>())?.uastBody
        }
        fun flow(root: UElement, kind: String): Boolean {
            val seen = mutableSetOf<com.intellij.psi.PsiElement>()
            fun scan(start: UElement, depth: Int): Boolean {
                var found = false
                start.accept(object : AbstractUastVisitor() {
                    override fun visitElement(node: UElement): Boolean {
                        if (found) return true
                        if (kind == "raw" && node is UBinaryExpression && (node.operator == UastBinaryOperator.EQUALS || node.operator == UastBinaryOperator.NOT_EQUALS)) return true
                        if (node is UQualifiedReferenceExpression && kind == "raw") {
                            val call = node.selector as? UCallExpression
                            if (call != null && (resourceLocalizer(call) || matches(call.methodName.orEmpty(), localizers) || annotation(call.resolve(), "DisplayText"))) return true
                        }
                        if (node is UCallExpression) {
                            val name = node.methodName.orEmpty()
                            val method = node.resolve()
                            if (kind == "exception" && name in setOf("getMessage", "toString")) {
                                val type = node.receiver?.getExpressionType()?.canonicalText.orEmpty()
                                if ((type.endsWith("Exception") || type.endsWith("Throwable") || type.endsWith("Error")) && config.userExceptions.none { type == it || type.endsWith(".$it") }) { found = true; return true }
                            }
                            val isLocal = resourceLocalizer(node) || matches(name, localizers) || annotation(method, "DisplayText")
                            if (kind == "localized" && isLocal) { found = true; return true }
                            if (kind == "raw" && isLocal) return true
                            if (kind == "raw" && annotation(method, "IdentifierDomain")) { found = true; return true }
                            if (kind == "localized") {
                                val type = node.getExpressionType()?.canonicalText
                                if (type != null && type !in setOf("java.lang.String", "kotlin.String", "java.lang.CharSequence", "kotlin.CharSequence")) return true
                            }
                            if (depth == 0 && (method == null || seen.add(method))) {
                                val body = sourceBody(node)
                                if (body != null) {
                                    if (scan(body, depth + 1)) { found = true; return true }
                                    if (kind == "raw" && flow(body, "localized")) return true
                                }
                            }
                        }
                        if (node is UReferenceExpression) {
                            val resolved = node.resolve()
                            if (kind == "raw" && annotation(resolved as? PsiModifierListOwner, "DisplayText")) return true
                            if (kind == "raw") {
                                val variable = resolved as? PsiVariable
                                val type = variable?.type?.canonicalText.orEmpty().substringAfterLast('.')
                                if (annotation(resolved as? PsiModifierListOwner, "IdentifierDomain") || config.domains.keys.any { it.equals(type, true) } || config.domains.values.any { it.source?.substringAfter(':') == node.resolvedName }) { found = true; return true }
                            }
                            if (resolved is PsiVariable && seen.add(resolved)) {
                                val initializer = (resolved.toUElement() as? UVariable)?.uastInitializer
                                if (initializer != null && scan(initializer, depth)) { found = true; return true }
                            }
                        }
                        if (kind == "exception" && node is UQualifiedReferenceExpression) {
                            val selector = node.selector.asSourceString()
                            val type = node.receiver.getExpressionType()?.canonicalText.orEmpty()
                            if ((selector == "message" || selector.startsWith("getMessage(") || selector.startsWith("toString(")) && (type.endsWith("Exception") || type.endsWith("Throwable") || type.endsWith("Error")) && config.userExceptions.none { type == it || type.endsWith(".$it") }) { found = true; return true }
                        }
                        return false
                    }
                })
                return found
            }
            return scan(root, 0)
        }
        fun checkCall(node: UCallExpression) {
                val name = node.methodName ?: return
                val args = node.valueArguments
                val owner = node.resolve()?.containingClass?.qualifiedName.orEmpty()
                val receiver = node.receiver?.getExpressionType()?.canonicalText.orEmpty()
                val dataSink = matches(name, config.dataSinks) || matches(owner, config.dataSinks) || matches(receiver, config.dataSinks)
                if (matches(name, config.uiSinks)) for (arg in args) {
                    if (flow(arg, "raw")) context.report(RAW_ID, arg, context.getLocation(arg), "Localize this identifier before displaying it.")
                    if (flow(arg, "exception")) context.report(EXCEPTION_UI, arg, context.getLocation(arg), "Do not show a technical exception message to users.")
                }
                if (dataSink) for (arg in args) {
                    if (flow(arg, "localized")) context.report(DATA_LEAK, arg, context.getLocation(arg), "Use the canonical identifier in this data path.")
                }
        }
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                methods.getOrPut(node.name) { mutableListOf() }.add(node)
            }
            override fun visitCallExpression(node: UCallExpression) {
                pendingCalls.add { checkCall(node) }
            }
            override fun visitTryExpression(node: UTryExpression) {
                var localizedThrow = false
                fun inspect(body: UElement, inline: Boolean) {
                    body.accept(object : AbstractUastVisitor() {
                        override fun visitThrowExpression(throwExpression: UThrowExpression): Boolean {
                            val expression = throwExpression.thrownExpression
                            val type = expression.getExpressionType()?.canonicalText.orEmpty()
                            if (config.userExceptions.none { type == it || type.endsWith(".$it") }) return false
                            expression.accept(object : AbstractUastVisitor() {
                                override fun visitCallExpression(call: UCallExpression): Boolean {
                                    if (call.methodName in setOf("getString", "stringResource") || matches(call.methodName.orEmpty(), localizers)) localizedThrow = true
                                    return false
                                }
                            })
                            return false
                        }
                        override fun visitCallExpression(call: UCallExpression): Boolean {
                            if (inline) call.resolve()?.toUElementOfType<UMethod>()?.uastBody?.let { inspect(it, false) }
                            return false
                        }
                    })
                }
                inspect(node.tryClause, true)
                if (!localizedThrow) return
                for (handler in node.catchClauses) {
                    val parameter = handler.parameters.firstOrNull() ?: continue
                    val type = parameter.type.canonicalText
                    if (type.substringAfterLast('.') !in config.userExceptions.map { it.substringAfterLast('.') } && type.substringAfterLast('.') !in setOf("Exception", "Throwable")) continue
                    val name = parameter.name ?: continue
                    var displaysMessage = false
                    handler.body.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(call: UCallExpression): Boolean {
                            if (matches(call.methodName.orEmpty(), config.uiSinks)) {
                                for (argument in call.valueArguments) argument.accept(object : AbstractUastVisitor() {
                                    override fun visitQualifiedReferenceExpression(reference: UQualifiedReferenceExpression): Boolean {
                                        val receiver = reference.receiver as? UReferenceExpression
                                        val selector = reference.selector
                                        val member = when (selector) {
                                            is UReferenceExpression -> selector.resolvedName
                                            is UCallExpression -> selector.methodName
                                            else -> null
                                        }
                                        if (receiver?.resolvedName == name && member in setOf("message", "getMessage")) displaysMessage = true
                                        return false
                                    }
                                })
                            }
                            return false
                        }
                    })
                    if (!displaysMessage) context.report(SWALLOWED, handler, context.getLocation(handler), "Preserve the localized user-facing exception message in this error handler.")
                }
            }
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator == UastBinaryOperator.ASSIGN && matches(node.leftOperand.asSourceString().substringAfterLast('.'), config.uiSinks)) {
                    val value = node.rightOperand
                    if (flow(value, "raw")) context.report(RAW_ID, value, context.getLocation(value), "Localize this identifier before displaying it.")
                    if (flow(value, "exception")) context.report(EXCEPTION_UI, value, context.getLocation(value), "Do not show a technical exception message to users.")
                }
                if ((node.operator == UastBinaryOperator.EQUALS || node.operator == UastBinaryOperator.NOT_EQUALS) &&
                    ((flow(node.leftOperand, "localized") && flow(node.rightOperand, "raw")) ||
                        (flow(node.rightOperand, "localized") && flow(node.leftOperand, "raw")))) {
                    context.report(DATA_LEAK, node, context.getLocation(node), "Compare canonical identifiers rather than localized labels.")
                }
            }
        }
    }
}
