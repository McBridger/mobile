import { ConfigPlugin, withAndroidManifest } from "@expo/config-plugins";

const withManifestMetadata: ConfigPlugin<{ [key: string]: string | boolean }> = (config, metadata) => {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults;
    const mainApplication = androidManifest.manifest.application?.[0];

    if (mainApplication) {
      const existingMetadata = mainApplication["meta-data"] || [];
      
      // Remove existing keys to avoid duplicates
      const filteredMetadata = existingMetadata.filter(
        (item: any) => !Object.keys(metadata).includes(item.$["android:name"])
      );

      // Add new metadata
      const newMetadata = Object.entries(metadata).map(([name, value]) => ({
        $: {
          "android:name": name,
          "android:value": value.toString(),
        },
      }));

      mainApplication["meta-data"] = [...filteredMetadata, ...newMetadata];
    }

    return config;
  });
};

export default withManifestMetadata;
