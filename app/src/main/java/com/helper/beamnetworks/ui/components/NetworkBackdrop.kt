package com.helper.beamnetworks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The network-of-nodes graphic from the Beam billing sign-in page (billing.beamnetworks.co.ke/login),
 * drawn from its own coordinates. The website stretches it to fit; here it is scaled evenly to cover the
 * screen and centred, so it never distorts and only the edges are cropped.
 */
@Composable
fun NetworkBackdrop(modifier: Modifier = Modifier) {
    val graph = remember { NetworkGraph.parse() }
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.matchParentSize().blur(3.dp)) {
            val scale = maxOf(size.width / GRAPH_W, size.height / GRAPH_H)
            val dx = (size.width - GRAPH_W * scale) / 2f
            val dy = (size.height - GRAPH_H * scale) / 2f
            fun at(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
            for (l in graph.lines) {
                drawLine(
                    color = (if (l.blue) Blue else Orange).copy(alpha = if (l.lit) 0.55f else 0.16f),
                    start = at(l.x1, l.y1),
                    end = at(l.x2, l.y2),
                    strokeWidth = scale,
                )
            }
            for (n in graph.nodes) {
                drawCircle((if (n.blue) Blue else Orange).copy(alpha = if (n.lit) 1f else 0.5f), radius = 4f * scale, center = at(n.x, n.y))
            }
        }
    }
}

private const val GRAPH_W = 660f
private const val GRAPH_H = 480f
private val Blue = Color(0xFF2563EB)
private val Orange = Color(0xFFE65C19)

private class GraphLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val blue: Boolean, val lit: Boolean)
private class GraphNode(val x: Float, val y: Float, val blue: Boolean, val lit: Boolean)

private class NetworkGraph(val lines: List<GraphLine>, val nodes: List<GraphNode>) {
    companion object {
        fun parse(): NetworkGraph {
            val nodes = NODES.split(';').map { it.split(',') }.map { GraphNode(it[0].toFloat(), it[1].toFloat(), it[2] == "1", it[3] == "1") }
            val lit = nodes.filter { it.lit }.map { it.x to it.y }.toSet()
            val lines = LINES.split(';').map { it.split(',') }.map {
                val x1 = it[0].toFloat(); val y1 = it[1].toFloat(); val x2 = it[2].toFloat(); val y2 = it[3].toFloat()
                GraphLine(x1, y1, x2, y2, it[4] == "1", (x1 to y1) in lit && (x2 to y2) in lit)
            }
            return NetworkGraph(lines, nodes)
        }
    }
}

