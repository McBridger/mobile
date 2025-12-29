import { $ } from "bun";

async function runTest() {
  console.log("🚀 Starting McBridger E2E Mock Flow...");

  try {
    // 1. Setup Phase
    console.log("\n[Step 1] Running Setup...");
    await $`~/.maestro/bin/maestro test .maestro/steps/1_setup.yaml`;

    // 2. Discovery Simulation
    console.log("\n[Step 2] Simulating Device Discovery via ADB...");
    await $`adb shell am broadcast -a expo.modules.connector.SCAN_DEVICE -p com.mc.bridger.mock --es address "MA:ES:TR:00:23:45" --es name "Maestro-Mac"`;
    await Bun.sleep(4000); 

    // 3. Discovery Check
    console.log("\n[Step 3] Verifying Connection Status...");
    await $`~/.maestro/bin/maestro test .maestro/steps/2_discovery_check.yaml`;

    // 4. Data Simulation
    console.log("\n[Step 4] Simulating Incoming Data via ADB...");
    // HEX for {"t":0,"p":"Maestro"} - correct format for Message.Transfer (t=0 - CLIPBOARD)
    const jsonHex = Buffer.from('{"t":0,"p":"Maestro"}').toString('hex');
    await $`adb shell am broadcast -a expo.modules.connector.RECEIVE_DATA -p com.mc.bridger.mock --es address "MA:ES:TR:00:23:45" --es data "${jsonHex}"`;
    await Bun.sleep(4000);

    // 5. Data Check
    console.log("\n[Step 5] Verifying Received Data...");
    await $`~/.maestro/bin/maestro test .maestro/steps/3_data_check.yaml`;

    console.log("\n✅ E2E Test Passed Successfully!");
  } catch (error) {
    console.error("\n❌ E2E Test Failed!");
    process.exit(1);
  }
}

runTest();