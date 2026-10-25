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
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The network-of-nodes graphic from the Beam billing sign-in page (billing.beamnetworks.co.ke/login),
 * drawn from its own coordinates. The website stretches it to fit; here it is scaled evenly to cover the
 * screen and centred, so it never distorts. The nodes and lines stay still; six green dots travel along
 * the lines from node to node, and a node flashes green when one arrives.
 */
@Composable
fun NetworkBackdrop(modifier: Modifier = Modifier) {
    val graph = remember { NetworkGraph.parse() }
    val packets = remember { Packets(graph) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(packets) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) packets.step(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
                frame = now
            }
        }
    }
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.matchParentSize().blur(8.dp)) {
            frame // read so the canvas redraws every frame
            val scale = maxOf(size.width / GRAPH_W, size.height / GRAPH_H)
            val dx = (size.width - GRAPH_W * scale) / 2f
            val dy = (size.height - GRAPH_H * scale) / 2f
            fun at(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
            for (l in graph.lines) {
                drawLine(
                    color = (if (l.blue) Blue else Orange).copy(alpha = if (l.lit) 0.55f else 0.16f),
                    start = at(graph.nodes[l.a].x, graph.nodes[l.a].y),
                    end = at(graph.nodes[l.b].x, graph.nodes[l.b].y),
                    strokeWidth = scale,
                )
            }
            for ((i, n) in graph.nodes.withIndex()) {
                drawCircle((if (n.blue) Blue else Orange).copy(alpha = if (n.lit) 1f else 0.5f), radius = 4f * scale, center = at(n.x, n.y))
                val flash = packets.flash[i]
                if (flash > 0f) drawCircle(Green.copy(alpha = 0.6f * flash), radius = 4f * scale, center = at(n.x, n.y))
            }
            for (p in packets.list) {
                val line = graph.lines[p.line]
                val from = graph.nodes[if (p.forward) line.a else line.b]
                val to = graph.nodes[if (p.forward) line.b else line.a]
                val c = at(from.x + (to.x - from.x) * p.t, from.y + (to.y - from.y) * p.t)
                drawCircle(Green.copy(alpha = 0.25f), radius = 9.6f * scale, center = c)
                drawCircle(Green, radius = 4f * scale, center = c)
            }
        }
    }
}

private class Packet(var line: Int, var forward: Boolean, var t: Float)

/** Six green packets, each hopping from node to node along the lines. */
private class Packets(private val graph: NetworkGraph) {
    private val rnd = Random(11)
    val list = List(6) { Packet(rnd.nextInt(graph.lines.size), rnd.nextBoolean(), rnd.nextFloat()) }
    val flash = FloatArray(graph.nodes.size)

    fun step(dt: Float) {
        for (i in flash.indices) flash[i] = (flash[i] - dt / 0.9f).coerceAtLeast(0f)
        for (p in list) {
            val line = graph.lines[p.line]
            val from = graph.nodes[if (p.forward) line.a else line.b]
            val to = graph.nodes[if (p.forward) line.b else line.a]
            val length = hypot(to.x - from.x, to.y - from.y).coerceAtLeast(1f)
            p.t += PACKET_SPEED / length * dt
            if (p.t >= 1f) {
                val node = if (p.forward) line.b else line.a
                flash[node] = 1f
                val options = graph.linesAt(node)
                val next = options[rnd.nextInt(options.size)]
                p.line = next
                p.forward = graph.lines[next].a == node
                p.t = 0f
            }
        }
    }
}

/** Graph units (the 660 x 480 canvas) per second. */
private const val PACKET_SPEED = 90f
private const val GRAPH_W = 660f
private const val GRAPH_H = 480f
private val Blue = Color(0xFF2563EB)
private val Orange = Color(0xFFE65C19)
private val Green = Color(0xFF22C55E)

private class GraphLine(val a: Int, val b: Int, val blue: Boolean, val lit: Boolean)
private class GraphNode(val x: Float, val y: Float, val blue: Boolean, val lit: Boolean)

private class NetworkGraph(val lines: List<GraphLine>, val nodes: List<GraphNode>) {
    private val byNode: Map<Int, List<Int>> =
        lines.indices.flatMap { i -> listOf(lines[i].a to i, lines[i].b to i) }.groupBy({ it.first }, { it.second })

    fun linesAt(node: Int): List<Int> = byNode[node].orEmpty().ifEmpty { listOf(0) }

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
