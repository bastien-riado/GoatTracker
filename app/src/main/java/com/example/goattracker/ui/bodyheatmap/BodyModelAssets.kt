package com.example.goattracker.ui.bodyheatmap

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.View
import io.github.sceneview.createEngine
import io.github.sceneview.createEnvironment
import io.github.sceneview.createEnvironmentLoader
import io.github.sceneview.createMainLightNode
import io.github.sceneview.createMaterialLoader
import io.github.sceneview.createModelLoader
import io.github.sceneview.createRenderer
import io.github.sceneview.createScene
import io.github.sceneview.createView
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model
import io.github.sceneview.node.LightNode
import io.github.sceneview.utils.OpenGL
import io.github.sceneview.utils.readBuffer
import java.nio.Buffer

/**
 * Every heavy Filament object a `Scene` composable needs, created once and shared app-wide.
 *
 * Passing these explicitly to `Scene(...)` skips the per-visit `remember*` defaults — which
 * otherwise re-create them on every entry AND destroy them on every exit. The expensive ones are
 * the [Engine] (+ EGL) and the [environment]: the default `rememberEnvironment` decodes the
 * `environments/neutral/neutral_ibl.ktx` cubemap and uploads it to the GPU on every single visit —
 * the main reason the model used to pop in ~half a second after the screen appeared.
 */
class SceneResources internal constructor(
    val engine: Engine,
    val modelLoader: ModelLoader,
    val materialLoader: MaterialLoader,
    val environmentLoader: EnvironmentLoader,
    val environment: Environment,
    val view: View,
    val renderer: Renderer,
    val scene: Scene,
    val mainLight: LightNode,
)

/**
 * App-scoped Filament resources backing the 3D body heatmap (see [SceneResources]). Created once,
 * kept for the app's lifetime (~20 MB native — the deliberate price for instant open/close).
 *
 * Per screen visit, [createModel] builds a fresh [Model] from the in-memory GLB buffer (a few ms,
 * no IO): the screen's `ModelNode` owns and destroys that model's entities on dispose, and
 * [destroyModel] then releases its GPU buffers so repeated visits don't accumulate assets in the
 * shared loader. A `Model`/`ModelInstance` must NOT be reused across visits — `ModelNode.destroy()`
 * destroys the instance's entities.
 *
 * All functions are main-thread only (Filament's single-thread contract; SceneView drives the
 * engine from the main thread). Call [prewarm] from the entry-point screen (Profile) so the first
 * navigation to the heatmap is as fast as the following ones.
 */
object BodyModelAssets {
    private const val MODEL_ASSET = "models/body_muscles.glb"

    private var resources: SceneResources? = null
    private var modelBuffer: Buffer? = null

    /** Creates the engine, loaders, environment and model buffer if not already alive. */
    fun prewarm(context: Context) {
        ensure(context)
    }

    fun sceneResources(context: Context): SceneResources = ensure(context)

    fun createModel(context: Context): Model {
        val res = ensure(context)
        // rewind: gltfio reads from the buffer's current position, which a previous visit advanced
        return res.modelLoader.createModel(modelBuffer!!.rewind())
    }

    fun destroyModel(model: Model) {
        resources?.modelLoader?.destroyModel(model)
    }

    private fun ensure(context: Context): SceneResources {
        resources?.let { return it }
        val appContext = context.applicationContext
        val engine = createEngine(OpenGL.createEglContext())
        val environmentLoader = engine.createEnvironmentLoader(appContext)
        modelBuffer = appContext.assets.readBuffer(MODEL_ASSET)
        return SceneResources(
            engine = engine,
            modelLoader = engine.createModelLoader(appContext),
            materialLoader = engine.createMaterialLoader(appContext),
            environmentLoader = environmentLoader,
            environment = createEnvironment(environmentLoader, isOpaque = true),
            view = createView(engine),
            renderer = createRenderer(engine),
            scene = createScene(engine),
            mainLight = createMainLightNode(engine),
        ).also { resources = it }
    }
}
