package com.izimi.eagent.brainstem.scheduler;

import java.util.HashMap;
import java.util.Map;

public class ReflexSatisfaction {

    // ── Default time thresholds (seconds) ──
    public static final double T_ACCEPT = 120;
    public static final double T_LIMIT = 600;
    public static final double T_MAX = 1800;

    // ── Default weights ──
    public static final double W_TIME = 0.3;
    public static final double W_SUCCESS = 0.7;

    // ── Domain-adaptive weight profiles per Perspective ──
    /** [w_time, w_risk (reserved), w_success, w_resource (reserved)] */
    public record DomainWeights(double wTime, double wRisk, double wSuccess, double wResource) {
        public static final DomainWeights DEFAULT = new DomainWeights(0.3, 0.0, 0.7, 0.0);
    }

    private static final Map<Perspective, DomainWeights> DOMAIN_WEIGHTS = new HashMap<>(Map.of(
        Perspective.SURVIVAL, new DomainWeights(0.2, 0.4, 0.4, 0.0),
        Perspective.TASK,     new DomainWeights(0.4, 0.0, 0.5, 0.1),
        Perspective.SOCIAL,   new DomainWeights(0.2, 0.0, 0.5, 0.3),
        Perspective.CURIOUS,  new DomainWeights(0.1, 0.0, 0.6, 0.3),
        Perspective.CAUTIOUS, new DomainWeights(0.3, 0.3, 0.3, 0.1)
    ));

    /** 根据 Perspective 获取领域自适应权重 */
    public static DomainWeights getWeights(Perspective perspective) {
        return DOMAIN_WEIGHTS.getOrDefault(perspective, DomainWeights.DEFAULT);
    }

    /** 运行时更新领域权重 (用于 /ai challenge tune) */
    public static void updateWeights(Perspective perspective, DomainWeights weights) {
        DOMAIN_WEIGHTS.put(perspective, weights);
    }

    // ── Per-atom estimated time (seconds) ──
    public static final double SECONDS_PER_ATOM = 2.0;

    /**
     * 三段折线时间满意度
     *
     * ≤ T_accept → S_high (满格)
     * T_accept ~ T_limit → 线性降到 S_low
     * T_limit ~ T_max → 线性降到 0
     * > T_max → 0
     */
    public static double timeScore(double estimatedSec, double T_accept, double T_limit, double T_max,
                                   double S_high, double S_low) {
        if (estimatedSec <= T_accept) {
            return S_high;
        } else if (estimatedSec <= T_limit) {
            double ratio = (estimatedSec - T_accept) / (T_limit - T_accept);
            return S_high - ratio * (S_high - S_low);
        } else if (estimatedSec <= T_max) {
            double ratio = (estimatedSec - T_limit) / (T_max - T_limit);
            return Math.max(0, S_low - ratio * S_low);
        } else {
            return 0.0;
        }
    }

    /** 默认阈值的时间满意度 */
    public static double timeScore(double estimatedSec) {
        return timeScore(estimatedSec, T_ACCEPT, T_LIMIT, T_MAX, 1.0, 0.3);
    }

    /**
     * 综合满意度 (加权和), 含时间缩放 + 风险 + 资源
     *
     * @param estimatedSec      预计耗时 (秒，外部已通过 timeScale 缩放)
     * @param reflexWeight      反射权重 (stw×0.7 + ltb×0.3)
     * @param atomProficiency   原子熟练度 (per-atom skill)
     * @param bayesianPosterior 贝叶斯后验 P(success|context)
     * @param decayFactor       时间衰减因子
     * @param wTime             timeScore 权重
     * @param wRisk             riskScore 权重
     * @param wSuccess          successScore 权重
     * @param wResource         resourceScore 权重
     * @param riskScore         风险评分 (0-1, 1=极安全)
     * @param resourceScore     资源评分 (0-1, 1=资源充足)
     */
    public static double compute(double estimatedSec, double reflexWeight, double atomProficiency,
                                   double bayesianPosterior, double decayFactor,
                                   double wTime, double wRisk, double wSuccess, double wResource,
                                   double riskScore, double resourceScore) {
        double tScore = timeScore(estimatedSec);
        double sScore = reflexWeight * atomProficiency * bayesianPosterior * decayFactor;
        return wTime * tScore + wRisk * riskScore + wSuccess * sScore + wResource * resourceScore;
    }

