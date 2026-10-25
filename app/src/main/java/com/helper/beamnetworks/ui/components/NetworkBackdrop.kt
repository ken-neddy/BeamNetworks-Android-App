package com.helper.beamnetworks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The network-of-nodes graphic from the Beam billing sign-in page (billing.beamnetworks.co.ke/login),
 * drawn from its own coordinates. The website stretches it to fit; here it is scaled evenly to cover the
 * screen and centred, so it never distorts. The dots drift and bounce off the edges of the screen, and the
 * lines stay attached to them.
 */
@Composable
fun NetworkBackdrop(modifier: Modifier = Modifier) {
    val graph = remember { NetworkGraph.parse() }
    val speed = with(LocalDensity.current) { 16.dp.toPx() }
    val field = remember { NodeField(graph.nodes.size) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(field, speed) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) field.step(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
                frame = now
            }
        }
    }
    Box(modifier.clipToBounds().onSizeChanged { field.fit(graph, it.width.toFloat(), it.height.toFloat(), speed) }) {
        Canvas(Modifier.matchParentSize().blur(3.dp)) {
            frame // read so the canvas redraws every frame
            val scale = maxOf(size.width / GRAPH_W, size.height / GRAPH_H)
            for (l in graph.lines) {
                drawLine(
                    color = (if (l.blue) Blue else Orange).copy(alpha = if (l.lit) 0.55f else 0.16f),
                    start = Offset(field.x[l.a], field.y[l.a]),
                    end = Offset(field.x[l.b], field.y[l.b]),
                    strokeWidth = scale,
                )
            }
            for ((i, n) in graph.nodes.withIndex()) {
                drawCircle((if (n.blue) Blue else Orange).copy(alpha = if (n.lit) 1f else 0.5f), radius = 4f * scale, center = Offset(field.x[i], field.y[i]))
            }
        }
    }
}

/** Node positions in screen pixels. They keep a constant speed and reflect off the screen edges. */
private class NodeField(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    private var w = 0f
    private var h = 0f
    private var radius = 0f

    fun fit(graph: NetworkGraph, width: Float, height: Float, speed: Float) {
        if (width <= 0f || height <= 0f) return
        val scale = maxOf(width / GRAPH_W, height / GRAPH_H)
        val dx = (width - GRAPH_W * scale) / 2f
        val dy = (height - GRAPH_H * scale) / 2f
        radius = 4f * scale
        val first = w == 0f
        w = width
        h = height
        val rnd = Random(7)
        graph.nodes.forEachIndexed { i, n ->
            // Start from the website's layout (nodes that fall off a cropped screen are pulled onto it).
            x[i] = (dx + n.x * scale).coerceIn(radius, width - radius)
            y[i] = (dy + n.y * scale).coerceIn(radius, height - radius)
            if (first) {
                val angle = rnd.nextFloat() * 2f * PI.toFloat()
                val v = speed * (0.5f + rnd.nextFloat())
                vx[i] = cos(angle) * v
                vy[i] = sin(angle) * v
            }
        }
    }

    fun step(dt: Float) {
        if (w == 0f) return
        for (i in x.indices) {
            var nx = x[i] + vx[i] * dt
            var ny = y[i] + vy[i] * dt
            if (nx < radius) { nx = radius; vx[i] = kotlin.math.abs(vx[i]) } else if (nx > w - radius) { nx = w - radius; vx[i] = -kotlin.math.abs(vx[i]) }
            if (ny < radius) { ny = radius; vy[i] = kotlin.math.abs(vy[i]) } else if (ny > h - radius) { ny = h - radius; vy[i] = -kotlin.math.abs(vy[i]) }
            x[i] = nx
            y[i] = ny
        }
    }
}

private const val GRAPH_W = 660f
private const val GRAPH_H = 480f
private val Blue = Color(0xFF2563EB)
private val Orange = Color(0xFFE65C19)

private class GraphLine(val a: Int, val b: Int, val blue: Boolean, val lit: Boolean)
private class GraphNode(val x: Float, val y: Float, val blue: Boolean, val lit: Boolean)

private class NetworkGraph(val lines: List<GraphLine>, val nodes: List<GraphNode>) {
    companion object {
        fun parse(): NetworkGraph {
            val nodes = NODES.split(';').map { it.split(',') }.map { GraphNode(it[0].toFloat(), it[1].toFloat(), it[2] == "1", it[3] == "1") }
            fun indexAt(x: Float, y: Float) = nodes.indexOfFirst { kotlin.math.abs(it.x - x) < 0.5f && kotlin.math.abs(it.y - y) < 0.5f }
            val lines = LINES.split(';').map { it.split(',') }.mapNotNull {
                val a = indexAt(it[0].toFloat(), it[1].toFloat())
                val b = indexAt(it[2].toFloat(), it[3].toFloat())
                if (a < 0 || b < 0) null else GraphLine(a, b, it[4] == "1", nodes[a].lit && nodes[b].lit)
            }
            return NetworkGraph(lines, nodes)
        }
    }
}

