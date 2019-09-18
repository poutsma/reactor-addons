package reactor.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
public class ByteBufMatcherTest {

	@Test
	public void matcher() {
		ByteBuf foo = Unpooled.copiedBuffer("foo", UTF_8);
		ByteBuf bar = Unpooled.copiedBuffer("bar", UTF_8);

		byte[] delim = "ooba".getBytes(UTF_8);
		ByteBufMatcher matcher = ByteBufMatcher.matcher(delim);
		int result = matcher.match(foo);
		assertThat(result).isEqualTo(-1);
		result = matcher.match(bar);
		assertThat(result).isEqualTo(1);
	}

	@Test
	public void matcher2() {
		ByteBuf foo = Unpooled.copiedBuffer("fooobar", UTF_8);

		byte[] delims = "oo".getBytes(UTF_8);
		ByteBufMatcher matcher = ByteBufMatcher.matcher(delims);
		int result = matcher.match(foo);
		assertThat(result).isEqualTo(2);
		foo.readerIndex(2);
		result = matcher.match(foo);
		assertThat(result).isEqualTo(3);
		foo.readerIndex(3);
		result = matcher.match(foo);
		assertThat(result).isEqualTo(-1);
	}

/*
	@Test
	public void compositeMatcher() {
		ByteBuf foo = Unpooled.copiedBuffer("foo", UTF_8);
		ByteBuf bar = Unpooled.copiedBuffer("bar", UTF_8);

		byte[] delims1 = "ook".getBytes(UTF_8);
		byte[] delims2 = "oba".getBytes(UTF_8);
		ByteBufMatcher matcher = ByteBufMatcher.matcher(delims1, delims2);
		int result = matcher.match(foo);
		assertThat(result).isEqualTo(-1);
		result = matcher.match(bar);
		assertThat(result).isEqualTo(1);
		assertThat(matcher.delimiter()).isEqualTo(delims2);
	}
*/

}