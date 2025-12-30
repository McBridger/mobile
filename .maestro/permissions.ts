import { $ } from "bun";
import { APP_ID } from "./constants";

export const PERMISSIONS = [
  "android.permission.BLUETOOTH_SCAN",
  "android.permission.BLUETOOTH_CONNECT",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.POST_NOTIFICATIONS",
];

/**
 * Automatically grants required Android permissions via ADB.
 * Essential for CI environments where the emulator is clean.
 */
export async function grantPermissions() {
  console.log("🔓 Granting permissions via ADB...");
  for (const permission of PERMISSIONS) {
    console.log(`  - ${permission}`);
    try {
      await $`adb shell pm grant ${APP_ID} ${permission}`;
    } catch (error) {
      console.warn(`  ⚠️  Failed to grant ${permission}:`, error instanceof Error ? error.message : error);
    }
  }
}
