import { $ } from "bun";
import { APP_ID } from "./constants";
import { testRegistry } from "./decorators";
import { connect, createServer, Socket } from "node:net";
import { randomUUID } from "node:crypto";

export enum AdbEvent {
  SCAN_DEVICE = "expo.modules.connector.SCAN_DEVICE",
  RECEIVE_DATA = "expo.modules.connector.RECEIVE_DATA",
}

/**
 * Binary Encoder matching Kotlin's [1b Type][16b UUID][8b Timestamp][Payload]
 */
class BinaryEncoder {
  private chunks: Buffer[] = [];

  writeByte(v: number) { this.chunks.push(Buffer.from([v])); return this; }
  
  writeUUID(uuid: string) {
    const hex = uuid.replace(/-/g, "");
    this.chunks.push(Buffer.from(hex, "hex"));
    return this;
  }

  writeDouble(v: number) {
    const buf = Buffer.alloc(8);
    buf.writeDoubleBE(v);
    this.chunks.push(buf);
    return this;
  }

  writeLong(v: number | bigint) {
    const buf = Buffer.alloc(8);
    buf.writeBigInt64BE(BigInt(v));
    this.chunks.push(buf);
    return this;
  }

  writeInt(v: number) {
    const buf = Buffer.alloc(4);
    buf.writeInt32BE(v);
    this.chunks.push(buf);
    return this;
  }

  writeString(s: string) {
    const bytes = Buffer.from(s, "utf-8");
    this.writeInt(bytes.length);
    this.chunks.push(bytes);
    return this;
  }

  writeRaw(data: Buffer | Uint8Array) {
    this.chunks.push(Buffer.from(data));
    return this;
  }

  build(): Buffer {
    return Buffer.concat(this.chunks);
  }
}

export abstract class MaestroTest {
  protected abstract name: string;
  protected maestroBin = process.env.MAESTRO_PATH || "maestro";
  protected appId = APP_ID;

  protected mockMac = {
    address: "MA:ES:TR:00:23:45",
    name: "Maestro-Mac",
  };

  async run() {
    console.log(`\n--- 📦 Suite: ${this.name} ---`);
    const allTests = testRegistry.get(this.constructor) || [];
    const testsToRun = allTests.some(t => t.options.only)
      ? allTests.filter(t => t.options.only)
      : allTests.filter(t => !t.options.skip);

    for (const test of testsToRun) {
      this.log(`Test: ${test.name}`);
      try {
        await (this as any)[test.methodName]();
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

  protected async asConnected() {
    this.log("Fixture: Setting up Connection...");
    await this.runFlow(import.meta.resolve("./basic/1_setup.yaml"));
    await this.simulateScan(this.mockMac);
  }

  protected async broadcast(event: AdbEvent, extras: Record<string, string>) {
    const extraArgs = Object.entries(extras)
      .map(([key, value]) => `--es ${key} "${value}"`)
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
    const uuid = randomUUID();
    const hex = new BinaryEncoder().writeByte(type).writeUUID(uuid).writeDouble(ts).writeString(payload).build().toString("hex");
    await this.simulateRawData(address, hex);
  }

  protected async simulateRawData(address: string, hexData: string) {
    await this.broadcast(AdbEvent.RECEIVE_DATA, { address, data: hexData });
    await Bun.sleep(1000);
  }

  /**
   * Simulate full binary file transfer: Announcement + Chunks
   */
  protected async simulateFileTransfer(address: string, name: string, content: Buffer) {
    const ts = Date.now() / 1000;
    const blobId = randomUUID();

    // 1. Send BLOB Announcement
    const announcement = new BinaryEncoder()
      .writeByte(2) // BLOB
      .writeUUID(blobId)
      .writeDouble(ts)
      .writeString(name)
      .writeLong(content.length)
      .writeString("FILE")
      .build();
    
    this.log(`Simulating file announcement: ${name} (${content.length} bytes)`);
    await this.simulateRawData(address, announcement.toString("hex"));

    // 2. Send Chunks
    const chunkSize = 16 * 1024; // Small chunks for ADB stability
    for (let offset = 0; offset < content.length; offset += chunkSize) {
      const end = Math.min(offset + chunkSize, content.length);
      const chunkData = content.slice(offset, end);
      
      const chunkMsg = new BinaryEncoder()
        .writeByte(3) // CHUNK
        .writeUUID(blobId) // id is blobId
        .writeDouble(0)    // timestamp 0 for chunks
        .writeLong(offset)
        .writeRaw(chunkData)
        .build();
      
      this.log(`  Sending chunk ${offset}/${content.length}`);
      await this.simulateRawData(address, chunkMsg.toString("hex"));
    }
  }

  protected async openNotifications() {
    await $`adb shell cmd statusbar expand-notifications`;
    await Bun.sleep(500);
  }

  protected async cleanupDownloads() {
    await $`adb shell rm -rf /storage/emulated/0/Download/McBridger`;
  }

  protected async openDownloadsUI() {
    await $`adb shell am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary:Download"`;
    await Bun.sleep(2000);
  }

  protected async pushFile(localPath: string, fileName: string): Promise<string> {
    const tmpPath = `/data/local/tmp/${fileName}`;
    const internalPath = `/data/data/${this.appId}/cache/${fileName}`;
    await $`adb push ${localPath} ${tmpPath}`;
    await $`adb shell run-as ${this.appId} cp ${tmpPath} ${internalPath}`;
    await $`adb shell run-as ${this.appId} chmod 666 ${internalPath}`;
    await $`adb shell rm ${tmpPath}`;
    return internalPath;
  }

  protected async shareFile(contentUri: string) {
    await $`adb shell am start -a android.intent.action.VIEW -d "${contentUri}" -n ${this.appId}/expo.modules.connector.ui.ClipboardActivity`;
    await Bun.sleep(2000);
  }

  /**
   * Starts a TCP server to catch outgoing files from the app.
   */
  protected async startTestServer(port = 49153): Promise<{ server: any, waitForFrame: (type: number) => Promise<any> }> {
    this.log(`Forwarding host port ${port} to device port ${port}...`);
    await $`adb reverse tcp:${port} tcp:${port}`;
    
    let resolveFrame: (data: any) => void;
    let targetType = -1;

    const server = createServer((socket) => {
      this.log("App connected to our test server!");
      let buffer = Buffer.alloc(0);
      
      socket.on("data", (chunk) => {
        buffer = Buffer.concat([buffer, chunk]);
        while (buffer.length >= 4) {
          const len = buffer.readInt32BE(0);
          if (buffer.length < len + 4) break;
          const frame = buffer.slice(4, len + 4);
          buffer = buffer.slice(len + 4);

          const type = frame[0];
          if (type === targetType && resolveFrame) {
            if (type === 2) { // BLOB
               const nameLen = frame.readInt32BE(25);
               const name = frame.slice(29, 29 + nameLen).toString("utf-8");
               resolveFrame({ name, type });
            }
          }
        }
      });
    }).listen(port);

    return {
      server,
      waitForFrame: (type: number) => {
        targetType = type;
        return new Promise(res => { resolveFrame = res; });
      }
    };
  }

  protected async simulateIntro(address: string, ip: string, port: number) {
    const encoder = new BinaryEncoder()
      .writeByte(1) // INTRO
      .writeUUID(randomUUID())
      .writeDouble(Date.now() / 1000)
      .writeString("Maestro-Mac")
      .writeString(ip)
      .writeInt(port);
    
    await this.simulateRawData(address, encoder.build().toString("hex"));
  }
}
