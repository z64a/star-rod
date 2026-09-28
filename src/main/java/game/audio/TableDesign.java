package game.audio;

import java.nio.ByteBuffer;

public class TableDesign
{
	public static class Table
	{
		public final int numPred;
		public final ByteBuffer buffer;

		public Table(int numPred, ByteBuffer buffer)
		{
			this.numPred = numPred;
			this.buffer = buffer;
		}
	}

	/**
	 * Computes a VADPCM predictor codebook from audio sample data using LPC analysis.
	 *
	 * <p>Performs the following steps:
	 * <ol>
	 *   <li>Computes autocorrelation vectors and matrices from each pair of sequential frames.</li>
	 *   <li>Solves the linear prediction equations via LU decomposition.</li>
	 *   <li>Transforms predictor coefficients into reflection coefficients for stability.</li>
	 *   <li>Clusters and refines predictors iteratively to form a codebook.</li>
	 *   <li>Outputs the quantized predictor coefficients ready for VADPCM encoding.</li>
	 * </ol>
	 *
	 * @param audioSamples   The raw input audio samples.
	 * @param order          The order of LPC prediction.
	 * @param pow 			 The final number of predictors will be 2^pow
	 * @return  {@code Table} containing the predictors.
	 */
	public static Table makeTable(short[] audioSamples, int order, int pow)
	{
		final int sampleCount = audioSamples.length;
		final int refineIteration = 2;
		final int frameSize = 16;
		final double threshold = 10.0;

		final int orderSize = order + 1;

		// we will process frames in overlapping pairs, the first half of this array holds samples
		// from the previous frame and the second half holds current frame samples
		short[] sampleBuffer = new short[frameSize * 2];

		double[][] framePredictors = new double[sampleCount][orderSize];
		double[][] autocorrMatrix = new double[orderSize][orderSize];
		double[][] codebook = new double[1 << pow][orderSize];
		double[] autocorr = new double[orderSize];
		double[] reflection = new double[orderSize];
		double[] perturbDelta = new double[orderSize];

		int numCollectedFrames = 0;
		int samplesPos = 0;

		// process audio into overlapping frame pairs
		while (sampleCount > samplesPos + 2 * frameSize) {
			System.arraycopy(audioSamples, samplesPos, sampleBuffer, frameSize, frameSize);

			// compute autocorrelation vector
			for (int lag = 0; lag <= order; lag++) {
				autocorr[lag] = 0.0;
				for (int j = 0; j < frameSize; j++) {
					// note: unusual sign for autocorrelation calculation
					autocorr[lag] -= sampleBuffer[frameSize + j - lag] * sampleBuffer[frameSize + j];
				}
			}

			if (Math.abs(autocorr[0]) > threshold) {
				// compute autocorrelation matrix
				for (int i = 1; i <= order; i++) {
					for (int j = 1; j <= order; j++) {
						autocorrMatrix[i][j] = 0.0;
						for (int k = 0; k < frameSize; k++) {
							autocorrMatrix[i][j] += sampleBuffer[frameSize + k - i] * sampleBuffer[frameSize + k - j];
						}
					}
				}

				int[] pivotIndices = new int[orderSize];

				// solve linear prediction equations using LU decomposition
				// in this case (the Yule-Walker equation) Ax = b has the meaning:
				// A = LU decomposition of autocorrelation matrix
				// x = predictor values (solving for these)
				// b = autocorrelation vector
				if (luDecomposition(autocorrMatrix, order, pivotIndices)) {
					// note: after evaluation, frameAutocorr now hold the prediction vector
					luSolve(autocorrMatrix, order, pivotIndices, autocorr);
					autocorr[0] = 1.0;

					// convert predictor coefficients to reflection coefficients and check stability
					if (predictorToReflection(autocorr, reflection, order) == 0) {
						framePredictors[numCollectedFrames][0] = 1.0;

						// clamp reflection coefficients for numerical stability
						for (int i = 1; i <= order; i++) {
							reflection[i] = clamp(reflection[i], -0.9999999999, 0.9999999999);
						}

						reflectionToPredictor(reflection, framePredictors[numCollectedFrames], order);
						numCollectedFrames++;
					}
				}
			}

			// store current frame as previous frame for next iteration
			for (int i = 0; i < frameSize; i++) {
				sampleBuffer[i] = sampleBuffer[i + frameSize];
			}

			samplesPos += frameSize;
		}

		// audio too short or too quiet to analyze gets predictors that predict silence
		if (numCollectedFrames == 0)
			return new Table(1 << pow, ByteBuffer.allocate(Short.BYTES * 8 * order * (1 << pow)));

		// compute averaged autocorrelation vector from collected data
		autocorr[0] = 1.0;
		for (int i = 1; i <= order; i++) {
			autocorr[i] = 0.0;
		}
		for (int i = 0; i < numCollectedFrames; i++) {
			predictorToAutocorr(framePredictors[i], codebook[0], order);
			for (int j = 1; j <= order; j++) {
				autocorr[j] += codebook[0][j];
			}
		}
		for (int i = 1; i <= order; i++) {
			autocorr[i] /= numCollectedFrames;
		}

		// derive initial reflection and predictor coefficients using Durbin recursion
		durbinRecursion(autocorr, order, reflection, codebook[0]);

		for (int i = 1; i <= order; i++) {
			reflection[i] = clamp(reflection[i], -0.9999999999, 0.9999999999);
		}

		reflectionToPredictor(reflection, codebook[0], order);

		// iteratively refine predictor codebook vectors
		int curBits = 0;
		while (curBits < pow) {
			for (int i = 0; i <= order; i++) {
				perturbDelta[i] = 0.0;
			}
			perturbDelta[order - 1] = -1.0;
			split(codebook, perturbDelta, order, (1 << curBits), 0.01);
			curBits++;
			refine(codebook, order, (1 << curBits), framePredictors, numCollectedFrames, refineIteration);
		}

		int nPredictors = (1 << curBits);

		ByteBuffer out = ByteBuffer.allocate(Short.BYTES * 8 * order * nPredictors);
		double[][] quantized = new double[8][order];

		// quantize predictor vectors into final codebook
		for (int p = 0; p < nPredictors; p++) {
			for (int i = 0; i < order; i++) {
				for (int j = 0; j < i; j++) {
					quantized[i][j] = 0.0;
				}
				for (int j = i; j < order; j++) {
					quantized[i][j] = -codebook[p][order - j + i];
				}
			}

			// zero-fill unused predictor coefficients
			// not necessary in Java, as arrays are zero-initialized
			for (int i = order; i < 8; i++) {
				for (int j = 0; j < order; j++) {
					quantized[i][j] = 0.0;
				}
			}

			for (int i = 1; i < 8; i++) {
				for (int j = 1; j <= order; j++) {
					if (i - j >= 0) {
						for (int k = 0; k < order; k++) {
							quantized[i][k] -= codebook[p][j] * quantized[i - j][k];
						}
					}
				}
			}

			for (int i = 0; i < order; i++) {
				for (int j = 0; j < 8; j++) {
					double fval = quantized[j][i] * 2048.0;
					int ival;

					if (fval < 0.0)
						ival = (int) (fval - 0.5);
					else
						ival = (int) (fval + 0.5);
					out.putShort((short) ival);
				}
			}
		}

		return new Table(nPredictors, out);
	}

