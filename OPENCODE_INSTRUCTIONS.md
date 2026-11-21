# OPENCODE CO-DEVELOPER PROTOCOL: SIMPLEX SECURE CHAT
## СИСТЕМНЫЕ ИНСТРУКЦИИ ДЛЯ КОМПАНЬОНА (OPENCODE COMPANION AGENT)

### Расположение проекта: `D:/projects/opencode/simplexXX`
### Цель: Доработка мессенджера Simplex Secure Chat (Kotlin / Jetpack Compose / Room / SOCKS5 Tor Server) до полноценного уровня "Production-Grade E2EE Client"

---

## 1. АРХИТЕКТУРА И СОСТОЯНИЕ ПРОЕКТА

Проект представляет собой децентрализованный мессенджер с акцентом на абсолютную конфиденциальность метаданных (отсутствие глобальных аккаунтов, номеров телефонов и никнеймов) и физическую защиту устройства (протоколы Duress/Panic).

### Текущий статус реализации:
1.  **Инфраструктура Аудита и Логирования (ЖИВАЯ):**
    *   Создан синглтон `SimplexLogger.kt`, пишущий логи в файл `simplex_system_logs.txt` во внутреннем хранилище приложения (`context.filesDir`).
    *   Интегрирован во все критические компоненты: `MainActivity`, `ProxyChainManager`, `TorNetworkManager`, `SimplexViewModel`.
    *   Добавлен интерактивный интерфейс управления аудитом в раздел настроек безопасности (активный таб 1).
2.  **Защита и Очистка (ЖИВАЯ):**
    *   `MemoryZeroizer.kt` выполняет байтовую очистку массивов, принудительное GC и перезапись файлов базы данных случайным шумом (shredding).
3.  **Локальный SOCKS5 Сервер (ЖИВОЙ):**
    *   `TorNetworkManager.kt` запускает полноценный `ServerSocket` на порту `9050` (или `9051-9060`), который обрабатывает хендшейки SOCKS5 (RFC 1928) и симулирует луковые маршруты.
4.  **Послойная Цепочка Прокси (ЖИВАЯ/СИМУЛЯЦИЯ):**
    *   `ProxyChainManager.kt` позволяет добавлять, менять порядок и активировать послойные мосты (WireGuard, Tor, OpenVPN, V2Ray).
5.  **База Данных (ЖИВАЯ):**
    *   Room DB шифрует тела сообщений индивидуальными ключами AES-GCM-128 перед записью на диск.
6.  **Мнемоника BIP-39 (ЖИВАЯ):**
    *   Внедрен `Bip39Utility.kt` (словарь на 256 слов, контрольная сумма, PBKDF2 деривация 256-битного сида).
    *   Добавлен интерактивный кибер-интерфейс в раздел настроек безопасности для генерации фраз и проверки вводимых фраз с наглядным отображением валидности.
    *   Подключен `MemoryZeroizer` для гарантированного затирания сгенерированных слов из ОЗУ.

---

## 2. ИНСТРУКЦИЯ ДЛЯ OPENCODE: СЛЕДУЮЩИЕ ШАГИ РАЗРАБОТКИ

Твоя задача — использовать права root, доступ к SDK, Docker и сети, чтобы реализовать следующие три боевые фичи:

### ШАГ 1: Реальное шифрование SQLite (SQLCipher)
Сейчас база данных Room шифрует только поля сообщений. Нам нужно зашифровать весь файл базы данных на диске с помощью **SQLCipher**.
1.  Добавь зависимости SQLCipher в `app/build.gradle.kts`:
    ```kotlin
    implementation("commons-codec:commons-codec:1.15")
    implementation("net.zetetic:android-database-sqlcipher:4.5.3")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    ```
