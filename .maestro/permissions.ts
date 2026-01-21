import { $ } from "bun";
import { APP_ID } from "./constants";


/**
 * Automatically grants required Android permissions via ADB.
 * Version-aware: skips permissions that don't exist on older API levels.
 */
export async function grantPermissions() {
  console.log("🔓 Granting permissions via ADB...");

  const sdkVersionStr = await $`adb shell getprop ro.build.version.sdk`.text();
  const sdkVersion = parseInt(sdkVersionStr.trim(), 10);
  console.log(`  📱 Detected Android API Level: ${sdkVersion}`);

  const permissions = ["android.permission.ACCESS_FINE_LOCATION"];

  if (sdkVersion >= 31) {
    permissions.push("android.permission.BLUETOOTH_SCAN");
    permissions.push("android.permission.BLUETOOTH_CONNECT");
  }

  if (sdkVersion >= 33) {
    permissions.push("android.permission.POST_NOTIFICATIONS");
  }

  for (const permission of permissions) {
    console.log(`  - ${permission}`);
    try {
      await $`adb shell pm grant ${APP_ID} ${permission}`;
    } catch (error) {
      console.warn(`  ⚠️  Failed to grant ${permission}:`, error instanceof Error ? error.message : error);
    }
  }
}