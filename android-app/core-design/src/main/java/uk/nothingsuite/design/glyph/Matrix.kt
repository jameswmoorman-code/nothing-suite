package uk.nothingsuite.design.glyph

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A 25×25 LED frame for the Phone (3) Glyph Matrix, and the drawing tools
 * the toys share: a 5×7 pixel font, icons, scrolling, rotation, sparks.
 * Values are brightness 0–255. Everything here is pure Kotlin — no SDK —
 * so it can be unit-tested and previewed off-device.
 */
class Matrix(val n: Int = 25) {
    val px = IntArray(n * n)

    fun clear() = px.fill(0)
    fun set(x: Int, y: Int, v: Int = 255) { if (x in 0 until n && y in 0 until n) px[y * n + x] = v.coerceIn(0, 255) }
    fun get(x: Int, y: Int) = if (x in 0 until n && y in 0 until n) px[y * n + x] else 0

    /** Draw an 11×11 (or any) grid of '#' at top-left (ox, oy), scaled by [scale]. */
    fun icon(grid: String, ox: Int, oy: Int, scale: Int = 1, v: Int = 255) {
        grid.trim('\n').lines().forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c == '#') for (dy in 0 until scale) for (dx in 0 until scale) set(ox + x * scale + dx, oy + y * scale + dy, v) }
        }
    }

    /** Draw text in the pixel font; returns pixel width. [scale] 1 = 5×7, 2 = 10×14. */
    fun text(s: String, ox: Int, oy: Int, scale: Int = 1, v: Int = 255): Int {
        var x = ox
        for (ch in s.uppercase()) {
            val g = Font.glyph(ch)
            g.forEachIndexed { r, bits ->
                for (c in 0 until Font.W) if (bits shr (Font.W - 1 - c) and 1 == 1)
                    for (dy in 0 until scale) for (dx in 0 until scale) set(x + c * scale + dx, oy + r * scale + dy, v)
            }
            x += (Font.W + 1) * scale
        }
        return x - ox - scale
    }

    fun textWidth(s: String, scale: Int = 1) = s.length * (Font.W + 1) * scale - scale

    /** Text centred horizontally at row [oy]. */
    fun textCentered(s: String, oy: Int, scale: Int = 1, v: Int = 255) =
        text(s, (n - textWidth(s, scale)) / 2, oy, scale, v)

    /** Copy of this frame rotated by [deg] about the centre (nearest-neighbour). */
    fun rotated(deg: Double): Matrix {
        val out = Matrix(n); val c = (n - 1) / 2.0
        val rad = Math.toRadians(-deg); val cs = cos(rad); val sn = sin(rad)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - c; val dy = y - c
            val sx = (c + dx * cs - dy * sn).roundToInt(); val sy = (c + dx * sn + dy * cs).roundToInt()
            out.px[y * n + x] = get(sx, sy)
        }
        return out
    }

    /** A ring of radius r (LEDs), filled clockwise from 12 o'clock to [fraction]. */
    fun ring(fraction: Double, r: Double = 11.0, thickness: Double = 1.6, v: Int = 255, dim: Int = 40) {
        val c = (n - 1) / 2.0
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - c; val dy = y - c
            val d = Math.hypot(dx, dy)
            if (d in (r - thickness)..(r + thickness / 2)) {
                var a = Math.toDegrees(Math.atan2(dx, -dy)); if (a < 0) a += 360
                set(x, y, if (a / 360.0 <= fraction) v else dim)
            }
        }
    }

    fun copyFrom(o: Matrix) = o.px.copyInto(px)
    fun dimmed(factor: Double): Matrix { val m = Matrix(n); for (i in px.indices) m.px[i] = (px[i] * factor).toInt(); return m }
}

