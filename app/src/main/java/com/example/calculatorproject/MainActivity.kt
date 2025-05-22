package com.example.calculatorproject

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Stack

class MainActivity : AppCompatActivity() {
    // UI elements
    private lateinit var expression: TextView
    private lateinit var result: TextView
    private lateinit var history: TextView
    private lateinit var memoryIndicator: TextView

    // Current expression state
    private var lastOperation = false
    private var calculationComplete = false
    private var openParentheses = 0

    // Memory management
    private var memoryValue: BigDecimal = BigDecimal.ZERO
    private var memoryActive: Boolean = false

    // History management
    private val maxHistoryItems = 5
    private val historyList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        try {
            // Set padding for system bars
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Initialize views
            expression = findViewById(R.id.expressionText)
            result = findViewById(R.id.resultText)
            history = findViewById(R.id.historyText)
            memoryIndicator = findViewById(R.id.memoryIndicator)

            // Set up expression TextView
            expression.movementMethod = ScrollingMovementMethod()
            history.movementMethod = ScrollingMovementMethod()

            // Set initial value
            expressionText("0")
            resultText("")
            updateMemoryIndicator()

            // Setup button click listeners
            setupButtons()

            // Restore state if available
            savedInstanceState?.let {
                if (it.containsKey("expression")) expressionText(it.getString("expression", "0"))
                if (it.containsKey("result")) resultText(it.getString("result", ""))
                if (it.containsKey("memoryValue")) memoryValue = BigDecimal(it.getString("memoryValue", "0"))
                if (it.containsKey("memoryActive")) memoryActive = it.getBoolean("memoryActive", false)
                if (it.containsKey("lastOperation")) lastOperation = it.getBoolean("lastOperation", false)
                if (it.containsKey("calculationComplete")) calculationComplete = it.getBoolean("calculationComplete", false)
                if (it.containsKey("openParentheses")) openParentheses = it.getInt("openParentheses", 0)

                // Restore history
                val savedHistory = it.getStringArrayList("history")
                if (savedHistory != null) {
                    historyList.clear()
                    historyList.addAll(savedHistory)
                    updateHistoryDisplay()
                }

                updateMemoryIndicator()
            }

        } catch (e: Exception) {
            // Handle initialization errors
            Toast.makeText(this, "Error initializing calculator: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save calculator state
        outState.putString("expression", expression.text.toString())
        outState.putString("result", result.text.toString())
        outState.putString("memoryValue", memoryValue.toString())
        outState.putBoolean("memoryActive", memoryActive)
        outState.putBoolean("lastOperation", lastOperation)
        outState.putBoolean("calculationComplete", calculationComplete)
        outState.putInt("openParentheses", openParentheses)

        // Save history
        outState.putStringArrayList("history", ArrayList(historyList))
    }

    private fun setupButtons() {
        // Number buttons (0-9)
        setupNumberButton(R.id.zero, "0")
        setupNumberButton(R.id.one, "1")
        setupNumberButton(R.id.two, "2")
        setupNumberButton(R.id.three, "3")
        setupNumberButton(R.id.four, "4")
        setupNumberButton(R.id.five, "5")
        setupNumberButton(R.id.six, "6")
        setupNumberButton(R.id.seven, "7")
        setupNumberButton(R.id.eight, "8")
        setupNumberButton(R.id.nine, "9")

        // Operation buttons
        setupOperationButton(R.id.add, "+")
        setupOperationButton(R.id.subtract, "-")
        setupOperationButton(R.id.multiply, "×")
        setupOperationButton(R.id.divide, "÷")

        // Memory buttons
        setupMemoryButtons()

        // Special buttons
        setupSpecialButtons()
    }

    private fun setupNumberButton(id: Int, number: String) {
        findViewById<MaterialButton>(id).setOnClickListener {
            animateButtonClick(it)
            if (calculationComplete) {
                expressionText(number)
                calculationComplete = false
            } else {
                appendNumber(number)
            }
            lastOperation = false
            calculateResult()
        }
    }

    private fun setupOperationButton(id: Int, operation: String) {
        findViewById<MaterialButton>(id).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()

            if (calculationComplete) {
                // If calculation is complete, use the result as starting point
                expressionText(result.text.toString().replace("=", "") + operation)
                calculationComplete = false
            } else if (lastOperation) {
                // Replace the last operator with the new one
                expressionText(currentExpression.substring(0, currentExpression.length - 1) + operation)
            } else if (currentExpression == "0") {
                // Handle starting with an operation (except minus)
                if (operation == "-") {
                    expressionText(operation)
                }
            } else {
                // Don't allow operator after open parenthesis (except minus)
                val lastChar = if (currentExpression.isNotEmpty()) currentExpression.last() else ' '
                if (lastChar == '(' && operation != "-") {
                    return@setOnClickListener
                }

                appendExpressionText(operation)
            }

            lastOperation = true
        }
    }

