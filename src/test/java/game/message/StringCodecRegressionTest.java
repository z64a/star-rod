package game.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import app.input.InputFileException;

public class StringCodecRegressionTest
{
	@Test
	public void messageMustEndAndCannotContainTextAfterEnd()
	{
		InputFileException missing = assertThrows(InputFileException.class, () -> StringEncoder.encode("No terminator"));
		assertEquals("Message is missing [End].", missing.getMessage());

		InputFileException trailing = assertThrows(InputFileException.class, () -> StringEncoder.encode("[End]trailing"));
		assertTrue(trailing.getMessage().contains("Message has text after [End]."));

		assertDoesNotThrow(() -> StringEncoder.encode("[eNd] \n\t"));
	}

	@Test
	public void unsignedArgumentsRoundTrip()
	{
		assertRoundTrip("[Pause 200][End]", "[Pause 200]");
		assertRoundTrip("[Speed delay=200 chars=201][End]", "[Speed delay=200 chars=201]");
		assertRoundTrip("[Color 0xC8][End]", "[Color 0xC8]");
	}

	@Test
	public void volumePercentageRoundTripsEveryRawValue()
	{
		for (int rawValue = 0; rawValue <= 0xFF; rawValue++) {
			byte[] original = bytes(StringEncoder.encode(String.format("[Volume %d][End]", rawValue)));
			String markup = StringDecoder.toMarkup(original);
			byte[] encoded = bytes(StringEncoder.encode(markup));

			assertTrue(markup.contains("[Volume percent="), "Decoded raw volume " + rawValue + ": " + markup);
			assertArrayEquals(original, encoded, "Raw volume " + rawValue + " did not round trip through " + markup);
		}
	}

	@Test
	public void keyValueArgumentsAcceptDecimalsAndSignedValues()
	{
		assertDoesNotThrow(() -> StringEncoder.encode("[Volume percent=50.5][End]"));
		assertDoesNotThrow(() -> StringEncoder.encode("[CustomVoice soundIDs=-1,+2][End]"));
	}

	@Test
	public void setCursorRoundTripsAllThreeArguments()
	{
		assertRoundTrip("[SetCursorPos index=2 pos=200,201][End]", "[SetCursorPos index=2 pos=200,201]");
	}

	private static void assertRoundTrip(String source, String expectedTag)
	{
		byte[] original = bytes(StringEncoder.encode(source));
		String markup = StringDecoder.toMarkup(original);

		assertTrue(markup.contains(expectedTag), markup);
		assertArrayEquals(original, bytes(StringEncoder.encode(markup)));
	}

	private static byte[] bytes(ByteBuffer buffer)
	{
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}
}
