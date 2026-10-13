<div align="center">

# 🎵 OffmineSoundAPI

**Звуки сервера — через Simple Voice Chat. Управление — через нативные диалоги Minecraft.**

Лёгкая библиотека звуков и публичный Java API для плагинов **Paper 26.3**.  
Воспроизведение игроку, всем игрокам или в заданной точке мира — **без ресурспака и без радио**.

[![Version](https://img.shields.io/badge/version-2.3.3-F4D4B5?style=flat-square)](https://github.com/ElDiavlos/offmineSoundAPI/releases)
[![Paper](https://img.shields.io/badge/Paper-26.3-C7D5B7?style=flat-square)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-E3AFAC?style=flat-square)](https://adoptium.net/)
[![JitPack](https://jitpack.io/v/ElDiavlos/offmineSoundAPI.svg)](https://jitpack.io/#ElDiavlos/offmineSoundAPI)

[**Установка**](#-установка) · [**Админ-панель**](#-админ-панель) · [**Java API**](#-подключение-api-в-другом-плагине) · [**Документация**](#-документация)

</div>

---

## ✨ Возможности

- **Публичный Java API** — воспроизведение звука одному игроку, всем игрокам и в указанной точке мира.
- **Нативная админ-панель Paper Dialog API** — библиотека звуков с перелистыванием страниц, прослушивание, загрузка и копирование готовых фрагментов Java-кода.
- **Загрузка аудио по прямой HTTP(S)-ссылке** или вручную в папку сервера.
- **Персональная громкость** — категория **«Server Sounds»** в настройках Simple Voice Chat (обычно `V` → регулировка громкости).
- **Постоянные звуковые точки** — радиус воспроизведения, зацикливание и очередь звуков.
- **Команды только для администрации** — скрыты от игроков без права `osv.admin`.
- **MiniMessage HEX** — нежная кремово-персиковая палитра интерфейса.
- **Никакого радио** — только звуковая библиотека и API.

## 📋 Требования

| Компонент | Требование |
|:--|:--|
| Сервер | **Paper 26.3** |
| Java | **JDK 25** |
| Голосовой мод | **Simple Voice Chat** для совместимого сервера и клиентов |
| Для MP3/OGG и других сжатых форматов | **FFmpeg** в окружении сервера |
| Сборка | Gradle Wrapper из репозитория |

> [!NOTE]
> Звуки воспроизводятся через Simple Voice Chat. Слушателям требуется совместимый клиентский мод и работающее соединение голосового чата. Сам плагин **не заменяет** Simple Voice Chat.

## 🚀 Установка

1. Установите **Paper 26.3** и **Simple Voice Chat** на сервер с Java 25.
2. Соберите OffmineSoundAPI из исходников:

   **Windows:**
   ```bat
   gradlew.bat clean build
   ```

   **Linux / macOS:**
   ```bash
   ./gradlew clean build
   ```

3. Поместите `build/libs/OffmineSoundAPI-2.3.3.jar` в папку `plugins/` сервера (вместе с JAR Simple Voice Chat).
4. Для MP3/OGG установите **FFmpeg** и при необходимости задайте путь к нему в `plugins/OffmineSoundAPI/config.yml`.
5. **Полностью перезапустите сервер.** Не оставляйте JAR старых версий OffmineSoundAPI рядом с новым.

> [!IMPORTANT]
> Версия 2.3.3 предоставляется как исходный проект. Полноценная сборка с реальными зависимостями Paper 26.3 и тест на работающем сервере пока **не подтверждены**. Перед использованием проверьте собственную Gradle-сборку и работу на тестовом сервере.

## 🌸 Админ-панель

Открыть библиотеку звуков можно командами:

```text
/soundadmin
/osv admin
```

В библиотеке сначала отображаются **количество звуков и страница**, затем кликабельные названия звуков, подсказка и кнопки управления. Выбор звука открывает карточку с прослушиванием и копированием Java-кода для разных способов воспроизведения.

Для добавления звука выберите **«＋ Загрузить звук»**, заполните поля **ID** и **Прямая ссылка**, затем нажмите **«Скачать»**. Например, ID `alarm` можно будет использовать как `sounds.play(player, "alarm")`.

**Альтернативный способ:** загрузите файл через файловый менеджер в `plugins/OffmineSoundAPI/sounds/`, затем выполните `/osv reload`. Библиотека хранит соответствия ID и файлов в `sounds.yml`.

### Форматы и ограничения

| Формат | Условия |
|:--|:--|
| WAV | Поддерживаемый WAV может обрабатываться без FFmpeg |
| MP3, OGG, FLAC, AAC, M4A, OPUS | Требуется FFmpeg |

По умолчанию ограничены размер загрузки **20 МБ** и продолжительность звука **300 секунд** (настраиваются в `config.yml`). Ссылка должна отдавать **сам аудиофайл**, а не HTML-страницу; действительность ссылки и доступность с Minecraft-хостинга обязательны.

## 🔊 Громкость через Simple Voice Chat

В **2.3.3** плагин проверяет регистрацию категории `osv` («Server Sounds») в Simple Voice Chat и при необходимости восстанавливает её. **Исправлена ошибка** `EncoderException: String too big (was 25 bytes encoded, max 16)`: имя категории сокращено до `Server Sounds` (13 байт UTF-8); ID `osv` не изменён. Если ползунок не появился в **V → Adjust Volumes**, выполните `/osv voicecheck`, затем полностью перезапустите сервер и переподключитесь к нему. Подробная инструкция и возможные причины — в [docs/VOLUME.md](docs/VOLUME.md).

**Важно:** проверить отображение клиентского ползунка можно только в игре; успешное сообщение сервера о регистрации не гарантирует корректную работу конкретной версии клиентского мода.

## 🛠️ Команды и права

| Команда | Что делает |
|:--|:--|
| `/soundadmin` | Открыть библиотеку звуков |
| `/osv admin` | Открыть админ-панель |
| `/osv download <id> <url>` | Скачать звук по прямой ссылке |
| `/osv sounds` | Показать ID звуков |
| `/osv code <id>` | Скопировать пример Java API |
| `/osv reload` | Перезагрузить библиотеку |
| `/osv voicecheck` | Проверить подключение SVC и категорию громкости `osv` |
| `/osv play <ник> <id> [loop]` | Воспроизвести игроку |
| `/osv playall <id> [loop]` | Воспроизвести всем |
| `/osv at <x> <y> <z> <радиус> <id> [loop]` | Временный звук в точке |
| `/osv place <x> <y> <z> <радиус> <beaconId> <id> [loop]` | Постоянный источник |
| `/osv help` | Полный список команд, включая остановку и очереди |

**Право доступа:** `osv.admin` (по умолчанию только OP). Команды не должны отображаться в подсказках обычных игроков; проверка прав выполняется и при вызове.

## 🧩 Подключение API в другом плагине

OffmineSoundAPI можно добавить в Gradle **без папки `libs`** через JitPack. Maven-координаты ниже соответствуют отображённым для репозитория `ElDiavlos/offmineSoundAPI`:

### 1. `settings.gradle` другого плагина

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri('https://repo.papermc.io/repository/maven-public/') }
        maven { url = uri('https://jitpack.io') }
    }
}
```

> Если `dependencyResolutionManagement` уже есть, добавьте JitPack **в существующий блок**. При `FAIL_ON_PROJECT_REPOS` перенесите репозитории из `build.gradle` в `settings.gradle`.

### 2. `build.gradle` другого плагина

```groovy
dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.3.build.+'
    compileOnly 'com.github.ElDiavlos:offmineSoundAPI:2.3.3'
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
```

> [!IMPORTANT]
> Зависимость доступна, только если JitPack **успешно собрал** тег `2.3.3` (проверьте [статус сборки](https://jitpack.io/#ElDiavlos/offmineSoundAPI)). Появление инструкции «How to» само по себе не гарантирует успешную публикацию. Регистр `offmineSoundAPI` важен.

### 3. `plugin.yml` другого плагина

```yaml
name: MyPlugin
version: '1.0.0'
main: example.MyPlugin
api-version: '26.3'
depend:
  - OffmineSoundAPI
```

`compileOnly` нужен **только для компиляции**. На сервере **OffmineSoundAPI и Simple Voice Chat устанавливаются отдельно**.

### 4. Пример использования Java API

```java
import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;

// Например, внутри команды вашего JavaPlugin (основной поток сервера):
SoundApi sounds = getServer().getServicesManager().load(SoundApi.class);

if (sounds != null && sounds.isReady() && sounds.exists("alarm")) {
    sounds.play(player, "alarm");
}
```

### Другие возможности API

```java
sounds.play(player, "alarm");                         // Одному игроку
sounds.play(player, "alarm", true);                   // Одному игроку по кругу
sounds.playAll("alarm");                             // Всем игрокам
sounds.playAt(player.getLocation(), "alarm", 24f);   // В точке (радиус 24 блока)
sounds.stopPlayer(player);                            // Остановить звуки игроку
sounds.stopGlobal();                                  // Остановить глобальные звуки

var ids = sounds.listSounds();                        // Список ID
boolean exists = sounds.exists("alarm");              // Существует ли звук
```

Для создания постоянных источников доступны `place(...)`, `enqueue(...)`, `removeBeacon(...)`. Методы воспроизведения вызывайте **из основного потока сервера**; обрабатывайте возможные исключения, если звук отсутствует или Voice Chat ещё не готов.

**Полная инструкция для разработчиков:** [docs/OTHER_PLUGIN.md](docs/OTHER_PLUGIN.md)  
**Все методы API:** [docs/API.md](docs/API.md)

## 📁 Структура проекта

```text
OffmineSoundAPI/
├── src/main/java/          # Реализация плагина и публичного API
├── src/main/resources/     # plugin.yml и config.yml
├── docs/
│   ├── API.md              # Справочник Java API
│   ├── OTHER_PLUGIN.md     # Подключение через JitPack
│   ├── INSTALL.md          # Установка, команды, аудиофайлы
│   └── DIALOGS.md          # Устройство интерфейса
├── examples/
│   └── DemoSoundConsumer/  # Пример плагина с командой /testalarm
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

## 📚 Документация

- **[Подробное руководство по установке](docs/INSTALL.md)** — сервер, загрузка файлов, FFmpeg, ошибки URL.
- **[Громкость и диагностика SVC](docs/VOLUME.md)** — категория `osv`, проверка и устранение проблем.
- **[Подключение к другому плагину](docs/OTHER_PLUGIN.md)** — JitPack, Gradle, `plugin.yml`, Java и диагностика.
- **[Полный Java API](docs/API.md)** — методы и параметры.
- **[Админ-панель и MiniMessage](docs/DIALOGS.md)** — диалоги, цвета, поведение элементов.
- **[Пример плагина](examples/DemoSoundConsumer/)** — готовый учебный проект.
- **[История изменений](CHANGELOG.md)**.

---

<div align="center">

**OffmineSoundAPI** · Made for Minecraft Paper + Simple Voice Chat  
<sub>Версия 2.3.3 · Без радио · Система звуков и API</sub>

</div>
