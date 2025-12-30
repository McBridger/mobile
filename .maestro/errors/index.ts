import { MaestroTest } from "../base";
import { Test } from "../decorators";

export class ErrorTest extends MaestroTest {
  protected name = "Error Handling";

  @Test("Mnemonic Validation")
  async testMnemonic() {
    this.log("Testing Mnemonic Validation...");
    await this.runFlow(import.meta.resolve("./1_mnemonic_validation.yaml"));
  }

  @Test("Invalid Data Handling")
  async testInvalidData() {
    await this.asConnected();

    this.log("Testing Malformed HEX Data...");
    await this.simulateRawData(this.mockMac.address, "ABCDE");

    this.log("Testing Expired Timestamp...");
    const expiredTs = (Date.now() / 1000) - 600;
    await this.simulateData(this.mockMac.address, 0, "Expired", expiredTs);

    this.log("Verifying App State...");
    await this.runFlow(import.meta.resolve("./2_invalid_data.yaml"));
  }
}