// x1,y1,x2,y2,blue
private const val LINES = "0,23.6,82.1,0,1;0,23.6,11.4,71,0;82.1,0,201.8,1.8,1;82.1,0,104.9,94.2,0;201.8,1.8,104.9,94.2,1;201.8,1.8,273,14.1,0;201.8,1.8,213,113.2,1;201.8,1.8,261.1,96.7,0;273,14.1,391.9,2.4,1;273,14.1,261.1,96.7,0;391.9,2.4,482.1,7.6,1;391.9,2.4,401.6,106.6,0;391.9,2.4,479.7,100.3,1;482.1,7.6,584,0,0;482.1,7.6,479.7,100.3,1;584,0,479.7,100.3,0;584,0,652.7,11.6,1;584,0,589.5,118.9,0;652.7,11.6,652.6,86.8,1;11.4,71,104.9,94.2,0;11.4,71,19.5,182.6,1;104.9,94.2,213,113.2,0;104.9,94.2,74.2,180.7,1;104.9,94.2,184.4,180.5,0;213,113.2,261.1,96.7,1;213,113.2,184.4,180.5,0;261.1,96.7,184.4,180.5,1;261.1,96.7,401.6,106.6,0;261.1,96.7,308.1,195.2,1;401.6,106.6,479.7,100.3,0;401.6,106.6,390.4,213.2,1;479.7,100.3,390.4,213.2,0;479.7,100.3,589.5,118.9,1;479.7,100.3,445.9,179.3,0;479.7,100.3,569.2,192.5,1;589.5,118.9,652.6,86.8,0;589.5,118.9,569.2,192.5,1;652.6,86.8,655.5,174.8,0;19.5,182.6,74.2,180.7,1;19.5,182.6,23.6,263.1,0;74.2,180.7,184.4,180.5,1;74.2,180.7,86.5,279.3,0;184.4,180.5,86.5,279.3,1;184.4,180.5,308.1,195.2,0;184.4,180.5,198.1,311.8,1;184.4,180.5,283.7,269.3,0;308.1,195.2,390.4,213.2,1;308.1,195.2,283.7,269.3,0;390.4,213.2,445.9,179.3,1;390.4,213.2,371.7,275.9,0;390.4,213.2,463.6,283.7,1;445.9,179.3,569.2,192.5,0;445.9,179.3,463.6,283.7,1;569.2,192.5,463.6,283.7,0;569.2,192.5,655.5,174.8,1;569.2,192.5,556,289.8,0;655.5,174.8,660,273.1,1;23.6,263.1,86.5,279.3,0;23.6,263.1,23.5,381.6,1;86.5,279.3,198.1,311.8,0;86.5,279.3,115.2,391.6,1;86.5,279.3,196.3,384.9,0;198.1,311.8,283.7,269.3,1;198.1,311.8,196.3,384.9,0;283.7,269.3,196.3,384.9,1;283.7,269.3,371.7,275.9,0;283.7,269.3,283.1,394.6,1;371.7,275.9,463.6,283.7,0;371.7,275.9,402.5,378.2,1;463.6,283.7,402.5,378.2,0;463.6,283.7,556,289.8,1;463.6,283.7,483.5,370.1,0;463.6,283.7,570.7,375.6,1;556,289.8,660,273.1,0;556,289.8,570.7,375.6,1;660,273.1,650.2,377.9,0;23.5,381.6,115.2,391.6,1;23.5,381.6,17,480,0;115.2,391.6,196.3,384.9,1;115.2,391.6,71.8,480,0;196.3,384.9,71.8,480,1;196.3,384.9,283.1,394.6,0;196.3,384.9,164.2,474.8,1;196.3,384.9,262.9,456.4,0;283.1,394.6,402.5,378.2,1;283.1,394.6,262.9,456.4,0;402.5,378.2,483.5,370.1,1;402.5,378.2,405.8,464.3,0;402.5,378.2,479.1,460.1,1;483.5,370.1,570.7,375.6,0;483.5,370.1,479.1,460.1,1;570.7,375.6,479.1,460.1,0;570.7,375.6,650.2,377.9,1;570.7,375.6,563.2,480,0;650.2,377.9,639.1,480,1;17,480,71.8,480,0;71.8,480,164.2,474.8,1;164.2,474.8,262.9,456.4,0;262.9,456.4,405.8,464.3,1;405.8,464.3,479.1,460.1,0;479.1,460.1,563.2,480,1;563.2,480,639.1,480,0"

// x,y,blue,lit
private const val NODES = "0,23.6,1,0;82.1,0,0,0;201.8,1.8,1,0;273,14.1,0,0;391.9,2.4,1,1;482.1,7.6,0,1;584,0,1,1;652.7,11.6,0,1;11.4,71,1,0;104.9,94.2,0,0;213,113.2,1,0;261.1,96.7,0,0;401.6,106.6,1,1;479.7,100.3,0,1;589.5,118.9,1,1;652.6,86.8,0,1;19.5,182.6,1,0;74.2,180.7,0,0;184.4,180.5,1,0;308.1,195.2,0,0;390.4,213.2,1,1;445.9,179.3,0,1;569.2,192.5,1,1;655.5,174.8,0,1;23.6,263.1,1,0;86.5,279.3,0,0;198.1,311.8,1,0;283.7,269.3,0,0;371.7,275.9,1,0;463.6,283.7,0,1;556,289.8,1,1;660,273.1,0,1;23.5,381.6,1,0;115.2,391.6,0,0;196.3,384.9,1,0;283.1,394.6,0,0;402.5,378.2,1,1;483.5,370.1,0,1;570.7,375.6,1,1;650.2,377.9,0,1;17,480,1,0;71.8,480,0,0;164.2,474.8,1,0;262.9,456.4,0,0;405.8,464.3,1,1;479.1,460.1,0,1;563.2,480,1,1;639.1,480,0,1"
