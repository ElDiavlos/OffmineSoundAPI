# DemoSoundConsumer: пример подключения OffmineSoundAPI через JitPack

Это отдельный, минимальный Gradle-плагин для **Paper 26.3 / Java 25**. Команда `/testalarm` воспроизводит звук с ID `alarm` игроку, который её вызвал.

1. Убедитесь, что JitPack успешно собрал `com.github.ElDiavlos:offmineSoundAPI:2.3.2` (важен регистр `offmineSoundAPI`).
2. Откройте **эту папку** как отдельный Gradle-проект в IntelliJ IDEA (или используйте `gradle build` с Gradle 9 и JDK 25).
3. `settings.gradle` содержит репозитории Paper + JitPack; `build.gradle` скачивает `paper-api` и OffmineSoundAPI через `compileOnly`.
4. После успешной сборки положите `build/libs/DemoSoundConsumer-1.0.0.jar` в `plugins/` сервера вместе с OffmineSoundAPI и Simple Voice Chat.
5. Создайте звук `alarm` через `/soundadmin`, перезапустите сервер и используйте `/testalarm`.

Если в вашем проекте уже настроены репозитории в `settings.gradle`, не нужно создавать второй `dependencyResolutionManagement`: добавьте JitPack в существующий.

Полная инструкция: [`../../docs/OTHER_PLUGIN.md`](../../docs/OTHER_PLUGIN.md), справочник методов: [`../../docs/API.md`](../../docs/API.md).
