import "tsx/cjs";

import { FormatRegistry, Static, Type } from "@sinclair/typebox";
import { TypeCompiler } from "@sinclair/typebox/compiler";
import { ConfigContext, ExpoConfig } from "expo/config";
import { validate as isUuid } from "uuid";

FormatRegistry.Set("uuid", (value) => isUuid(value));

const Extra = Type.Object({
  BRIDGER_SERVICE_UUID: Type.String({ format: "uuid" }),
  WRITE_CHARACTERISTIC_UUID: Type.String({ format: "uuid" }),
  NOTIFY_CHARACTERISTIC_UUID: Type.String({ format: "uuid" }),
});
const TExtra = TypeCompiler.Compile(Extra);

export default ({ config }: ConfigContext): AppConfig => {
  const extra = {
    BRIDGER_SERVICE_UUID: process.env.BRIDGER_SERVICE_UUID,
    WRITE_CHARACTERISTIC_UUID: process.env.ANDROID_TO_MAC_CHARACTERISTIC_UUID,
    NOTIFY_CHARACTERISTIC_UUID: process.env.MAC_TO_ANDROID_CHARACTERISTIC_UUID,
  };

  if (!TExtra.Check(extra)) {
    console.error([...TExtra.Errors(extra)]);
    throw new Error("Invalid extra config");
  }

  return {
    ...config,
    name: "rn-bridger",
    slug: "rn-bridger",

    extra,
  };
};

export interface AppConfig extends ExpoConfig {
  extra: Static<typeof Extra>;
}
