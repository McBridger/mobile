import { ConfigContext, ExpoConfig } from "expo/config";
import { capitalize } from "lodash";
import 'tsx/cjs';
import { z } from "zod";

const Extra = z.object({
  APP_VARIANT: z.enum(["dev", "preview", "prod", "e2e"]).default("dev"),
  ENCRYPTION_SALT: z.string(),
  MNEMONIC_LENGTH: z.number().default(6),
  MNEMONIC_LOCAL: z.string().optional(),
  eas: z.object({
    projectId: z.uuidv4(),
  }),
});

export default ({ config }: ConfigContext): AppConfig => {
  const appName = ['com', 'mc', 'bridger'];
  const extra = Extra.parse({
    ...config.extra,
    APP_VARIANT: process.env.APP_VARIANT,
    ENCRYPTION_SALT: process.env.ENCRYPTION_SALT,
    MNEMONIC_LOCAL: process.env.MNEMONIC_LOCAL,
    MNEMONIC_LENGTH: process.env.MNEMONIC_LENGTH ? parseInt(process.env.MNEMONIC_LENGTH) : undefined,
  });

  const suffix = extra.APP_VARIANT === "prod" ? "" : extra.APP_VARIANT;
  if (suffix) appName.push(suffix);

  return {
    ...config,
    name: `McBridger${capitalize(suffix)}`,
    slug: 'bridger',
    android: {
      ...config.android,
      package: appName.join('.'),
    },
    plugins: [
      [
        "expo-build-properties",
        {
          android: {
            minSdkVersion: 25,
            buildArchs: process.env.CI ? ["x86_64"] : ["arm64-v8a"],
          },
        },
      ],
      // @ts-expect-error
      ...config.plugins,
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
          "org.gradle.caching": "true"
        }
      ]
    ],
    extra,
  };
};

export interface AppConfig extends ExpoConfig {
  extra: z.infer<typeof Extra>;
}
