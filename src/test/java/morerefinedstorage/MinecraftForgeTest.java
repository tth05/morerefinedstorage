package morerefinedstorage;

import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

public interface MinecraftForgeTest {

    @BeforeAll
    static void setup() {
        Bootstrap.register();
    }
}
