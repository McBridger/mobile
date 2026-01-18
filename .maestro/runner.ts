import { BasicTest } from "./basic";
import { ErrorTest } from "./errors";
import { FileTest } from "./files";
import { grantPermissions } from "./permissions";

async function runTest() {
  try {
    // Ensure app has all required permissions before starting tests
    await grantPermissions();

    // Run Happy Path
    await new BasicTest().run();

    // Run File Transfers
    await new FileTest().run();

    // Run Error Cases
    await new ErrorTest().run();

    console.log("\n✅ All E2E Tests Passed Successfully!");
  } catch (error) {
    console.error("\n❌ E2E Test Suite Failed!");
    process.exit(1);
  }
}

runTest();
