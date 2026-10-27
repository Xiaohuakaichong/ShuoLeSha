package com.example.shuolesa.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*
import kotlin.random.Random

enum class CeerPose(val label:String) { IDLE("侧耳"),LISTENING("正在听"),THINKING("正在整理"),SPEAKING("正在回听"),PAUSED("已暂停"),SLEEP("暂无记忆"),SUCCESS("已保存"),ERROR("需要处理") }

/**
 * v5.1: where the last touch landed (window coordinates). The root provides it, every [Ceer] turns its head and eyes toward it
 * for a few seconds after a touch, then goes back to glancing around on its own.
 */
@Stable class GazeState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var at by mutableLongStateOf(-1L)
    fun seen(position:Offset) { x=position.x;y=position.y;at=SystemClock.uptimeMillis() }
}
val LocalGaze=staticCompositionLocalOf<GazeState?> { null }
/** Wraps the app: watches every touch (without consuming it) so the characters inside can look at it. */
@Composable fun GazeHost(content:@Composable ()->Unit) {
    val gaze=remember { GazeState() }
    CompositionLocalProvider(LocalGaze provides gaze) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while(true) {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull()?.let { gaze.seen(it.position) }
        } } }) { content() }
    }
}
/** v5.1: false = no frame loop, the character is drawn once in its pose (tests, and when the system animation scale is 0). */
val LocalCeerMotion=staticCompositionLocalOf { true }

private val CheekGreen=Color(0xFF5DCAA5)
private val SealRed=Color(0xFFD4533A)
private class Palette(val ink:Color,val paper:Color,val accent:Color)

/**
 * v5.1 墨云 (the C3 design). A cloud of ink that breathes, blinks, turns its head and eyes toward the finger, hops when happy,
 * and wears a small red seal on its forehead. No tail: the outline itself wobbles. All motion lives in [Spirit] so it can be
 * tested without Compose.
 */
internal class Spirit {
    var t=0f
    val ph=Random.nextFloat()*6f
    var yaw=0f;var pitch=0f
    private var blinkT=-1f;private var nextBlink=1f+Random.nextFloat()*2f
    private var wanderAt=0f;private var wy=0f;private var wp=0f
    var lid=1f;var stretch=1f;var tilt=0f
    var pokeAt=-10f
    /** Where this character sits in the window, set by onGloballyPositioned. */
    var cx=0f;var cy=0f;var span=1f
    var open=1f;var mouth=0f;var breath=0f;var hop=0f;var happy=false
    var pose=CeerPose.IDLE;var energy=0f
    val path=Path();val xs=FloatArray(12);val ys=FloatArray(12)

    /** [look] = where the finger is, as -1..1 from this character's centre; null = nobody is touching, look around. */
    fun step(dt:Float,pose:CeerPose,energy:Float,look:Offset?) {
        t+=dt;this.pose=pose;this.energy=energy
        happy=pose==CeerPose.SUCCESS || (t-pokeAt<0.9f && pose!=CeerPose.SLEEP && pose!=CeerPose.ERROR)
        val ty:Float;val tp:Float
        when {
            pose==CeerPose.THINKING -> { ty=0.55f;tp=-0.7f }
            pose==CeerPose.SLEEP -> { ty=0f;tp=0.5f }
            pose==CeerPose.PAUSED -> { ty=0f;tp=0.4f }
            pose==CeerPose.ERROR -> { ty=-0.5f;tp=0.3f }
            look!=null -> { ty=look.x;tp=look.y }
            else -> {
                if(t>wanderAt) { wy=(Random.nextFloat()*2f-1f)*0.8f;wp=(Random.nextFloat()*2f-1f)*0.4f;wanderAt=t+2f+Random.nextFloat()*2.5f }
                ty=wy;tp=wp
            }
        }
        val k=1f-exp(-dt*7f)
        yaw+=(ty-yaw)*k;pitch+=(tp-pitch)*k
        lid+=((when(pose) { CeerPose.SLEEP->0.05f;CeerPose.PAUSED->0.45f;else->1f })-lid)*(1f-exp(-dt*10f))
        var blink=1f
        if(pose!=CeerPose.SLEEP) {
            if(blinkT<0f && t>nextBlink) blinkT=0f
            if(blinkT>=0f) {
                blinkT+=dt;val u=blinkT/0.16f
                if(u>=1f) { blinkT=-1f;nextBlink=t+1.8f+Random.nextFloat()*3.5f;if(Random.nextFloat()<0.2f) nextBlink=t+0.25f }
                else blink=1f-sin(u*PI.toFloat())*0.95f
            }
        }
        open=lid*blink*(if(pose==CeerPose.LISTENING) 1.1f+0.15f*energy else 1f)
        val synthetic=(0.15f+0.55f*abs(sin(t*9f+ph))*(0.6f+0.4f*sin(t*3.1f))).coerceIn(0f,1f)
        mouth=when {
            happy -> 0.35f
            pose==CeerPose.SPEAKING -> if(energy>0.02f) (energy*1.4f).coerceIn(0.1f,1f) else synthetic
            else -> 0f
        }
        breath=sin(t*(if(pose==CeerPose.SLEEP) 1.1f else 2f)+ph)
        val tiltTo=when(pose) { CeerPose.LISTENING->-6f;CeerPose.THINKING->4f;CeerPose.SLEEP->8f;CeerPose.PAUSED->3f;CeerPose.ERROR->-3f;else->sin(t*0.6f+ph)*1.5f }
        tilt+=(tiltTo-tilt)*(1f-exp(-dt*6f))
        val stretchTo=when(pose) { CeerPose.LISTENING->1.05f+0.04f*energy;CeerPose.SLEEP->0.94f;else->1f }
        stretch+=(stretchTo-stretch)*(1f-exp(-dt*8f))
        hop=if(happy) abs(sin(t*5f)) else 0f
    }

