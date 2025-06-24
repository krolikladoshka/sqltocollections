package dataselector.interpreter

import dataselector.Interpreter
import syntax.Ast

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
            "Expected column argument in sum"
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
