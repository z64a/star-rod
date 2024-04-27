package game.yay0;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;

import util.Logger;
import util.Priority;

public class Yay0EncodeHelper
{
	// all limits are inclusive
	private static final int MIN_LINK_LENGTH = 3;
	private static final int MAX_LINK_LENGTH = 273;
	private static final int MAX_OFFSET = 4096;

	private final byte[] source;
	private int bufferPosition = 0;

	private final Yay0Encoder encoder;
	private Collection<Encode> codeList = new LinkedList<>();
	private Deque<EncodeLink> linkQueue = new LinkedList<>();

	public Yay0EncodeHelper(byte[] src, boolean logUpates)
	{
		source = src;
		encoder = new Yay0Encoder(source.length);

		// determine how to encode the source
		while (bufferPosition < source.length || !linkQueue.isEmpty()) {
			if (logUpates && bufferPosition % 1024 == 0) {
				String progress = String.format("(%.1f%%)", 100.0f * bufferPosition / source.length);
				Logger.log("Compressing bytes... " + progress, Priority.UPDATE);
			}

			EncodeLink newLink = findPatternFrom(source, bufferPosition);
			if (newLink == null) {
				// trivial case, no optimizations applied
				if (linkQueue.size() == 1) {
					codeList.add(linkQueue.pollFirst());
					continue;
				}

				// sequence of links has ended, attempt to optimize them
				if (linkQueue.size() > 1) {
					optimizeLinks(false);
					continue;
				}

				// no links, just keep copying bytes
				codeList.add(new EncodeCopy(source[bufferPosition]));
				bufferPosition++;
			}
			else {
				// Nth link in a row (could be first), add it to the queue and keep reading
				linkQueue.addLast(newLink);
				bufferPosition += newLink.length;
			}
		}

		// ready to do encoding
		for (Encode e : codeList)
			e.exec(encoder);

		encoder.flush();
	}

	/**
	 * Uses a simple heuristic to optimize a series of links: compare the length and 'cost'
	 * in terms of encoded bytes of the original series with a revised series. The revised
	 * series copies the first byte in the sequence before checking for links.
	 * @param debug - prints debugging information to System.out
	 */
	private void optimizeLinks(boolean debug)
	{
		//	debug = linkQueue.size() > 125;

		int totalLength = 0;
		int totalBudget = 0;
		for (Encode e : linkQueue) {
			totalBudget += e.getBudgetCost();
			totalLength += e.getEncodeLength();
		}

		if (debug) {
			System.out.println(String.format("%4X: Optimizing %d links, total length = 0x%02X",
				bufferPosition - totalLength, linkQueue.size(), totalLength));
		}

		Deque<Encode> revisedQueue = new LinkedList<>();
		int revisedEncoded = bufferPosition - totalLength;
		int revisedBudget = 0;

		revisedQueue.add(new EncodeCopy(source[revisedEncoded]));
		revisedEncoded++;
		revisedBudget++;

		while (revisedBudget < totalBudget) {
			EncodeLink revisedLink = findPatternFrom(source, revisedEncoded);
			if (revisedLink == null)
				break;

			revisedQueue.add(revisedLink);
			revisedEncoded += revisedLink.getEncodeLength();
			revisedBudget += revisedLink.getBudgetCost();
		}

		int revisedLength = revisedEncoded - (bufferPosition - totalLength);

		if (debug) {
			System.out.print("      Old: ");
			for (Encode e : linkQueue)
				System.out.print(e + " ");
			System.out.println(" (" + totalBudget + " bytes, length " + totalLength + ")");

			System.out.print("      New: ");
			for (Encode e : revisedQueue)
				System.out.print(e + " ");
			System.out.println(" (" + revisedBudget + " bytes, length " + revisedLength + ")");
		}

		boolean better = (revisedBudget < totalBudget) && (totalLength == revisedLength);
		boolean longer = (revisedBudget <= totalBudget) && (totalLength < revisedLength); // <- maybe not quite right...

		if (debug) {
			System.out.println("      Better? " + better);
			System.out.println("      Longer? " + longer);
		}

		boolean useRecursive = true;

		if (better || longer) {
			if (debug)
				System.out.println("      Using REVISED encoding.");

			if (useRecursive) {

				Encode revision = revisedQueue.pollFirst();
				codeList.add(revision);
				bufferPosition += revision.getEncodeLength();

				revision = revisedQueue.pollFirst();
				codeList.add(revision);
				bufferPosition += revision.getEncodeLength();

				while (!linkQueue.isEmpty()) {
					Encode e = linkQueue.pollFirst();
					bufferPosition -= e.getEncodeLength();
				}
			}
			else {
				for (Encode e : revisedQueue) {
					codeList.add(e);
					bufferPosition += e.getEncodeLength();
				}

				while (!linkQueue.isEmpty()) {
					Encode e = linkQueue.pollFirst();
					bufferPosition -= e.getEncodeLength();
				}
			}
		}
		else {
			if (debug)
				System.out.println("      Using ORIGINAL encoding.");

			if (useRecursive) {
				codeList.add(linkQueue.pollFirst());

				while (!linkQueue.isEmpty()) {
					Encode e = linkQueue.pollFirst();
					bufferPosition -= e.getEncodeLength();
				}
			}
			else {
				codeList.add(linkQueue.pollFirst());

				while (!linkQueue.isEmpty()) {
					Encode e = linkQueue.pollFirst();
					bufferPosition -= e.getEncodeLength();
				}
			}
		}

		if (debug)
			System.out.println("");
	}