    /** Jump straight to the resting look of [pose] (no frame loop). */
    fun settle(pose:CeerPose,energy:Float) {
        lid=when(pose) { CeerPose.SLEEP->0.05f;CeerPose.PAUSED->0.45f;else->1f }
        stretch=when(pose) { CeerPose.LISTENING->1.05f;CeerPose.SLEEP->0.94f;else->1f }
        tilt=when(pose) { CeerPose.LISTENING->-6f;CeerPose.THINKING->4f;CeerPose.SLEEP->8f;CeerPose.PAUSED->3f;CeerPose.ERROR->-3f;else->0f }
        yaw=when(pose) { CeerPose.THINKING->0.55f;CeerPose.ERROR->-0.5f;else->0f }
        pitch=when(pose) { CeerPose.THINKING->-0.7f;CeerPose.SLEEP->0.5f;CeerPose.PAUSED->0.4f;CeerPose.ERROR->0.3f;else->0f }
        step(0f,pose,energy,null)
    }
}

/**
 * [ink] = the body and pupils, [paper] = the whites of the eyes and the mouth, [accent] = ripple and thought bubbles.
 * [energy] 0..1 = how loud it is right now (recording level, or playback level when [CeerPose.SPEAKING]).
 * [rings] = draw the listening ripple itself (the recording page draws its own glow instead).
 * [interactive] = a tap makes it hop.
 */
@Composable fun Ceer(pose:CeerPose,modifier:Modifier=Modifier,ink:Color=MaterialTheme.colorScheme.onSurface,paper:Color=MaterialTheme.colorScheme.surface,
    accent:Color=MaterialTheme.colorScheme.primary,energy:Float=0f,rings:Boolean=true,interactive:Boolean=false) {
    val spirit=remember { Spirit() }
    val density=LocalDensity.current
    val context=LocalContext.current
    val gaze=LocalGaze.current
    val motion=LocalCeerMotion.current && remember { runCatching { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) }.getOrDefault(1f) }>0f
    val currentPose by rememberUpdatedState(pose)
    val currentEnergy by rememberUpdatedState(energy)
    var tick by remember { mutableLongStateOf(0L) }
    val reachMin=with(density) { 160.dp.toPx() }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(motion,lifecycle,if(motion) null else pose) {
        if(!motion) { spirit.settle(currentPose,currentEnergy);tick++;return@LaunchedEffect }
        fun look():Offset? {
            val g=gaze ?: return null
            if(g.at<0L || SystemClock.uptimeMillis()-g.at>4_000L) return null
            val reach=max(spirit.span*0.9f,reachMin)
            return Offset(((g.x-spirit.cx)/reach).coerceIn(-1f,1f),((g.y-spirit.cy)/reach).coerceIn(-1f,1f))
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while(true) withFrameNanos { now ->
                val dt=if(last==0L) 0f else min(0.05f,(now-last)/1e9f)
                last=now
                spirit.step(dt,currentPose,currentEnergy,look());tick=now
            }
        }
    }
    val measurer=rememberTextMeasurer()
    val seal=remember(measurer,density) { measurer.measure("听",TextStyle(fontSize=with(density) { 12f.toSp() },fontWeight=FontWeight.Medium)) }
    val zLow=remember(measurer,density) { measurer.measure("z",TextStyle(fontSize=with(density) { 14f.toSp() },fontWeight=FontWeight.Medium)) }
    val zUp=remember(measurer,density) { measurer.measure("Z",TextStyle(fontSize=with(density) { 14f.toSp() },fontWeight=FontWeight.Medium)) }
    val palette=Palette(ink,paper,accent)
    val poke=if(interactive && motion) Modifier.pointerInput(Unit) { detectTapGestures { spirit.pokeAt=spirit.t } } else Modifier
    Canvas(modifier.size(48.dp).onGloballyPositioned { c ->
        val p=c.positionInWindow();spirit.cx=p.x+c.size.width/2f;spirit.cy=p.y+c.size.height/2f;spirit.span=min(c.size.width,c.size.height).toFloat()
    }.then(poke).semantics { contentDescription=pose.label }) {
        @Suppress("UNUSED_EXPRESSION") tick // reading it redraws the character on every frame without recomposing
        drawSpirit(spirit,palette,rings,seal,zLow,zUp)
    }
}

