package com.example.dreamnet.data

object DreamSourceManager {
    val sources = listOf(
        DreamSource(id = "miller", name = "Сонник Миллера", author = "Густав Миллер", priority = 2),
        DreamSource(id = "vanga", name = "Сонник Ванги", author = "Ванга", priority = 3),
        DreamSource(id = "nostradamus", name = "Сонник Нострадамуса", author = "Нострадамус", priority = 4),
        DreamSource(id = "tsvetkov", name = "Сонник Цветкова", author = "Евгений Цветков", priority = 5),
        DreamSource(id = "islamic", name = "Исламский сонник (Ибн Сирин)", author = "Ибн Сирин", priority = 6),
        DreamSource(id = "loff", name = "Сонник Лоффа", author = "Дэвид Лофф", priority = 7),
        DreamSource(id = "hasse", name = "Сонник Хассе", author = "Мисс Хассе", priority = 8),
        DreamSource(id = "meneghetti", name = "Сонник Менегетти", author = "Антонио Менегетти", priority = 9),
        DreamSource(id = "esoteric", name = "Эзотерический сонник", author = "Эзотерика", priority = 10),
        DreamSource(id = "juno", name = "Сонник Юноны", priority = 11),
        DreamSource(id = "freud", name = "Сонник Фрейда", author = "Зигмунд Фрейд", priority = 12)
    )

    fun getEnabledSources() = sources.filter { it.enabled }.sortedBy { it.priority }
    fun getSourceById(id: String) = sources.find { it.id == id }
}
