package dataselector.interpreter

import dataselector.Interpreter
import syntax.Ast
import kotlin.math.pow
import kotlin.math.round

abstract class AggregateCall(
    val name: String
) {
    abstract fun call(interpreter: Interpreter, args: List<Ast.Expression.Identifier>, group: List<Row>): Any?
}

class CountCall: AggregateCall("count") {
    override fun call(interpreter: Interpreter, args: List<Ast.Expression.Identifier>, group: List<Row>): Any? {
        require(args.isEmpty()) {
            "Only count() without args is supported "
        }

        return group.count()
    }
}

class SumCall: AggregateCall("sum") {
    override fun call(interpreter: Interpreter, args: List<Ast.Expression.Identifier>, group: List<Row>): Any? {
        require(args.size == 1) {
            "Incorrect number of arguments was passed: expected 1 but got $args"
        }

        return group.sumOf {
                row ->
            val currentRow = interpreter.currentRow
            interpreter.currentRow = row
            val value = interpreter.evaluateExpression(args[0]) as Number
            interpreter.currentRow = currentRow

            value.toDouble()
        }
    }
}

class AvgCall: AggregateCall("avg") {
    override fun call(interpreter: Interpreter, args: List<Ast.Expression.Identifier>, group: List<Row>): Any? {
        require(args.size == 1) {
            "Expected column argument in avg"
        }

        if (group.isEmpty()) {
            return 0
        }

        val sum = group.sumOf {
                row ->
            val currentRow = interpreter.currentRow
            interpreter.currentRow = row
            val value = interpreter.evaluateExpression(args[0]) as Number
            interpreter.currentRow = currentRow

            value.toDouble()
        }

        return sum / group.size
    }
}


sealed class InnerGroupingKey(
    open val identifier: Ast.Expression.Identifier
) {
    data class Number(
        val number: kotlin.Number,
        override val identifier: Ast.Expression.Identifier
    ) : InnerGroupingKey(identifier) {
        override fun hashCode(): Int {
            return this.number.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (javaClass != other?.javaClass) {
                return false
            }

            other as Number

            return this.number == other.number
        }
    }

    data class String(
        val string: kotlin.String,
        override val identifier: Ast.Expression.Identifier
    ) : InnerGroupingKey(identifier) {
        override fun hashCode(): Int {
            return this.string.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (javaClass != other?.javaClass) {
                return false
            }

            other as String

            return this.string == other.string
        }
    }

    data class Null(
        override val identifier: Ast.Expression.Identifier
    ): InnerGroupingKey(identifier) {
        override fun hashCode(): Int {
            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (other == null) {
                return true
            }

            if (this === other) {
                return true
            }
            if (javaClass != other.javaClass) {
                return false
            }

            return true
        }
    }
}

typealias GroupingKey = List<InnerGroupingKey>

abstract class Call(
    val name: String
) {
    fun call(interpreter: Interpreter, args: List<Ast.Expression>): Any? {
        require(args.size == this.argumentsCount()) {
            "Function ${this.name} expects ${this.argumentsCount()} arguments, but got ${args.size}"
        }

        val evaluatedArgs = args.map {
                arg -> interpreter.evaluateExpression(arg)
        }

        return this.execute(interpreter, evaluatedArgs)
    }

    abstract fun argumentsCount(): Int

    abstract fun execute(interpreter: Interpreter, args: List<Any?>): Any?

}

class RoundCall: Call("round") {
    override fun argumentsCount(): Int {
        return 2
    }

    override fun execute(
        interpreter: Interpreter,
        args: List<Any?>
    ): Any? {
        require(args.size == 2) {
            "${this.name} expects only 2 arguments, but got ${args.size}"
        }
        require(args.all { it is Number || it is Boolean }) {
            "Only numbers can be passed to ${this.name} function"
        }

        val args = args.map { if (it is Boolean) { if (it) { 1 } else { 0}} else it as Number }
        val value = args[0].toDouble()
        val place = args[1].toInt()

        require(place > 0) {
            "Can't round up to <1 places"
        }

        val scale = 10.0.pow(place)

        return round(value * scale) / scale
    }
}

class ToIntCall: Call("toInt") {
    override fun argumentsCount(): Int {
        return 1
    }

    override fun execute(
        interpreter: Interpreter,
        args: List<Any?>
    ): Any? {
        val arg = args[0] as Boolean

        return if (arg) {
            1
        } else {
            0
        }
    }

}