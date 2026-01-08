import { MaestroTest } from "../base";
import { Test } from "../decorators";

export class BasicTest extends MaestroTest {
  protected name = "Happy Path";

  @Test("Complete Sync Flow")
  async testSync() {
    await this.asConnected();

    this.log("Verifying Connection UI...");
    await this.runFlow(import.meta.resolve("./2_discovery_check.yaml"));

    this.log("Simulating Incoming Data...");
    await this.simulateData(this.mockMac.address, 0, "Maestro");

    this.log("Verifying Data & Clipboard...");
    await this.runFlow(import.meta.resolve("./3_data_check.yaml"));
  }

  @Test("History Persistence After Restart (2 items limit)")
  async testPersistence() {
    await this.asConnected();
    
    this.log("Sending 5 messages...");
    for (let i = 0; i < 5; i++) {
      await this.simulateData(this.mockMac.address, 0, `Message ${i + 1}`);
    }
    
    this.log("Restarting app and verifying history...");
    await this.runFlow(import.meta.resolve("./4_persistence.yaml"));
  }
}