import { resolve } from "path";
import { MaestroTest } from "../base";
import { Test } from "../decorators";

export class FileTest extends MaestroTest {
  protected name = "File Transfer Operations";

  @Test("Receive and Download File (Binary)")
  async testFileTransfer() {
    await this.cleanupDownloads();
    await this.asConnected();

    const localFile = resolve(import.meta.dirname, "test.txt");
    const content = Buffer.from(await Bun.file(localFile).arrayBuffer());

    this.log("Simulating Full Binary File Transfer...");
    await this.simulateFileTransfer(
      this.mockMac.address,
      "maestro_test.txt",
      content,
    );

    this.log("Verifying file arrival in UI...");
    await this.runFlow(import.meta.resolve("./2_verify_file.yaml"));
  }

  @Test("Send and Host File (Binary)")
  async testSendFile() {
    await this.asConnected();

    // 1. Start TCP Server on Host and tell the App about it
    const { server, waitForFrame } = await this.startTestServer(49153);

    // Android Emulator host address is 10.0.2.2
    await this.simulateIntro(this.mockMac.address, "10.0.2.2", 49153);

    const localFile = resolve(import.meta.dirname, "test.txt");
    const internalPath = await this.pushFile(localFile, "maestro_send.txt");

    this.log("Triggering file share from App...");
    const blobPromise = waitForFrame(2); // Wait for BLOB announcement

    await this.shareFile(`file://${internalPath}`);

    const blob = await blobPromise;
    this.log(`Successfully intercepted BLOB: ${blob.name}`);

    if (blob.name !== "maestro_send.txt") {
      throw new Error(
        `Filename mismatch: expected maestro_send.txt, got ${blob.name}`,
      );
    }

    this.log("Verifying 'Sent' UI card...");
    await this.runFlow(import.meta.resolve("./3_verify_sent.yaml"));

    server.close();
    this.log("✅ End-to-End Binary File Hosting verified.");
  }
}
