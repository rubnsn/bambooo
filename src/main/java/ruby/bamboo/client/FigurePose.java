package ruby.bamboo.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * フィギュアの可動部解決・適用 (クライアントのみ)。
 * モデルクラスの ModelPart フィールドをリフレクションで列挙し、
 * フィールド名 (小文字) をキーに角度 [x,y,z] (度) を保持・適用する。
 */
@OnlyIn(Dist.CLIENT)
public final class FigurePose {

    private FigurePose() {
    }

    public record Part(String key, String label, ModelPart part) {
    }

    /** よくある部位を先頭に寄せるための優先度 */
    private static int priority(String key) {
        return switch (key) {
            case "head" -> 0;
            case "body" -> 1;
            case "rightarm" -> 2;
            case "leftarm" -> 3;
            case "rightleg" -> 4;
            case "leftleg" -> 5;
            default -> 10;
        };
    }

    /**
     * モデルの可動部を列挙する。同一インスタンスは1回のみ。
     */
    public static List<Part> discover(EntityModel<?> model) {
        Map<ModelPart, String> seen = new java.util.IdentityHashMap<>();
        List<Part> parts = new ArrayList<>();
        Class<?> cls = model.getClass();
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                try {
                    Object value = field.get(model);
                    if (!(value instanceof ModelPart part)) continue;
                    if (seen.containsKey(part)) continue;
                    String key = field.getName().toLowerCase(Locale.ROOT);
                    seen.put(part, key);
                    parts.add(new Part(key, field.getName(), part));
                } catch (Exception e) {
                    // 読めないフィールドは無視
                }
            }
            cls = cls.getSuperclass();
        }
        parts.sort((a, b) -> {
            int pa = priority(a.key());
            int pb = priority(b.key());
            if (pa != pb) return Integer.compare(pa, pb);
            return a.key().compareTo(b.key());
        });
        return parts;
    }

    /**
     * 操作対象のグループ親のみ列挙する。子パーツ (例: 頭の子であるくちばし) は
     * 親の変形に追従するのが自然なため、単独スライダーを出さない。
     */
    public static List<Part> groupRoots(EntityModel<?> model) {
        List<Part> all = discover(model);
        Map<ModelPart, Part> byInstance = new java.util.IdentityHashMap<>();
        for (Part p : all) {
            byInstance.putIfAbsent(p.part(), p);
        }
        List<Part> roots = new ArrayList<>();
        for (Part p : all) {
            if (!isDescendantOfOther(p.part(), byInstance)) roots.add(p);
        }
        // 頭部グループ (AgeableListModel.headParts と同義) は代表1件に集約する。
        // くちばし等の兄弟パーツは頭と一体で動かすため単独スライダーを出さない
        java.util.Set<ModelPart> headGroup = headGroupParts(model);
        if (!headGroup.isEmpty()) {
            String rep = null;
            for (Part p : roots) {
                if (headGroup.contains(p.part()) && p.key().equals("head")) {
                    rep = p.key();
                    break;
                }
            }
            if (rep == null) {
                for (Part p : roots) {
                    if (headGroup.contains(p.part())) {
                        rep = p.key();
                        break;
                    }
                }
            }
            final String repKey = rep;
            if (repKey != null) {
                roots.removeIf(p -> headGroup.contains(p.part()) && !p.key().equals(repKey));
            }
        }
        return roots;
    }

    /**
     * 頭部グループ (バニラの headParts と同義。ニワトリのくちばし・肉垂等)。
     * 幼体缩放用の区分だが、頭蓋に剛結すべき兄弟パーツの集合としても使える。
     */
    public static java.util.Set<ModelPart> headGroupParts(EntityModel<?> model) {
        java.util.Set<ModelPart> out = new java.util.HashSet<>();
        if (!(model instanceof net.minecraft.client.model.AgeableListModel)) return out;
        try {
            var m = net.minecraft.client.model.AgeableListModel.class.getDeclaredMethod("headParts");
            m.setAccessible(true);
            Object o = m.invoke(model);
            if (o instanceof Iterable<?> it) {
                for (Object e : it) {
                    if (e instanceof ModelPart part) out.add(part);
                }
            }
        } catch (Exception ignored) {
            // 取れなければグループなし扱い
        }
        return out;
    }

    private static boolean isDescendantOfOther(ModelPart part, Map<ModelPart, Part> byInstance) {
        for (ModelPart candidate : byInstance.keySet()) {
            if (candidate == part) continue;
            if (candidate.getAllParts().anyMatch(d -> d == part)) return true;
        }
        return false;
    }

    /** ポーズタグ ({part:[x,y,z]} 度) をモデルへ適用する。setupAnim 後に呼ぶこと。 */
    public static void apply(EntityModel<?> model, CompoundTag pose) {
        if (pose == null || pose.isEmpty()) return;
        List<Part> parts = discover(model);
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Part part : parts) {
            if (!pose.contains(part.key(), CompoundTag.TAG_LIST)) continue;
            ListTag list = pose.getList(part.key(), CompoundTag.TAG_FLOAT);
            if (list.size() < 3) continue;
            part.part().xRot = (float) Math.toRadians(list.getFloat(0));
            part.part().yRot = (float) Math.toRadians(list.getFloat(1));
            part.part().zRot = (float) Math.toRadians(list.getFloat(2));
            done.add(part.key());
        }
        // 頭部グループ追従: 明示角度のない兄弟パーツ (くちばし等) は代表と同角にする。
        // 子孫は親変形に自動追従するため触らない。明示角度があればそちらを優先する
        java.util.Set<ModelPart> group = headGroupParts(model);
        if (!group.isEmpty()) {
            Map<ModelPart, Part> byInstance = new java.util.IdentityHashMap<>();
            for (Part p : parts) {
                byInstance.putIfAbsent(p.part(), p);
            }
            ListTag groupAngles = null;
            if (pose.contains("head", CompoundTag.TAG_LIST)) {
                groupAngles = pose.getList("head", CompoundTag.TAG_FLOAT);
            } else {
                for (ModelPart member : group) {
                    Part mp = byInstance.get(member);
                    if (mp != null && pose.contains(mp.key(), CompoundTag.TAG_LIST)) {
                        groupAngles = pose.getList(mp.key(), CompoundTag.TAG_FLOAT);
                        break;
                    }
                }
            }
            if (groupAngles != null && groupAngles.size() >= 3) {
                float gx = (float) Math.toRadians(groupAngles.getFloat(0));
                float gy = (float) Math.toRadians(groupAngles.getFloat(1));
                float gz = (float) Math.toRadians(groupAngles.getFloat(2));
                for (ModelPart member : group) {
                    Part mp = byInstance.get(member);
                    if (mp == null || done.contains(mp.key())) continue;
                    if (isDescendantOfOther(member, byInstance)) continue;
                    member.xRot = gx;
                    member.yRot = gy;
                    member.zRot = gz;
                }
            }
        }
    }

    /** 角度マップ ({part:float[3]} 度) をタグ化する。 */
    public static CompoundTag toTag(Map<String, float[]> angles) {
        CompoundTag tag = new CompoundTag();
        for (var e : angles.entrySet()) {
            float[] v = e.getValue();
            if (v == null || v.length < 3) continue;
            if (v[0] == 0.0F && v[1] == 0.0F && v[2] == 0.0F) continue;
            ListTag list = new ListTag();
            list.add(FloatTag.valueOf(v[0]));
            list.add(FloatTag.valueOf(v[1]));
            list.add(FloatTag.valueOf(v[2]));
            tag.put(e.getKey(), list);
        }
        return tag;
    }

    /** タグを角度マップへ戻す。 */
    public static Map<String, float[]> fromTag(CompoundTag pose) {
        Map<String, float[]> map = new LinkedHashMap<>();
        if (pose == null) return map;
        for (String key : pose.getAllKeys()) {
            if (!pose.contains(key, CompoundTag.TAG_LIST)) continue;
            ListTag list = pose.getList(key, CompoundTag.TAG_FLOAT);
            if (list.size() < 3) continue;
            map.put(key, new float[] { list.getFloat(0), list.getFloat(1), list.getFloat(2) });
        }
        return map;
    }
}
