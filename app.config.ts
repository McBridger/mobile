import "tsx/cjs";

import { ConfigContext, ExpoConfig } from "expo/config";
import { z } from "zod";

const Extra = z.object({
  BRIDGER_SERVICE_UUID: z.uuidv4(),
  WRITE_CHARACTERISTIC_UUID: z.uuidv4(),
  NOTIFY_CHARACTERISTIC_UUID: z.uuidv4(),
});

export default ({ config }: ConfigContext): AppConfig => {
  const extra = Extra.parse({
    BRIDGER_SERVICE_UUID: process.env.BRIDGER_SERVICE_UUID,
    WRITE_CHARACTERISTIC_UUID: process.env.ANDROID_TO_MAC_CHARACTERISTIC_UUID,
    NOTIFY_CHARACTERISTIC_UUID: process.env.MAC_TO_ANDROID_CHARACTERISTIC_UUID,
  });

  return {
    ...config,
    name: "rn-bridger",
    slug: "rn-bridger",

    extra,
  };
};

export interface AppConfig extends ExpoConfig {
  extra: z.infer<typeof Extra>;
}
