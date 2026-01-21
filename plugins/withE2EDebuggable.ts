import { ConfigPlugin, withAndroidManifest } from "@expo/config-plugins";

/**
 * Ensures android:debuggable="true" is set in the AndroidManifest.xml
 * only when specifically requested (e.g., for E2E testing).
 */
const withE2EDebuggable: ConfigPlugin<{ enabled: boolean }> = (config, { enabled }) => {
    return withAndroidManifest(config, (config) => {
        const androidManifest = config.modResults;
        const mainApplication = androidManifest.manifest.application?.[0];

        if (mainApplication && enabled) {
            androidManifest.manifest.$["xmlns:tools"] = "http://schemas.android.com/tools";

            mainApplication.$["android:debuggable"] = "true";
            mainApplication.$["tools:ignore"] = "HardcodedDebugMode";
        }

        return config;
    });
};

export default withE2EDebuggable;