    private fun setupMemoryButtons() {
        // Memory Clear (MC)
        findViewById<MaterialButton>(R.id.memoryClear).setOnClickListener {
            animateButtonClick(it)
            memoryValue = BigDecimal.ZERO
            memoryActive = false
            updateMemoryIndicator()
            showToast("Memory cleared")
        }

        // Memory Recall (MR)
        findViewById<MaterialButton>(R.id.memoryRecall).setOnClickListener {
            animateButtonClick(it)
            if (memoryActive) {
                if (calculationComplete) {
                    expressionText(memoryValue.stripTrailingZeros().toPlainString())
                    calculationComplete = false
                } else if (expression.text.toString() == "0") {
                    expressionText(memoryValue.stripTrailingZeros().toPlainString())
                } else if (lastOperation) {
                    appendExpressionText(memoryValue.stripTrailingZeros().toPlainString())
                    lastOperation = false
                } else {
                    // Only insert memory if there's an operator before
                    val currentExpr = expression.text.toString()
                    val lastChar = if (currentExpr.isNotEmpty()) currentExpr.last() else ' '
                    if (isOperator(lastChar.toString()) || lastChar == '(') {
                        appendExpressionText(memoryValue.stripTrailingZeros().toPlainString())
                    }
                }
                calculateResult()
            } else {
                showToast("No value stored in memory")
            }
        }

        // Memory Add (M+)
        findViewById<MaterialButton>(R.id.memoryAdd).setOnClickListener {
            animateButtonClick(it)
            try {
                val currentValue = if (calculationComplete) {
                    BigDecimal(result.text.toString().replace("=", ""))
                } else if (!endsWithOperator(expression.text.toString()) &&
                    expression.text.toString() != "0") {
                    evaluateExpression(expression.text.toString())
                } else {
                    null
                }

                if (currentValue != null) {
                    memoryValue = memoryValue.add(currentValue)
                    memoryActive = true
                    updateMemoryIndicator()
                    showToast("Added to memory: ${memoryValue.stripTrailingZeros().toPlainString()}")
                } else {
                    showToast("Invalid operation for M+")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            }
        }

        // Memory Subtract (M-)
        findViewById<MaterialButton>(R.id.memorySubtract).setOnClickListener {
            animateButtonClick(it)
            try {
                val currentValue = if (calculationComplete) {
                    BigDecimal(result.text.toString().replace("=", ""))
                } else if (!endsWithOperator(expression.text.toString()) &&
                    expression.text.toString() != "0") {
                    evaluateExpression(expression.text.toString())
                } else {
                    null
                }

                if (currentValue != null) {
                    memoryValue = memoryValue.subtract(currentValue)
                    memoryActive = true
                    updateMemoryIndicator()
                    showToast("Subtracted from memory: ${memoryValue.stripTrailingZeros().toPlainString()}")
                } else {
                    showToast("Invalid operation for M-")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun setupSpecialButtons() {
        // Clear button
        findViewById<MaterialButton>(R.id.clear).setOnClickListener {
            animateButtonClick(it)
            expressionText("0")
            resultText("")
            lastOperation = false
            calculationComplete = false
            openParentheses = 0
        }

        // Backspace button
        findViewById<MaterialButton>(R.id.backSpace).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()
            if (calculationComplete) {
                expressionText("0")
                resultText("")
                calculationComplete = false
            } else if (currentExpression.isNotEmpty() && currentExpression != "0") {
                val lastChar = currentExpression.last()
                if (lastChar == '(') {
                    openParentheses--
                } else if (lastChar == ')') {
                    openParentheses++
                }

                val newExpression = currentExpression.substring(0, currentExpression.length - 1)
                expressionText(if (newExpression.isEmpty()) "0" else newExpression)

                // Check if the last character was an operator
                lastOperation = if (newExpression.isNotEmpty()) {
                    isOperator(newExpression.last().toString())
                } else {
                    false
                }

                calculateResult()
            }
        }

        // Percent button
        findViewById<MaterialButton>(R.id.percent).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()
            if (currentExpression != "0" && !endsWithOperator(currentExpression) && !endsWithOpenParenthesis(currentExpression)) {
                try {
                    // Find the last number in the expression
                    val lastOperatorIndex = findLastOperatorIndex(currentExpression)

                    if (lastOperatorIndex == -1) {
                        // No operators, convert the whole expression
                        val value = BigDecimal(currentExpression).divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
                        expressionText(formatNumber(value))
                    } else {
                        // Get the last number
                        val lastNumber = currentExpression.substring(lastOperatorIndex + 1)

                        if (lastNumber.isNotEmpty()) {
                            val percentValue = BigDecimal(lastNumber).divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)

                            // If there's a number before the operator, apply the percentage to it
                            if (lastOperatorIndex > 0 &&
                                !isOperator(currentExpression[lastOperatorIndex - 1].toString()) &&
                                currentExpression[lastOperatorIndex - 1] != '(') {

                                // Find the operand before this operator
                                val prevOperatorIndex = findPreviousOperatorIndex(currentExpression, lastOperatorIndex - 1)
                                val prevNumber = if (prevOperatorIndex == -1) {
                                    currentExpression.substring(0, lastOperatorIndex)
                                } else {
                                    currentExpression.substring(prevOperatorIndex + 1, lastOperatorIndex)
                                }

                                if (prevNumber.isNotEmpty() && prevNumber != "-") {
                                    val baseValue = BigDecimal(prevNumber)
                                    val calculatedPercent = baseValue.multiply(percentValue)

                                    // Replace the last number with calculated percentage value
                                    expressionText(currentExpression.substring(0, lastOperatorIndex + 1) +
                                            formatNumber(calculatedPercent))
                                } else {
                                    // Simple percentage
                                    expressionText(currentExpression.substring(0, lastOperatorIndex + 1) +
                                            formatNumber(percentValue))
                                }
                            } else {
                                // Simple percentage
                                expressionText(currentExpression.substring(0, lastOperatorIndex + 1) +
                                        formatNumber(percentValue))
                            }
                        }
                    }

                    calculateResult()
                } catch (e: NumberFormatException) {
                    showToast("Invalid operation")
                }
            }
        }

        // Decimal point button
        findViewById<MaterialButton>(R.id.dot).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()

            if (calculationComplete) {
                expressionText("0.")
                calculationComplete = false
            } else {
                val lastOperatorIndex = findLastOperatorIndex(currentExpression)
                val lastNumber = if (lastOperatorIndex == -1) {
                    currentExpression
                } else {
                    currentExpression.substring(lastOperatorIndex + 1)
                }

                // Check for closing parenthesis
                val lastClosingParenIndex = lastNumber.lastIndexOf(')')
                val effectiveLastNumber = if (lastClosingParenIndex != -1) {
                    lastNumber.substring(lastClosingParenIndex + 1)
                } else {
                    lastNumber
                }

                if (!effectiveLastNumber.contains(".")) {
                    if (effectiveLastNumber.isEmpty() || endsWithOperator(currentExpression) ||
                        currentExpression.endsWith("(") || currentExpression.endsWith(")")) {
                        appendExpressionText("0.")
                    } else {
                        appendExpressionText(".")
                    }
                }
            }

            lastOperation = false
        }

        // Parentheses button
        findViewById<MaterialButton>(R.id.parentheses).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()

            if (calculationComplete) {
                expressionText("(")
                openParentheses = 1
                calculationComplete = false
            } else if (currentExpression == "0") {
                expressionText("(")
                openParentheses = 1
            } else {
                val lastChar = if (currentExpression.isNotEmpty()) currentExpression.last() else ' '

                // Decide whether to add opening or closing parenthesis
                if (openParentheses > 0 && !isOperator(lastChar.toString()) && lastChar != '(') {
                    // Add closing parenthesis if we can close and previous char is not an operator
                    appendExpressionText(")")
                    openParentheses--
                } else {
                    // Add opening parenthesis
                    // If previous char is a number or closing parenthesis, add multiplication sign
                    if ((lastChar.isDigit() || lastChar == ')' || lastChar == '.') && currentExpression != "0") {
                        appendExpressionText("×(")
                    } else {
                        appendExpressionText("(")
                    }
                    openParentheses++
                }
            }

            lastOperation = false
            calculateResult()
        }

        // Equals button
        findViewById<MaterialButton>(R.id.equal_to).setOnClickListener {
            animateButtonClick(it)

            val currentExpression = expression.text.toString()
            if (currentExpression.isEmpty() || currentExpression == "0" || endsWithOperator(currentExpression)) {
                return@setOnClickListener
            }

            try {
                // Auto-close any open parentheses
                var finalExpression = currentExpression
                for (i in 1..openParentheses) {
                    finalExpression += ")"
                }

                val resultValue = evaluateExpression(finalExpression)
                val formattedResult = formatResult(resultValue)

                // Update history
                addToHistory("$finalExpression = $formattedResult")

                // Display the result without the equals sign in the expression
                resultText("=$formattedResult")
                calculationComplete = true
                openParentheses = 0
            } catch (e: Exception) {
                Toast.makeText(this, "Calculation error: ${e.message}", Toast.LENGTH_SHORT).show()
                resultText("Error")
            }
        }
    }