    /** 向后兼容: riskScore=1.0, resourceScore=1.0 */
    public static double compute(double estimatedSec, double reflexWeight, double atomProficiency,
                                   double bayesianPosterior, double decayFactor,
                                   double wTime, double wRisk, double wSuccess, double wResource) {
        return compute(estimatedSec, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor,
                wTime, wRisk, wSuccess, wResource, 1.0, 1.0);
    }

    /** 指定 timeScale 缩放 estimatedSec 后计算 (默认权重) */
    public static double computeWithScale(double rawEstimatedSec, double timeScale,
                                           double reflexWeight, double atomProficiency,
                                           double bayesianPosterior, double decayFactor) {
        return compute(rawEstimatedSec * timeScale, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, W_TIME, 0.0, W_SUCCESS, 0.0);
    }

    /** 指定 timeScale + 显式权重 */
    public static double computeWithScale(double rawEstimatedSec, double timeScale,
                                           double reflexWeight, double atomProficiency,
                                           double bayesianPosterior, double decayFactor,
                                           double wTime, double wRisk, double wSuccess, double wResource) {
        return compute(rawEstimatedSec * timeScale, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, wTime, wRisk, wSuccess, wResource);
    }

    /** 带风险/资源评分的 timeScale + 默认权重 */
    public static double computeWithScale(double rawEstimatedSec, double timeScale,
                                           double reflexWeight, double atomProficiency,
                                           double bayesianPosterior, double decayFactor,
                                           double riskScore, double resourceScore) {
        return compute(rawEstimatedSec * timeScale, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, W_TIME, 0.0, W_SUCCESS, 0.0,
                riskScore, resourceScore);
    }

    /** 使用默认权重的综合满意度 (timeScale=1.0) */
    public static double compute(double estimatedSec, double reflexWeight, double atomProficiency,
                                  double bayesianPosterior, double decayFactor) {
        return compute(estimatedSec, reflexWeight, atomProficiency, bayesianPosterior, decayFactor,
                W_TIME, 0.0, W_SUCCESS, 0.0);
    }

    /**
     * 使用领域自适应权重的综合满意度 (无时间缩放)
     */
    public static double computeForDomain(double estimatedSec, double reflexWeight, double atomProficiency,
                                           double bayesianPosterior, double decayFactor,
                                           Perspective perspective) {
        return computeForDomainWithScale(estimatedSec, 1.0, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, perspective);
    }

    /**
     * 使用领域自适应权重 + 时间缩放的综合满意度
     */
    public static double computeForDomainWithScale(double rawEstimatedSec, double timeScale,
                                                     double reflexWeight, double atomProficiency,
                                                     double bayesianPosterior, double decayFactor,
                                                     Perspective perspective) {
        DomainWeights w = getWeights(perspective);
        return compute(rawEstimatedSec * timeScale, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, w.wTime(), w.wRisk(), w.wSuccess(), w.wResource());
    }

    /** 带风险/资源评分的领域自适应 + 时间缩放 */
    public static double computeForDomainWithScale(double rawEstimatedSec, double timeScale,
                                                     double reflexWeight, double atomProficiency,
                                                     double bayesianPosterior, double decayFactor,
                                                     double riskScore, double resourceScore,
                                                     Perspective perspective) {
        DomainWeights w = getWeights(perspective);
        return compute(rawEstimatedSec * timeScale, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, w.wTime(), w.wRisk(), w.wSuccess(), w.wResource(),
                riskScore, resourceScore);
    }

    /** 从原子数估算总耗时 (秒) */
    public static double estimateSeconds(int atomCount) {
        return Math.max(1, atomCount) * SECONDS_PER_ATOM;
    }
}