// x1,y1,x2,y2,blue
private const val LINES = "0,23.6,82.1,0,1;0,23.6,11.4,71,0;82.1,0,201.8,1.8,1;82.1,0,104.9,94.2,0;201.8,1.8,104.9,94.2,1;201.8,1.8,273,14.1,0;201.8,1.8,213,113.2,1;201.8,1.8,261.1,96.7,0;273,14.1,391.9,2.4,1;273,14.1,261.1,96.7,0;391.9,2.4,482.1,7.6,1;391.9,2.4,401.6,106.6,0;391.9,2.4,479.7,100.3,1;482.1,7.6,584,0,0;482.1,7.6,479.7,100.3,1;584,0,479.7,100.3,0;584,0,652.7,11.6,1;584,0,589.5,118.9,0;652.7,11.6,652.6,86.8,1;11.4,71,104.9,94.2,0;11.4,71,19.5,182.6,1;104.9,94.2,213,113.2,0;104.9,94.2,74.2,180.7,1;104.9,94.2,184.4,180.5,0;213,113.2,261.1,96.7,1;213,113.2,184.4,180.5,0;261.1,96.7,184.4,180.5,1;261.1,96.7,401.6,106.6,0;261.1,96.7,308.1,195.2,1;401.6,106.6,479.7,100.3,0;401.6,106.6,390.4,213.2,1;479.7,100.3,390.4,213.2,0;479.7,100.3,589.5,118.9,1;479.7,100.3,445.9,179.3,0;479.7,100.3,569.2,192.5,1;589.5,118.9,652.6,86.8,0;589.5,118.9,569.2,192.5,1;652.6,86.8,655.5,174.8,0;19.5,182.6,74.2,180.7,1;19.5,182.6,23.6,263.1,0;74.2,180.7,184.4,180.5,1;74.2,180.7,86.5,279.3,0;184.4,180.5,86.5,279.3,1;184.4,180.5,308.1,195.2,0;184.4,180.5,198.1,311.8,1;184.4,180.5,283.7,269.3,0;308.1,195.2,390.4,213.2,1;308.1,195.2,283.7,269.3,0;390.4,213.2,445.9,179.3,1;390.4,213.2,371.7,275.9,0;390.4,213.2,463.6,283.7,1;445.9,179.3,569.2,192.5,0;445.9,179.3,463.6,283.7,1;569.2,192.5,463.6,283.7,0;569.2,192.5,655.5,174.8,1;569.2,192.5,556,289.8,0;655.5,174.8,660,273.1,1;23.6,263.1,86.5,279.3,0;23.6,263.1,23.5,381.6,1;86.5,279.3,198.1,311.8,0;86.5,279.3,115.2,391.6,1;86.5,279.3,196.3,384.9,0;198.1,311.8,283.7,269.3,1;198.1,311.8,196.3,384.9,0;283.7,269.3,196.3,384.9,1;283.7,269.3,371.7,275.9,0;283.7,269.3,283.1,394.6,1;371.7,275.9,463.6,283.7,0;371.7,275.9,402.5,378.2,1;463.6,283.7,402.5,378.2,0;463.6,283.7,556,289.8,1;463.6,283.7,483.5,370.1,0;463.6,283.7,570.7,375.6,1;556,289.8,660,273.1,0;556,289.8,570.7,375.6,1;660,273.1,650.2,377.9,0;23.5,381.6,115.2,391.6,1;23.5,381.6,17,480,0;115.2,391.6,196.3,384.9,1;115.2,391.6,71.8,480,0;196.3,384.9,71.8,480,1;196.3,384.9,283.1,394.6,0;196.3,384.9,164.2,474.8,1;196.3,384.9,262.9,456.4,0;283.1,394.6,402.5,378.2,1;283.1,394.6,262.9,456.4,0;402.5,378.2,483.5,370.1,1;402.5,378.2,405.8,464.3,0;402.5,378.2,479.1,460.1,1;483.5,370.1,570.7,375.6,0;483.5,370.1,479.1,460.1,1;570.7,375.6,479.1,460.1,0;570.7,375.6,650.2,377.9,1;570.7,375.6,563.2,480,0;650.2,377.9,639.1,480,1;17,480,71.8,480,0;71.8,480,164.2,474.8,1;164.2,474.8,262.9,456.4,0;262.9,456.4,405.8,464.3,1;405.8,464.3,479.1,460.1,0;479.1,460.1,563.2,480,1;563.2,480,639.1,480,0"

// x,y,blue,lit
private const val NODES = "0,23.6,1,0;82.1,0,0,0;201.8,1.8,1,0;273,14.1,0,0;391.9,2.4,1,1;482.1,7.6,0,1;584,0,1,1;652.7,11.6,0,1;11.4,71,1,0;104.9,94.2,0,0;213,113.2,1,0;261.1,96.7,0,0;401.6,106.6,1,1;479.7,100.3,0,1;589.5,118.9,1,1;652.6,86.8,0,1;19.5,182.6,1,0;74.2,180.7,0,0;184.4,180.5,1,0;308.1,195.2,0,0;390.4,213.2,1,1;445.9,179.3,0,1;569.2,192.5,1,1;655.5,174.8,0,1;23.6,263.1,1,0;86.5,279.3,0,0;198.1,311.8,1,0;283.7,269.3,0,0;371.7,275.9,1,0;463.6,283.7,0,1;556,289.8,1,1;660,273.1,0,1;23.5,381.6,1,0;115.2,391.6,0,0;196.3,384.9,1,0;283.1,394.6,0,0;402.5,378.2,1,1;483.5,370.1,0,1;570.7,375.6,1,1;650.2,377.9,0,1;17,480,1,0;71.8,480,0,0;164.2,474.8,1,0;262.9,456.4,0,0;405.8,464.3,1,1;479.1,460.1,0,1;563.2,480,1,1;639.1,480,0,1"
