package com.routechain.simulation;

import java.util.Random;

public class SimulationMath {

    /**
     * Approximates a Poisson process random value using Knuth's algorithm.
     * Use with caution if lambda is large; for large lambda, use Gaussian approximation.
     */
    public static int nextPoisson(double lambda, Random rng) {
        if (lambda <= 0.0) return 0;
        
        // For large lambda, normal approximation is better to prevent slow loop
        if (lambda > 500) {
            double randVar = Math.max(0, Math.round(rng.nextGaussian() * Math.sqrt(lambda) + lambda));
            return (int) randVar;
        }

        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > L && p > 0);
        return k - 1;
    }

    /**
     * Negative Binomial using Gamma-Poisson mixture pattern (approximated for simplicity if pure Java Random).
     * @param targetMean The desired mean (lambda_z_t)
     * @param dispersion The dispersion parameter (k > 0). Smaller k means higher variance (more bursty).
     * @param rng Random instance
     */
    public static int nextNegativeBinomial(double targetMean, double dispersion, Random rng) {
        if (targetMean <= 0) return 0;
        if (dispersion <= 0) return nextPoisson(targetMean, rng); // Fallback to Poisson

        // For NegBin, we sample lambda from a Gamma distribution first.
        // Mean of gamma = alpha / beta
        // We set alpha = dispersion, beta = dispersion / targetMean
        double lambdaGamma = nextGamma(dispersion, dispersion / targetMean, rng);
        if (lambdaGamma <= 0) return 0;
        return nextPoisson(lambdaGamma, rng);
    }

    /**
     * Simple Marsaglia and Tsang’s Method for Gamma sampling.
     * Works well for alpha > 1. For alpha < 1, uses Johnk's generator trick.
     */
    private static double nextGamma(double alpha, double beta, Random rng) {
        if (alpha <= 0 || beta <= 0) return 0.0;
        
        double d, c, x, v, u;
        if (alpha < 1.0) {
            // For alpha < 1, use transformation alpha_new = alpha + 1
            // and multiply result by U^(1/alpha)
            return nextGamma(alpha + 1.0, beta, rng) * Math.pow(rng.nextDouble(), 1.0 / alpha);
        }

        d = alpha - 1.0 / 3.0;
        c = 1.0 / Math.sqrt(9.0 * d);

        while (true) {
            do {
                x = rng.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            
            v = v * v * v;
            u = rng.nextDouble();

            if (u < 1.0 - 0.0331 * x * x * x * x) {
                return (d * v) / beta;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return (d * v) / beta;
            }
        }
    }
}
