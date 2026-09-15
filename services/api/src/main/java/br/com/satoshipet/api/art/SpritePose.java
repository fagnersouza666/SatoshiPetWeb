package br.com.satoshipet.api.art;

import java.util.Arrays;
import java.util.List;

/** Poses do conjunto visual do pet (PRD §8.2, ART-03). */
public enum SpritePose {
    IDLE,
    LOOK,
    SMILE,
    FEEDING,
    CELEBRATION,
    THINKING,
    UPSET,
    HUNGRY,
    CRITICAL,
    HIBERNATION,
    SLEEP,
    BIRTH,
    RETURN,
    REDUCED_MOTION;

    public static final int ATLAS_COLUMNS = 4;
    public static final int ATLAS_ROWS = 4;
    public static final int FRAME_SIZE_PX = 32;

    /** Ordem de recorte no atlas 4×4 (14 poses + 2 células reservadas). */
    public static List<SpritePose> atlasOrder() {
        return Arrays.asList(values());
    }

    public String fileName() {
        return name().toLowerCase() + ".png";
    }
}
