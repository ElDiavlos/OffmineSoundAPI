# OffmineSoundAPI — документация Java API

**Версия проекта:** 2.3.3 • **Сервер:** Paper 26.3 • **Java сервера и официальной сборки:** 25 • **Simple Voice Chat:** 2.6.24

## 1. Подключение другого плагина через JitPack

**Пошаговая инструкция** с `settings.gradle`, `build.gradle`, `plugin.yml`, примером команды, настройкой IDE и решениями ошибок: **[`docs/OTHER_PLUGIN.md`](OTHER_PLUGIN.md)**.

Главное отличие от старой схемы: теперь можно использовать автоматическую Gradle-зависимость **вместо** `libs/OffmineSoundAPI-2.3.3.jar`.

**`settings.gradle` другого проекта** (добавьте JitPack в имеющийся блок репозиториев):

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

**`build.gradle` другого проекта**:

```groovy
dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.3.build.+'
    compileOnly 'com.github.ElDiavlos:offmineSoundAPI:2.3.3'
}
```

Это **точная запись из предоставленной инструкции JitPack** (артефакт `offmineSoundAPI` с маленькой буквы). Для работы требуется, чтобы у версии `2.3.3` был **зелёный статус** сборки JitPack. Если у вашего релиза другие координаты, используйте те, которые JitPack показывает для него.

В `plugin.yml` другого плагина укажите `depend: [OffmineSoundAPI]`, а на сервер установите OffmineSoundAPI и Simple Voice Chat как отдельные плагины. `compileOnly` не помещает API в ваш JAR. Если используется `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, не оставляйте блок `repositories` в `build.gradle`.

Локальное подключение через `compileOnly files('libs/OffmineSoundAPI-2.3.3.jar')` остаётся **резервным вариантом** на случай недоступности JitPack; оба способа одновременно не нужны.

## 2. Получение API

Полное имя класса: `ElTeam.offmineSimpleVoiceElTeam.api.SoundApi`.

```java
import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;

SoundApi api = SoundApi.get();
```

Альтернативно, через Bukkit ServicesManager (проще при работе с зависимостями):

```java
SoundApi api = getServer().getServicesManager().load(SoundApi.class);
if (api == null || !api.isReady()) {
    getLogger().warning("Sound API / Simple Voice Chat пока не готов");
    return;
}
```

`SoundApi.get()` получает singleton активного плагина. Если OffmineSoundAPI отключён, вызов может выбросить `NullPointerException` / `IllegalStateException`; используйте `depend` и проверку `isReady()`.

## 3. Справочник методов

| Метод | Возвращает | Назначение |
|---|---|---|
| `static SoundApi get()` | `SoundApi` | Глобальный экземпляр |
| `isReady()` | `boolean` | Voice Chat готов для воспроизведения |
| `listSounds()` | `List<String>` | Список зарегистрированных ID |
| `exists(String id)` | `boolean` | Есть ли звук |
| `file(String id)` | `Path` | Путь аудиофайла; ошибка, если ID отсутствует |
| `play(Player player, String id)` | `void` | Воспроизвести одному игроку |
| `play(Player player, String id, boolean loop)` | `void` | То же, с повтором при `loop=true` |
| `playAll(String id)` | `void` | Воспроизвести всем игрокам |
| `playAll(String id, boolean loop)` | `void` | Всем игрокам, с возможным повтором |
| `playAt(Location location, String id, float radius)` | `UUID` | Временный 3D-звук в радиусе, без повтора |
| `playAt(Location location, String id, float radius, boolean loop)` | `UUID` | Временный 3D-звук, с возможным повтором |
| `stopAt(UUID handle)` | `boolean` | Остановить конкретный временный 3D-звук |
| `stopAllAt()` | `void` | Остановить все временные 3D-звуки |
| `place(String beaconId, Location location, float radius, String id, boolean loop)` | `void` | Создать сохраняемый позиционный звуковой маяк |
| `enqueue(String beaconId, String id)` | `void` | Добавить трек в очередь маяка |
| `removeBeacon(String beaconId)` | `boolean` | Удалить маяк и его сохранённую запись |
| `stopPlayer(Player player)` | `void` | Остановить персональные аудиоканалы игрока |
| `stopAllPlayers()` | `void` | Остановить все персональные и глобальные воспроизведения |
| `stopGlobal()` | `void` | Остановить глобальные воспроизведения |

Встроенный `shutdown()` — служебный метод самого API; **не вызывайте его** из чужого плагина.

`id` — строка из `a-z`, `0-9`, `_`, `-` (1–48 символов), например `boss_intro`. В коде обращайтесь к **ID**, а не к имени `boss_intro.mp3` или пути.

## 4. Примеры воспроизведения

Все примеры подразумевают корректные переменные `Player player`, `Location location` и существующий ID `alarm`. На сервере должен работать Simple Voice Chat.

```java
SoundApi api = SoundApi.get();

// Только одному игроку
api.play(player, "alarm");

// Только одному игроку и по кругу
api.play(player, "alarm", true);

// Всем онлайн-игрокам
api.playAll("alarm");

// В радиусе 24 блока от заданной локации
UUID handle = api.playAt(location, "alarm", 24f);

