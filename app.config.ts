import "tsx/cjs";

import { ConfigContext, ExpoConfig } from "expo/config";
import { z } from "zod";

const Extra = z.object({
  BRIDGER_SERVICE_UUID: z.uuidv4(),
  CHARACTERISTIC_UUID: z.uuidv4(),
  ADVERTISE_UUID: z.string(),
});

export default ({ config }: ConfigContext): AppConfig => {
  const extra = Extra.parse({
    BRIDGER_SERVICE_UUID: process.env.BRIDGER_SERVICE_UUID,
    CHARACTERISTIC_UUID: process.env.CHARACTERISTIC_UUID,
    ADVERTISE_UUID: process.env.ADVERTISE_UUID,
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
