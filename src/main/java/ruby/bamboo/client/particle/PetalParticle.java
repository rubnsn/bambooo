package ruby.bamboo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
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
 * <li>空気抵抗を受けるわずかな回転 (ランダムトルクを減衰させつつ積算)</li>
 * <li>環境風 PetalWind (風向・風速は時刻で緩やかに変化、突風時は強まる。ローカルのみ)</li>
 * <li>着地で回転停止、水中で浮遊 (stopFall 相当)</li>
 * </ul>
 * 色は addParticle の速度引数 (xd,yd,zd) で RGB を受け取る。
 */
public class PetalParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private float swayPhase;
    private float swaySpeed;
    /** 空気抵抗で減衰する回転角速度とその積算 (roll に重畳) */
    private float spin;
    private float spinSum;

    /** Wind由来パーティクルの一時的な風ベクトル受け渡し (ThreadLocalでaddParticle前にset) */
    private static final ThreadLocal<Vec3> NEXT_WIND = new ThreadLocal<>();

    /** WindEntity側から呼ぶ: 次に生成する petal に風を適用する */
    public static void pushWind(Vec3 wind) {
        if (wind != null) {
            NEXT_WIND.set(wind);
        }
    }

    public static void clearWind() {
        NEXT_WIND.remove();
    }

    protected PetalParticle(ClientLevel level, double x, double y, double z,
            double colorR, double colorG, double colorB, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.setSprite(sprites.get(level.random));

        // 色 (葉バリアント色: 白ベース×乗算。バニラ葉は白 0xFFFFFF のまま)
        this.setColor((float) colorR, (float) colorG, (float) colorB);

        // 初速: Wind由来なら旧 SakuraPetal.setMotion を再現、非Windはランダム漂い
        Vec3 wind = NEXT_WIND.get();
        if (wind != null) {
            // 旧 SakuraPetal.setMotion 相当
            // randomF = (rand+rand+1)*0.15 → 0.15-0.45, sqで正規化
            float randomF = (float) (level.random.nextFloat() + level.random.nextFloat() + 1.0D) * 0.15F;
            double sq = Math.sqrt(wind.x * wind.x + wind.y * wind.y + wind.z * wind.z);
            if (sq < 1.0e-4) {
                sq = 1.0;
            }
            this.xd = wind.x / sq * randomF;
            this.yd = wind.y / sq;
            // 元の花びらは y=-0.01相当の落下を維持しつつ風のyを加える。水平風のみなら -0.01を加算
            if (Math.abs(wind.y) < 1.0e-4) {
                this.yd = -0.01;
            }
            this.zd = wind.z / sq * randomF;
            // 軽いランダム揺らぎを加える
            this.xd += (level.random.nextFloat() - 0.5) * 0.02;
            this.zd += (level.random.nextFloat() - 0.5) * 0.02;
            NEXT_WIND.remove();
        } else {
            // 非Windはランダム漂い + 環境風 (ローカル、PetalWind)
            Vec3 env = PetalWind.getWind(level);
            this.xd = (level.random.nextFloat() - 0.5) * 0.1 + env.x;
            this.yd = -0.01;
            this.zd = (level.random.nextFloat() - 0.5) * 0.1 + env.z;
        }

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
        if (this.level.getFluidState(this.posAt(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            this.yd *= 0.8D;
            this.xd *= 0.9D;
            this.zd *= 0.9D;
        }

        // 環境風 (風向・風速は時刻で緩やかに変化、突風時は強まる。ローカルのみ)
        Vec3 env = PetalWind.getWind(this.level);
        this.xd += env.x * 0.02D;
        this.zd += env.z * 0.02D;
        // ひらひら: swayに連動した微小な横揺れ (揚力っぽさ)
        this.xd += Math.cos(this.swayPhase) * 0.0006D;
        this.zd += Math.sin(this.swayPhase * 0.9D) * 0.0006D;

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

        // 空気抵抗を受けるわずかな回転: ランダムトルクを抵抗で減衰させつつ積算し、swayに重畳
        this.spin += (this.level.random.nextFloat() - 0.5F) * 0.02F;
        double hSpeed = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
        this.spin += (float) hSpeed * 0.02F * (Math.cos(this.swayPhase) >= 0.0 ? 1.0F : -1.0F);
        this.spin *= 0.95F;
        if (this.spin > 0.15F) {
            this.spin = 0.15F;
        } else if (this.spin < -0.15F) {
            this.spin = -0.15F;
        }
        if (this.onGround) {
            this.spin *= 0.6F;
        }
        this.spinSum += this.spin;

        // スイング回転 (roll を往復させる) + 空気抵抗回転の重畳
        this.swayPhase += this.swaySpeed;
        float prevRoll = this.roll;
        this.roll = (float) Math.sin(this.swayPhase) * 0.6F + this.spinSum;
        this.oRoll = prevRoll;
    }

    private BlockPos.MutableBlockPos posAt(double x, double y, double z) {
        return new BlockPos.MutableBlockPos(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Provider (registerSpriteSet 用) */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public PetalParticle createParticle(SimpleParticleType type,
                ClientLevel level, double x, double y, double z,
                double colorR, double colorG, double colorB) {
            return new PetalParticle(level, x, y, z, colorR, colorG, colorB, this.sprites);
        }
    }
}