	private static double clamp(double val, double min, double max)
	{
		return Math.max(min, Math.min(max, val));
	}

	/**
	 * Performs in-place LU decomposition on a given square matrix using partial pivoting,
	 * decomposing <code>matrix</code> into a lower triangular matrix L and an upper
	 * triangular matrix U, such that <code>LU = matrix</code>.
	 *
	 * @param matrix  the square matrix to be decomposed, modified in place
	 * @param order  size of matrix is this + 1
	 * @param pivotIndices  output array of length order + 1 that records the pivoting row swaps
	 * @return was the decomposition was successful? <code>false</code> if the matrix is singular
	 * or numerically unstable
	 */
	private static boolean luDecomposition(double[][] matrix, int order, int[] pivotIndices)
	{
		double[] scalingFactors = new double[order + 1];

		// compute implicit scaling factors for each row
		for (int row = 1; row <= order; row++) {
			double maxPivotValue = 0.0;
			for (int col = 1; col <= order; col++) {
				maxPivotValue = Math.max(maxPivotValue, Math.abs(matrix[row][col]));
			}
			if (maxPivotValue == 0.0)
				return false; // singular matrix
			scalingFactors[row] = 1.0 / maxPivotValue;
		}

		// perform LU decomposition with pivoting
		for (int col = 1; col <= order; col++) {
			// compute upper triangular part (U)
			for (int row = 1; row < col; row++) {
				double sum = matrix[row][col];
				for (int k = 1; k < row; k++) {
					sum -= matrix[row][k] * matrix[k][col];
				}
				matrix[row][col] = sum;
			}

			double maxPivotValue = 0.0;
			int pivotRow = 0;

			// find pivot row
			for (int row = col; row <= order; row++) {
				double sum = matrix[row][col];
				for (int k = 1; k < col; k++) {
					sum -= matrix[row][k] * matrix[k][col];
				}
				matrix[row][col] = sum;
				double curPivotValue = scalingFactors[row] * Math.abs(sum);
				if (curPivotValue >= maxPivotValue) {
					maxPivotValue = curPivotValue;
					pivotRow = row;
				}
			}

			// swap rows if necessary
			if (col != pivotRow) {
				for (int k = 1; k <= order; k++) {
					double swap = matrix[pivotRow][k];
					matrix[pivotRow][k] = matrix[col][k];
					matrix[col][k] = swap;
				}
				scalingFactors[pivotRow] = scalingFactors[col];
			}
			pivotIndices[col] = pivotRow;

			// if pivot is zero, the matrix is singular
			if (matrix[col][col] == 0.0)
				return false;

			// normalize lower triangular matrix
			if (col != order) {
				double invPivot = 1.0 / (matrix[col][col]);
				for (int i = col + 1; i <= order; i++)
					matrix[i][col] *= invPivot;
			}
		}

		// check numerical stability
		double min = 1e10;
		double max = 0.0;
		for (int i = 1; i <= order; i++) {
			double diagonal = Math.abs(matrix[i][i]);
			if (diagonal < min)
				min = diagonal;
			if (diagonal > max)
				max = diagonal;
		}

		return (min / max) >= 1e-10;
	}

