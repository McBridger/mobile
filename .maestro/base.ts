import { $ } from "bun";
import { testRegistry } from "./decorators";
import { APP_ID } from "./constants";

export enum AdbEvent {
  SCAN_DEVICE = "expo.modules.connector.SCAN_DEVICE",
  RECEIVE_DATA = "expo.modules.connector.RECEIVE_DATA",
}

export abstract class MaestroTest {
  protected abstract name: string;
  protected maestroBin = process.env.MAESTRO_PATH || "maestro";
  protected appId = "com.mc.bridger.e2e";
  
  protected mockMac = {
    address: "MA:ES:TR:00:23:45",
    name: "Maestro-Mac",
  };

  /**
   * Main entry point that executes all registered @Test methods
   */
  async run() {
    console.log(`\n--- 📦 Suite: ${this.name} ---`);
    
    const allTests = testRegistry.get(this.constructor) || [];
    if (allTests.length === 0) {
      console.log("  ⚠️ No tests found in this suite.");
      return;
    }

    const hasOnly = allTests.some(t => t.options.only);
    const testsToRun = hasOnly 
      ? allTests.filter(t => t.options.only) 
      : allTests.filter(t => !t.options.skip);

    for (const test of testsToRun) {
      this.log(`Test: ${test.name}`);
      try {
        // @ts-ignore - calling dynamic method
        await this[test.methodName]();
        console.log(`  ✅ Passed`);
      } catch (e) {
        console.log(`  ❌ Failed: ${e}`);
        throw e;
      }
    }
  }

  protected log(message: string) {
    console.log(`  [${this.name}] ${message}`);
  }

  protected async runFlow(path: string) {
    const localPath = path.startsWith("file://") ? path.slice(7) : path;
    await $`${this.maestroBin} test ${localPath}`;
  }

  /**
   * FIXTURE: Brings the app to a "Connected" state.
   */
  protected async asConnected() {
    this.log("Fixture: Setting up Connection...");
    await this.runFlow(import.meta.resolve("./basic/1_setup.yaml"));
    await this.simulateScan(this.mockMac);
  }

  protected async broadcast(event: AdbEvent, extras: Record<string, string>) {
    const extraArgs = Object.entries(extras)
      .map(([key, value]) => `--es ${key} "${value}"`) // Note: escaped quotes here are intentional for shell command
      .join(" ");
    
    await $`adb shell am broadcast -a ${event} -p ${APP_ID} ${extraArgs}`;
  }

  protected async simulateScan(payload: { address: string; name: string }) {
    await this.broadcast(AdbEvent.SCAN_DEVICE, {
      address: payload.address,
      name: payload.name,
    });
    await Bun.sleep(1000);
  }

  protected async simulateData(address: string, type: number, payload: string, timestamp?: number) {
    const ts = timestamp ?? Date.now() / 1000;
    const jsonStr = JSON.stringify({ t: type, p: payload, ts });
    const hex = Buffer.from(jsonStr).toString("hex");
    
    await this.broadcast(AdbEvent.RECEIVE_DATA, {
      address,
      data: hex,
    });
    await Bun.sleep(1000);
  }

  protected async simulateRawData(address: string, hexData: string) {
    await this.broadcast(AdbEvent.RECEIVE_DATA, {
      address,
      data: hexData,
    });
    await Bun.sleep(1000);
  }
}
