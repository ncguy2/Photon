package net.ncguy.photon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.badlogic.gdx.graphics.GL20.GL_RGBA;
import static com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE;
import static com.badlogic.gdx.graphics.GL30.GL_RGBA8;

public class PhotonOffline {

    public boolean enabled = true;
    public final Vector3 targetOrigin = new Vector3();
    public final Vector3 targetStepSize = new Vector3();

    public final Vector3 actualOrigin = new Vector3();
    public final Vector3 actualStepSize = new Vector3();
    public final Vector3 actualExtents = new Vector3();
    public float globalLightIntensity = 8;

    public int volumeTextureHandle = -1;
    public List<SamplePoint> samplePoints;

    public boolean CanRender() {
        return enabled && volumeTextureHandle >= 0;
    }

    public void BuildTexture(final List<PWorld.Triangle> tris, List<PLight> lights, int resolution) {
        BuildTexture(tris, lights, resolution, targetOrigin, targetStepSize);
    }

    public void BuildTexture(final List<PWorld.Triangle> tris, List<PLight> lights, int resolution, Vector3 origin, Vector3 stepSize) {
        System.out.println("Compositing GL texture");
        samplePoints = GetSamplePoints(tris, lights, resolution, origin, stepSize);
        ByteBuffer texels = GetTexelData(samplePoints, resolution);
        BuildTexture(texels, resolution);
    }

    public void BuildTexture(ByteBuffer texels, int resolution) {
        Gdx.app.postRunnable(() -> {
            System.out.println("Building GL texture");

            if(volumeTextureHandle < 0) {
                volumeTextureHandle = Gdx.gl.glGenTexture();
            }

            Gdx.gl.glBindTexture(GL30.GL_TEXTURE_3D, volumeTextureHandle);
            Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_3D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
            Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_3D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
            Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_3D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_REPEAT);
            Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_3D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_REPEAT);
            Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_3D, GL30.GL_TEXTURE_WRAP_R, GL30.GL_REPEAT);
            Gdx.gl30.glTexImage3D(GL30.GL_TEXTURE_3D, 0, GL_RGBA8, resolution, resolution, resolution, 0, GL_RGBA, GL_UNSIGNED_BYTE, texels);
        });
    }

    public ByteBuffer GetTexelData(List<SamplePoint> samplePoints, int resolution) {
        System.out.println("Populate texel data buffer");
        ByteBuffer byteBuffer = BufferUtils.newByteBuffer((resolution * resolution * resolution) * 4);
        Function<Float, Byte> parse = f -> (byte) (MathUtils.clamp(f * 255, 0f, 255));
        byteBuffer.position(0);
        for (SamplePoint samplePoint : samplePoints) {
            Color v = samplePoint.colour;
            byteBuffer.put(parse.apply(v.r));
            byteBuffer.put(parse.apply(v.g));
            byteBuffer.put(parse.apply(v.b));
            byteBuffer.put((byte) 255);
        }
        byteBuffer.position(0);
        return byteBuffer;
    }

    public List<SamplePoint> GetSamplePoints(final List<PWorld.Triangle> tris, List<PLight> lights, int resolution, Vector3 origin, Vector3 stepSize) {

        actualOrigin.set(origin);
        actualStepSize.set(stepSize);
        int halfRes = resolution / 2;

        actualExtents.set(halfRes, halfRes, halfRes).scl(stepSize);

        System.out.println("Building GL texel data");
        System.out.println("\tByte buffer allocated");

        List<SamplePoint> samplePoints = new ArrayList<>();

        for(int x = -halfRes; x < halfRes; x++) {
            for(int y = -halfRes; y < halfRes; y++) {
                for(int z = -halfRes; z < halfRes; z++) {
                    samplePoints.add(new SamplePoint(new Vector3(x, y, z).scl(stepSize).add(origin)));
                }
            }
        }
        System.out.println("\tSample points calculated");

        for (SamplePoint p : samplePoints) {
            p.colour.set(SamplePointVisibility(p.point, lights, tris));
        }

        return samplePoints;
    }

    public Color SamplePointVisibility(Vector3 point, List<PLight> lightPositions, final List<PWorld.Triangle> tris) {
        System.out.println("\t\t" + point.toString());
        List<Color> colMap = lightPositions.stream()
                .map(l -> getLightInfluence(point, tris, l))
                .collect(Collectors.toList());

        boolean first = true;
        Color target = new Color();
        for (Color col : colMap) {
            if(first) {
                first = false;
                target.set(col);
            }else{
                target.lerp(col, 0.5f);
            }
        }

        return target;
    }

    public Color getLightInfluence(Vector3 point, List<PWorld.Triangle> tris, PLight light) {
        System.out.println("\t\t\t" + light.position.toString());
        Vector3 intersection = new Vector3();

        Ray ray = new Ray();
        ray.origin.set(point);
        ray.direction.set(light.position.cpy().sub(point).nor());

        float distanceToLight = point.dst(light.position);
        if(intersects(ray, tris, intersection)) {
            float distanceToIntersection = point.dst(intersection);

            if(distanceToIntersection < distanceToLight) {
                return Color.BLACK;
            }
        }

        float attenuation = 1f / (distanceToLight * distanceToLight);
        return light.colour.cpy().mul(attenuation);
    }

    public boolean intersects(Ray ray, List<PWorld.Triangle> tris) {
        return intersects(ray, tris, null);
    }

    public boolean intersects(Ray ray, List<PWorld.Triangle> tris, Vector3 intersection) {
        List<VectorTriangle> vecTris = tris.stream().map(VectorTriangle::new).collect(Collectors.toList());

        boolean intersects = false;
        float closestIntersectionDst = Float.MAX_VALUE;

        Vector3 closestIntersection;
        if(intersection != null) {
            closestIntersection = new Vector3();
        }else{
            closestIntersection = null;
        }

        for (VectorTriangle tri : vecTris) {
            if(Intersector.intersectRayTriangle(ray, tri.a, tri.b, tri.c, intersection)) {
                if(intersection == null) {
                    return true;
                }

                intersects = true;

                float distance = intersection.dst(ray.origin);
                if(distance < closestIntersectionDst) {
                    closestIntersectionDst = distance;
                    closestIntersection.set(intersection);
                }
            }
        }

        if(intersection != null) {
            intersection.set(closestIntersection);
        }
        return intersects;
    }

    public void dispose() {
        if(volumeTextureHandle >= 0) {
            Gdx.gl.glDeleteTexture(volumeTextureHandle);
            volumeTextureHandle = -1;
        }
        if(samplePoints != null) {
            samplePoints.clear();
            samplePoints = null;
        }
    }

    public static class VectorTriangle {

        public final PWorld.Triangle tri;
        public final Vector3 a;
        public final Vector3 b;
        public final Vector3 c;

        public VectorTriangle(PWorld.Triangle tri) {
            this.tri = tri;
            this.a = new Vector3(tri.a.posX, tri.a.posY, tri.a.posZ);
            this.b = new Vector3(tri.b.posX, tri.b.posY, tri.b.posZ);
            this.c = new Vector3(tri.c.posX, tri.c.posY, tri.c.posZ);
        }
    }

    public static class SamplePoint {

        public final Vector3 point;
        public final Color colour;

        public SamplePoint(Vector3 point) {
            this(point, new Color());
        }

        public SamplePoint(Vector3 point, Color colour) {
            this.point = point;
            this.colour = colour;
        }
    }
    
}
