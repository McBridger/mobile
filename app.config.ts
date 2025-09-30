import { ConfigContext, ExpoConfig } from "expo/config";
import { z } from "zod";

const Extra = z.object({
  BRIDGER_SERVICE_UUID: z.uuidv4(),
  CHARACTERISTIC_UUID: z.uuidv4(),
  ADVERTISE_UUID: z.string(),
  APP_VARIANT: z.enum(["development", "preview", "production"]).optional(),
  eas: z.object({
    projectId: z.uuidv4(),
  }),
});

export default ({ config }: ConfigContext): AppConfig => {
  const extra = Extra.parse({
    ...config.extra,
    BRIDGER_SERVICE_UUID: process.env.BRIDGER_SERVICE_UUID,
    CHARACTERISTIC_UUID: process.env.CHARACTERISTIC_UUID,
    ADVERTISE_UUID: process.env.ADVERTISE_UUID,
    APP_VARIANT: process.env.APP_VARIANT,
  });

  return {
    ...config,
    name: extra.APP_VARIANT === "production" ? "McBridger" : "McBridgerDev",
    slug: 'mc-bridger',

    extra,
  };
};

export interface AppConfig extends ExpoConfig {
  extra: z.infer<typeof Extra>;
}