	/**
	 * Solves a system of linear equations Ax=b using previously computed LU decomposition.
	 * Requires an LU-decomposed matrix and pivot indices.
	 *
	 * @param luMatrix  the LU-decomposed matrix from luDecomposition (modified matrix A)
	 * @param order  size of matrix is this + 1
	 * @param pivotIndices  pivot indices from decomposition
	 * @param solution  as input, right-hand-side vector b; as output, the solution (computed in-place)
	 */
	private static void luSolve(double[][] luMatrix, int order, int[] pivotIndices, double[] solution)
	{
		int firstNonZeroRow = 0;

		// forward substitution: solve Ly = Pb
		for (int row = 1; row <= order; row++) {
			int pivotIdx = pivotIndices[row];
			double sum = solution[pivotIdx];
			solution[pivotIdx] = solution[row];

			if (firstNonZeroRow != 0) {
				for (int col = firstNonZeroRow; col <= row - 1; col++)
					sum -= luMatrix[row][col] * solution[col];
			}
			else if (sum != 0) {
				firstNonZeroRow = row;
			}

			solution[row] = sum;
		}

		// backward substitution: solve Ux = y
		for (int row = order; row >= 1; row--) {
			double sum = solution[row];

			for (int col = row + 1; col <= order; col++)
				sum -= luMatrix[row][col] * solution[col];

			solution[row] = sum / luMatrix[row][row];
		}
	}

