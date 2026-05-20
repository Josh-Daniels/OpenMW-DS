package org.openmw.utils

import org.openmw.Constants
import java.io.File

fun patchShaders() {
    val groundcoverFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/groundcover.frag")
    var content = groundcoverFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#define GROUNDCOVER","#define GROUNDCOVER\n#pragma import_defines(WRITE_NORMALS, CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        content = content.replace("#if !@disableNormals", "#if defined(WRITE_NORMALS) && WRITE_NORMALS")
        groundcoverFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val groundcoverVert = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/groundcover.vert")
    content = groundcoverVert.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#version 120\n","#version 120\n#pragma import_defines(CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        groundcoverVert.writeText(content + "\n#pragma CONVERTED\n")
    }

    val objectsFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/objects.frag")
    content = objectsFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("(FORCE_OPAQUE, DISTORTION)", "(FORCE_OPAQUE, DISTORTION, FORCE_PPL, WRITE_NORMALS, CLASSIC_FALLOFF, MAX_LIGHTS)")
        content = content.replace("#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)", "#if defined(FORCE_PPL)\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || FORCE_PPL)\n#else\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)\n#endif")
        content = content.replace("#if !defined(FORCE_OPAQUE) && !@disableNormals", "#if !defined(FORCE_OPAQUE) && defined(WRITE_NORMALS) && WRITE_NORMALS")
        objectsFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val objectsVert = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/objects.vert")
    content = objectsVert.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#version 120","#version 120\n#pragma import_defines(FORCE_PPL, CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        content = content.replace("#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)", "#if defined(FORCE_PPL)\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || FORCE_PPL)\n#else\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)\n#endif")
        objectsVert.writeText(content + "\n#pragma CONVERTED\n")
    }

    val terrainFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/terrain.frag")
    content = terrainFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#version 120","#version 120\n#pragma import_defines(WRITE_NORMALS, FORCE_PPL, CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        content = content.replace("#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)", "#if defined(FORCE_PPL)\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || FORCE_PPL)\n#else\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)\n#endif")
        content = content.replace("#if !@disableNormals && @writeNormals", "#if defined(WRITE_NORMALS) && WRITE_NORMALS && @writeNormals")
        terrainFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val terrainVert = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/terrain.vert")
    content = terrainVert.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#version 120","#version 120\n#pragma import_defines(FORCE_PPL, CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        content = content.replace("#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)", "#if defined(FORCE_PPL)\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || FORCE_PPL)\n#else\n#define PER_PIXEL_LIGHTING (@normalMap || @specularMap || @forcePPL)\n#endif")
        terrainVert.writeText(content + "\n#pragma CONVERTED\n")
    }

    val waterFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/water.frag")
    content = waterFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#version 120","#version 120\n#pragma import_defines(WRITE_NORMALS, CLASSIC_FALLOFF, MAX_LIGHTS)\n")
        content = content.replace("#if !@disableNormals", "#if defined(WRITE_NORMALS) && WRITE_NORMALS")
        waterFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val lighting = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/light/lighting.glsl")
    content = lighting.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#if !@classicFalloff && !@lightingMethodFFP", "#if defined(CLASSIC_FALLOFF) && !CLASSIC_FALLOFF && !@lightingMethodFFP")
        lighting.writeText(content + "\n#pragma CONVERTED\n")
    }

    val lightingUtil =
        File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/light/lighting_util.glsl")
    content = lightingUtil.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#define LIB_LIGHTING_UTIL", "#define LIB_LIGHTING_UTIL\n#pragma import_defines(CLAMP_LIGHTING)")
        content = content.replace("#if @clamp", "#if defined(CLAMP_LIGHTING) && CLAMP_LIGHTING")
        content = content.replace("uniform int PointLightIndex[@maxLights];", "#if defined(MAX_LIGHTS)\nuniform int PointLightIndex[MAX_LIGHTS];\n#else\nuniform int PointLightIndex[@maxLights];\n#endif")
        content = content.replace("uniform mat4 LightBuffer[@maxLights];", "#if defined(MAX_LIGHTS)\nuniform mat4 LightBuffer[MAX_LIGHTS];\n#else\nuniform mat4 LightBuffer[@maxLights];\n#endif")
        content = content.replace("#if !@classicFalloff && !@lightingMethodFFP", "#if defined(CLASSIC_FALLOFF) && !CLASSIC_FALLOFF && !@lightingMethodFFP")
        lightingUtil.writeText(content + "\n#pragma CONVERTED\n")
    }

    val alpha = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/material/alpha.glsl")
    content = alpha.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("textureSize2D(diffuseMap, 0);", "vec2(256.0);")
        alpha.writeText(content + "\n#pragma CONVERTED\n")
    }

}