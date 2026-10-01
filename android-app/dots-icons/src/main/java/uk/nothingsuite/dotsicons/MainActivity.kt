package uk.nothingsuite.dotsicons

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.nothingsuite.design.NothingTheme
import uk.nothingsuite.design.NothingTheme.colors
import uk.nothingsuite.design.NothingTheme.typography
import uk.nothingsuite.design.components.DotMatrixText
import uk.nothingsuite.design.components.NothingButton
import uk.nothingsuite.design.components.NothingButtonStyle
import uk.nothingsuite.design.components.NothingCard

/**
 * The pack's own screen: a preview, and the shortest route to applying it in
 * whichever launcher is installed. Launchers read the icons straight from
 * this app's resources; nothing here runs in the background.
 */
class MainActivity : ComponentActivity() {

    private data class Launcher(val name: String, val pkg: String, val how: String, val apply: (() -> Intent)?)

    private val launchers = listOf(
        Launcher("Nothing Launcher", "com.nothing.launcher",
            "Long-press the home screen → Customisation → Icon pack → Dots Icons.",
            { Intent(Settings.ACTION_HOME_SETTINGS) }),
        Launcher("Nova Launcher", "com.teslacoilsw.launcher", "Nova Settings → Look & feel → Icon style → Icon theme → Dots Icons.",
            { Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME").setPackage("com.teslacoilsw.launcher").putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO").putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", packageName) }),
        Launcher("Lawnchair", "app.lawnchair", "Lawnchair settings → General → Icon pack → Dots Icons.", null),
        Launcher("Niagara Launcher", "bitpit.launcher", "Niagara settings → Look → Icon pack → Dots Icons.", null),
        Launcher("Smart Launcher", "ginlemon.flowerfree", "Settings → Global appearance → Icon pack → Dots Icons.", null),
    )

    private fun installed(pkg: String) = runCatching { packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** The launcher actually in charge of the home screen right now. */
    private fun currentHome(): Pair<String, String>? {
        val info = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = info.activityInfo?.packageName ?: return null
        return pkg to info.loadLabel(packageManager).toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = currentHome()
        val present = launchers.filter { installed(it.pkg) || it.pkg == home?.first }.toMutableList()
        // A Nothing phone whose launcher we didn't recognise by package name: still show the Nothing steps.
        if (present.none { it.pkg == home?.first } && home != null && (home.first.contains("nothing", true) || android.os.Build.MANUFACTURER.equals("Nothing", true)))
            present.add(0, launchers[0].copy(pkg = home.first))

        setContent {
            NothingTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    DotMatrixText("DOTS ICONS", size = 28)
                    Text("Dot-matrix icons for every app. Black tile, white dots.", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(20.dp))

                    // Preview: a few rows of the pack
                    val preview = listOf(
                        R.drawable.p_phone, R.drawable.p_message, R.drawable.p_camera, R.drawable.p_browser, R.drawable.p_mail,
                        R.drawable.p_music, R.drawable.p_map, R.drawable.p_settings, R.drawable.p_bank, R.drawable.p_parcel,
                        R.drawable.p_food, R.drawable.p_train, R.drawable.p_weather, R.drawable.p_calendar, R.drawable.p_clock,
                        R.drawable.l_a, R.drawable.l_g, R.drawable.l_w, R.drawable.l_x, R.drawable.l_7,
                    )
                    preview.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { Image(painterResource(it), null, Modifier.size(56.dp)) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("42 pictograms, 40 letters, 500+ apps mapped. Apps we don't know get a black tile with their own icon inside.", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(20.dp))

                    DotMatrixText("APPLY", size = 14)
                    Spacer(Modifier.height(4.dp))
                    Text("Current home screen: ${home?.second ?: "unknown"} (${home?.first ?: "?"})", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(8.dp))
                    if (present.isEmpty()) {
                        NothingCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("No launcher with icon-pack support found. Nothing Launcher, Nova, Lawnchair and Niagara all work — install one, then come back here.", style = typography.body)
                                Spacer(Modifier.height(10.dp))
                                NothingButton("HOME SCREEN SETTINGS", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
                            }
                        }
                    }
                    present.forEach { l ->
                        NothingCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                DotMatrixText(l.name.uppercase(), size = 14)
                                Spacer(Modifier.height(6.dp))
                                Text(l.how, style = typography.caption, color = colors.onBackgroundMuted)
                                Spacer(Modifier.height(10.dp))
                                NothingButton(if (l.apply != null) "OPEN ${l.name.uppercase()}" else "OPEN LAUNCHER", NothingButtonStyle.Accent, Modifier.fillMaxWidth()) {
                                    val i = l.apply?.invoke() ?: packageManager.getLaunchIntentForPackage(l.pkg)
                                    if (i != null) open(i)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                    DotMatrixText("NOT CHANGING?", size = 14)
                    Spacer(Modifier.height(6.dp))
                    Text("Launchers match icons on each app's exact internal name. Export the list from this phone and send it to us; we add exact matches for every app on it.", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(8.dp))
                    NothingButton("EXPORT APP LIST", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) { exportAppList() }
                    Spacer(Modifier.height(16.dp))
                    DotMatrixText("MISSING AN APP?", size = 14)
                    Spacer(Modifier.height(6.dp))
                    Text("The pack is open source. Ask for an icon, or add one yourself, on GitHub.", style = typography.caption, color = colors.onBackgroundMuted)
                    Spacer(Modifier.height(8.dp))
                    NothingButton("REQUEST AN ICON", NothingButtonStyle.Outline, Modifier.fillMaxWidth()) {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jameswmoorman-code/nothing-suite/issues/new?title=Dots%20Icons%3A%20icon%20request")))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No permissions, no internet, no data collected.", style = typography.caption, color = colors.onBackgroundMuted)
                }
            }
        }
    }

    /** "package/activity — label" for every launchable app, handed to the share sheet. */
    private fun exportAppList() {
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { "${it.activityInfo.packageName}/${it.activityInfo.name} — ${it.loadLabel(packageManager)}" }
            .sorted()
        val text = "Dots Icons app list (${apps.size} apps)\n" + apps.joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Dots Icons app list").putExtra(Intent.EXTRA_TEXT, text)
        open(Intent.createChooser(send, "Send app list"))
    }

    private fun open(i: Intent) { try { startActivity(i) } catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} } }
}
