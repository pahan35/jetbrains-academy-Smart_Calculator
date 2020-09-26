package calculator

import java.lang.Exception
import java.lang.NumberFormatException
import java.math.BigInteger
import java.util.*
import java.util.regex.Pattern

enum class Operations(val sign: String, val priority: Int) {
    PLUS("+", 0),
    MINUS("-", 0),
    DIVIDE("/", 1),
    MULTIPLY("*", 1),
    LEFT_PARENTHESIS("(", 99),
    RIGHT_PARENTHESIS(")", 99);

    companion object {
        fun findBySign(sign: String): Operations {
            return findBySignOrNull(sign) ?: throw InvalidExpressionException("Unknown operator $sign")
        }

        private fun findBySignOrNull(sign: String): Operations? {
            return values().find { it.sign == sign }
        }
    }
}

data class Element(val isNumber: Boolean = false, val isOperation: Boolean = false, val isVariable: Boolean = false, val value: String)

class InvalidExpressionException(message: String) : Exception(message)

class UnknownVariableException(message: String) : Exception(message)

val VAR_REGEX = Regex("[A-Za-z]+")

fun isVariable(name: String) = name.matches(VAR_REGEX)

class Expression(input: String, private val calculator: SmartCalculator) {
    private val postfix = mutableListOf<Element>()

    init {
        val operatorStack = mutableListOf<Operations>()

        input
                .replace(Regex("--"), "+")
                .replace(Regex("[+]+"), "+")
                .replace(Regex("(\\+-|-+)"), "-")
                .replace(Pattern.compile("(-?\\d+|[${Operations.values().joinToString("") { it.sign }}])").toRegex(), " $1 ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .split(Regex("\\s+"))
                .forEach {
                    val isNumber = Regex("-?\\d+").matches(it)
                    val isVariable_ = isVariable(it)
                    if (isVariable_ || isNumber) {
                        postfix.add(Element(isNumber = isNumber, isVariable = isVariable_, value = it))
                    } else {
                        for (c in it) {
                            val currentOperator = Operations.findBySign(it)
                            when {
                                operatorStack.isEmpty() || operatorStack.last() == Operations.LEFT_PARENTHESIS -> {
                                    operatorStack.add(currentOperator)
                                }
                                it == Operations.LEFT_PARENTHESIS.sign -> operatorStack.add(Operations.findBySign(it))
                                it == Operations.RIGHT_PARENTHESIS.sign -> {
                                    do {
                                        if (operatorStack.isEmpty()) {
                                            throw InvalidExpressionException("Right brace without left brace")
                                        }
                                        val lastIndex = operatorStack.lastIndex
                                        val operator = operatorStack[lastIndex]
                                        operatorStack.removeAt(lastIndex)
                                        val isLeftParenthesis = operator == Operations.LEFT_PARENTHESIS
                                        if (!isLeftParenthesis) {
                                            postfix.add(Element(isOperation = true, value = operator.sign))
                                        }
                                    } while (!isLeftParenthesis)
                                }
                                currentOperator.priority > operatorStack.last().priority -> operatorStack.add(currentOperator)
                                else -> {
                                    do {
                                        val lastIndex = operatorStack.lastIndex
                                        val lastOperator = operatorStack[lastIndex]
                                        val shouldMoveToPostfix = !(currentOperator.priority > lastOperator.priority || lastOperator == Operations.LEFT_PARENTHESIS)
                                        if (shouldMoveToPostfix) {
                                            postfix.add(Element(isOperation = true, value = lastOperator.sign))
                                            operatorStack.removeAt(lastIndex)
                                        }
                                        val shouldFinish = !shouldMoveToPostfix || operatorStack.isEmpty()
                                        if (shouldFinish) {
                                            operatorStack.add(currentOperator)
                                        }
                                    } while (!shouldFinish)
                                }
                            }
                        }
                    }
                }
        while (operatorStack.isNotEmpty()) {
            val lastIndex = operatorStack.lastIndex
            val operator = operatorStack[lastIndex]
            postfix.add(Element(isOperation = true, value = operator.sign))
            operatorStack.removeAt(lastIndex)
        }
    }

    fun calculate(): BigInteger {
        val result = mutableListOf<BigInteger>()
        for (element in postfix) {
            when {
                element.isNumber -> result.add(element.value.toBigInteger())
                element.isVariable -> result.add(calculator.getVariable(element.value))
                element.isOperation -> {
                    if (result.size < 2) {
                        throw InvalidExpressionException("Too many signs in expression")
                    }
                    val n2Index = result.lastIndex
                    val n2 = result[n2Index]
                    result.removeAt(n2Index)
                    val n1Index = result.lastIndex
                    val n1 = result[n1Index]
                    result.removeAt(n1Index)
                    result.add(when (element.value) {
                        Operations.PLUS.sign -> n1 + n2
                        Operations.MINUS.sign -> n1 - n2
                        Operations.MULTIPLY.sign -> n1 * n2
                        Operations.DIVIDE.sign -> n1 / n2
                        else -> throw InvalidExpressionException("Unknown operator ${element.value}")
                    })
                }
            }
        }
        return result[0]
    }
}

class SmartCalculator() {
    private val variables = mutableMapOf<String, BigInteger>()

    private fun existsVariable(variable: String) {
        if (!variables.contains(variable)) {
            throw UnknownVariableException("Unknown variable $variable")
        }
    }

    fun getVariable(variable: String): BigInteger {
        existsVariable(variable)
        return variables[variable]!!
    }

    fun setVariable(variable: String, value: BigInteger) {
        variables[variable] = value
    }

    fun setVariable(variable: String, sourceVariable: String) {
        existsVariable(sourceVariable)
        variables[variable] = variables[sourceVariable]!!
    }
}

fun main() {
    val scanner = Scanner(System.`in`).useLocale(Locale.US)

    val calculator = SmartCalculator()
    while (scanner.hasNextLine()) {
        val input = scanner.nextLine()
        if (input.isEmpty()) {
            continue
        }
        if (input.startsWith('/')) {
            when (input) {
                "/help" -> {
                    println("""The program calculates the sum of numbers.
Unary and binary minuses are supported, e.g. 2 -- 2 = 4""")
                    continue
                }
                "/exit" -> {
                    print("Bye!")
                    break
                }
                else -> {
                    println("Unknown command")
                    continue
                }
            }
        }
        try {
            if (input.contains('=')) {
                try {
                    val parts = input.replace(" ", "").split('=')
                    if (parts.count() != 2) {
                        println("Invalid assignment")
                        continue
                    }
                    val (variable, value) = parts
                    if (!isVariable(variable)) {
                        println("Invalid identifier")
                        continue
                    }
                    if (isVariable(value)) {
                        calculator.setVariable(variable, value)
                        continue
                    }
                    calculator.setVariable(variable, value.toBigInteger())
                } catch (e: NumberFormatException) {
                    println("Invalid assignment")
                }
                continue
            }
            if (isVariable(input)) {
                println(calculator.getVariable(input))
                continue
            }
            try {
                val expression = Expression(input, calculator)
                println(expression.calculate())
            } catch (e: InvalidExpressionException) {
                println("Invalid expression")
            }
        } catch (e: UnknownVariableException) {
            println("Unknown variable")
        }
    }
}
