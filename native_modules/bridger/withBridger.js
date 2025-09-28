const {
  withAppBuildGradle,
  withDangerousMod,
  withMainApplication,
  withAndroidManifest,
  createRunOncePlugin,
  withStringsXml
} = require("@expo/config-plugins");
const { resolve } = require("path");
const { mkdirSync, cpSync } = require("fs");

const withBridgerBle = (config) => {
  // 1. Модифицируем build.gradle
  config = withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // Блок с зависимостями, который нужно вставить
    const dependenciesToAdd = `
    implementation 'no.nordicsemi.android.support.v18:scanner:1.6.0'
    implementation 'no.nordicsemi.android:ble:2.10.2'
    implementation 'com.google.code.gson:gson:2.10.1'`;

    // "Якорь", после которого мы вставим наши зависимости.
    // Это надежнее, чем просто искать "dependencies {"
    const anchor = `    implementation("com.facebook.react:react-android")`;

    // Вставляем наш блок
    contents = contents.replace(anchor, `${anchor}${dependenciesToAdd}`);

    config.modResults.contents = contents;
    console.log(
      "[BridgerBlePlugin] Успешно добавлены зависимости в app/build.gradle."
    );

    return config;
  });

  // 2. Копируем Java файлы
  config = withDangerousMod(config, [
    "android", // Используем стандартный модификатор для доступа к корню android-проекта
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const platformProjectRoot = config.modRequest.platformProjectRoot; // Путь к ./android/

      const sourceDir = resolve(
        projectRoot,
        "native_modules/bridger/android/src/main/java/com/rn/bridger"
      );
      const targetDir = resolve(
        platformProjectRoot,
        "app/src/main/java/com/rn/bridger"
      );

      console.log(
        `[BridgerBlePlugin] Copying Java files from ${sourceDir} to ${targetDir}`
      );

      mkdirSync(targetDir, { recursive: true });

      const filesToCopy = [
        "BleBridgerPackage.java",
        "BleConnectorModule.java",
        "BleScannerModule.java",
        "BleSingleton.java",
        "BridgerForegroundService.java",
        "BridgerHeadlessTask.java",
        "BridgerMessage.java",
        "ClipboardSenderActivity.java",
        "ClipboardTileService.java",
      ];

      for (const file of filesToCopy) {
        cpSync(resolve(sourceDir, file), resolve(targetDir, file));
      }

      const resSourceDir = resolve(projectRoot, "native_modules/bridger/android/res");
      const resTargetDir = resolve(platformProjectRoot, "app/src/main/res");
      
      console.log(`[BridgerBlePlugin] Copying resource files from ${resSourceDir} to ${resTargetDir}`);      
      cpSync(resSourceDir, resTargetDir, { recursive: true });

      return config;
    },
  ]);

  // 3. Модифицируем MainApplication (подходит для Java и Kotlin)
  config = withMainApplication(config, (config) => {
    let contents = config.modResults.contents;

    const addPackageRegex =
      /(?<spaces> *)(?<anchor>return packages)(?<semi>;?)/;
    const props = addPackageRegex.exec(contents);
    const original = props[0];
    const spaces = props?.groups?.spaces;
    const semi = props?.groups?.semi || "";

    const packageToAdd = "packages.add(BleBridgerPackage())";
    contents = contents.replace(
      `${original}`,
      `${spaces}${packageToAdd}${semi}\n${original}`
    );

    config.modResults.contents = contents;
    return config;
  });

  // 4. Modify AndroidManifest.xml to include permissions, activities, and services
  config = withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;
    manifest.application = manifest.application || [{}];
    manifest.application[0] ??= {};

    manifest.application[0].activity ??= [];
    manifest.application[0].service ??= [];
    manifest["uses-permission"] ??= [];

    const permissionsToAdd = [
      { $: { "android:name": "android.permission.WAKE_LOCK" } },
      { $: { "android:name": "android.permission.FOREGROUND_SERVICE" } },
      {
        $: {
          "android:name":
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
        },
      },
      {
        $: {
          "android:name": "android.permission.BLUETOOTH_SCAN",
          "android:usesPermissionFlags": "neverForLocation",
        },
      },
      { $: { "android:name": "android.permission.BLUETOOTH_CONNECT" } },
      { $: { "android:name": "android.permission.BLUETOOTH_ADVERTISE" } },
      { $: { "android:name": "android.permission.POST_NOTIFICATIONS" } },
      {
        $: {
          "android:name": "android.permission.BLUETOOTH",
          "android:maxSdkVersion": "30",
        },
      },
      {
        $: {
          "android:name": "android.permission.BLUETOOTH_ADMIN",
          "android:maxSdkVersion": "30",
        },
      },
      { $: { "android:name": "android.permission.ACCESS_COARSE_LOCATION" } },
      { $: { "android:name": "android.permission.ACCESS_FINE_LOCATION" } },
    ];

    const ourPermissionNames = new Set(
      permissionsToAdd.map((p) => p.$["android:name"])
    );
    const otherPermissions = manifest["uses-permission"].filter(
      (p) => !ourPermissionNames.has(p.$["android:name"])
    );
    manifest["uses-permission"] = [...otherPermissions, ...permissionsToAdd];

    // Add ClipboardSenderActivity
    const clipboardSenderActivity = {
      $: {
        "android:name": ".ClipboardSenderActivity",
        "android:exported": "true",
        "android:noHistory": "true",
        "android:excludeFromRecents": "true",
        "android:theme": "@android:style/Theme.Translucent.NoTitleBar",
        "android:launchMode": "singleInstance",
      },
      "intent-filter": [
        {
          action: [
            {
              $: {
                "android:name": "android.intent.action.VIEW",
              },
            },
          ],
          category: [
            {
              $: {
                "android:name": "android.intent.category.DEFAULT",
              },
            },
          ],
        },
      ],
    };
    const activityExists = manifest.application[0].activity.some(
      (a) => a.$["android:name"] === clipboardSenderActivity.$["android:name"]
    );
    if (!activityExists)
      manifest.application[0].activity.push(clipboardSenderActivity);

    // Add ClipboardTileService
    const clipboardTileService = {
      $: {
        "android:name": ".ClipboardTileService",
        "android:enabled": "true",
        "android:exported": "true",
        "android:icon": "@drawable/ic_send_clipboard",
        "android:label": "Send Clipboard",
        "android:permission": "android.permission.BIND_QUICK_SETTINGS_TILE",
      },
      "intent-filter": [
        {
          action: [
            {
              $: {
                "android:name": "android.service.quicksettings.action.QS_TILE",
              },
            },
          ],
        },
      ],
    };
    const tileServiceExists = manifest.application[0].service.some(
      (s) => s.$["android:name"] === clipboardTileService.$["android:name"]
    );
    if (!tileServiceExists)
      manifest.application[0].service.push(clipboardTileService);

    // Add BridgerForegroundService
    const bridgerForegroundService = {
      $: {
        "android:name": ".BridgerForegroundService",
        "android:enabled": "true",
        "android:exported": "false",
        "android:foregroundServiceType": "connectedDevice",
      },
    };

    const foregroundServiceExists = manifest.application[0].service.some(
      (s) => s.$["android:name"] === bridgerForegroundService.$["android:name"]
    );
    if (!foregroundServiceExists)
      manifest.application[0].service.push(bridgerForegroundService);

    const bridgerHeadlessTask = {
      $: {
        "android:name": ".BridgerHeadlessTask",
        "android:enabled": "true",
        "android:exported": "false",
      },
    };

    const headlessTaskExists = manifest.application[0].service.some(
      (s) => s.$["android:name"] === bridgerHeadlessTask.$["android:name"]
    );
    if (!headlessTaskExists)
      manifest.application[0].service.push(bridgerHeadlessTask);

    return config;
  });

  config = withStringsXml(config, (config) => {
    const strings = config.modResults.resources.string || [];
    const stringsToAdd = [
      { name: "shortcut_send_clipboard_short", value: "Send Clipboard" },
      { name: "shortcut_send_clipboard_long", value: "Send clipboard to device" },
    ];

    for (const newString of stringsToAdd) {
        const existingString = strings.find(s => s.$ && s.$.name === newString.name);
        
        if (!existingString) {
            strings.push({
                $: { name: newString.name, translatable: "false" },
                _: newString.value,
            });
        }
    }
    
    // Обновляем массив строк в объекте манифеста
    config.modResults.resources.string = strings;
    return config;
  });

  return config;
};

module.exports = createRunOncePlugin(withBridgerBle, "withBridgerBle", "1.0.0");
