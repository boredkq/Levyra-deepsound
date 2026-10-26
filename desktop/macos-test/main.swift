import Cocoa
import WebKit

class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    var window: NSWindow!
    var webView: WKWebView!

    func applicationDidFinishLaunching(_ notification: Notification) {
        let app = NSApplication.shared
        app.setActivationPolicy(.regular)

        let rect = NSRect(x: 0, y: 0, width: 1440, height: 920)
        window = NSWindow(
            contentRect: rect,
            styleMask: [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        window.center()
        window.title = "Levyra Desktop"
        window.titleVisibility = .hidden
        window.titlebarAppearsTransparent = true
        window.backgroundColor = NSColor(red: 5/255, green: 7/255, blue: 12/255, alpha: 1.0)
        window.isMovableByWindowBackground = true
        window.delegate = self
        window.minSize = NSSize(width: 1024, height: 680)

        let config = WKWebViewConfiguration()
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        
        webView = WKWebView(frame: rect, configuration: config)
        webView.autoresizingMask = [.width, .height]
        webView.setValue(false, forKey: "drawsBackground")

        window.contentView = webView

        setupMenus()
        setupShortcuts()

        // Load index.html from bundle resources or local directory
        var htmlUrl = Bundle.main.url(forResource: "index", withExtension: "html")
        if htmlUrl == nil {
            let bundleDir = Bundle.main.bundleURL
            let candidate = bundleDir.appendingPathComponent("Contents/Resources/index.html")
            if FileManager.default.fileExists(atPath: candidate.path) {
                htmlUrl = candidate
            } else {
                let currentDir = FileManager.default.currentDirectoryPath
                htmlUrl = URL(fileURLWithPath: currentDir).appendingPathComponent("index.html")
            }
        }
        
        if let url = htmlUrl, FileManager.default.fileExists(atPath: url.path) {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }

        window.makeKeyAndOrderFront(nil)
        app.activate(ignoringOtherApps: true)
    }

    func setupShortcuts() {
        NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            guard let self = self else { return event }
            
            // Space for Play/Pause (when not typing in an input)
            if event.keyCode == 49 && event.modifierFlags.intersection(.deviceIndependentFlagsMask).isEmpty {
                self.webView.evaluateJavaScript("if (document.activeElement.tagName !== 'INPUT') { togglePlayState(); }")
                return nil
            }
            
            // Cmd + K for Search
            if event.modifierFlags.contains(.command) && event.charactersIgnoringModifiers == "k" {
                self.webView.evaluateJavaScript("switchView('home'); document.querySelector('input')?.focus();")
                return nil
            }

            // Cmd + L for Lyrics
            if event.modifierFlags.contains(.command) && event.charactersIgnoringModifiers == "l" {
                self.webView.evaluateJavaScript("toggleLyricsPanel()")
                return nil
            }

            return event
        }
    }

    func setupMenus() {
        let mainMenu = NSMenu()
        let appMenuItem = NSMenuItem()
        mainMenu.addItem(appMenuItem)
        
        let appMenu = NSMenu()
        appMenu.addItem(withTitle: "О программе Levyra Desktop", action: #selector(NSApplication.orderFrontStandardAboutPanel(_:)), keyEquivalent: "")
        appMenu.addItem(NSMenuItem.separator())
        appMenu.addItem(withTitle: "Скрыть Levyra", action: #selector(NSApplication.hide(_:)), keyEquivalent: "h")
        appMenu.addItem(withTitle: "Скрыть остальные", action: #selector(NSApplication.hideOtherApplications(_:)), keyEquivalent: "h").keyEquivalentModifierMask = [.command, .option]
        appMenu.addItem(withTitle: "Показать все", action: #selector(NSApplication.unhideAllApplications(_:)), keyEquivalent: "")
        appMenu.addItem(NSMenuItem.separator())
        appMenu.addItem(withTitle: "Завершить Levyra", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        appMenuItem.submenu = appMenu

        // Controls Menu
        let controlsMenuItem = NSMenuItem()
        mainMenu.addItem(controlsMenuItem)
        let controlsMenu = NSMenu(title: "Воспроизведение")
        controlsMenu.addItem(withTitle: "Воспроизведение / Пауза", action: #selector(togglePlayMenu), keyEquivalent: " ")
        controlsMenuItem.submenu = controlsMenu

        NSApplication.shared.mainMenu = mainMenu
    }

    @objc func togglePlayMenu() {
        webView.evaluateJavaScript("togglePlayState()")
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