    // Helper methods
    private fun expressionText(str: String) {
        expression.text = str
    }

    private fun resultText(text: String) {
        result.text = text
    }

    private fun appendExpressionText(text: String) {
        if (expression.text.toString() == "0" && text != ".") {
            expressionText(text)
        } else {
            expressionText(expression.text.toString() + text)
        }
    }

    private fun appendNumber(number: String) {
        val currentExpression = expression.text.toString()
        if (currentExpression == "0") {
            expressionText(number)
        } else {
            // Check if the last character is a closing parenthesis
            if (currentExpression.isNotEmpty() && currentExpression.last() == ')') {
                appendExpressionText("×$number")
            } else {
                appendExpressionText(number)
            }
        }
    }

    private fun calculateResult() {
        val expr = expression.text.toString()
        if (expr.isNotEmpty() && !endsWithOperator(expr) && !endsWithOpenParenthesis(expr)) {
            try {
                // Auto-close any open parentheses for calculation
                var finalExpression = expr
                for (i in 1..openParentheses) {
                    finalExpression += ")"
                }

                val resultValue = evaluateExpression(finalExpression)
                resultText("=" + formatResult(resultValue))
            } catch (e: Exception) {
                resultText("")
            }
        } else {
            resultText("")
        }
    }

    private fun evaluateExpression(expr: String): BigDecimal {
        // Process the expression and replace UI symbols with calculation symbols
        val processedExpr = expr.replace("×", "*").replace("÷", "/")
        return ExpressionEvaluator().evaluate(processedExpr)
    }

