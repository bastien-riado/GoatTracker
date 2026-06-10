package com.example.goattracker.ui.bodyheatmap

import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.createEngine
import io.github.sceneview.createModelLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model
import io.github.sceneview.utils.OpenGL
import io.github.sceneview.utils.readBuffer
import java.nio.Buffer

/**
 * App-scoped Filament resources backing the 3D body heatmap.
 *
 * Creating the Filament [Engine] (+ EGL context) is what makes the 3D screen slow to appear, and
 * destroying it on back-navigation stalls the main thread mid pop-animation. SceneView's own docs
 * recommend a single engine per process, shared across `Scene` composables — so the engine, its
 * [ModelLoader] and the GLB bytes (~300 KB) are created once and kept for the app's lifetime
 * (~20 MB native, the deliberate price for instant open/close of the heatmap).
 *
 * Per screen visit, [createModel] builds a fresh [Model] from the in-memory buffer (a few ms, no
 * IO): the screen's `ModelNode` owns and destroys that model's entities on dispose, and
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

    private var engine: Engine? = null
    private var modelLoader: ModelLoader? = null
    private var modelBuffer: Buffer? = null

    /** Creates the engine, loader and model buffer if not already alive. */
    fun prewarm(context: Context) {
        ensure(context)
    }

    fun engine(context: Context): Engine = ensure(context).first

    fun modelLoader(context: Context): ModelLoader = ensure(context).second

    fun createModel(context: Context): Model {
        val (_, loader) = ensure(context)
        // rewind: gltfio reads from the buffer's current position, which a previous visit advanced
        return loader.createModel(modelBuffer!!.rewind())
    }

    fun destroyModel(model: Model) {
        modelLoader?.destroyModel(model)
    }

    private fun ensure(context: Context): Pair<Engine, ModelLoader> {
        val appContext = context.applicationContext
        val e = engine ?: createEngine(OpenGL.createEglContext()).also { engine = it }
        val loader = modelLoader ?: e.createModelLoader(appContext).also { modelLoader = it }
        if (modelBuffer == null) {
            modelBuffer = appContext.assets.readBuffer(MODEL_ASSET)
        }
        return e to loader
    }
}
