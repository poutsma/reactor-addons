package reactor.multipart;

import java.util.Map;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

/**
 * @author Arjen Poutsma
 */
public final class  Part {

	private final Iterable<Map.Entry<String, String>> headers;

	private final Flux<ByteBuf> content;

	Part(Iterable<Map.Entry<String, String>> headers, Flux<ByteBuf> content) {
		this.headers = headers;
		this.content = content;
	}

	public Iterable<Map.Entry<String, String>> headers() {
		return this.headers;
	}

	public Flux<ByteBuf> content() {
		return this.content;
	}
}
