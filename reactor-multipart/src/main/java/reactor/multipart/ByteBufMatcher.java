package reactor.multipart;

import java.util.Arrays;
import java.util.Objects;

import io.netty.buffer.ByteBuf;

/**
 * @author Arjen Poutsma
 */
abstract class ByteBufMatcher {

	abstract int match(ByteBuf buf);

	abstract byte[] delimiter();

	abstract void reset();

	public static ByteBufMatcher matcher(byte[] delimiter) {
		Objects.requireNonNull(delimiter);
		if (delimiter.length == 0) {
			throw new IllegalArgumentException("Delimiter must not be empty");
		}
		return new KnuthMorrisPrattMatcher(delimiter);
	}

/*
	public static ByteBufMatcher matcher(byte[]... delimiters) {
		Objects.requireNonNull(delimiters);
		if (delimiters.length == 1) {
			return matcher(delimiters[0]);
		}
		else {
			ByteBufMatcher[] matchers = new ByteBufMatcher[delimiters.length];
			for (int i = 0; i < delimiters.length; i++) {
				matchers[i] = matcher(delimiters[i]);
			}
			return new CompositeMatcher(matchers);
		}
	}
*/

	private static class KnuthMorrisPrattMatcher extends ByteBufMatcher {

			private final byte[] delimiter;

			private final int[] table;

			private int matches = 0;


			public KnuthMorrisPrattMatcher(byte[] delimiter) {
				this.delimiter = Arrays.copyOf(delimiter, delimiter.length);
				this.table = longestSuffixPrefixTable(delimiter);
			}

			private static int[] longestSuffixPrefixTable(byte[] delimiter) {
				int[] result = new int[delimiter.length];
				result[0] = 0;
				for (int i = 1; i < delimiter.length; i++) {
					int j = result[i - 1];
					while (j > 0 && delimiter[i] != delimiter[j]) {
						j = result[j - 1];
					}
					if (delimiter[i] == delimiter[j]) {
						j++;
					}
					result[i] = j;
				}
				return result;
			}

			@Override
			public int match(ByteBuf buf) {
				for (int i = buf.readerIndex(); i < buf.writerIndex(); i++) {
					byte b = buf.getByte(i);

					while (this.matches > 0 && b != this.delimiter[this.matches]) {
						this.matches = this.table[this.matches - 1];
					}

					if (b == this.delimiter[this.matches]) {
						this.matches++;
						if (this.matches == this.delimiter.length) {
							reset();
							return i;
						}
					}
				}
				return -1;
			}

			@Override
			public byte[] delimiter() {
				return Arrays.copyOf(this.delimiter, this.delimiter.length);
			}

			@Override
			public void reset() {
				this.matches = 0;
			}

		}

/*
	private static class CompositeMatcher extends ByteBufMatcher {

		private static final byte[] NO_DELIMITER = new byte[0];

		private final ByteBufMatcher[] matchers;

		byte[] longestDelimiter = NO_DELIMITER;

		public CompositeMatcher(ByteBufMatcher[] matchers) {
			this.matchers = matchers;
		}

		@Override
		public int match(ByteBuf dataBuffer) {
			this.longestDelimiter = NO_DELIMITER;
			int bestEndIdx = Integer.MAX_VALUE;


			for (ByteBufMatcher matcher : this.matchers) {
				int endIdx = matcher.match(dataBuffer);
				if (endIdx != -1 &&
						endIdx <= bestEndIdx &&
						matcher.delimiter().length > this.longestDelimiter.length) {
					bestEndIdx = endIdx;
					this.longestDelimiter = matcher.delimiter();
				}
			}
			if (bestEndIdx == Integer.MAX_VALUE) {
				this.longestDelimiter = NO_DELIMITER;
				return -1;
			}
			else {
				reset();
				return bestEndIdx;
			}
		}

		@Override
		public byte[] delimiter() {
			if (this.longestDelimiter == NO_DELIMITER) {
				throw new IllegalStateException();
			}
			return this.longestDelimiter;
		}

		@Override
		public void reset() {
			for (ByteBufMatcher matcher : this.matchers) {
				matcher.reset();
			}
		}
	}
*/




}
