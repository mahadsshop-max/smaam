const { app, BrowserWindow, session } = require("electron");
const path = require("path");
app.whenReady().then(() => {
  // allow camera and microphone for alarm proof videos
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(perm === "media"));
  const win = new BrowserWindow({ width: 480, height: 860, minWidth: 360, backgroundColor: "#e8dfcc",
    webPreferences: { autoplayPolicy: "no-user-gesture-required" } });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, "../www/index.html"));
});
app.on("window-all-closed", () => app.quit());
