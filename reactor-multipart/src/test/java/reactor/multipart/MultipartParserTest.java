package reactor.multipart;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
public class MultipartParserTest {

	private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer iaculis metus id vestibulum nullam.\r\n";

	private static final String MUSPI_MEROL = ".mallun mulubitsev di sutem silucai regetnI .tile gnicsipida rutetcesnoc ,tema tis rolod muspi meroL\r\n";

	private static final int BUFFER_SIZE = 50;

	private ByteBufAllocator allocator = new PooledByteBufAllocator();


	@Test
	public void firefox() throws Exception {
		testBrowser("firefox.multipart", "---------------------------18399284482060392383840973206");
	}

	@Test
	public void chrome() throws Exception {
		testBrowser("chrome.multipart", "----WebKitFormBoundaryEveBLvRT65n21fwU");
	}

	@Test
	public void safari() throws Exception {
		testBrowser("safari.multipart", "----WebKitFormBoundaryG8fJ50opQOML0oGD");
	}

	private void testBrowser(String resource, String boundary) throws Exception {
		Path path = Paths.get(getClass().getResource(resource).toURI());
		Flux<ByteBuf> buffers = TestUtils.fromPath(path, BUFFER_SIZE, this.allocator);
		Flux<Part> result = MultipartParser.parse(buffers, boundary.getBytes(UTF_8));

		CountDownLatch latch = new CountDownLatch(5);
		StepVerifier.create(result)
				.consumeNextWith(part -> testPart(part, "text1", "a", latch)).as("text1")
				.consumeNextWith(part -> testPart(part, "text2", "b", latch)).as("text2")
				.consumeNextWith(part -> testPart(part, "file1", LOREM_IPSUM, latch)).as("file1")
				.consumeNextWith(part -> testPart(part, "file2", LOREM_IPSUM, latch)).as("file2-1")
				.consumeNextWith(part -> testPart(part, "file2", MUSPI_MEROL, latch)).as("file2-2")
				.verifyComplete();

		latch.await();

	}

	private void testPart(Part part, String expectedName, String expectedContents, CountDownLatch latch) {
			String name = getName(part);
			assertThat(name).isEqualTo(expectedName);

		Mono<String> content = TestUtils.join(part.content())
				.map(buf -> {
					String result = buf.toString(UTF_8);
					buf.release();
					return result;
				});

		content.subscribe(s -> {
					assertThat(s).isEqualTo(expectedContents);
				},
				throwable -> {
					throw new AssertionError(throwable.getMessage(), throwable);
				},
				latch::countDown);
	}

	private static String getName(Part part) {
		String contentDisposition = getHeaderValue(part, "Content-Disposition");
		if (contentDisposition != null) {
			int idx = contentDisposition.indexOf("name=\"");
			if (idx != -1) {
				int start = idx + 6;
				int end = contentDisposition.indexOf('"', start);
				if (end != -1) {
					return contentDisposition.substring(start, end);
				}
			}
		}
		return null;
	}

	private static String getHeaderValue(Part part, String name) {
		for (Map.Entry<String, String> header : part.headers()) {
			if (header.getKey().equalsIgnoreCase(name)) {
				return header.getValue();
			}
		}
		return null;
	}


	@Test
	public void noHeaders() throws Exception{
		Path path = Paths.get(getClass().getResource("part-no-header.multipart").toURI());
		Flux<ByteBuf> buffers = TestUtils.fromPath(path, BUFFER_SIZE, this.allocator);
		String boundary = "boundary";
		Flux<Part> result = MultipartParser.parse(buffers, boundary.getBytes(UTF_8));

		StepVerifier.create(result)
				.consumeNextWith(part -> {
					assertThat(part.headers()).isEmpty();
					part.content().subscribe(ReferenceCounted::release);
				})
				.verifyComplete();
	}

	@Test
	public void partNoEndBoundary() throws Exception {
		Path path = Paths.get(getClass().getResource("part-no-end-boundary.multipart").toURI());
		Flux<ByteBuf> buffers = TestUtils.fromPath(path, BUFFER_SIZE, this.allocator);
		String boundary = "boundary";
		Flux<Part> result = MultipartParser.parse(buffers, boundary.getBytes(UTF_8));

		StepVerifier.create(result)
				.consumeNextWith(part ->
					part.content().subscribe(ReferenceCounted::release)
				)
				.verifyComplete();
	}

}