	/**
	 * Returns a link for the longest matching sequence that occurs in the buffer,
	 * including those which 'wrap around' the end of the buffer. If no suitable
	 * match is found, returns null. This can happen in three ways:
	 * (1) No matching pattern is found.
	 * (2) There are less than MIN_LINK_LENGTH bytes left in the source.
	 * (3) The buffer has read less than MIN_LINK_LENGTH bytes (special case of 1).
	 */
	private static EncodeLink findPatternFrom(byte[] source, int encoderBufferPosition)
	{
		int remainingBytes = source.length - encoderBufferPosition;

		// this check is probably unnecessary, but helpful for readable code
		if (remainingBytes < MIN_LINK_LENGTH)
			return null;

		// only search for pattens up to the maximum length
		int maxMatchLength = (remainingBytes > MAX_LINK_LENGTH) ? MAX_LINK_LENGTH : remainingBytes;

		// do not search further back than MAX_OFFSET, links cannot reach that far
		int minWindowStart = (encoderBufferPosition <= MAX_OFFSET) ? 0 : encoderBufferPosition - MAX_OFFSET;

		// record the best match
		int bestMatchLength = 0;
		int bestMatchStart = 0;

		for (int windowStart = minWindowStart; windowStart < encoderBufferPosition; windowStart++) // don't allow distance = 0
		{
			int matchingLength = 0;
			int windowPos = windowStart;

			byte nextSource, nextMatch;

			while (true) {
				// wrap the window
				if (windowPos >= encoderBufferPosition)
					windowPos = windowStart;

				nextSource = source[encoderBufferPosition + matchingLength];
				nextMatch = source[windowPos];

				if (nextMatch == nextSource) {
					matchingLength++;
					windowPos++;

					if (matchingLength > bestMatchLength) {
						bestMatchLength = matchingLength;
						bestMatchStart = windowStart;
					}
				}
				else
					break;

				if (matchingLength == maxMatchLength)
					break;
			}
		}

		if (bestMatchLength < MIN_LINK_LENGTH)
			return null;

		return new EncodeLink(bestMatchLength, encoderBufferPosition - bestMatchStart);
	}

	public byte[] getFile()
	{
		return encoder.getFile();
	}
}
