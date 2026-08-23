package ruby.bamboo.core.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;

/**
 * ParticleType 登録 (花びらパーティクル用に新規追加)。
 * <p>
 * 旧 SakuraPetal エンティティの ParticleType 化。
 * クライアント側の描画は ruby.bamboo.client.particle.PetalParticle
 * (RegisterParticleProvidersEvent で registerSpriteSet 接続)。
 * <p>
 * DeferredRegister 本体は {@link ruby.bamboo.BambooMod#PARTICLE_TYPES} を使用する。
 */
public final class BambooParticles {

    /**
     * 花びらパーティクル (旧 petal.png の texNum 1-3 相当)。
     * 1=桜属 / 2=広葉(緑・赤) / 3=広葉(黄・橙)。
     * 色は addParticle の速度引数 (xd,yd,zd) で RGB を渡す。
     */
    public static final RegistryObject<SimpleParticleType> PETAL_1 = BambooMod.PARTICLE_TYPES.register("petal_1",
            () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> PETAL_2 = BambooMod.PARTICLE_TYPES.register("petal_2",
            () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> PETAL_3 = BambooMod.PARTICLE_TYPES.register("petal_3",
            () -> new SimpleParticleType(false));

    /**
     * 静的初期化順序の保証用ダミー。BambooMod コンストラクタから呼ばれる。
     */
    public static void init() {
        // static フィールド初期化は本クラスがロードされた時点で完了している
    }
}