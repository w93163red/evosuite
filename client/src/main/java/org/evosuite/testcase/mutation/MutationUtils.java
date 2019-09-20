package org.evosuite.testcase.mutation;

import org.evosuite.graphs.ddg.MethodEntry;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.statements.EntityWithParametersStatement;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class MutationUtils {
    private static final int parallelComputationThreshold = 50; // just arbitrarily picked
    private static final int binarySearchThreshold = 10;        // just arbitrarily picked

    /**
     * Returns a pseudorandom double value between 0.0 (inclusive) and the specified bound
     * (exclusive), using a random number generator that is isolated to the current thread. Using
     * such isolated generators in concurrent applications (such as this one) as opposed to
     * accessing the same shared instance of {@code java.util.Random} usually entails much less
     * overhead and contention.
     *
     * @param bound the upper bound (exclusive)
     * @return a pseudorandom double value between zero (inclusive) and the bound (exclusive)
     */
    public static double nextRandomDouble(final double bound) {
        return ThreadLocalRandom.current().nextDouble(bound);
    }

    /**
     * Returns a pseudorandom integer value between 0 (inclusive) and the specified bound
     * (exclusive), using a random number generator that is isolated to the current thread. Using
     * such isolated generators in concurrent applications (such as this one) as opposed to
     * accessing the same shared instance of {@code java.util.Random} usually entails much less
     * overhead and contention.
     *
     * @param bound the upper bound (exclusive)
     * @return a pseudorandom integer value between zero (inclusive) and the bound (exclusive)
     */
    public static int nextRandomInt(final int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * Performs a roulette wheel selection on the given collection of gaols. The probability of a
     * goal being selected is inversely proportional to the cyclomatic complexity of the target
     * executable (i.e., method or constructor) of that goal. This means that two executables with
     * the same cyclomatic complexity have the same probability of being selected, while an
     * executable that is twice as complex as another executable only has half the probability of
     * being selected.
     *
     * @param goals the goals on which to perform the selection
     * @return a goal chosen via biased-random selection
     */
    public static TestFitnessFunction rouletteWheelSelect(final Collection<TestFitnessFunction> goals) {
        /*
         * The collection of goals could be unordered (e.g., when it's a set). Still, the inner
         * workings of the roulette wheel selection require some arbitrary but fixed order. We
         * impose this order by converting the collection of goals to an array (arrays also offer
         * good performance and random access, which the algorithm also benefits from). The imposed
         * order, even though being arbitrary, has no impact on the outcome of the selection,
         * since it's fixed during the course of the selection.
         */
        final TestFitnessFunction[] gs = goals.toArray(new TestFitnessFunction[0]);

        // by construction, gs cannot be empty here, so no need to check for gs.length == 0

        if (gs.length == 1) {
            return gs[0];
        }

        if (gs.length == 2) {
            final int cc0 = gs[0].getCyclomaticComplexity();
            final int cc1 = gs[1].getCyclomaticComplexity();
            final int pivot = nextRandomInt(cc0 + cc1);
            return pivot < cc0 ? gs[1] : gs[0];
        }

        /*
         * The reciprocal of the cyclomatic complexity of a target method is directly proportional
         * to the probability of the corresponding goal being selected. The prefix sum of these
         * reciprocal values is used to determine the index of the selected goal later on.
         */
        final double[] prefixSums = reciprocalPrefixSum(gs);

        // We spin the roulette wheel and obtain a pivot point. This is the point on the wheel
        // where the roulette ball falls onto after having lost all of its momentum.
        final double sum = prefixSums[gs.length - 1];
        final double pivot = nextRandomDouble(sum);

        // Finds the pocket on the wheel where the pivot point is located in and converts it to an
        // array  index. This index corresponds to the selected goal.
        final int index = findIndex(prefixSums, pivot);

        return gs[index];
    }

    /**
     * Returns the prefix sums of the reciprocal cyclomatic complexities for the target methods of
     * the given goals. By construction, the resulting array is sorted.
     *
     * @param goals the goals whose target methods to consider
     * @return the prefix sums of the reciprocal cyclomatic complexities
     */
    private static double[] reciprocalPrefixSum(final TestFitnessFunction[] goals) {
        final double[] prefixSum;

        /*
         * For our applications a naive implementation such as the following one using floating-
         * point operations is sufficiently accurate and numerical stability is not an issue at all.
         * It turns out that the computation offsets don't accumulate enough to produce a
         * fundamentally flawed result, even when millions of coverage goals are considered.
         */

        final boolean parallelComputation = goals.length > parallelComputationThreshold;
        if (parallelComputation) {
            prefixSum = Arrays.stream(goals).parallel()
                    .mapToDouble(g -> 1d / g.getCyclomaticComplexity())
                    .toArray();
            Arrays.parallelPrefix(prefixSum, Double::sum);
        } else {
            prefixSum = new double[goals.length];
            prefixSum[0] = 1d / goals[0].getCyclomaticComplexity();
            for (int i = 1; i < goals.length; i++) {
                prefixSum[i] = prefixSum[i - 1] + 1d / goals[i].getCyclomaticComplexity();
            }
        }

        return prefixSum;
    }

    /**
     * Searches the given strictly sorted array for the specified key and returns the appropriate
     * index where the key is found. If the array does not contain the key, the insertion point of
     * the key (i.e. the index where it would be inserted) is returned instead.
     *
     * @param sortedArray the array to be searched (must be sorted and not contain duplicates)
     * @param key         the value to search for in the array
     * @return the index of the key or its insertion point if the key is not found
     */
    private static int findIndex(final double[] sortedArray, final double key) {
        final boolean binarySearch = sortedArray.length > binarySearchThreshold;
        if (binarySearch) {
            final int index = Arrays.binarySearch(sortedArray, key);
            return index < 0 ? ~index : index;
        } else { // linear search
            final int lastIndex = sortedArray.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                if (key < sortedArray[i]) { // the array is sorted and free of duplicates
                    return i;
                }
            }
            return lastIndex;
        }
    }

    public static MethodEntry toMethodEntry(final TestFitnessFunction f) {
        final String clazz = f.getTargetClassName();
        final String nameDesc = f.getTargetMethodName();
        return new MethodEntry(clazz, nameDesc);
    }

    public static MethodEntry toMethodEntry(final EntityWithParametersStatement stmt) {
        final String className = stmt.getDeclaringClassName();
        final String methodName = stmt.getMethodName();
        final String descriptor = stmt.getDescriptor();
        return new MethodEntry(className, methodName, descriptor);
    }
}
