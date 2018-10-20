package net.ncguy.photon.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import net.ncguy.Photon;

public class PhotonSampleDebugRenderer {

    private ModelInstance inst;
    private ColorAttribute diffuse;
    private ModelInstance getInstance() {
        if (inst == null) {
            diffuse = ColorAttribute.createDiffuse(Color.WHITE);
            inst = new ModelInstance(new ModelBuilder().createSphere(1, 1, 1, 16, 16, new Material(diffuse), VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
        }
        return inst;
    }

    public void render(ModelBatch batch, Environment env) {
        if(Photon.samplePoints == null) {
            return;
        }

        for (Photon.SamplePoint samplePoint : Photon.samplePoints) {
            ModelInstance inst = getInstance();
            inst.transform.idt();

            Vector3 point = samplePoint.point.cpy();
            point.add(Photon.actualStepSize.cpy().scl(0.5f));
            inst.transform.translate(point);
            inst.transform.scale(0.32f, 0.32f, 0.32f);

            diffuse.color.set(samplePoint.colour).mul(Photon.globalLightIntensity);
            batch.render(inst, new Environment());
        }

    }
}
