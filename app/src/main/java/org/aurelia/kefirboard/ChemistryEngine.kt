package org.aurelia.kefirboard

import java.math.BigInteger

/**
 * Offline formula formatter and a small exact equation balancer.
 * Supports normal molecular formulas (including parenthesis groups).
 * This is a stoichiometry tool, not a reaction predictor or a chemistry safety adviser.
 */
object ChemistryEngine {
    data class Result(val result: String, val steps: List<String>, val error: Boolean = false)

    private val subscriptDigits = "₀₁₂₃₄₅₆₇₈₉"
    private val standardDigits = "0123456789"
    private val elements = ("H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn " +
        "Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd " +
        "Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th " +
        "Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og")
        .split(' ').toSet()

    fun normalizeFormula(s: String): String = buildString {
        for (c in s.trim()) {
            val idx = subscriptDigits.indexOf(c)
            append(if (idx >= 0) standardDigits[idx] else c)
        }
    }

    fun prettyFormula(raw: String): String = buildString {
        val plain = normalizeFormula(raw)
        for ((idx, ch) in plain.withIndex()) {
            val digit = standardDigits.indexOf(ch)
            // In formula chips digits after symbols and parentheses are subscripts.
            append(if (digit >= 0 && idx > 0) subscriptDigits[digit] else ch)
        }
    }

    fun parseFormula(raw: String): Map<String, Int> {
        val s = normalizeFormula(raw).replace(" ", "")
        require(s.isNotBlank() && s.length < 200) { "Введите формулу длиной до 199 символов" }
        var i = 0
        fun count(): Int {
            val start = i
            while (i < s.length && s[i].isDigit()) i++
            return if (i == start) 1 else s.substring(start, i).toInt().also {
                require(it in 1..100000) { "Некорректный индекс атомов" }
            }
        }
        fun parseGroup(depth: Int): MutableMap<String, Int> {
            require(depth < 12) { "Слишком много вложенных скобок" }
            val group = linkedMapOf<String, Int>()
            while (i < s.length && s[i] != ')') {
                if (s[i] == '(') {
                    i++
                    val nested = parseGroup(depth + 1)
                    require(i < s.length && s[i] == ')') { "Не закрыта скобка" }
                    i++
                    val multiplier = count()
                    for ((symbol, n) in nested) {
                        val value = group.getOrDefault(symbol, 0).toLong() + n.toLong() * multiplier
                        require(value <= 10000000L) { "Слишком большие индексы" }
                        group[symbol] = value.toInt()
                    }
                } else {
                    require(s[i].isUpperCase()) { "Ожидается символ элемента" }
                    val start = i++
                    if (i < s.length && s[i].isLowerCase()) i++
                    val symbol = s.substring(start, i)
                    require(symbol in elements) { "Неизвестный элемент: " + symbol }
                    val multiplier = count()
                    val sum = group.getOrDefault(symbol, 0).toLong() + multiplier
                    require(sum <= 10000000) { "Слишком большие индексы" }
                    group[symbol] = sum.toInt()
                }
            }
            return group
        }
        val atoms = parseGroup(0)
        require(i == s.length) { "Лишняя закрывающая скобка" }
        require(atoms.isNotEmpty()) { "Формула не содержит элементов" }
        return atoms
    }

    private data class Fraction private constructor(val n: BigInteger, val d: BigInteger) {
        companion object {
            val ZERO = of(0)
            val ONE = of(1)
            fun of(x: Int) = make(BigInteger.valueOf(x.toLong()), BigInteger.ONE)
            fun make(a: BigInteger, b: BigInteger): Fraction {
                require(b != BigInteger.ZERO)
                val sign = if (b.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
                val num = a.multiply(sign)
                val den = b.multiply(sign)
                val gcd = num.abs().gcd(den)
                return Fraction(num.divide(gcd), den.divide(gcd))
            }
        }
        operator fun plus(o: Fraction) = make(n * o.d + o.n * d, d * o.d)
        operator fun minus(o: Fraction) = make(n * o.d - o.n * d, d * o.d)
        operator fun times(o: Fraction) = make(n * o.n, d * o.d)
        operator fun div(o: Fraction) = make(n * o.d, d * o.n)
        fun isZero() = n == BigInteger.ZERO
    }

