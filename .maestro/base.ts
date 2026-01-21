import { $ } from "bun";
import { APP_ID } from "./constants";
import { testRegistry } from "./decorators";

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

  protected async simulateFile(address: string, name: string, url: string, size: string) {
    const ts = Date.now() / 1000;
    // Android FileMessage: t=2, u=url, n=name, s=size
    const jsonStr = JSON.stringify({ t: 2, u: url, n: name, s: size, ts });
    const hex = Buffer.from(jsonStr).toString("hex");

    await this.broadcast(AdbEvent.RECEIVE_DATA, { address, data: hex });
    await Bun.sleep(1000);
  }

  protected async openNotifications() {
    await $`adb shell cmd statusbar expand-notifications`;
    await Bun.sleep(500);
  }

  protected async cleanupDownloads() {
    this.log("Cleaning up Downloads/McBridger folder...");
    await $`adb shell rm -rf /storage/emulated/0/Download/McBridger`;
  }

  protected async openDownloadsUI() {
    this.log("Opening Downloads UI...");

    // Intent to open the built-in Android Files/Downloads explorer specifically at Download folder
    await $`adb shell am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary:Download"`;
    await Bun.sleep(2000); // Give it some time to load the UI
  }

  protected async pushFile(localPath: string, fileName: string): Promise<string> {
    const tmpPath = `/data/local/tmp/${fileName}`;
    const internalPath = `/data/data/${this.appId}/cache/${fileName}`;

    this.log(`Injecting ${localPath} into app internal storage...`);

    // 1. Push to common temp area accessible by shell
    await $`adb push ${localPath} ${tmpPath}`;

    // 2. Use run-as to copy it into the app's private area (effectively changing owner/permissions)
    await $`adb shell run-as ${this.appId} cp ${tmpPath} ${internalPath}`;
    await $`adb shell run-as ${this.appId} chmod 666 ${internalPath}`;
    await $`adb shell rm ${tmpPath}`;

    return internalPath;
  }

  protected async shareFile(contentUri: string) {
    this.log(`Sharing file via Intent: ${contentUri}`);
    // Launch ClipboardActivity with DATA uri
    await $`adb shell am start -a android.intent.action.VIEW -d "${contentUri}" -n ${this.appId}/expo.modules.connector.ui.ClipboardActivity`;
    await Bun.sleep(2000); // Give it a bit more time for the server to spin up
  }

  protected async setupForwarding(port: number) {
    this.log(`Forwarding host port ${port} to device port ${port}...`);
    await $`adb forward tcp:${port} tcp:${port}`;
  }

  /**
   * ESTABLISH REAL BRIDGE: Connect to the Android TCP Server
   */
  protected async connectToBridge(port = 49152): Promise<WebSocket> {
    await this.setupForwarding(port);
    this.log(`Connecting to McBridger WebSocket at 127.0.0.1:${port}...`);

    return new Promise((resolve, reject) => {
      const ws = new WebSocket(`ws://127.0.0.1:${port}/bridge`);
      ws.onopen = () => {
        this.log("Bridge connected!");
        resolve(ws);
      };
      ws.onerror = (e) => reject(new Error(`WS Connection failed: ${e}`));
    });
  }

  protected async waitForFileMessage(ws: WebSocket, timeout = 5000): Promise<any> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Timeout waiting for FileMessage")), timeout);

      ws.onmessage = async (event) => {
        try {
          let dataStr: string;

          if (typeof event.data === "string") {
            dataStr = event.data;
          } else if (event.data instanceof Blob) {
            dataStr = await event.data.text();
          } else {
            // Probably ArrayBuffer
            dataStr = new TextDecoder().decode(event.data);
          }

          this.log(`Received message: ${dataStr.substring(0, 100)}...`);
          const msg = JSON.parse(dataStr);

          if (msg.t === 2 && msg.u) { // MessageType.FILE_URL
            clearTimeout(timer);
            resolve({
              url: msg.u,
              name: msg.n,
              size: msg.s
            });
          }
        } catch (e) {
          this.log(`Failed to parse bridge message: ${e}`);
        }
      };
    });
  }
}