/** 5×7 pixel font: A–Z, 0–9 and the few symbols the toys use. Each row is 5 bits, MSB = left. */
object Font {
    const val W = 5
    private val map = HashMap<Char, IntArray>()
    private fun g(c: Char, vararg rows: String) { map[c] = IntArray(7) { rows[it].replace('.', '0').replace('#', '1').toInt(2) } }
    init {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.")
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.")
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.")
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####")
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....")
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####")
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        g('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####")
        g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..")
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#")
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####")
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#")
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#")
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....")
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#")
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#")
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.")
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..")
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..")
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#")
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#")
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..")
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####")
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.")
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.")
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####")
        g('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.")
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.")
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.")
        g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.")
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...")
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.")
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..")
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....")
        g('%', "##..#", "##.#.", "...#.", "..#..", ".#...", "#.##.", "#..##")
        g(':', ".....", "..#..", "..#..", ".....", "..#..", "..#..", ".....")
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..")
        g('.', ".....", ".....", ".....", ".....", ".....", "..#..", ".....")
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....")
        g('°', ".##..", "#..#.", ".##..", ".....", ".....", ".....", ".....")
        g('\'', "..#..", "..#..", ".....", ".....", ".....", ".....", ".....")
        g(',', ".....", ".....", ".....", ".....", ".....", "..#..", ".#...")
        g('/', "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#....")
    }
    fun glyph(c: Char): IntArray = map[c] ?: map['.']!!
}

/** 11×11 pictograms, same ones as the widgets. */
object Icons {
    const val CIGARETTE = """
.........#.
........#..
.........#.
...........
####.......
###########
###########
####.......
...........
...........
..........."""
    const val DRINK = """
.#########.
.#########.
..#######..
...#####...
....###....
.....#.....
.....#.....
.....#.....
.....#.....
..#######..
..........."""
    const val HEART = """
...........
..##...##..
.####.####.
###########
###########
.#########.
..#######..
...#####...
....###....
.....#.....
..........."""
    const val STAR = """
.....#.....
.....#.....
....###....
#..#####..#
###########
.#########.
..#######..
..#######..
.###...###.
.#.......#.
..........."""
    const val SUN = """
.....#.....
.#...#...#.
..#.....#..
....###....
...#####...
#..#####..#
...#####...
....###....
..#.....#..
.#...#...#.
.....#....."""
    const val CLOUD = """
...........
...........
....####...
...######..
.#########.
###########
###########
.#########.
...........
...........
..........."""
    const val RAIN = """
....####...
...######..
.#########.
###########
.#########.
...........
..#..#..#..
.#..#..#...
..#..#..#..
.#..#..#...
..........."""
    const val BOLT = """
.....##....
....##.....
...##......
..#####....
.....##....
....##.....
...##......
..##.......
...........
...........
..........."""
    fun forKey(key: String?) = when (key) { "drink" -> DRINK; "heart" -> HEART; "star" -> STAR; "none" -> null; else -> CIGARETTE }
}

/** Fireworks: a handful of bursts, each a ring of sparks flying out and fading. */
class Fireworks(private val n: Int = 25) {
    private class Spark(var x: Double, var y: Double, val vx: Double, var vy: Double, var life: Double)
    private val sparks = ArrayList<Spark>()
    private var tick = 0
    private val rnd = java.util.Random()

    fun burst(cx: Double, cy: Double, count: Int = 14) {
        for (i in 0 until count) {
            val a = 2 * Math.PI * i / count + rnd.nextDouble() * 0.3
            val sp = 0.9 + rnd.nextDouble() * 0.6
            sparks += Spark(cx, cy, cos(a) * sp, sin(a) * sp, 1.0)
        }
    }

    /** Advance one step (~50 ms) and draw. Returns false when the show is over. */
    fun step(m: Matrix, autoBurst: Boolean = true): Boolean {
        if (autoBurst && tick % 14 == 0 && tick < 60) burst(4 + rnd.nextDouble() * (n - 8), 4 + rnd.nextDouble() * (n - 8))
        tick++
        m.clear()
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx; s.y += s.vy; s.vy += 0.06; s.life -= 0.055
            if (s.life <= 0) { it.remove(); continue }
            m.set(s.x.roundToInt(), s.y.roundToInt(), (255 * s.life).toInt())
        }
        return sparks.isNotEmpty() || tick < 60
    }
}