	/**
	* Converts LPC predictor coefficients (A-values) into reflection coefficients (K-values).
	* This function is the inverse of {@link #reflectionToPredictor}, transforming an autoregressive (AR) model
	* into its corresponding PARCOR (Partial Autocorrelation) coefficients.
	*
	* <p>
	* This algorithm implements the stepwise recursion for extracting reflection coefficients
	* from a given set of predictor coefficients in linear predictive coding (LPC).
	*
	* @param avals  The input array containing predictor coefficients (A-values).
	*				Must have at least {@code order + 1} elements.
	* @param kvals  The output array to store the computed reflection coefficients (K-values).
	*				Must have at least {@code order + 1} elements.
	* @param order  The prediction order (i.e., the number of coefficients to compute).
	* @return The number of unstable coefficients detected (i.e., |K[i]| > 1).
	*/
	private static int predictorToReflection(double[] avals, double[] kvals, int order)
	{
		double[] next = new double[order + 1];
		int unstableCount = 0;

		kvals[order] = avals[order];
		for (int i = order - 1; i >= 1; i--) {
			double ki = kvals[i + 1];
			double denom = 1.0 - (ki * ki);

			if (denom == 0.0)
				return 1; // instability

			for (int j = 0; j <= i; j++)
				next[j] = (avals[j] - avals[i + 1 - j] * ki) / denom;

			for (int j = 0; j <= i; j++)
				avals[j] = next[j];

			kvals[i] = next[i];

			// stability check: reflection coefficients must be within [-1,1]
			if (Math.abs(kvals[i]) > 1.0) {
				unstableCount++;
			}
		}

		return unstableCount;
	}

	/**
	 * Converts reflection coefficients (K-values) into LPC predictor coefficients (A-values).
	 * This transformation is part of the Levinson-Durbin recursion used in Linear Predictive Coding (LPC).
	 *
	 * <p>
	 * Given an array of reflection coefficients (PARCOR coefficients), this function recursively computes
	 * the predictor coefficients, which are used in linear prediction models.
	 *
	 * @param kvals  Input reflection coefficients (K-values).
	 * @param avals  Output predictor coefficients (A-values).
	 * @param order  Prediction order, arrays must be of size {@code order + 1}.
	 */
	private static void reflectionToPredictor(double[] kvals, double[] avals, int order)
	{
		avals[0] = 1.0;
		for (int i = 1; i <= order; i++) {
			avals[i] = kvals[i];
			for (int j = 1; j <= i - 1; j++) {
				avals[j] += avals[i - j] * avals[i];
			}
		}
	}

	/**
	 * Computes the autocorrelation sequence (R-values) from a given set of LPC predictor
	 * coefficients (A-values).
	 *
	 * @param avals  Input predictor coefficients (A-values).
	 * @param rvals  Output autocorrelation coefficients (R-values).
	 * @param order  Prediction order, arrays must be of size {@code order + 1}.
	 */
	private static void predictorToAutocorr(double[] avals, double[] autocorr, int order)
	{
		double[][] mat = new double[order + 1][order + 1];

		mat[order][0] = 1.0;
		for (int i = 1; i <= order; i++) {
			mat[order][i] = -avals[i];
		}

		for (int i = order; i >= 1; i--) {
			double div = 1.0 - mat[i][i] * mat[i][i];
			for (int j = 1; j <= i - 1; j++) {
				mat[i - 1][j] = (mat[i][i - j] * mat[i][i] + mat[i][j]) / div;
			}
		}

		autocorr[0] = 1.0;
		for (int lag = 1; lag <= order; lag++) {
			autocorr[lag] = 0.0;
			for (int j = 1; j <= lag; j++) {
				autocorr[lag] += mat[lag][j] * autocorr[lag - j];
			}
		}
	}

