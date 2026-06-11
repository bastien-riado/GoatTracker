# Re-segments the GoatTracker body model into per-muscle glTF materials.
#
# Run inside Blender (5.x) with the repo's current asset:
#   1. File > Import > glTF 2.0  ->  app/src/main/assets/models/body_muscles.glb
#      (imports a single mesh object named "body_heatmap" with named material slots)
#   2. Open this file in the Text Editor and Run Script.
#   3. The script re-exports the GLB in place (EXPORT_PATH below).
#
# Conventions (the model <-> code contract, see MuscleGroup.kt):
#   - 1 glTF material per muscle, material name == MuscleGroup.id
#   - neutral materials: "head" (head/neck) and "body" (hands, feet, shins, knees,
#     groin, clavicles, untracked areas) — the app tints unknown materials grey
#   - Blender axes: +Z up, **+Y = front of the body** (the side the app camera faces;
#     device-verified in commit 2d62f76)
#   - body normalized: feet at z=0, height 1.8
#
# Pipeline: subdivide long edges (precision) -> limb labels from the previous
# segmentation -> anatomical z/normal/midline rules -> neighbour-majority smoothing
# (synchronous then sequential passes; sequential kills checkerboard oscillation).

import bpy
import json
from collections import defaultdict, Counter

OBJECT_NAME = "body_heatmap"
EXPORT_PATH = r"C:\Users\DrPixel\Desktop\Projects\GoatTracker\app\src\main\assets\models\body_muscles.glb"

ORDER = ["chest", "front_delts", "rear_delts", "biceps", "triceps", "forearms",
         "abs", "obliques", "lats", "traps", "lower_back", "glutes", "quads",
         "hamstrings", "calves", "head", "body"]

DEBUG_COLORS = {
    "chest": (0.90, 0.10, 0.10), "front_delts": (1.00, 0.50, 0.00), "rear_delts": (0.60, 0.30, 0.00),
    "biceps": (0.10, 0.40, 0.95), "triceps": (0.00, 0.80, 0.80), "forearms": (0.50, 0.20, 0.70),
    "abs": (1.00, 0.90, 0.00), "obliques": (0.70, 0.70, 0.20), "lats": (0.10, 0.70, 0.10),
    "traps": (0.95, 0.30, 0.60), "lower_back": (0.40, 0.90, 0.40), "glutes": (0.55, 0.27, 0.07),
    "quads": (0.20, 0.20, 0.90), "hamstrings": (0.90, 0.45, 0.45), "calves": (0.00, 0.50, 0.30),
    "head": (0.85, 0.85, 0.85), "body": (0.55, 0.55, 0.55),
}

# Anatomical landmarks (z, body height 1.8, measured on the mannequin)
ANKLE, CALF_TOP, KNEE_TOP = 0.10, 0.46, 0.52
GLUTE_FOLD, GLUTE_TOP, LB_TOP = 0.79, 0.98, 1.18
ABS_BOT, ABS_TOP = 0.85, 1.21
CHEST_TOP, CLAV_TOP = 1.43, 1.49
LATS_BOT, ARMPIT, DELT_BOT = 1.03, 1.32, 1.28
WRIST, ELBOW = 0.74, 1.00
SHOULDER_X, GLUTE_X = 0.155, 0.165
ABS_HALF_W = 0.062

ARM_M = {"biceps", "triceps", "forearms", "front_delts", "rear_delts"}
LEG_M = {"quads", "hamstrings", "calves"}


def subdivide(me, max_edge=0.05, passes=4):
    """Split edges longer than max_edge so boundaries can be painted precisely."""
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(me)
    for _ in range(passes):
        long_edges = [e for e in bm.edges if e.calc_length() > max_edge]
        if not long_edges:
            break
        bmesh.ops.subdivide_edges(bm, edges=long_edges, cuts=1, use_grid_fill=False)
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    bm.to_mesh(me)
    bm.free()
    me.update()


def ensure_materials(me):
    def ensure(name):
        m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
        m.use_nodes = True
        bsdf = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
        bsdf.inputs['Base Color'].default_value = (0.6, 0.6, 0.6, 1.0)  # app re-tints at runtime
        bsdf.inputs['Metallic'].default_value = 0.0
        bsdf.inputs['Roughness'].default_value = 0.85
        m.diffuse_color = (*DEBUG_COLORS[name], 1.0)  # viewport-only debug color
        return m
    me.materials.clear()
    for name in ORDER:
        me.materials.append(ensure(name))


