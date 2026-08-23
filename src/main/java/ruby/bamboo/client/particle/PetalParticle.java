package ruby.bamboo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import ruby.bamboo.core.init.BambooParticles;

/**
 * 花びらパーティクル (旧 SakuraPetal エンティティの移植)。
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>初速: y=-0.01、水平 ±0.05 ランダム</li>
 * <li>毎tick: gravity -0.004、全軸 drag ×0.95</li>
 * <li>寿命 60+rand(120) tick</li>
 * <li>スイング回転 (旧 rx/ry/rz の往復相当 → roll の sin 揺れで再現)</li>
 * <li>着地で回転停止、水中で浮遊 (stopFall 相当)</li>
 * </ul>
 * 色は addParticle の速度引数 (xd,yd,zd) で RGB を受け取る。
 */
public class PetalParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private float swayPhase;
    private float swaySpeed;

    protected PetalParticle(ClientLevel level, double x, double y, double z,
            double colorR, double colorG, double colorB, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.setSprite(sprites.get(level.random));

        // 色 (葉バリアント色)
        this.setColor((float) colorR, (float) colorG, (float) colorB);

        // 初速 (旧コンストラクタ相当)
        this.xd = (level.random.nextFloat() - 0.5) * 0.1;
        this.yd = -0.01;
        this.zd = (level.random.nextFloat() - 0.5) * 0.1;

        // 寿命 60+rand(120)
        this.lifetime = level.random.nextInt(120) + 60;

        // サイズ・スイング
        this.quadSize = 0.1F + level.random.nextFloat() * 0.05F;
        this.swayPhase = level.random.nextFloat() * (float) (Math.PI * 2);
        this.swaySpeed = 0.025F + level.random.nextFloat() * 0.02F;
        this.hasPhysics = true;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // 重力 (旧 motionY -= 0.004)
        this.yd -= 0.004D;

        // 水中では浮遊 (旧 stopFall 相当)
        if (this.level.getFluidState(this.posAt(this.x, this.y, this.z)).is(net.minecraft.tags.FluidTags.WATER)) {
            this.yd *= 0.8D;
            this.xd *= 0.9D;
            this.zd *= 0.9D;
        }

        this.move(this.xd, this.yd, this.zd);

        // 全軸 drag ×0.95
        this.xd *= 0.95D;
        this.yd *= 0.95D;
        this.zd *= 0.95D;

        // 着地したら回転と移動をほぼ止める (旧 onGround → rad=0.0001 相当)
        if (this.onGround) {
            this.xd *= 0.7D;
            this.zd *= 0.7D;
        }

        // スイング回転 (roll を往復させる)
        this.swayPhase += this.swaySpeed;
        float prevRoll = this.roll;
        this.roll = (float) Math.sin(this.swayPhase) * 0.6F;
        this.oRoll = prevRoll;
    }

    private net.minecraft.core.BlockPos.MutableBlockPos posAt(double x, double y, double z) {
        return new net.minecraft.core.BlockPos.MutableBlockPos(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Provider (registerSpriteSet 用) */
    public record Provider(SpriteSet sprites) implements net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType> {
        @Override
        public PetalParticle createParticle(net.minecraft.core.particles.SimpleParticleType type,
                ClientLevel level, double x, double y, double z,
                double colorR, double colorG, double colorB) {
            return new PetalParticle(level, x, y, z, colorR, colorG, colorB, this.sprites);
        }
    }
}