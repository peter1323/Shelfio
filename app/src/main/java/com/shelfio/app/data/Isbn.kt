package com.shelfio.app.data

/**
 * Normaliza y valida un ISBN escrito o escaneado. Devuelve el ISBN-13 sin guiones,
 * o null si no es válido. Acepta ISBN-10 (lo convierte a ISBN-13) y quita espacios y guiones.
 */
fun normalizeIsbn(input: String): String? {
    val clean = input.uppercase().filter { it.isDigit() || it == 'X' }
    return when {
        clean.length == 13 && clean.all(Char::isDigit) && isValidIsbn13(clean) &&
            (clean.startsWith("978") || clean.startsWith("979")) -> clean
        clean.length == 10 && isValidIsbn10(clean) -> isbn10To13(clean)
        else -> null
    }
}

private fun isValidIsbn13(isbn: String): Boolean {
    val sum = isbn.dropLast(1).mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum()
    return (10 - sum % 10) % 10 == isbn.last().digitToInt()
}

private fun isValidIsbn10(isbn: String): Boolean {
    if (!isbn.dropLast(1).all(Char::isDigit)) return false
    val last = if (isbn.last() == 'X') 10 else isbn.last().digitToIntOrNull() ?: return false
    val sum = isbn.dropLast(1).mapIndexed { i, c -> c.digitToInt() * (10 - i) }.sum() + last
    return sum % 11 == 0
}

private fun isbn10To13(isbn10: String): String {
    val body = "978" + isbn10.dropLast(1)
    val sum = body.mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum()
    return body + (10 - sum % 10) % 10
}