def segment(me, old_names):
    """old_names: material slot names per face BEFORE re-painting (limb prior)."""
    idx = {n: i for i, n in enumerate(ORDER)}

    cent, norm, limb = [], [], []
    for p, mat in zip(me.polygons, old_names):
        c, n = p.center, p.normal
        cent.append((c.x, c.y, c.z))
        norm.append((n.x, n.y, n.z))
        ax = abs(c.x)
        if mat == "head":
            limb.append("HEAD")
        elif mat in ARM_M or (0.55 <= c.z <= 0.78 and ax > 0.19):  # hanging hands
            limb.append("ARM_L" if c.x < 0 else "ARM_R")
        elif mat in LEG_M or c.z < 0.52:
            limb.append("LEG_L" if c.x < 0 else "LEG_R")
        else:
            limb.append("TORSO")

    # per-limb y-midline per 2 cm z-bin (anterior/posterior split line)
    BIN = 0.02
    mid = defaultdict(lambda: [9e9, -9e9])
    for f, (x, y, z) in enumerate(cent):
        m = mid[(limb[f], int(z / BIN))]
        m[0] = min(m[0], y)
        m[1] = max(m[1], y)

    def yc(lb, z):
        b = int(z / BIN)
        for d in (0, 1, -1, 2, -2, 3, -3, 4, -4):
            if (lb, b + d) in mid:
                lo, hi = mid[(lb, b + d)]
                return (lo + hi) / 2.0
        return -0.03

    def shoulder_min_x(z):
        # delts' medial boundary follows the trap slope up toward the neck
        return 0.15 + max(0.0, z - 1.42) * 1.3

    def torso_upper(f):
        x, y, z = cent[f]
        nx, ny, nz = norm[f]
        ax = abs(x)
        if nz > 0.45 and z >= 1.38 and ax < 0.16:
            return "traps"
        if ny > 0.15:
            return "chest" if (z < CHEST_TOP and ax < 0.16) else "body"
        if ny < -0.15:
            w = 0.05 + 0.45 * (z - ARMPIT)
            return "traps" if ax < min(w, 0.17) else "body"
        return "body"

    def assign(f):
        x, y, z = cent[f]
        nx, ny, nz = norm[f]
        lb = limb[f]
        ax = abs(x)
        if lb == "HEAD" or z >= CLAV_TOP:
            return "head"
        if lb == "TORSO" and nz > 0.45 and 1.38 <= z < CLAV_TOP and ax < 0.16:
            return "traps"  # upper-trap ridge between neck and delts
        if lb.startswith("ARM"):
            if z < WRIST:
                return "body"  # hand
            if z < ELBOW:
                return "forearms"
            if z < DELT_BOT:
                return "biceps" if y > yc(lb, z) else "triceps"
            if ax < shoulder_min_x(z):
                return torso_upper(f)  # medial clavicle/upper-back, not delt
            return "front_delts" if y > yc(lb, z) else "rear_delts"
        if lb.startswith("LEG"):
            if z < ANKLE:
                return "body"  # foot
            if z < CALF_TOP:
                return "calves" if y < yc(lb, z) - 0.005 else "body"  # shin = neutral
            if z < KNEE_TOP:
                return "body"  # knee band
            if y > yc(lb, z) - 0.01:  # anterior thigh
                if z >= 0.74 and ax < 0.05 + 1.2 * (z - 0.74):
                    return "body"  # groin crease
                return "quads" if z < 0.82 else "body"
            if z < GLUTE_FOLD:
                return "hamstrings"
            return "glutes" if ax < GLUTE_X else "body"
        # TORSO
        if ax >= max(SHOULDER_X, shoulder_min_x(z)) and DELT_BOT <= z < CLAV_TOP:
            return "front_delts" if y > yc("TORSO", z) else "rear_delts"
        if ny > 0.15:  # front
            if ABS_TOP <= z < CHEST_TOP and ax < 0.16:
                return "chest"
            if ABS_BOT <= z < ABS_TOP:
                return "abs" if ax < ABS_HALF_W else "obliques"
            return "body"  # clavicle strip, lower belly, groin
        if ny < -0.15:  # back
            if z >= ARMPIT:
                w = 0.05 + 0.45 * (z - ARMPIT)
                return "traps" if ax < min(w, 0.17) else "body"
            if 1.20 <= z < ARMPIT and ax < 0.05:
                return "traps"  # mid-trap column
            if LATS_BOT <= z < ARMPIT and ax > 0.055 + (ARMPIT - z) * 0.12:
                return "lats"  # V-shaped wings
            if 0.96 <= z < LB_TOP and ax < 0.07:
                return "lower_back"
            if GLUTE_FOLD <= z < GLUTE_TOP:
                return "glutes" if ax < GLUTE_X else "body"
            if z < GLUTE_FOLD:
                return "hamstrings" if ax > 0.04 else "body"
            return "body"
        # side faces
        return "obliques" if ABS_BOT <= z < LB_TOP else "body"

    for p in me.polygons:
        p.material_index = idx[assign(p.index)]


def smooth(me, sync_passes=2, seq_passes=3):
    edge_faces = defaultdict(list)
    for p in me.polygons:
        for ek in p.edge_keys:
            edge_faces[ek].append(p.index)
    adj = defaultdict(list)
    for fs in edge_faces.values():
        if len(fs) == 2:
            a, b = fs
            adj[a].append(b)
            adj[b].append(a)
    labels = [p.material_index for p in me.polygons]
    for _ in range(sync_passes):
        new = labels[:]
        for f in range(len(labels)):
            ns = [labels[g] for g in adj[f]]
            if ns:
                top, n = Counter(ns).most_common(1)[0]
                if top != labels[f] and n >= 2:
                    new[f] = top
        labels = new
    for _ in range(seq_passes):  # in-place: converges, no checkerboard oscillation
        for f in range(len(labels)):
            ns = [labels[g] for g in adj[f]]
            if ns:
                top, n = Counter(ns).most_common(1)[0]
                if top != labels[f] and n >= 2:
                    labels[f] = top
    for p in me.polygons:
        p.material_index = labels[p.index]
        p.use_smooth = True
    me.update()


def export(obj):
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.export_scene.gltf(
        filepath=EXPORT_PATH, export_format='GLB', use_selection=True,
        export_apply=True, export_materials='EXPORT', export_normals=True,
        export_texcoords=False, export_tangents=False, export_animations=False,
        export_skins=False, export_morph=False, export_cameras=False, export_lights=False,
    )


if __name__ == "__main__":
    obj = bpy.data.objects[OBJECT_NAME]
    me = obj.data
    old_slot_names = [m.name for m in me.materials]
    subdivide(me)
    old_names = [old_slot_names[p.material_index] for p in me.polygons]
    ensure_materials(me)
    segment(me, old_names)
    smooth(me)
    export(obj)
    dist = Counter(ORDER[p.material_index] for p in me.polygons)
    print(json.dumps(dist, indent=1, sort_keys=True))
