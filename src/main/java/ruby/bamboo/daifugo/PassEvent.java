package ruby.bamboo.daifugo;

/**
 * パスした時点の場の条件記録 (カードカウンティング・定石「相手のパスは確定情報」用)。
 * スルーパス禁止により、パスした席は場が流れるまで出せないため、
 * 場が流れるまで (clearField/startRound で消えるまで) 有効な確定情報となる。
 * 場の条件 (枚数・役種・実効序列) が一致する場にだけ適用すること。
 *
 * @param seat パスした席
 * @param count 場の枚数
 * @param power 場の強さ (記録時点の実効序列での値)
 * @param stairs 場の役種 (階段=true)
 * @param revolution 記録時点の革命状態
 * @param jback 記録時点のJバック場
 */
public record PassEvent(int seat, int count, int power, boolean stairs,
        boolean revolution, boolean jback) {
}