	/**
	 * Implements the Durbin recursion algorithm to compute reflection coefficients
	 * and prediction coefficients from an autocorrelation sequence.
	 *
	 * This algorithm is used in linear predictive coding (LPC) to estimate a
	 * signal's spectral envelope by solving the Yule-Walker equations.
	 *
	 * @param autocorr  The autocorrelation sequence of the input signal.
	 *                   Must have a length of at least {@code order + 1}.
	 * @param order  The prediction order (number of coefficients to compute).
	 * @param reflection (out) calculated reflection coefficients.
	 * @param prediction (out) calculated prediction coefficients.
	 * @return The number of unstable reflection coefficients (i.e., those with
	 *         absolute values greater than 1.0, indicating instability).
	 */
	private static int durbinRecursion(double[] autocorr, int order, double[] reflection, double[] prediction)
	{
		double sumPrediction = 0.0;
		double errorEnergy = autocorr[0];
		int unstableCount = 0;

		prediction[0] = 1.0;

		// Durbin's recursion
		for (int lag = 1; lag <= order; lag++) {
			sumPrediction = 0.0;

			// compute predictor estimate using past coefficients
			for (int j = 1; j <= lag - 1; j++) {
				sumPrediction += prediction[j] * autocorr[lag - j];
			}

			// compute reflection coefficient (ki)
			prediction[lag] = (errorEnergy > 0.0) ? -(autocorr[lag] + sumPrediction) / errorEnergy : 0.0;
			reflection[lag] = prediction[lag];

			// check stability (reflection coefficients must be within [-1,1])
			if (Math.abs(reflection[lag]) > 1.0) {
				unstableCount++;
			}

			// update predictor coefficients
			for (int j = 1; j < lag; j++) {
				prediction[j] += prediction[lag - j] * prediction[lag];
			}

			// update prediction error energy
			errorEnergy *= 1.0 - prediction[lag] * prediction[lag];
		}

		return unstableCount;
	}

	/**
	 * Splits a set of predictor coefficient vectors by duplicating them and applying
	 * an incremental perturbation defined by {@code delta} scaled by {@code scale}.
	 * Used during codebook refinement to increase predictor variety.
	 *
	 * @param codebook  The input/output array of predictor coefficient vectors to split.
	 * @param delta  The perturbation vector applied to split the predictors.
	 * @param order  Prediction order.
	 * @param npredictors  The number of predictor vectors to duplicate and perturb.
	 * @param scale  The (usually small) scale factor applied to the perturbation.
	 */
	private static void split(double[][] codebook, double[] delta, int order, int npredictors, double scale)
	{
		for (int i = 0; i < npredictors; i++) {
			for (int j = 0; j <= order; j++) {
				codebook[i + npredictors][j] = codebook[i][j] + delta[j] * scale;
			}
		}
	}

	/**
	 * Computes the spectral distance metric between two LPC models, used to measure
	 * how closely two predictor coefficient sets match in terms of spectral characteristics.
	 * This metric is used to cluster similar LPC frames during predictor codebook refinement.
	 *
	 * @param modelA  The first LPC predictor coefficient set.
	 * @param modelB  The second LPC predictor coefficient set.
	 * @param order  Prediction order.
	 * @return Spectral distance between the two LPC models (lower means more similar).
	 */
	private static double computeModelDistance(double[] modelA, double[] modelB, int order)
	{
		double[] autocorrA = new double[order + 1];
		double[] autocorrB = new double[order + 1];
		double distance;

		// get autocorrelation of modelB using predictor-to-autocorrelation conversion
		predictorToAutocorr(modelB, autocorrB, order);

		// compute autocorrelation of modelA from predictor coefficients
		for (int lag = 0; lag <= order; lag++) {
			autocorrA[lag] = 0.0;
			for (int j = 0; j <= order - lag; j++) {
				autocorrA[lag] += modelA[j] * modelA[lag + j];
			}
		}

		// compute the distance
		distance = autocorrA[0] * autocorrB[0];
		for (int i = 1; i <= order; i++) {
			distance += 2 * autocorrB[i] * autocorrA[i];
		}

		return distance;
	}

