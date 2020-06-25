package com.raoulvdberge.refinedstorage.render;

import com.raoulvdberge.refinedstorage.util.RenderUtils;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CubeBuilder {
    public enum UvRotation {
        CLOCKWISE_0,
        CLOCKWISE_90,
        CLOCKWISE_180,
        CLOCKWISE_270
    }

    private static class Uv {
        private float xFrom;
        private float xTo;
        private float yFrom;
        private float yTo;
    }

    public static class Face {
        private final EnumFacing enumFacing;
        private final TextureAtlasSprite sprite;
        private int light;
        private UvRotation uvRotation = UvRotation.CLOCKWISE_0;

        public Face(EnumFacing enumFacing, TextureAtlasSprite sprite) {
            this.enumFacing = enumFacing;
            this.sprite = sprite;
        }

        public Face(EnumFacing enumFacing, TextureAtlasSprite sprite, UvRotation uvRotation) {
            this(enumFacing, sprite);

            this.uvRotation = uvRotation;
        }

        public Face(EnumFacing enumFacing, TextureAtlasSprite sprite, UvRotation uvRotation, int light) {
            this(enumFacing, sprite, uvRotation);

            this.light = light;
        }
    }

    private Vector3f from;
    private Vector3f to;
    private VertexFormat format = DefaultVertexFormats.ITEM;
    private final Map<EnumFacing, Face> faces = new EnumMap<>(EnumFacing.class);
    private int color = 0xFFFFFFFF;

    public CubeBuilder from(float x, float y, float z) {
        this.from = new Vector3f(x / 16, y / 16, z / 16);

        return this;
    }

    public CubeBuilder to(float x, float y, float z) {
        this.to = new Vector3f(x / 16, y / 16, z / 16);

        return this;
    }

    public CubeBuilder color(int color) {
        this.color = color;

        return this;
    }

    public CubeBuilder lightmap() {
        this.format = RenderUtils.getFormatWithLightMap(format);

        return this;
    }

    public CubeBuilder addFaces(Function<EnumFacing, Face> faceSupplier) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            addFace(faceSupplier.apply(facing));
        }

        return this;
    }

    public CubeBuilder addFace(Face face) {
        faces.put(face.enumFacing, face);

        return this;
    }

    public List<BakedQuad> bake() {
        List<BakedQuad> quads = new ArrayList<>();

        for (Map.Entry<EnumFacing, Face> entry : faces.entrySet()) {
            quads.add(bakeFace(entry.getKey(), entry.getValue()));
        }

        return quads;
    }

    private BakedQuad bakeFace(EnumFacing facing, Face cubeFace) {
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);

        builder.setTexture(cubeFace.sprite);
        builder.setQuadOrientation(facing);
        builder.setQuadTint(-1);
        builder.setApplyDiffuseLighting(true);

        Uv uv = getDefaultUv(facing, cubeFace.sprite, from.x, from.y, from.z, to.x, to.y, to.z);

        switch (facing) {
            case DOWN:
                addVertexTopRight(builder, cubeFace, to.x, from.y, from.z, uv);
                addVertexBottomRight(builder, cubeFace, to.x, from.y, to.z, uv);
                addVertexBottomLeft(builder, cubeFace, from.x, from.y, to.z, uv);
                addVertexTopLeft(builder, cubeFace, from.x, from.y, from.z, uv);
                break;
            case UP:
                addVertexTopLeft(builder, cubeFace, from.x, to.y, from.z, uv);
                addVertexBottomLeft(builder, cubeFace, from.x, to.y, to.z, uv);
                addVertexBottomRight(builder, cubeFace, to.x, to.y, to.z, uv);
                addVertexTopRight(builder, cubeFace, to.x, to.y, from.z, uv);
                break;
            case NORTH:
                addVertexBottomRight(builder, cubeFace, to.x, to.y, from.z, uv);
                addVertexTopRight(builder, cubeFace, to.x, from.y, from.z, uv);
                addVertexTopLeft(builder, cubeFace, from.x, from.y, from.z, uv);
                addVertexBottomLeft(builder, cubeFace, from.x, to.y, from.z, uv);
                break;
            case SOUTH:
                addVertexBottomLeft(builder, cubeFace, from.x, to.y, to.z, uv);
                addVertexTopLeft(builder, cubeFace, from.x, from.y, to.z, uv);
                addVertexTopRight(builder, cubeFace, to.x, from.y, to.z, uv);
                addVertexBottomRight(builder, cubeFace, to.x, to.y, to.z, uv);
                break;
            case WEST:
                addVertexTopLeft(builder, cubeFace, from.x, from.y, from.z, uv);
                addVertexTopRight(builder, cubeFace, from.x, from.y, to.z, uv);
                addVertexBottomRight(builder, cubeFace, from.x, to.y, to.z, uv);
                addVertexBottomLeft(builder, cubeFace, from.x, to.y, from.z, uv);
                break;
            case EAST:
                addVertexBottomRight(builder, cubeFace, to.x, to.y, from.z, uv);
                addVertexBottomLeft(builder, cubeFace, to.x, to.y, to.z, uv);
                addVertexTopLeft(builder, cubeFace, to.x, from.y, to.z, uv);
                addVertexTopRight(builder, cubeFace, to.x, from.y, from.z, uv);
                break;
        }

        return builder.build();
    }

    private Uv getDefaultUv(EnumFacing face, TextureAtlasSprite texture, float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        Uv uv = new Uv();

        switch (face) {
            case DOWN:
                uv.xFrom = texture.getInterpolatedU(fromX * 16);
                uv.yFrom = texture.getInterpolatedV(16 - fromZ * 16);
                uv.xTo = texture.getInterpolatedU(toX * 16);
                uv.yTo = texture.getInterpolatedV(16 - toZ * 16);
                break;
            case UP:
                uv.xFrom = texture.getInterpolatedU(fromX * 16);
                uv.yFrom = texture.getInterpolatedV(fromZ * 16);
                uv.xTo = texture.getInterpolatedU(toX * 16);
                uv.yTo = texture.getInterpolatedV(toZ * 16);
                break;
            case NORTH:
                uv.xFrom = texture.getInterpolatedU(16 - fromX * 16);
                uv.yFrom = texture.getInterpolatedV(16 - fromY * 16);
                uv.xTo = texture.getInterpolatedU(16 - toX * 16);
                uv.yTo = texture.getInterpolatedV(16 - toY * 16);
                break;
            case SOUTH:
                uv.xFrom = texture.getInterpolatedU(fromX * 16);
                uv.yFrom = texture.getInterpolatedV(16 - fromY * 16);
                uv.xTo = texture.getInterpolatedU(toX * 16);
                uv.yTo = texture.getInterpolatedV(16 - toY * 16);
                break;
            case WEST:
                uv.xFrom = texture.getInterpolatedU(fromZ * 16);
                uv.yFrom = texture.getInterpolatedV(16 - fromY * 16);
                uv.xTo = texture.getInterpolatedU(toZ * 16);
                uv.yTo = texture.getInterpolatedV(16 - toY * 16);
                break;
            case EAST:
                uv.xFrom = texture.getInterpolatedU(16 - toZ * 16);
                uv.yFrom = texture.getInterpolatedV(16 - fromY * 16);
                uv.xTo = texture.getInterpolatedU(16 - fromZ * 16);
                uv.yTo = texture.getInterpolatedV(16 - toY * 16);
                break;
        }

        return uv;
    }

    private void addVertexTopLeft(UnpackedBakedQuad.Builder builder, Face face, float x, float y, float z, Uv uv) {
        float u;
        float v;

        switch (face.uvRotation) {
            default:
            case CLOCKWISE_0:
                u = uv.xFrom;
                v = uv.yFrom;
                break;
            case CLOCKWISE_90:
                u = uv.xFrom;
                v = uv.yTo;
                break;
            case CLOCKWISE_180:
                u = uv.xTo;
                v = uv.yTo;
                break;
            case CLOCKWISE_270:
                u = uv.xTo;
                v = uv.yFrom;
                break;
        }

        addVertex(builder, face, x, y, z, u, v);
    }

    private void addVertexTopRight(UnpackedBakedQuad.Builder builder, Face face, float x, float y, float z, Uv uv) {
        float u;
        float v;

        switch (face.uvRotation) {
            default:
            case CLOCKWISE_0:
                u = uv.xTo;
                v = uv.yFrom;
                break;
            case CLOCKWISE_90:
                u = uv.xFrom;
                v = uv.yFrom;
                break;
            case CLOCKWISE_180:
                u = uv.xFrom;
                v = uv.yTo;
                break;
            case CLOCKWISE_270:
                u = uv.xTo;
                v = uv.yTo;
                break;
        }

        addVertex(builder, face, x, y, z, u, v);
    }

    private void addVertexBottomRight(UnpackedBakedQuad.Builder builder, Face face, float x, float y, float z, Uv uv) {
        float u;
        float v;

        switch (face.uvRotation) {
            default:
            case CLOCKWISE_0:
                u = uv.xTo;
                v = uv.yTo;
                break;
            case CLOCKWISE_90:
                u = uv.xTo;
                v = uv.yFrom;
                break;
            case CLOCKWISE_180:
                u = uv.xFrom;
                v = uv.yFrom;
                break;
            case CLOCKWISE_270:
                u = uv.xFrom;
                v = uv.yTo;
                break;
        }

        addVertex(builder, face, x, y, z, u, v);
    }

    private void addVertexBottomLeft(UnpackedBakedQuad.Builder builder, Face face, float x, float y, float z, Uv uv) {
        float u;
        float v;

        switch (face.uvRotation) {
            default:
            case CLOCKWISE_0:
                u = uv.xFrom;
                v = uv.yTo;
                break;
            case CLOCKWISE_90:
                u = uv.xTo;
                v = uv.yTo;
                break;
            case CLOCKWISE_180:
                u = uv.xTo;
                v = uv.yFrom;
                break;
            case CLOCKWISE_270:
                u = uv.xFrom;
                v = uv.yFrom;
                break;
        }

        addVertex(builder, face, x, y, z, u, v);
    }

    private void addVertex(UnpackedBakedQuad.Builder builder, Face face, float x, float y, float z, float u, float v) {
        VertexFormat vertexFormat = builder.getVertexFormat();

        for (int i = 0; i < vertexFormat.getElementCount(); i++) {
            VertexFormatElement e = vertexFormat.getElement(i);

            switch (e.getUsage()) {
                case POSITION:
                    builder.put(i, x, y, z);
                    break;
                case NORMAL:
                    builder.put(i, face.enumFacing.getXOffset(), face.enumFacing.getYOffset(), face.enumFacing.getZOffset());
                    break;
                case COLOR:
                    float r = (color >> 16 & 0xFF) / 255F;
                    float g = (color >> 8 & 0xFF) / 255F;
                    float b = (color & 0xFF) / 255F;
                    float a = (color >> 24 & 0xFF) / 255F;

                    builder.put(i, r, g, b, a);
                    break;
                case UV:
                    if (e.getIndex() == 0) {
                        builder.put(i, u, v);
                    } else {
                        builder.put(i, (float) (face.light * 0x20) / 0xFFFF, (float) (face.light * 0x20) / 0xFFFF);
                    }

                    break;
                default:
                    builder.put(i);
                    break;
            }
        }
    }
}
