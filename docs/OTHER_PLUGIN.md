# Подключение OffmineSoundAPI в другой Paper-плагин (Gradle + JitPack)

**Для:** OffmineSoundAPI **2.3.2**, Paper **26.3**, Java **25**.  
**Проверено по исходникам этой версии:** публичный класс `ElTeam.offmineSimpleVoiceElTeam.api.SoundApi`; серверный `plugin.yml` объявляет плагин как `OffmineSoundAPI`.

> Это инструкция для **плагина-потребителя**, то есть вашего **другого** проекта. Не требуется копировать исходники OffmineSoundAPI в него.

## 0. Что нужно заранее

1. Исходники OffmineSoundAPI должны находиться в публичном репозитории GitHub, а релиз/тег `2.3.2` — успешно собираться через [JitPack](https://jitpack.io/). Проверьте **зелёный статус сборки**, а не только появление подсказки «How to».
2. В качестве Maven-координат используйте **точно то, что показано JitPack для вашего репозитория**. На предоставленном скриншоте:

   ```groovy
   com.github.ElDiavlos:offmineSoundAPI:2.3.2
   ```

   Регистр букв важен: `offmineSoundAPI` начинается с маленькой `o`. Если вы переименовали GitHub-репозиторий или ваш JitPack показывает другое написание, берите его актуальные координаты.
3. Сам **сервер** должен иметь JAR OffmineSoundAPI и Simple Voice Chat в `plugins/`. Gradle скачивает зависимость **для компиляции**, а не для автоматической установки на сервер.

## 1. Настройте репозитории в `settings.gradle` другого плагина

Откройте корневой `settings.gradle` **другого** Java/Gradle-проекта (Groovy DSL) и добавьте JitPack.

Если проект использует `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, рекомендуется хранить репозитории в **`settings.gradle`**:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri('https://repo.papermc.io/repository/maven-public/') }
        maven { url = uri('https://jitpack.io') }
    }
}

rootProject.name = 'MySoundPlugin'
```

- Если в файле уже есть `dependencyResolutionManagement`, **добавьте** `maven { url = uri('https://jitpack.io') }` в существующий `repositories`, не создавайте второй блок.
- Если настроен `FAIL_ON_PROJECT_REPOS`, удалите `repositories { ... }` из `build.gradle` **после переноса** их содержимого в `settings.gradle`. Иначе Gradle выдаст ошибку о репозиториях проекта.
- Если нет централизованного `dependencyResolutionManagement`, можно вместо этого добавить JitPack в `repositories` в `build.gradle`; используйте **один** согласованный способ настройки.
- `rootProject.name` — имя **вашего** плагина, а не OffmineSoundAPI. Не дублируйте его, если строка уже есть.

## 2. Добавьте `compileOnly` в `build.gradle` другого плагина

В его `build.gradle`:

```groovy
plugins {
    id 'java'
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.3.build.+'
    compileOnly 'com.github.ElDiavlos:offmineSoundAPI:2.3.2'
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 25
}
```

Если `plugins`, `dependencies` или `java` уже присутствуют, **добавьте нужные строки в существующие блоки**, не стирайте настройки других библиотек и Shadow.

**Почему `compileOnly`, а не `implementation`?** OffmineSoundAPI — самостоятельный плагин на сервере. Его классы не нужно встраивать в ваш JAR (например, через Shadow); иначе могут появиться конфликты классов и экземпляров API.

После редактирования нажмите **Reload All Gradle Projects** в IntelliJ IDEA или выполните:

```bash
./gradlew --refresh-dependencies compileJava
```

На Windows: `gradlew.bat --refresh-dependencies compileJava`.

## 3. Укажите зависимость в `plugin.yml`

Файл **другого** проекта: `src/main/resources/plugin.yml`:

```yaml
name: MySoundPlugin
version: '1.0.0'
main: example.MySoundPlugin
api-version: '26.3'
depend:
  - OffmineSoundAPI
commands:
  testalarm:
    description: Проиграть alarm через OffmineSoundAPI
```

- `depend` — **имя Bukkit-плагина** `OffmineSoundAPI`, не координаты JitPack и не имя Java-класса.
- Путь `main` должен соответствовать **вашему** Java-классу.
- `OffmineSoundAPI` зависит от `voicechat`, поэтому установите и Simple Voice Chat на сервер.

## 4. Пример вызова API на Java

```java
package example;

import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MySoundPlugin extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков");
            return true;
        }

        // API регистрируется самим OffmineSoundAPI в Bukkit ServicesManager.
        SoundApi sounds = getServer().getServicesManager().load(SoundApi.class);
        if (sounds == null || !sounds.isReady()) {
            player.sendMessage("Simple Voice Chat пока не готов");
            return true;
        }
        if (!sounds.exists("alarm")) {
            player.sendMessage("Звук alarm ещё не добавлен администратором");
            return true;
        }
        try {
            sounds.play(player, "alarm");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            getLogger().warning("Не удалось воспроизвести alarm: " + ex.getMessage());
        }
        return true;
    }
}
```

Альтернативный короткий доступ: `SoundApi.get()`. В рабочем коде сначала убедитесь, что зависимый плагин включён и Simple Voice Chat готов; через `ServicesManager` удобно проверять `null`.

Другие методы:

```java
// Выполнить в основном потоке Paper, после проверки isReady() и exists(id):
sounds.play(player, "alarm");               // одному игроку
sounds.playAll("alarm");                    // всем игрокам
sounds.playAt(player.getLocation(), "alarm", 24f); // звук в радиусе 24 блоков
sounds.stopPlayer(player);                   // остановить персональные звуки
```

Полный список `playAt`, `place`, `enqueue`, `stopAt` и других методов — в [`API.md`](API.md). Аргумент `"alarm"` — **ID звука**, а не путь к MP3.

**Потоки:** вызывать методы воспроизведения следует **в основном потоке сервера**. Из асинхронной задачи переключайтесь явно на `Runnable`:

```java
getServer().getScheduler().runTask(this, (Runnable) () -> {
    SoundApi sounds = getServer().getServicesManager().load(SoundApi.class);
    if (sounds != null && sounds.isReady() && sounds.exists("alarm")) {
        sounds.play(player, "alarm");
    }
});
```

Явное приведение к `Runnable` помогает избежать неоднозначного выбора перегрузки `runTask` в некоторых версиях Paper.

## 5. Соберите плагин-потребитель и установите на сервер

В IntelliJ IDEA выполните Gradle-задачу `build` в **другом** проекте, либо `./gradlew clean build` (Windows: `gradlew.bat clean build`).

В `plugins/` Paper должны находиться **три отдельных плагина**:

```text
plugins/
├── voicechat-....jar
├── OffmineSoundAPI-2.3.2.jar
└── MySoundPlugin-1.0.0.jar
```

Перезапустите сервер, добавьте звук под ID `alarm` через `/soundadmin` (доступен администраторам) и введите `/testalarm`.

Громкость звуков настраивается каждым игроком в клиентском моде **Simple Voice Chat**: обычно `V` → **Регулировка громкости** → **Звуки сервера**.

## 6. Если Gradle не скачивает API

| Ошибка | Причина и решение |
|---|---|
| `Could not find com.github.ElDiavlos:offmineSoundAPI:2.3.2` | Убедитесь, что JitPack показывает **успешную** сборку тега `2.3.2`, координаты совпадают с сайтом **по регистру**, а репозиторий публичен. |
| `Build was configured to prefer settings repositories over project repositories` | Перенесите `repositories` из `build.gradle` в `settings.gradle` или согласуйте режим репозиториев. |
| `package ElTeam.offmineSimpleVoiceElTeam.api does not exist` | Проверьте, что Gradle скачал JAR нужной версии и в нём есть `api/SoundApi.class`. |
| `No matching toolchains found` | Для Paper 26.3 требуется установленная JDK 25 либо настроенная автоматическая установка toolchain. |
| `NoClassDefFoundError: ...SoundApi` на сервере | Установите OffmineSoundAPI на сервер, проверьте `depend: [OffmineSoundAPI]`, отсутствие конфликтующих JAR и ошибки загрузки в консоли. |
| `Simple Voice Chat пока не готов` | Проверьте работающий `voicechat`, его серверный запуск и UDP-подключение клиента. |
| `Звук alarm ещё не добавлен` | Загрузите или положите звук на сервер и убедитесь, что его **ID** равен `alarm`. |

Если JitPack по-прежнему красный, **изменение строки `compileOnly` проблему не исправит**: сначала откройте Build log соответствующей версии на JitPack и устраните ошибку сборки OffmineSoundAPI.

## 7. Локальный JAR как запасной вариант

Если JitPack недоступен, используйте старый способ **только как резервный**:

```groovy
dependencies {
    compileOnly files('libs/OffmineSoundAPI-2.3.2.jar')
}
```

Это не нужно при работающем JitPack. **Не добавляйте обе зависимости одновременно.**

## 8. Готовый образец

Папка `examples/DemoSoundConsumer/` теперь содержит самостоятельные `settings.gradle`, `build.gradle`, `plugin.yml` и Java-класс с командой `/testalarm`. Это минимальный рабочий шаблон проекта, который можно открыть в IntelliJ IDEA. Убедитесь, что версия доступна на JitPack и установлен JDK 25.
