package net.ncguy.photon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

public class PLight {

    public final Vector3 position;
    public final Color colour;

    public PLight(Vector3 position, Color colour) {
        this.position = position;
        this.colour = colour;
    }
}