// Временный позиционный звук по кругу
UUID loopHandle = api.playAt(location, "wind", 32f, true);

// Временные звуки можно остановить по handle
api.stopAt(loopHandle);
```

`playAt` возвращает `UUID`-идентификатор конкретного запуска, **не ID звука**. Держите его в своей коллекции, если планируете остановить звук позднее. Временный звук не сохраняется после перезагрузки. Для `radius` требуется конечное число в интервале `(0; 1024]`.

## 5. Постоянные звуковые маяки

**Маяки — не радио.** Это позиционные источники звука, которые можно включить в определённой точке и восстановить при перезапуске сервера.

```java
SoundApi api = SoundApi.get();
api.place("spawn_ambience", location, 32f, "forest", true);
api.enqueue("spawn_ambience", "birds");
api.removeBeacon("spawn_ambience");
```

`beaconId` — уникальный ID пользовательского источника; он отличается от `soundId`. `place` бросает исключение, если маяк уже существует. `enqueue` добавляет следующий трек в очередь, а `removeBeacon` удаляет маяк из постоянного хранилища.

## 6. Поиск, проверка и остановка

```java
SoundApi api = SoundApi.get();

for (String id : api.listSounds()) {
    getLogger().info("Звук: " + id);
}
if (api.exists("boss_intro") && api.isReady()) {
    api.play(player, "boss_intro");
}

api.stopPlayer(player);
api.stopGlobal();
api.stopAllPlayers();
api.stopAllAt();
```

`stopPlayer` и глобальные операции остановки действуют на воспроизведения, управляемые OffmineSoundAPI, а не на музыку/звуки других независимых плагинов.

## 7. Потоки, нагрузка и обработка ошибок

**Важное правило:** методы управления и воспроизведения вызывайте **только из основного потока сервера**. Если вызов нужен из асинхронной задачи, переключитесь на планировщик:

```java
Bukkit.getScheduler().runTask(this, (Runnable) () -> {
    SoundApi api = SoundApi.get();
    if (api.isReady() && api.exists("alarm")) {
        api.play(player, "alarm");
    }
});
```

Для крупных MP3/OGG/FLAC требуется FFmpeg и преобразование в 48 кГц моно PCM. Загрузка и декодирование аудио при воспроизведении может быть ресурсоёмким и на больших файлах приводить к задержкам тиков; ограничивайте длительность (`audio.max_duration_seconds`) и тестируйте нагрузку.

Типичные ошибки:

- `IllegalArgumentException`: неверный ID, файл не найден, игрок офлайн, неверная локация или радиус, уже создан маяк;
- `IllegalStateException`: Simple Voice Chat ещё не готов, вызов из неосновного потока, ошибка запуска/декодирования;
- отсутствие звука у игрока: клиент не подключён к Simple Voice Chat, не настроен UDP или нет голосового соединения.

Пример безопасного вызова:

```java
try {
    SoundApi api = SoundApi.get();
    if (!api.isReady() || !api.exists("alarm")) return;
    api.play(player, "alarm");
} catch (IllegalArgumentException | IllegalStateException ex) {
    getLogger().warning("Невозможно воспроизвести alarm: " + ex.getMessage());
}
```

## 8. Пример самостоятельного плагина

Готовый пример исходников лежит в `examples/DemoSoundConsumer/`. В нём есть команда `/testalarm`, подключение через `ServicesManager`, проверка готовности и вызов `api.play(...)`.

## 9. Администратор: откуда берутся ID

ID регистрируются при скачивании из GUI или `/osv download <id> <ссылка>`. Можно вручную положить аудиофайл в `plugins/OffmineSoundAPI/sounds/`, а затем запустить `/osv reload`: реестр `sounds.yml` автоматически присвоит ему нормализованный ID.

Подробности, список команд и разрешений: `docs/INSTALL.md`.

## 10. Нативное меню администратора

Начиная с версии **2.3.3** `/soundadmin` вызывает нативный диалог Paper 26.3 со списком звуков, полями для ID/URL и кнопками копирования готового Java-кода. API `SoundApi` не изменялся: вашему плагину достаточно получить `SoundApi.get()` и вызвать нужный метод. Подробнее: `docs/INSTALL.md` и `docs/DIALOGS.md`.

## Громкость на стороне клиента

Все созданные API аудиоканалы назначаются на категорию Simple Voice Chat с идентификатором `osv` и названием **«Server Sounds»**. Игрок открывает `V` → «Регулировка громкости» → «Server Sounds». Ползунок влияет на все звуки, отправленные через `SoundApi`, включая `play`, `playAll`, `playAt` и звуковые маяки. Стороннему плагину не требуется вызывать дополнительные методы изменения громкости; каждое клиентское устройство регулирует звук независимо.

Команды администрирования скрыты для пользователей без `osv.admin`, но классы публичного Java API по-прежнему доступны плагинам с `depend: [OffmineSoundAPI]`.


### Громкость Simple Voice Chat

Все звуковые каналы используют одну клиентскую категорию `osv` («Server Sounds»). Диагностика: `/osv voicecheck`. Подробности: [`VOLUME.md`](VOLUME.md).
