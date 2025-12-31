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
}