    private fun formatResult(result: BigDecimal): String {
        // Format the result to remove trailing zeros
        return result.stripTrailingZeros().toPlainString()
    }

    private fun formatNumber(number: BigDecimal): String {
        // Format a number to remove unnecessary decimal zeros
        return number.stripTrailingZeros().toPlainString()
    }

    private fun endsWithOperator(str: String): Boolean {
        return str.isNotEmpty() && isOperator(str.last().toString())
    }

    private fun endsWithOpenParenthesis(str: String): Boolean {
        return str.isNotEmpty() && str.last() == '('
    }

    private fun isOperator(str: String): Boolean {
        return str == "+" || str == "-" || str == "×" || str == "÷" ||
                str == "*" || str == "/"
    }

    private fun findLastOperatorIndex(expression: String): Int {
        var parenthesesCount = 0

        for (i in expression.length - 1 downTo 0) {
            val c = expression[i]

            if (c == ')') {
                parenthesesCount++
                continue
            } else if (c == '(') {
                parenthesesCount--
                continue
            }

            // Only consider operators outside parentheses
            if (parenthesesCount == 0 && isOperator(c.toString())) {
                return i
            }
        }
        return -1
    }

    private fun findPreviousOperatorIndex(expression: String, startIndex: Int): Int {
        var parenthesesCount = 0

        for (i in startIndex downTo 0) {
            val c = expression[i]

            if (c == ')') {
                parenthesesCount++
                continue
            } else if (c == '(') {
                parenthesesCount--
                continue
            }

            // Only consider operators outside parentheses
            if (parenthesesCount == 0 && isOperator(c.toString())) {
                return i
            }
        }
        return -1
    }

    private fun animateButtonClick(view: View) {
        val animation = AlphaAnimation(1f, 0.7f)
        animation.duration = 100
        animation.repeatCount = 1
        animation.repeatMode = Animation.REVERSE
        view.startAnimation(animation)
    }

    private fun updateMemoryIndicator() {
        if (memoryActive) {
            memoryIndicator.visibility = View.VISIBLE
        } else {
            memoryIndicator.visibility = View.INVISIBLE
        }
    }

    private fun addToHistory(entry: String) {
        // Add to history list
        historyList.add(0, entry) // Add at the beginning

        // Trim history if needed
        if (historyList.size > maxHistoryItems) {
            historyList.removeAt(historyList.size - 1)
        }

        // Update the display
        updateHistoryDisplay()
    }

