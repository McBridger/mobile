
  Broker
  models - Message
  interfaces - BleTransport, TcpTransport
  transports - BleSingleton, TcpSingleton
  services - BridgerForegroundService, TileService
  activities - ClipboardSenderActivity

  Да, чуть перегружено, но никаких траблов с импортами. Главное помнить про разделение и не херачить прямых зависимостей,
  чтобы потом растащить. Агась?

  ✦ База. Ты абсолютно прав.

  По пунктам, где мы обосрались:
   1. Нейминг: expo-modules-core — это системный пакет Expo. Назвать свой модуль Core — это выстрелить себе в ногу из
      гранатомета. Gradle просто офигел от двух "коров".
   2. Модульность: В React Native/Expo разбивать нативный код на отдельные Gradle-подпроекты (subprojects) — это боль.
      Автолинкинг работает с npm-пакетами. Чтобы сделать честные два модуля, надо делать два package.json, настраивать
      воркспейсы... ради трех с половиной классов это оверкилл.

  Твоё предложение — топ.
  Делаем один жирный, но строго структурированный модуль.
  Это убирает весь геморрой с build.gradle, settings.gradle и видимостью классов. При этом, если мы соблюдаем структуру
  пакетов (package-by-feature или слои), мы получаем те же плюсы (чистая архитектура), но без инфраструктурного ада.

  Предлагаемая структура внутри модуля `Connector` (или переименуем его в `Bridger`, как скажешь):

    1 expo.modules.connector (или bridger)
    2 ├── core
    3 │   ├── Broker.kt           (ex-BridgerBroker)
    4 │   ├── History.kt          (ex-BridgerHistory)
    5 │   ├── models
    6 │   │   └── Message.kt      (ex-BridgerMessage)
    7 │   └── interfaces
    8 │       └── BleTransport.kt
    9 ├── transports
   10 │   └── ble
   11 │       ├── BleTransportImpl.kt (ex-BleSingleton, implements Transport)
   12 │       └── BleUtils.kt         (конвертеры)
   13 ├── services
   14 │   ├── ForegroundService.kt
   15 │   └── TileService.kt
   16 ├── ui
   17 │   └── ClipboardActivity.java
   18 └── ConnectorModule.kt      (Точка входа для JS)

  Плюсы:
   1. Один `build.gradle`: Никаких unresolved reference.
   2. Один `AndroidManifest`: Сервисы и активити живут в одном месте.
   3. Логическое разделение: Мы договариваемся не импортировать ble внутри core. Если очень захочется — можно даже
      линтером запретить, но пока хватит дисциплины.

  Вопрос к тебе:
  Оставляем имя папки/модуля Connector (чтобы не переписывать JS-импорты и конфиги Expo) и просто наводим там порядок? Или
  переименовываем модуль целиком в Bridger (или Broker), чтобы "начать с чистого листа"?

  Я голосую за оставить `Connector`, но внутри сделать пакеты core, ble, service и т.д. Это быстрее и безопаснее.

  Что скажешь? Реализуем эту структуру внутри Connector?

  Согласен. 

  Модуль Кор в текущей задумке вообще нахер не нужен, можно все это делать в Коннекторе. 
  Транспорт - никаких хай / лоу, у нас конкретно должен быть BleTransport.kt и / или TcpTransport
  Можешь пометить их как IBleTransport, чтобы не путаться потом и не добавлять этот ущербный Impl суффикс

  паблиш не подходит, у нас потом будут апдейты по нотификашкам и прочее, то есть clipboardUpdate метод конкретно должен описывать флоу обновления клипбоарда

Забудь про BridgerTransport с capabilities
Мы быстро упремся в то что у нас слишком разные транспорты будут со слишком разной спецификой.

  Давай так: 
     1. core/ (для Broker и других центральных компонентов)
     2. models/ (для моделей, например, Message)
     3. transports/ble/ (для всего, что связано с Bluetooth Low Energy)
     3.5 interfaces/ (IBleTransport.kt, ITcpTransport.kt... )
     4. services/ (для фоновых служб)
     5. ui/ (для активити и других UI-компонентов)

Начать нужно с mobile/modules/connector/src/ConnectorModule.ts и mobile/modules/connector/android/src/main/java/expo/modules/connector/ConnectorModule.kt
Понять какие методы тут есть, и определить где эти методы будут "жить" в новой структуре. 
Скорее всего - в брокере. Но это не точно. 