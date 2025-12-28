import 'tsx/cjs';
import { ConfigContext, ExpoConfig } from "expo/config";
import { capitalize } from "lodash";
import { z } from "zod";

const Extra = z.object({
  APP_VARIANT: z.enum(["dev", "preview", "prod", "mock"]),
  ENCRYPTION_SALT: z.string(),
  MNEMONIC_LENGTH: z.number().default(6),
  MNEMONIC_LOCAL: z.string().optional(),
  eas: z.object({
    projectId: z.uuidv4(),
  }),
});

export default ({ config }: ConfigContext): AppConfig => {
  const extra = Extra.parse({
    ...config.extra,
    APP_VARIANT: process.env.APP_VARIANT,
    ENCRYPTION_SALT: process.env.ENCRYPTION_SALT,
    MNEMONIC_LOCAL: process.env.MNEMONIC_LOCAL,
    MNEMONIC_LENGTH: process.env.MNEMONIC_LENGTH ? parseInt(process.env.MNEMONIC_LENGTH) : undefined,
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