    /**
     * Algebraic nullspace by rational RREF. Pick free variables = 1 and
     * return only strictly positive integer solutions verified against every atom.
     */
    fun balance(input: String): Result = try {
        val arrow = when {
            "→" in input -> "→"
            "->" in input -> "->"
            "=" in input -> "="
            else -> error("Добавьте стрелку реакции: →")
        }
        val halves = input.split(arrow)
        require(halves.size == 2) { "Нужна одна стрелка между реагентами и продуктами" }
        fun parts(side: String) = side.split('+').map { it.trim() }.also { p ->
            require(p.isNotEmpty() && p.all { it.isNotEmpty() }) { "Пустое вещество в уравнении" }
        }
        val left = parts(halves[0])
        val right = parts(halves[1])
        val species = left + right
        require(species.size in 2..18) { "Поддерживаются реакции от 2 до 18 веществ" }
        fun withoutCoefficient(s: String) = normalizeFormula(s).replaceFirst(Regex("^\\d+\\s*"), "")
        val formulas = species.map(::withoutCoefficient)
        val parsed = formulas.map(::parseFormula)
        val atoms = parsed.flatMap { it.keys }.distinct().sorted()
        val width = species.size
        val height = atoms.size
        val mat = Array(height) { r -> Array(width) { c ->
            val number = parsed[c][atoms[r]] ?: 0
            Fraction.of(if (c < left.size) number else -number)
        }}
        val pivots = mutableListOf<Int>()
        var row = 0
        for (col in 0 until width) {
            val candidate = (row until height).firstOrNull { !mat[it][col].isZero() } ?: continue
            val swap = mat[row]; mat[row] = mat[candidate]; mat[candidate] = swap
            val div = mat[row][col]
            for (j in col until width) mat[row][j] = mat[row][j] / div
            for (r in 0 until height) if (r != row) {
                val factor = mat[r][col]
                if (!factor.isZero()) for (j in col until width) {
                    mat[r][j] = mat[r][j] - factor * mat[row][j]
                }
            }
            pivots += col
            row++
            if (row >= height) break
        }
        val free = (0 until width).filter { it !in pivots }
        require(free.isNotEmpty()) { "Невозможно уравнять реакцию без изменения веществ" }
        val answer = Array(width) { Fraction.ZERO }
        free.forEach { answer[it] = Fraction.ONE }
        for (r in pivots.indices.reversed()) {
            val pivot = pivots[r]
            var sum = Fraction.ZERO
            for (j in pivot + 1 until width) sum = sum + mat[r][j] * answer[j]
            answer[pivot] = Fraction.ZERO - sum
        }
        require(answer.all { it.n.signum() > 0 }) {
            "Для этой реакции не найдено положительных коэффициентов"
        }
        val denominator = answer.map { it.d }.reduce { a, b -> a / a.gcd(b) * b }
        val scaled = answer.map { it.n * (denominator / it.d) }
        val gcd = scaled.reduce { a, b -> a.gcd(b) }
        val coefficient = scaled.map { it / gcd }
        require(coefficient.all { it > BigInteger.ZERO && it <= BigInteger.valueOf(1000000) }) {
            "Коэффициенты слишком большие"
        }
        for (element in atoms) {
            val lhs = (left.indices).fold(BigInteger.ZERO) { acc, i ->
                acc + coefficient[i] * BigInteger.valueOf((parsed[i][element] ?: 0).toLong())
            }
            val rhs = right.indices.fold(BigInteger.ZERO) { acc, i ->
                val at = left.size + i
                acc + coefficient[at] * BigInteger.valueOf((parsed[at][element] ?: 0).toLong())
            }
            check(lhs == rhs) { "Нарушен баланс элемента " + element }
        }
        fun printed(i: Int) = (if (coefficient[i] == BigInteger.ONE) "" else coefficient[i].toString()) +
            prettyFormula(formulas[i])
        val finished = left.indices.joinToString(" + ") { printed(it) } +
            " → " + right.indices.joinToString(" + ") { printed(it + left.size) }
        val counts = atoms.joinToString(", ") { element ->
            val n = left.indices.fold(BigInteger.ZERO) { acc, i ->
                acc + coefficient[i] * BigInteger.valueOf((parsed[i][element] ?: 0).toLong())
            }
            element + ": " + n
        }
        Result(finished, listOf(
            "Определены элементы: " + atoms.joinToString(", "),
            "Составлена система законов сохранения атомов.",
            "Получены минимальные целые коэффициенты: " + coefficient.joinToString(", "),
            "Проверка атомов на каждой стороне: " + counts
        ))
    } catch (e: Exception) {
        Result("Ошибка", listOf(e.message ?: "Проверьте химическую формулу"), error = true)
    }
}