    private fun updateHistoryDisplay() {
        val historyText = StringBuilder()

        for (entry in historyList) {
            historyText.append(entry).append("\n")
        }

        history.text = historyText.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Enhanced expression evaluator that handles basic arithmetic operations.
 * Includes support for parentheses and follows mathematical order of operations.
 */
class ExpressionEvaluator {
    // Custom exception for evaluation errors
    class EvaluationException(message: String) : Exception(message)

    // Main evaluation function
    fun evaluate(expression: String): BigDecimal {
        try {
            return evaluateExpression(expression)
        } catch (e: Exception) {
            throw EvaluationException("Error evaluating expression: ${e.message}")
        }
    }

    private fun evaluateExpression(expression: String): BigDecimal {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) return BigDecimal.ZERO

        return evaluateTokens(tokens)
    }

    // Convert string expression to tokens
    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0

        while (i < expression.length) {
            val c = expression[i]

            when {
                c.isDigit() || c == '.' -> {
                    // Parse number
                    val sb = StringBuilder()
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                        sb.append(expression[i])
                        i++
                    }
                    tokens.add(sb.toString())
                    continue // Skip the increment at the end
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')' -> {
                    // Handle negative numbers
                    if (c == '-' && (i == 0 || expression[i-1] == '(' ||
                                expression[i-1] == '+' || expression[i-1] == '-' ||
                                expression[i-1] == '*' || expression[i-1] == '/')) {
                        // This is a negative sign, not a subtraction
                        tokens.add("-1")
                        tokens.add("*")
                        i++
                        continue
                    }

                    // Regular operators and parentheses
                    tokens.add(c.toString())
                }
                c.isWhitespace() -> {
                    // Skip whitespace
                }
                else -> {
                    throw EvaluationException("Invalid character: $c")
                }
            }
            i++
        }

        return tokens
    }

    // Evaluate the tokenized expression
    private fun evaluateTokens(tokens: List<String>): BigDecimal {
        // Implementation using Dijkstra's Shunting Yard algorithm
        val outputQueue = mutableListOf<String>()
        val operatorStack = Stack<String>()

        // Shunting yard algorithm - convert infix to postfix notation
        for (token in tokens) {
            when {
                isNumber(token) -> {
                    outputQueue.add(token)
                }
                token == "(" -> {
                    operatorStack.push(token)
                }
                token == ")" -> {
                    while (operatorStack.isNotEmpty() && operatorStack.peek() != "(") {
                        outputQueue.add(operatorStack.pop())
                    }
                    if (operatorStack.isNotEmpty() && operatorStack.peek() == "(") {
                        operatorStack.pop() // Discard the open parenthesis
                    } else {
                        throw EvaluationException("Mismatched parentheses")
                    }
                }
                isOperator(token) -> {
                    while (operatorStack.isNotEmpty() &&
                        operatorStack.peek() != "(" &&
                        hasHigherPrecedence(operatorStack.peek(), token)) {
                        outputQueue.add(operatorStack.pop())
                    }
                    operatorStack.push(token)
                }
            }
        }

        // Pop remaining operators
        while (operatorStack.isNotEmpty()) {
            val op = operatorStack.pop()
            if (op == "(") {
                throw EvaluationException("Mismatched parentheses")
            }
            outputQueue.add(op)
        }

        // Evaluate the postfix expression
        return evaluatePostfix(outputQueue)
    }

    // Evaluate postfix notation
    private fun evaluatePostfix(postfix: List<String>): BigDecimal {
        val stack = Stack<BigDecimal>()

        for (token in postfix) {
            if (isNumber(token)) {
                stack.push(BigDecimal(token))
            } else if (isOperator(token)) {
                if (stack.size < 2) {
                    throw EvaluationException("Invalid expression: insufficient operands")
                }

                val b = stack.pop()
                val a = stack.pop()

                val result = when (token) {
                    "+" -> a.add(b)
                    "-" -> a.subtract(b)
                    "*" -> a.multiply(b)
                    "/" -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) {
                            throw EvaluationException("Division by zero")
                        }
                        a.divide(b, 10, RoundingMode.HALF_UP)
                    }
                    else -> throw EvaluationException("Unknown operator: $token")
                }

                stack.push(result)
            }
        }

        if (stack.size != 1) {
            throw EvaluationException("Invalid expression: too many operands")
        }

        return stack.pop()
    }

    // Check if token is a number
    private fun isNumber(token: String): Boolean {
        return try {
            token.toBigDecimalOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    // Check if token is an operator
    private fun isOperator(token: String): Boolean {
        return token == "+" || token == "-" || token == "*" || token == "/"
    }

    // Check operator precedence
    private fun hasHigherPrecedence(op1: String, op2: String): Boolean {
        val precedence = mapOf(
            "+" to 1,
            "-" to 1,
            "*" to 2,
            "/" to 2
        )

        return (precedence[op1] ?: 0) >= (precedence[op2] ?: 0)
    }
}