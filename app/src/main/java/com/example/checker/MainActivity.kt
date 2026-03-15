package com.example.checker

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val foundData = mutableListOf<String>()
    private val observers = mutableListOf<FileObserver>()

    private val keywords = arrayOf(
        "NewLight", "Horion", "Borion", "Ahmed", "Kion", "Everlast",
        "ProtoHax", "Protohack", "Apollon", "Blaze", "ESP", "Aura",
        "KillAura", "Reach", "AutoClicker", "AutoClick", "Scaffold", "Velocity",
        "NoFall", "Speed", "Fly", "XRay", "toolbox", "innercore",
        "blocklauncher", "xposed", "lsposed", "cheat", "hack", "sigma",
        "ambrosia", "strike", "flare", "clover", "ghost", "modmenu",
        "injector", "clicker", "triggerbot", "hitbox", "Aimbot",
        "AimAssist", "AirJump", "AirStuck", "Ambience", "Ambrosial",
        "AnchorAura", "Anti-Bot", "Anti-Immobile", "Anti-Knockback",
        "Anti-Void", "Arraylist", "AutoArmor", "AutoCrystal", "AutoEat",
        "AutoLeave", "AutoLoot", "AutoPot", "AutoSneak", "AutoSprint",
        "AutoSteal", "AutoTotem", "AutoWeapon", "BedFucker", "Blink",
        "BlockReach", "BoatFly", "BowAimbot", "BowSpam", "Breadcrumbs",
        "BunnyHop", "CameraClip", "ChestESP", "ChestStealer", "ClickGui",
        "CommandHandler", "Criticals", "CrystalAura", "CustomCapes",
        "Derp", "Disabler", "DiscordRPC", "EditionFaker", "Exploit",
        "FakeLag", "FalsePack", "FastBow", "FastBreak", "FastEat",
        "FastLadder", "FastPlace", "Flarial", "Freecam", "FullBright",
        "Glide", "GodMode", "HighJump", "HiveFly", "HiveSpeed", "HoloGui",
        "InfiniteReach", "Instabreak", "InstantStop", "InventoryWalk",
        "InvMove", "ItemESP", "Jesus", "Jetpack", "Keystrokes",
        "KillauraSwitch", "KnockbackResistance", "Latite", "LongJump",
        "LowHop", "MultiAura", "NameTags", "NightVision", "NoClip",
        "NoFriends", "NoHurtCam", "NoPacket", "NoSlowDown", "NoSwing",
        "NoWeb", "Nuker", "Offhand", "Onix Client", "PacketCanceller",
        "Panic", "Phase", "PlayerESP", "Regen",
        "Script", "ServerCrasher", "ShieldBreaker", "SkyWalk", "SoftAim",
        "Spider", "SpinBot", "Step", "Strafe", "Surround",
        "SwingSpeed", "TargetHud", "Teleport", "TeleportHit", "TickBase",
        "Timer", "Tracers", "Trajectories", "TriggerBot",
        "WallHack", "WaterWalk", "Waypoints", "X-Ray", "Zephyr", "Zoom",
        ".json", ".cfg"
    )

    private val textExtensions = arrayOf(".json", ".cfg", ".txt", ".log", ".ini", ".yml")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logContainer = findViewById(R.id.logContainer)
        scrollView = findViewById(R.id.scrollView)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            if (checkPermission()) runScanner()
        }

        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            val input = EditText(this)
            input.setTextColor(0xFFFFFFFF.toInt())
            input.setHintTextColor(0xFF888888.toInt())
            input.hint = "keyword, path..."
            AlertDialog.Builder(this)
                .setTitle("Search")
                .setView(input)
                .setPositiveButton("Find") { _, _ ->
                    val query = input.text.toString().lowercase()
                    if (query.isEmpty()) return@setPositiveButton
                    val matches = mutableListOf<Int>()
                    for (i in 0 until logContainer.childCount) {
                        val tv = logContainer.getChildAt(i) as? TextView ?: continue
                        if (tv.text.toString().lowercase().contains(query)) matches.add(i)
                    }
                    if (matches.isEmpty()) return@setPositiveButton
                    var current = 0
                    fun scrollTo(index: Int) {
                        val tv = logContainer.getChildAt(matches[index]) as? TextView ?: return
                        scrollView.smoothScrollTo(0, tv.top)
                    }
                    scrollTo(0)
                    if (matches.size > 1) {
                        AlertDialog.Builder(this)
                            .setTitle("1 / ${matches.size}")
                            .setPositiveButton("Next →") { d, _ ->
                                current = (current + 1) % matches.size
                                scrollTo(current)
                                d.dismiss()
                            }
                            .setNegativeButton("Close", null)
                            .show()
                            .also { dialog ->
                                var idx = 0
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                    idx = (idx + 1) % matches.size
                                    scrollTo(idx)
                                    dialog.setTitle("${idx + 1} / ${matches.size}")
                                }
                            }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }



    }

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    private fun startObservers() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        val mask = FileObserver.CREATE or FileObserver.DELETE or
                FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.MODIFY

        val root = Environment.getExternalStorageDirectory()
        val allDirs = mutableListOf<File>()
        allDirs.add(root)
        collectDirs(root, allDirs)

        for (dir in allDirs) {
            val obs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(dir, mask) {
                    override fun onEvent(event: Int, filename: String?) =
                        handleEvent(event, filename, dir.absolutePath)
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(dir.absolutePath, mask) {
                    override fun onEvent(event: Int, filename: String?) =
                        handleEvent(event, filename, dir.absolutePath)
                }
            }
            obs.startWatching()
            observers.add(obs)
        }
    }

    private fun collectDirs(dir: File, result: MutableList<File>) {
        val children = try { dir.listFiles() } catch (e: Exception) { null } ?: return
        for (f in children) {
            if (f.isDirectory && !f.name.startsWith(".")) {
                result.add(f)
                collectDirs(f, result)
            }
        }
    }

    private fun handleEvent(event: Int, filename: String?, dir: String) {
        if (filename == null) return
        val label = when (event and FileObserver.ALL_EVENTS) {
            FileObserver.CREATE -> "[+]"
            FileObserver.DELETE -> "[-]"
            FileObserver.MOVED_FROM -> "[~] FROM"
            FileObserver.MOVED_TO -> "[~] TO"
            FileObserver.MODIFY -> "[M]"
            else -> return
        }
        appendLog("$label ${sdf.format(Date())} | $dir/$filename")
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return false
        }
        return true
    }

    private fun getprop(prop: String): String {
        return try {
            BufferedReader(InputStreamReader(Runtime.getRuntime().exec(arrayOf("getprop", prop)).inputStream))
                .readLine()?.trim() ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }

    private fun appendLog(text: String) {
        runOnUiThread {
            val tv = TextView(this)
            tv.text = text.trimEnd('\n')
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.typeface = android.graphics.Typeface.MONOSPACE
            tv.textSize = 11f
            tv.setLineSpacing(2f, 1f)
            tv.alpha = 0f
            logContainer.addView(tv)
            ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
            scrollView.post { scrollView.smoothScrollTo(0, logContainer.height) }
        }
    }

    private fun runScanner() {
        foundData.clear()
        findViewById<Button>(R.id.btnScan).isEnabled = false

        appendLog("")
        appendLog("[#] Android Checker - by mianxed, aka defozys")
        appendLog("")
        appendLog("System:")
        appendLog("Manufacturer: ${getprop("ro.product.brand").uppercase()}")
        appendLog("Device Model: ${getprop("ro.product.model")}")
        appendLog("Android Ver: ${getprop("ro.build.version.release")}")
        appendLog("Architecture: ${getprop("ro.product.cpu.abi")}")
        appendLog("Build ID: ${getprop("ro.build.display.id")}")
        appendLog("Current Time: ${sdf.format(Date())}")
        Thread {
            val limit24h = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            val storage = Environment.getExternalStorageDirectory()
            val recent = mutableListOf<Triple<Long, String, Boolean>>()

            deepSearch(storage, limit24h, recent)

            if (recent.isNotEmpty()) {
                appendLog("")
                appendLog("Check:")
                recent.sortByDescending { it.first }
                for ((time, path, match) in recent) {
                    val ts = sdf.format(Date(time))
                    if (match) appendLog("[!] $ts | SUSPECT: $path")
                    else appendLog("[*] $ts | $path")
                }
            }

            appendLog("")
            appendLog("[*] Checking Keywords...")
            appendLog("")
            appendLog("Keywords:")
            val unique = foundData.distinct().sorted()
            if (unique.isNotEmpty()) unique.forEach { appendLog(it) } else appendLog("-")

            appendLog("")
            appendLog("[*] Checking installed apps...")
            appendLog("")
            appendLog("Installed Apps:")
            val packages = packageManager.getInstalledPackages(0)

            for (pkg in packages) {
                val packageName = pkg.packageName
                val appName = pkg.applicationInfo?.loadLabel(packageManager).toString()
                val combined = "$appName $packageName".lowercase()
                var sus = false
                for (kw in keywords) {
                    if (combined.contains(kw.lowercase())) {
                        sus = true
                        break
                    }
                }
                if (sus) appendLog("[!] $appName ($packageName)")
                else appendLog("$appName ($packageName)")
            }


            runOnUiThread { findViewById<Button>(R.id.btnScan).isEnabled = true }
            startObservers()
        }.start()
    }

    private fun deepSearch(dir: File, limit: Long, recent: MutableList<Triple<Long, String, Boolean>>) {
        val files = try { dir.listFiles() } catch (e: Exception) { null } ?: return
        for (f in files) {
            val match = checkSus(f.name, f.absolutePath)
            if (f.lastModified() > limit) recent.add(Triple(f.lastModified(), f.absolutePath, match))
            if (f.isDirectory && !f.name.startsWith(".")) {
                deepSearch(f, limit, recent)
            } else if (f.isFile) {
                scanContent(f)
            }
        }
    }

    private fun checkSus(text: String, path: String): Boolean {
        val lower = text.lowercase()
        var found = false
        for (kw in keywords) {
            if (lower.contains(kw.lowercase())) {
                foundData.add("$kw | $path")
                found = true
            }
        }
        return found
    }

    private fun scanContent(file: File) {
        if (!textExtensions.any { file.name.lowercase().endsWith(it) }) return
        if (file.length() >= 1048576) return
        try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isNotEmpty()) checkSus(content, "CONTENT: ${file.absolutePath}")
        } catch (e: Exception) { }
    }
}