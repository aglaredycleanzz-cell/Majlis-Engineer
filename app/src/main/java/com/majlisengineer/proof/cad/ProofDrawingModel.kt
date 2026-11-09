package com.majlisengineer.proof.cad

data class ProofLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true
)

data class ProofEntity(
    val id: String,
    val layerId: String,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val label: String = ""
)

data class ProofDrawingSnapshot(
    val layers: List<ProofLayer>,
    val entities: List<ProofEntity>
)

class ProofDrawingModel {
    private val mutableLayers = linkedMapOf<String, ProofLayer>()
    private val mutableEntities = linkedMapOf<String, ProofEntity>()

    val layers: List<ProofLayer> get() = mutableLayers.values.toList()
    val entities: List<ProofEntity> get() = mutableEntities.values.toList()

    fun createLayer(id: String, name: String): Boolean {
        if (id.isBlank() || name.isBlank() || mutableLayers.containsKey(id)) return false
        mutableLayers[id] = ProofLayer(id, name)
        return true
    }

    fun addEntity(entity: ProofEntity): Boolean {
        if (entity.id.isBlank() || entity.type.isBlank() ||
            !mutableLayers.containsKey(entity.layerId) ||
            mutableEntities.containsKey(entity.id) ||
            !entity.x.isFinite() || !entity.y.isFinite() ||
            !entity.width.isFinite() || !entity.height.isFinite()
        ) return false
        mutableEntities[entity.id] = entity
        return true
    }

    fun moveEntity(id: String, dx: Double, dy: Double): Boolean {
        val entity = mutableEntities[id] ?: return false
        if (!dx.isFinite() || !dy.isFinite()) return false
        mutableEntities[id] = entity.copy(x = entity.x + dx, y = entity.y + dy)
        return true
    }

    fun editEntity(id: String, label: String, width: Double, height: Double): Boolean {
        val entity = mutableEntities[id] ?: return false
        if (!width.isFinite() || !height.isFinite()) return false
        mutableEntities[id] = entity.copy(label = label, width = width, height = height)
        return true
    }

    fun deleteEntity(id: String): Boolean = mutableEntities.remove(id) != null

    fun setLayerVisibility(id: String, visible: Boolean): Boolean {
        val layer = mutableLayers[id] ?: return false
        mutableLayers[id] = layer.copy(visible = visible)
        return true
    }

    fun snapshot(): ProofDrawingSnapshot = ProofDrawingSnapshot(layers, entities)

    fun reopen(snapshot: ProofDrawingSnapshot) {
        mutableLayers.clear()
        mutableEntities.clear()
        snapshot.layers.forEach { mutableLayers[it.id] = it }
        snapshot.entities.filter { mutableLayers.containsKey(it.layerId) }
            .forEach { mutableEntities[it.id] = it }
    }

    fun visibleEntities(): List<ProofEntity> {
        val visibleIds = mutableLayers.values.filter { it.visible }.map { it.id }.toSet()
        return mutableEntities.values.filter { it.layerId in visibleIds }
    }
}