private fun DrawScope.drawSpirit(s:Spirit,pal:Palette,rings:Boolean,seal:TextLayoutResult,zLow:TextLayoutResult,zUp:TextLayoutResult) {
    val side=min(size.width,size.height)
    val unit=side/200f
    val pose=s.pose;val y=s.yaw;val pi=s.pitch;val hop=s.hop
    val sy=if(s.happy) 0.92f+0.16f*hop else s.stretch+0.012f*s.breath
    val sx=1f/sy.pow(0.8f)
    val lift=(if(s.happy) -hop*14f else if(pose==CeerPose.SPEAKING) -abs(sin(s.t*9f+s.ph))*1.5f else 0f)+s.breath*1.2f+
        (if(pose==CeerPose.ERROR) sin(s.t*35f)*0.6f else 0f)
    withTransform({ translate((size.width-side)/2f,(size.height-side)/2f);scale(unit,unit,Offset.Zero) }) {
        // the ground shadow stays put while the body hops
        val ground=1f-hop*0.25f
        drawOval(pal.ink.copy(alpha=0.12f),Offset(100f-46f*ground,178f-6.5f),Size(92f*ground,13f))
        withTransform({ translate(0f,lift);rotate(s.tilt,Offset(100f,170f));scale(sx,sy,Offset(100f,170f)) }) {
            if(rings && pose==CeerPose.LISTENING) {
                val u=(s.t*0.8f)%1f
                drawCircle(pal.accent.copy(alpha=(1f-u)*0.7f),60f+u*26f,Offset(100f,108f),style=Stroke(2f))
            }
            // body: twelve points on a wobbling ellipse, joined by midpoint quadratics, leaning a little with the head
            val shift=y*6f;val skew=tan(Math.toRadians((-y*4f).toDouble())).toFloat()
            for(i in 0 until 12) {
                val a=i/12f*2f*PI.toFloat()
                val r=60f+5f*sin(s.t*1.4f+i*1.7f+s.ph)+3f*sin(s.t*2.3f+i*0.9f+s.ph*2f)
                val py=108f+sin(a)*r*0.92f
                s.xs[i]=100f+cos(a)*r*1.08f+shift+(py-110f)*skew;s.ys[i]=py
            }
            val p=s.path;p.rewind()
            p.moveTo((s.xs[0]+s.xs[1])/2f,(s.ys[0]+s.ys[1])/2f)
            for(i in 1..12) { val q=i%12;val n=(i+1)%12;p.quadraticTo(s.xs[q],s.ys[q],(s.xs[q]+s.xs[n])/2f,(s.ys[q]+s.ys[n])/2f) }
            p.close()
            drawPath(p,pal.ink)
            // face: the features move further than the outline, which is what reads as turning
            val fx=shift+y*14f;val fy=pi*6f
            val cheekY=125f+fy;val cheekX=y*6f
            drawOval(CheekGreen.copy(alpha=0.5f),Offset(66f+fx+cheekX-9f,cheekY-5.5f),Size(18f,11f))
            drawOval(CheekGreen.copy(alpha=0.5f),Offset(134f+fx+cheekX-9f,cheekY-5.5f),Size(18f,11f))
            val sl=1f-0.3f*max(0f,-y)+0.1f*max(0f,y);val sr=1f-0.3f*max(0f,y)+0.1f*max(0f,-y)
            val er=12.5f;val ey=108f+fy;val eo=max(0.02f,s.open)
            fun eye(cx:Float,wide:Float,grow:Float) {
                val ex=cx+fx
                if(s.happy) {
                    val a=er*0.72f*wide
                    p.rewind();p.moveTo(ex-a,ey+5f);p.quadraticTo(ex,ey-er*0.72f*0.9f,ex+a,ey+5f)
                    drawPath(p,pal.paper,style=Stroke(3.4f,cap=StrokeCap.Round))
                } else if(pose==CeerPose.SLEEP) {
                    p.rewind();p.moveTo(ex-9f,ey);p.quadraticTo(ex,ey+7f,ex+9f,ey)
                    drawPath(p,pal.paper,style=Stroke(3.2f,cap=StrokeCap.Round))
                } else {
                    val rx=er*wide*grow;val ry=er*eo*grow
                    drawOval(pal.paper,Offset(ex-rx,ey-ry),Size(rx*2f,ry*2f))
                    val pr=er*0.48f*grow*(if(pose==CeerPose.ERROR) 0.7f else 1f)
                    val px=ex+y*6f*wide;val py=ey+pi*5f*eo
                    drawOval(pal.ink,Offset(px-pr*wide,py-pr*eo),Size(pr*wide*2f,pr*eo*2f))
                    drawOval(pal.paper,Offset(px-2.4f*wide-2.3f*wide,py-2.6f*eo-2.3f*eo),Size(4.6f*wide,4.6f*eo))
                }
            }
            val worried=pose==CeerPose.ERROR
            eye(78f,sl,1f);eye(122f,sr,if(worried) 0.82f else 1f)
            if(worried) {
                val brow=Stroke(3f,cap=StrokeCap.Round)
                p.rewind();p.moveTo(78f+fx-10f,ey-18f);p.lineTo(78f+fx+8f,ey-25f);drawPath(p,pal.paper,style=brow)
                p.rewind();p.moveTo(122f+fx-8f,ey-25f);p.lineTo(122f+fx+10f,ey-18f);drawPath(p,pal.paper,style=brow)
            }
            val mx=100f+fx;val my=128f+fy;val m=s.mouth
            p.rewind();p.moveTo(mx-7f,my)
            if(worried) { p.quadraticTo(mx-3.5f,my-4f,mx,my);p.quadraticTo(mx+3.5f,my+4f,mx+7f,my) }
            else { p.quadraticTo(mx,my+5f+m*14f,mx+7f,my);if(m>0.05f) p.close() }
            if(m>0.05f && !worried) drawPath(p,pal.paper)
            drawPath(p,pal.paper,style=Stroke(3f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            // the 朱砂 seal on the forehead moves with the face
            withTransform({ translate(100f+fx,62f+fy);rotate(-6f,Offset.Zero) }) {
                drawRoundRect(SealRed,Offset(-10f,-10f),Size(20f,20f),CornerRadius(3f))
                drawText(seal,Color.White,Offset(-seal.size.width/2f,-seal.size.height/2f))
            }
            // three ink drops orbit outside the body
            val speeds=floatArrayOf(4f,3f,5f)
            for(k in 0 until 3) {
                val an=s.t*(0.7f+k*0.25f)+k*2.1f
                drawCircle(pal.ink.copy(alpha=0.8f),speeds[k],Offset(100f+cos(an)*(82f+k*2f),108f+sin(an)*(66f+k*3f)))
            }
            if(pose==CeerPose.THINKING) {
                val bob=sin(s.t*3f)*2f
                drawCircle(pal.accent,4f,Offset(156f,28f+bob));drawCircle(pal.accent,5f,Offset(168f,18f+bob));drawCircle(pal.accent,6f,Offset(182f,10f+bob))
            }
            if(pose==CeerPose.SLEEP) {
                val a=0.6f+0.4f*sin(s.t*2f)
                drawText(zLow,pal.ink.copy(alpha=a),Offset(156f,30f-zLow.firstBaseline))
                drawText(zUp,pal.ink.copy(alpha=a),Offset(170f,16f-zUp.firstBaseline))
            }
        }
    }
}