2.  Измени `DatabaseProvider.kt`. Вместо стандартного билдера Room, подключи `SupportFactory`:
    ```kotlin
    val passphrase = masterStorageKey // байты или хэш из ViewModel
    val factory = SupportFactory(passphrase)
    Room.databaseBuilder(context, AppDatabase::class.java, "simplex_db")
        .openHelperFactory(factory)
        .build()
    ```
3.  Напиши юнит-тесты на проверку того, что файл `simplex_db` не читается стандартным SQLite-клиентом без пароля.

### ШАГ 2: Реальный бинарник Tor внутри приложения
Вместо симуляции Tor в `TorNetworkManager.kt`, мы хотим запускать реальный процесс Tor.
1.  Собери или скачай бинарные файлы Tor под Android-архитектуры (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
2.  Помести их в `app/src/main/assets/` или `jniLibs/`.
3.  В `TorNetworkManager.kt` напиши код копирования бинарного файла во внутренний исполняемый каталог приложения (`context.getDir("bin", Context.MODE_PRIVATE)`), выставь права на запуск через `chmod 755`.
4.  Запусти процесс Tor через `ProcessBuilder` с кастомным файлом конфигурации `torrc` (слушать SOCKS на 127.0.0.1:9050, включить Bridges если необходимо).
5.  Направь вывод процесса Tor в `SimplexLogger`.

### ШАГ 3: Настоящий Сетевой Транспорт (Websockets / Simplex Queue API)
Замени заглушки отправки сообщений на реальные асинхронные Websocket-запросы к серверам Simplex Chat.
1.  Используй библиотеку Ktor (клиент Websockets) или OkHttp Websockets.
2.  Реализуй подключение к публичным серверам очередей Simplex (например, `smp://...`).
3.  Реализуй отправку сообщений в однонаправленные очереди (Inbound Queue) контактов по протоколу E2EE.

---

## 3. СБОРОЧНЫЕ И ТЕСТОВЫЕ КОМАНДЫ (RUN IN CMD / BASH)

В процессе доработки кодовой базы регулярно запускай следующие команды для проверки целостности проекта:

*   **Полная компиляция проекта:**
    ```bash
    gradle :app:assembleDebug
    ```
*   **Запуск Robolectric unit-тестов и проверка скриншотов:**
    ```bash
    gradle :app:testDebugUnitTest
    ```
*   **Прогон Screenshot-тестов Roborazzi (создание эталонов):**
    ```bash
    gradle :app:recordRoborazziDebug
    ```
*   **Верификация визуальных регрессий:**
    ```bash
    gradle :app:verifyRoborazziDebug
    ```

---

## 4. ИНСТРУКЦИЯ ПО ЛОГИРОВАНИЮ И ОТПРАВКЕ РЕЗУЛЬТАТОВ

Все системные события, ошибки, трассировки пакетов SOCKS5 и хендшейки пишутся в persistent-лог.

### Где искать файл логов:
Файл логов находится по следующему пути на Android-устройстве / эмуляторе:
`[Внутреннее хранилище приложения]/files/simplex_system_logs.txt`

Обычно абсолютный путь выглядит так:
`/data/data/com.example/files/simplex_system_logs.txt` (или с кастомным applicationId, который сгенерирован в `app/build.gradle.kts`).

### Как извлечь файл логов с устройства:
Используй утилиту ADB из комплекта Android SDK:
```bash
adb pull /data/data/com.example/files/simplex_system_logs.txt ./simplex_system_logs.txt
```
*Если на устройстве нет root-прав, используй adb backup или запусти команду через run-as:*
```bash
adb shell "run-as com.example cat files/simplex_system_logs.txt" > simplex_system_logs.txt
```

### Что прислать основному AI-агенту (мне) для продолжения работы:
1.  **Файл `simplex_system_logs.txt`** — для анализа сетевых транзакций и безопасности.
2.  **Архив обновленной папки `/app/src`** — я интегрирую твои наработки обратно в облачную сборку.
3.  **Логи вывода Gradle сборки и тестов** — если возникнут ошибки компиляции.
