/* eslint-disable no-unused-expressions */
import { ConfigContext, ExpoConfig } from "expo/config";
import { capitalize } from "lodash";
import "tsx/cjs";
import { Static, Type } from "typebox";
import { Value } from "typebox/value";

// const Extra = z.object({
//   APP_VARIANT: z.enum(["dev", "preview", "prod", "e2e"]).default("dev"),
//   ENCRYPTION_SALT: z.string(),
//   MNEMONIC_LENGTH: z.number().default(6),
//   MNEMONIC_LOCAL: z.string().optional(),
//   eas: z.object({
//     projectId: z.uuidv4(),
//   }),
// });

const Extra = Type.Object({
  APP_VARIANT: Type.Enum(["dev", "preview", "prod", "e2e"], { default: "dev" }),
  ENCRYPTION_SALT: Type.String(),
  MNEMONIC_LENGTH: Type.Number({ default: 6 }),
  MNEMONIC_LOCAL: Type.Optional(Type.String()),
  eas: Type.Object({
    projectId: Type.String(),
  }),
});

export default ({ config }: ConfigContext): AppConfig => {
  const scheme = ["bridger"];
  const appName = ["com", "mc", "bridger"];

  const extra = Value.Parse(Extra, {
    ...config.extra,
    APP_VARIANT: process.env.APP_VARIANT,
    ENCRYPTION_SALT: process.env.ENCRYPTION_SALT,
    MNEMONIC_LOCAL: process.env.MNEMONIC_LOCAL,
    MNEMONIC_LENGTH: process.env.MNEMONIC_LENGTH,
  });

  const suffix = extra.APP_VARIANT === "prod" ? "" : extra.APP_VARIANT;
  if (suffix) (appName.push(suffix), scheme.push(suffix));

  const identifier = appName.join(".");
  const nativeArch = process.arch === "arm64" ? "arm64-v8a" : "x86_64";

  return {
    ...config,
    name: `McBridger${capitalize(suffix)}`,
    scheme: scheme.join("-"),
    android: {
      ...config.android,
      package: identifier,
    },
    plugins: [
      [
        "expo-build-properties",
        {
          android: {
            minSdkVersion: 25,
            buildArchs:
              extra.APP_VARIANT === "e2e" ? [nativeArch] : ["arm64-v8a"],
          },
        },
      ],
      // @ts-expect-error
      ...config.plugins,
      [
        "./plugins/withE2EDebuggable.ts",
        {
          enabled: extra.APP_VARIANT === "e2e",
        },
      ],
      [
        "./plugins/withManifestMetadata.ts",
        {
          "expo.modules.connector.TILE_LABEL":
            extra.APP_VARIANT === "prod"
              ? "Send Clipboard"
              : `Send (${capitalize(extra.APP_VARIANT)})`,
        },
      ],
      // [
      //   "./plugins/withGradleSettings.ts",
      //   {
      //     plugins: ['id("com.develocity.enterprise") version "4.2.2"'],
      //     develocity: {
      //       buildScan: {
      //         termsOfService: {
      //           agree: "yes"
      //         }
      //       }
      //     }
      //   }
      // ],
      [
        "./plugins/withGradleProperties.ts",
        {
          "org.gradle.caching": "true",
        },
      ],
    ],
    extra,
  };
};

export interface AppConfig extends ExpoConfig {
  extra: Static<typeof Extra>;
}
