import 'tsx/cjs';
import { ConfigContext, ExpoConfig } from "expo/config";
import { capitalize } from "lodash";
import { z } from "zod";

const Extra = z.object({
  SERVICE_UUID: z.uuidv4(),
  CHARACTERISTIC_UUID: z.uuidv4(),
  ADVERTISE_UUID: z.string(),
  APP_VARIANT: z.enum(["dev", "preview", "prod"]),
  ENCRYPTION_SALT: z.string().optional(),
  MNEMONIC_LOCAL: z.string().optional(),
  eas: z.object({
    projectId: z.uuidv4(),
  }),
});

export default ({ config }: ConfigContext): AppConfig => {
  const extra = Extra.parse({
    ...config.extra,
    SERVICE_UUID: process.env.SERVICE_UUID,
    CHARACTERISTIC_UUID: process.env.CHARACTERISTIC_UUID,
    ADVERTISE_UUID: process.env.ADVERTISE_UUID,
    APP_VARIANT: process.env.APP_VARIANT,
    ENCRYPTION_SALT: process.env.ENCRYPTION_SALT,
    MNEMONIC_LOCAL: process.env.MNEMONIC_LOCAL,
  });

  const appNameSuffix = extra.APP_VARIANT === "prod" ? "" : extra.APP_VARIANT;
  const packageNameSuffix = extra.APP_VARIANT === "prod" ? "" : `.${extra.APP_VARIANT}`;

  return {
    ...config,
    name: `McBridger${capitalize(appNameSuffix)}`,
    slug: 'bridger',
    android: {
      ...config.android,
      package: `com.mc.bridger${packageNameSuffix}`,
    },
    plugins: [
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
