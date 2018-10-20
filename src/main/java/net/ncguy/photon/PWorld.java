package net.ncguy.photon;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PWorld {

    private RenderableProvider provider;

    public PWorld(RenderableProvider provider) {
        this.provider = provider;
    }

    final List<Triangle> tris = new ArrayList<>();

    public void invalidate() {
        tris.clear();
    }

    public void getWorldTris(Consumer<List<Triangle>> callback) {

        System.out.println("Fetching world tris");

        if(!tris.isEmpty()) {
            callback.accept(tris);
            return;
        }

        System.out.println("No cached tris, calculating...");

        Array<Renderable> world = new Array<>();
        provider.getRenderables(world, new Pool<Renderable>() {
            @Override
            protected Renderable newObject() {
                return new Renderable();
            }
        });

        List<Renderable> renderables = new ArrayList<>();
        world.forEach(renderables::add);

        List<Mesh> meshes = renderables.stream()
                .map(r -> r.meshPart.mesh)
                .distinct()
                .collect(Collectors.toList());

        List<Future<List<Triangle>>> futures = meshes.stream()
                .map(m -> ThreadPool.submit(() -> getTrisFromMesh(m)))
                .collect(Collectors.toList());

        while(!futures.isEmpty()) {
            futures.removeIf(Future::isCancelled);
            for (Future<List<Triangle>> next : futures) {
                if (next.isDone()) {
                    try {
                        tris.addAll(next.get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
            futures.removeIf(Future::isDone);
        }

        System.out.println("Calcultions finished, resuming...");

        callback.accept(tris);
    }

    public List<Triangle> getTrisFromMesh(Mesh mesh) {
        System.out.println("Calculating tris from mesh: " + mesh.toString());
        VertexAttribute posAttr = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
        VertexAttribute norAttr = mesh.getVertexAttribute(VertexAttributes.Usage.Normal);

        int vertSize = mesh.getVertexSize() / Float.BYTES;
        FloatBuffer verticesBuffer = mesh.getVerticesBuffer();

        List<Vertex> vertices = new ArrayList<>();
        while(verticesBuffer.hasRemaining()) {
            Vertex vertex = new Vertex();
            vertex.data = new float[vertSize];
            try{
                verticesBuffer.get(vertex.data, 0, vertSize);
            }catch (Exception e) {
                e.printStackTrace();
            }
            vertices.add(vertex);
        }


        vertices.forEach(v -> v.extractFromData(posAttr, norAttr));

        List<Triangle> tris = new ArrayList<>();
        ShortBuffer indicesBuffer = mesh.getIndicesBuffer().duplicate();
        while(indicesBuffer.hasRemaining()) {
            Triangle tri = new Triangle();
            tri.a = vertices.get(indicesBuffer.get());
            tri.b = vertices.get(indicesBuffer.get());
            tri.c = vertices.get(indicesBuffer.get());
            tris.add(tri);
        }

        // reset buffers
        verticesBuffer.position(0);
        indicesBuffer.position(0);

        return tris;
    }

    public static class Vertex {
        public float posX;
        public float posY;
        public float posZ;

        public float norX;
        public float norY;
        public float norZ;

        public transient float[] data;

        @SuppressWarnings("PointlessArithmeticExpression")
        public void extractFromData(VertexAttribute posAttr, VertexAttribute norAttr) {

            int posOffset = posAttr.offset / Float.BYTES;
            int norOffset = norAttr.offset / Float.BYTES;

            posX = data[posOffset + 0];
            posY = data[posOffset + 1];
            posZ = data[posOffset + 2];

            norX = data[norOffset + 0];
            norY = data[norOffset + 1];
            norZ = data[norOffset + 2];
        }


        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String prefix) {
            StringBuilder sb = new StringBuilder();

            sb.append(prefix).append("Pos:{").append(posX).append(", ").append(posY).append(", ").append(posZ).append("}\n");
            sb.append(prefix).append("Nor:{").append(norX).append(", ").append(norY).append(", ").append(norZ).append("}");

            return sb.toString();
        }
    }

    public static class Triangle {
        public Vertex a;
        public Vertex b;
        public Vertex c;

        public float norX;
        public float norY;
        public float norZ;

        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String prefix) {
            StringBuilder sb = new StringBuilder();

            sb.append(prefix).append("Triangle").append("\n");
            sb.append(a.toString(prefix + "\t")).append("\n");
            sb.append(b.toString(prefix + "\t")).append("\n");
            sb.append(c.toString(prefix + "\t"));

            return sb.toString();
        }
    }

}
