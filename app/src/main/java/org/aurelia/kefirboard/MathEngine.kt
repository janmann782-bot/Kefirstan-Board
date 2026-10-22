package org.aurelia.kefirboard

import kotlin.math.*

/** Deterministic local calculator: arithmetic, elementary functions, linear equations. */
object MathEngine {
    data class Result(val answer: String, val steps: List<String>)
    fun solve(input: String): Result {
        val s = input.replace('×', '*').replace('÷', '/').replace(',', '.').trim()
        if (s.isEmpty()) return Result("", listOf("Введите выражение"))
        return try {
            if (s.contains('=') && s.contains('x')) linear(s)
            else if (s.contains('=')) {
                val sides = s.split('=')
                require(sides.size == 2) { "Одно равенство" }
                val a = Parser(sides[0], 0.0).parse()
                val b = Parser(sides[1], 0.0).parse()
                Result(if (abs(a - b) < 1e-8) "Верно" else "Неверно", listOf("${fmt(a)} = ${fmt(b)}"))
            } else {
                val number = Parser(s, null).parse()
                Result(fmt(number), listOf("$s = ${fmt(number)}"))
            }
        } catch (e: Exception) {
            Result("Ошибка", listOf(e.message ?: "Не удалось вычислить"))
        }
    }
    private fun linear(s: String): Result {
        val sides = s.split('=')
        require(sides.size == 2) { "Ожидается одно равенство" }
        fun f(x: Double) = Parser(sides[0], x).parse() - Parser(sides[1], x).parse()
        val b = f(0.0)
        val a = f(1.0) - b
        require(abs(f(2.0) - (2*a + b)) < 1e-7 && abs(f(-1.0) - (b-a)) < 1e-7) {
            "Пока поддерживаются только линейные уравнения"
        }
        if (abs(a) < 1e-12) return if (abs(b) < 1e-12) Result("Любое x", listOf("Тождество"))
            else Result("Нет решений", listOf("Противоречие"))
        val x = -b / a
        return Result("x = ${fmt(x)}", listOf(
            "Переносим все влево: ${fmt(a)}x + ${fmt(b)} = 0",
            "Переносим свободный член: ${fmt(a)}x = ${fmt(-b)}",
            "Делим на коэффициент: x = ${fmt(-b)} / ${fmt(a)}",
            "Ответ: x = ${fmt(x)}"
        ))
    }
    private fun fmt(v: Double): String {
        require(v.isFinite()) { "Бесконечный/неопределенный результат" }
        return if (v == v.toLong().toDouble()) v.toLong().toString()
        else "%.8f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }
    private class Parser(private val s: String, private val x: Double?) {
        private var i = 0
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun take(c: Char): Boolean { skip(); if (i < s.length && s[i] == c) { i++; return true }; return false }
        fun parse(): Double { val n = expr(); skip(); require(i == s.length) { "Лишние символы на позиции ${i+1}" }; return n }
        private fun expr(): Double {
            var n = term()
            while (true) n = when {
                take('+') -> n + term()
                take('-') -> n - term()
                else -> return n
            }
        }
        private fun term(): Double {
            var n = signed()
            while (true) n = when {
                take('*') -> n * signed()
                take('/') -> n / signed()
                (i < s.length && s[i] == 'x' && x != null) -> n * signed() // 2x
                else -> return n
            }
        }
        private fun signed(): Double = when {
            take('+') -> signed()
            take('-') -> -signed()
            else -> pow()
        }
        private fun pow(): Double {
            val n = atom()
            return if (take('^')) n.pow(signed()) else n
        }
        private fun atom(): Double {
            skip()
            if (take('(')) {
                val n = expr()
                require(take(')')) { "Нет закрывающей скобки" }
                return n
            }
            if (take('x')) return x ?: error("x допустим только в уравнениях")
            if (i < s.length && s[i].isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i)
                require(take('(')) { "Нужна скобка после функции" }
                val n = expr()
                require(take(')')) { "Нет закрывающей скобки" }
                return when (name) {
                    "sqrt" -> sqrt(n)
                    "sin" -> sin(n)
                    "cos" -> cos(n)
                    "tan" -> tan(n)
                    "ln" -> ln(n)
                    "log" -> log10(n)
                    else -> error("Неизвестная функция: $name")
                }
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start) { "Ожидается число" }
            return s.substring(start, i).toDouble()
        }
    }
}
