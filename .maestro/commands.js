output.address = "MA:ES:TR:O1:23:45";
output.name = "Maestro Mac";
// Maestro will execute this JS, but it can't run shell directly.
// Wait, I can use the `exec` from Maestro's JS engine if available, 
// OR I can use the `runScript`'s ability to call external commands.