	/**
	 * Performs iterative refinement of LPC predictor codebook vectors, using vector quantization (VQ).
	 * This function adjusts the LPC predictor entries in the provided predictor table by minimizing the
	 * spectral distance between the predictors and the input data. It does this by clustering the input
	 * LPC frames around the best-matching predictor vectors, and recalculating predictor coefficients
	 * from these clusters.
	 *
	 * <p>The refinement steps are as follows:
	 * <ol>
	 *   <li>Each LPC vector in the input data is matched to the closest predictor vector in the codebook (by minimal model distance).</li>
	 *   <li>Clusters of matched LPC vectors are formed around each predictor.</li>
	 *   <li>For each cluster, an averaged autocorrelation (reflection) representation is computed.</li>
	 *   <li>From this averaged autocorrelation, new predictor coefficients are derived using the Durbin recursion.</li>
	 *   <li>These updated coefficients replace the original predictor vectors in the codebook.</li>
	 * </ol>
	 * These steps repeat for a specified number of iterations, progressively refining the predictor codebook.
	 *
	 * @param codebook  Predictor codebook (LPC coefficients) to be refined.
	 * @param order  Prediction order.
	 * @param npredictors  Number of predictors in the codebook.
	 * @param inputPredictors  LPC coefficient vectors derived from input audio frames.
	 * @param numInputVectors  Number of LPC vectors provided as input data.
	 * @param refineIters  Number of refinement iterations to perform.
	 */
	private static void refine(double[][] codebook, int order, int npredictors, double[][] inputPredictors, int numInputVectors, int refineIters)
	{
		// accumulated reflection coefficients per predictor
		double[][] avgReflectionCoeffs = new double[npredictors][order + 1];

		// number of matches per predictor
		int[] predictorMatches = new int[npredictors];

		for (int iter = 0; iter < refineIters; iter++) {
			// reset accumulators for this iteration
			for (int i = 0; i < npredictors; i++) {
				predictorMatches[i] = 0;
				for (int j = 0; j <= order; j++) {
					avgReflectionCoeffs[i][j] = 0.0;
				}
			}

			// temporary reflection coefficients for current vector
			double[] reflection = new double[order + 1];

			// cluster each LPC vector in the data to the nearest predictor
			for (int frame = 0; frame < numInputVectors; frame++) {
				double closestValue = Double.MAX_VALUE;
				int closestIndex = 0;

				// find predictor vector closest to current data vector
				for (int p = 0; p < npredictors; p++) {
					double dist = computeModelDistance(codebook[p], inputPredictors[frame], order);
					if (dist < closestValue) {
						closestValue = dist;
						closestIndex = p;
					}
				}

				// accumulate reflection coefficients of matched LPC vector
				predictorMatches[closestIndex]++;
				predictorToAutocorr(inputPredictors[frame], reflection, order);
				for (int j = 0; j <= order; j++) {
					avgReflectionCoeffs[closestIndex][j] += reflection[j];
				}
			}

			// compute the average reflection coefficients for each predictor's cluster
			for (int i = 0; i < npredictors; i++) {
				if (predictorMatches[i] > 0) {
					for (int j = 0; j <= order; j++) {
						avgReflectionCoeffs[i][j] /= predictorMatches[i];
					}
				}
			}

			// update predictor codebook vectors from averaged reflections
			for (int i = 0; i < npredictors; i++) {
				// convert averaged autocorrelation/reflection back into predictor coefficients
				durbinRecursion(avgReflectionCoeffs[i], order, reflection, codebook[i]);

				// clamp reflection coefficients to ensure numerical stability
				for (int j = 1; j <= order; j++) {
					reflection[j] = clamp(reflection[j], -0.9999999999, 0.9999999999);
				}

				// convert stabilized reflections into predictor coefficients
				reflectionToPredictor(reflection, codebook[i], order);
			}
		}
	}
}
